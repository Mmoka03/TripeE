package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.EditLockRepository;
import com.tripee.backend.common.exception.ApiException;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("postgres")
public class EditLockService {
  private static final String BINDINGS = EditLockService.class.getName() + ".bindings";
  private final EditLockRepository locks;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public EditLockService(EditLockRepository locks, TransactionTemplate transactions, Clock clock) {
    this.locks = locks;
    this.transactions = transactions;
    this.clock = clock;
  }

  public View view(long cityId) {
    return transactions.execute(status -> {
      locks.lockCity(cityId);
      Instant now = clock.instant();
      var lock = locks.find(cityId).filter(item -> now.isBefore(item.expiresAt()));
      return lock.map(item -> new View(true, actor(item), item.editorId(), item.expiresAt(), now))
          .orElseGet(() -> new View(false, null, null, null, now));
    });
  }

  public Lease acquire(long cityId, long actorId, UUID editorId, HttpSession session) {
    synchronized (session) {
      byte[] bytes = new byte[32];
      random.nextBytes(bytes);
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      String hash = hash(token);
      Lease lease = transactions.execute(status -> {
        locks.lockCity(cityId);
        Instant now = clock.instant();
        AdminSession.requireActive(session, now);
        if (locks.find(cityId).filter(item -> now.isBefore(item.expiresAt())).isPresent()) {
          throw conflict("EDIT_LOCK_HELD", "다른 편집 창이 이 도시를 편집 중입니다.");
        }
        locks.save(cityId, actorId, editorId, hash, now);
        var current = locks.find(cityId).orElseThrow();
        return new Lease(Long.toString(cityId), actor(current), editorId, token, current.expiresAt(), now, 60, 300);
      });
      bindings(session).put(cityId, new Binding(hash, editorId));
      return lease;
    }
  }

  public Heartbeat heartbeat(long cityId, long actorId, String token, HttpSession session) {
    synchronized (session) {
      return transactions.execute(status -> {
        locks.lockCity(cityId);
        Instant now = clock.instant();
        AdminSession.requireActive(session, now);
        var current = requireOwner(cityId, actorId, token, session, now);
        Instant renewed = now.isAfter(current.expiresAt().minusSeconds(300))
            ? now : current.expiresAt().minusSeconds(300);
        locks.renew(cityId, renewed);
        return new Heartbeat(renewed.plusSeconds(300), now);
      });
    }
  }

  public void release(long cityId, long actorId, String token, HttpSession session) {
    synchronized (session) {
      transactions.executeWithoutResult(status -> {
        locks.lockCity(cityId);
        Instant now = clock.instant();
        AdminSession.requireActive(session, now);
        var lock = locks.find(cityId);
        if (lock.isEmpty() || !now.isBefore(lock.get().expiresAt())) return;
        requireOwner(cityId, actorId, token, session, now);
        locks.delete(cityId);
      });
      bindings(session).remove(cityId);
    }
  }

  public void releaseSession(HttpSession session) {
    synchronized (session) {
      var snapshot = new HashMap<>(bindings(session));
      transactions.executeWithoutResult(status -> snapshot.entrySet().stream()
          .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            locks.lockCityIfPresent(entry.getKey());
            locks.deleteIfTokenMatches(entry.getKey(), entry.getValue().hash());
          }));
      bindings(session).clear();
    }
  }

  // 도시 행 잠금과 세션 모니터를 유지하며 같은 트랜잭션에서 세션·편집권을 검사함.
  public <T> T withLease(long cityId, long actorId, String token, HttpSession session,
                         java.util.function.Supplier<T> operation) {
    synchronized (session) {
      return transactions.execute(status -> {
        locks.lockCity(cityId);
        Instant now = clock.instant();
        AdminSession.requireActive(session, now);
        requireOwner(cityId, actorId, token, session, now);
        return operation.get();
      });
    }
  }

  private EditLockRepository.Lock requireOwner(long cityId, long actorId, String token, HttpSession session, Instant now) {
    var current = locks.find(cityId).orElseThrow(() -> conflict("EDIT_LOCK_EXPIRED", "편집권이 만료되었습니다."));
    if (!now.isBefore(current.expiresAt())) throw conflict("EDIT_LOCK_EXPIRED", "편집권이 만료되었습니다.");
    Binding bound = bindings(session).get(cityId);
    if (token == null || token.isBlank() || bound == null || current.actorId() != actorId
        || !current.editorId().equals(bound.editorId()) || !current.tokenHash().equals(bound.hash())
        || !MessageDigest.isEqual(current.tokenHash().getBytes(StandardCharsets.US_ASCII),
            hash(token).getBytes(StandardCharsets.US_ASCII))) {
      throw conflict("EDIT_LOCK_NOT_OWNED", "이 세션의 편집권이 아닙니다.");
    }
    return current;
  }

  @SuppressWarnings("unchecked")
  private static Map<Long, Binding> bindings(HttpSession session) {
    var bindings = (Map<Long, Binding>) session.getAttribute(BINDINGS);
    if (bindings == null) {
      bindings = new HashMap<>();
      session.setAttribute(BINDINGS, bindings);
    }
    return bindings;
  }

  private static String hash(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static Actor actor(EditLockRepository.Lock lock) {
    return new Actor(Long.toString(lock.actorId()), lock.displayName());
  }

  private static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  private record Binding(String hash, UUID editorId) implements Serializable {}
  public record Actor(String id, String displayName) {}
  public record View(boolean isLocked, Actor owner, UUID editorInstanceId, Instant expiresAt, Instant serverTime) {}
  public record Lease(String cityId, Actor owner, UUID editorInstanceId, String leaseToken,
                      Instant expiresAt, Instant serverTime, int heartbeatIntervalSeconds, int leaseDurationSeconds) {}
  public record Heartbeat(Instant expiresAt, Instant serverTime) {}
}
