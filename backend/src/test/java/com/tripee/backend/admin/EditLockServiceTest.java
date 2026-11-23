package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.EditLockRepository;
import com.tripee.backend.service.admin.EditLockService;
import com.tripee.backend.service.admin.AdminSession;
import com.tripee.backend.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("service")
class EditLockServiceTest {
  private final EditLockRepository repository = mock(EditLockRepository.class);
  private final TransactionTemplate transactions = mock(TransactionTemplate.class);
  private final Instant now = Instant.parse("2026-11-19T00:00:00Z");
  private final EditLockService service = new EditLockService(repository, transactions, Clock.fixed(now, ZoneOffset.UTC));

  @SuppressWarnings("unchecked")
  private void executeCallbacks() {
    when(transactions.execute(any())).thenAnswer(call ->
        ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
  }

  @Test
  void sessionExpiringWhileWaitingForCityLockCannotAcquireLease() {
    executeCallbacks();
    var clock = mock(Clock.class);
    when(clock.instant()).thenReturn(now);
    var guarded = new EditLockService(repository, transactions, clock);
    var session = new MockHttpSession();
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("7", "A", now));
    var editor = UUID.randomUUID();
    doAnswer(call -> {
      when(clock.instant()).thenReturn(now.plusSeconds(3600));
      return null;
    }).when(repository).lockCity(1);
    when(repository.find(1)).thenReturn(Optional.empty(), Optional.of(new EditLockRepository.Lock(
        7, "A", editor, "hash", now.plusSeconds(3900))));
    assertThatThrownBy(() -> guarded.acquire(1, 7, editor, session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SESSION_EXPIRED"));
    verify(repository, never()).save(anyLong(), anyLong(), any(), anyString(), any());
  }

  @Test
  void idleSessionCannotRenewAnOtherwiseValidLease() {
    executeCallbacks();
    var clock = mock(Clock.class);
    when(clock.instant()).thenReturn(now);
    var guarded = new EditLockService(repository, transactions, clock);
    var session = new MockHttpSession();
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("7", "A", now.minusSeconds(3599)));
    var editor = UUID.randomUUID();
    String[] savedHash = new String[1];
    doAnswer(call -> { savedHash[0] = call.getArgument(3); return null; })
        .when(repository).save(eq(1L), eq(7L), eq(editor), anyString(), eq(now));
    when(repository.find(1)).thenAnswer(call -> savedHash[0] == null ? Optional.empty()
        : Optional.of(new EditLockRepository.Lock(7, "A", editor, savedHash[0], now.plusSeconds(300))));
    var lease = guarded.acquire(1, 7, editor, session);
    // 잠금 유효 시간은 남았지만 요청이 DB 잠금을 기다리는 사이 비활동 세션이 만료됨.
    doAnswer(call -> { when(clock.instant()).thenReturn(now.plusSeconds(1)); return null; })
        .when(repository).lockCity(1);
    assertThatThrownBy(() -> guarded.heartbeat(1, 7, lease.leaseToken(), session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SESSION_EXPIRED"));
    verify(repository, never()).renew(anyLong(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"acquire", "heartbeat", "release", "write"})
  @SuppressWarnings("unchecked")
  void sessionInvalidatedWhileWaitingForCityLockReturnsUnauthorized(String operation) {
    executeCallbacks();
    doAnswer(call -> {
      ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>) call.getArgument(0))
          .accept(null);
      return null;
    }).when(transactions).executeWithoutResult(any());
    var session = new MockHttpSession();
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("7", "A", now));
    doAnswer(call -> { session.invalidate(); return null; }).when(repository).lockCity(1);
    when(repository.find(1)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> {
      switch (operation) {
        case "acquire" -> service.acquire(1, 7, UUID.randomUUID(), session);
        case "heartbeat" -> service.heartbeat(1, 7, "token", session);
        case "release" -> service.release(1, 7, "token", session);
        default -> service.withLease(1, 7, "token", session, () -> "saved");
      }
    }).isInstanceOfSatisfying(ApiException.class, e -> {
      assertThat(e.status().value()).isEqualTo(401);
      assertThat(e.code()).isEqualTo("SESSION_EXPIRED");
    });
    verify(repository, never()).save(anyLong(), anyLong(), any(), anyString(), any());
    verify(repository, never()).renew(anyLong(), any());
    verify(repository, never()).delete(anyLong());
  }

  @Test
  void activeLeaseBlocksEvenSameAdministrator() {
    executeCallbacks();
    var session = new MockHttpSession();
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("7", "A", now));
    when(repository.find(1)).thenReturn(Optional.of(new EditLockRepository.Lock(
        7, "A", UUID.randomUUID(), "hash", now.plusSeconds(1))));
    assertThatThrownBy(() -> service.acquire(1, 7, UUID.randomUUID(), session))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("EDIT_LOCK_HELD"));
    verify(repository, never()).save(anyLong(), anyLong(), any(), anyString(), any());
  }

  @Test
  void expiredLeaseAppearsUnlockedWithoutWriting() {
    executeCallbacks();
    when(repository.find(1)).thenReturn(Optional.of(new EditLockRepository.Lock(
        7, "A", UUID.randomUUID(), "hash", now)));
    var view = service.view(1);
    assertThat(view.isLocked()).isFalse();
    assertThat(view.owner()).isNull();
    verify(repository, never()).delete(anyLong());
  }

  @Test
  void tokenCannotBeUsedFromAnotherSessionAndExpiredTokenCannotBeRenewed() throws Exception {
    executeCallbacks();
    var originalSession = new MockHttpSession();
    var otherSession = new MockHttpSession();
    originalSession.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("7", "A", now));
    otherSession.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("7", "A", now));
    var editor = UUID.randomUUID();
    String[] savedHash = new String[1];
    doAnswer(call -> {
      savedHash[0] = call.getArgument(3);
      return null;
    }).when(repository).save(eq(1L), eq(7L), eq(editor), anyString(), eq(now));
    when(repository.find(1)).thenAnswer(call -> savedHash[0] == null ? Optional.empty()
        : Optional.of(new EditLockRepository.Lock(7, "A", editor, savedHash[0], now.plusSeconds(300))));

    var lease = service.acquire(1, 7, editor, originalSession);
    String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(lease.leaseToken().getBytes(StandardCharsets.UTF_8)));
    assertThat(savedHash[0]).isEqualTo(expectedHash).isNotEqualTo(lease.leaseToken());
    assertThatThrownBy(() -> service.heartbeat(1, 7, lease.leaseToken(), otherSession))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("EDIT_LOCK_NOT_OWNED"));
    when(repository.find(1)).thenReturn(Optional.of(new EditLockRepository.Lock(
        7, "A", editor, savedHash[0], now)));
    assertThatThrownBy(() -> service.heartbeat(1, 7, lease.leaseToken(), originalSession))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("EDIT_LOCK_EXPIRED"));
    verify(repository, never()).renew(anyLong(), any());
  }

  @Test
  void validOwnerCanRenewAndUseLeaseWhileOtherActorsAndTokensCannot() {
    executeCallbacks();
    var session = new MockHttpSession();
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("7", "A", now));
    var editor = UUID.randomUUID();
    String[] savedHash = new String[1];
    doAnswer(call -> { savedHash[0] = call.getArgument(3); return null; })
        .when(repository).save(eq(1L), eq(7L), eq(editor), anyString(), eq(now));
    when(repository.find(1)).thenAnswer(call -> savedHash[0] == null ? Optional.empty()
        : Optional.of(new EditLockRepository.Lock(7, "A", editor, savedHash[0], now.plusSeconds(300))));
    var lease = service.acquire(1, 7, editor, session);
    assertThat(service.withLease(1, 7, lease.leaseToken(), session, () -> "saved")).isEqualTo("saved");
    assertThat(service.heartbeat(1, 7, lease.leaseToken(), session).expiresAt()).isEqualTo(now.plusSeconds(300));
    verify(repository).renew(1, now);
    assertThatThrownBy(() -> service.withLease(1, 8, lease.leaseToken(), session, () -> "bad"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("EDIT_LOCK_NOT_OWNED"));
    assertThatThrownBy(() -> service.withLease(1, 7, "wrong", session, () -> "bad"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("EDIT_LOCK_NOT_OWNED"));
    session.removeAttribute(AdminSession.ATTRIBUTE);
    assertThatThrownBy(() -> service.withLease(1, 7, lease.leaseToken(), session, () -> "bad"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SESSION_EXPIRED"));
  }
}
