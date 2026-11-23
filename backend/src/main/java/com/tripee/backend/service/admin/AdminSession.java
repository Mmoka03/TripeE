package com.tripee.backend.service.admin;

import com.tripee.backend.common.exception.ApiException;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;

public final class AdminSession implements Serializable {
  public static final String ATTRIBUTE = AdminSession.class.getName();
  private final Actor admin;
  private Instant lastActivityAt;

  public AdminSession(String id, String displayName, Instant now) {
    this.admin = new Actor(id, displayName);
    this.lastActivityAt = now;
  }

  public synchronized boolean expired(Instant now) {
    return !now.isBefore(lastActivityAt.plus(Duration.ofHours(1)));
  }

  // 요청 진입 후 DB 잠금을 기다리는 동안에도 세션이 만료되거나 무효화될 수 있음.
  public static void requireActive(HttpSession session, Instant now) {
    Object value;
    try {
      value = session == null ? null : session.getAttribute(ATTRIBUTE);
    } catch (IllegalStateException invalidated) {
      throw expiredSession();
    }
    if (!(value instanceof AdminSession state) || state.expired(now)) throw expiredSession();
  }

  public synchronized View view(Instant now, boolean activity) {
    if (expired(now)) {
      throw expiredSession();
    }
    if (activity && now.isAfter(lastActivityAt)) lastActivityAt = now;
    return new View(admin, lastActivityAt, lastActivityAt.plus(Duration.ofHours(1)), now);
  }

  private static ApiException expiredSession() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "세션이 만료되었습니다.");
  }

  public record Actor(String id, String displayName) implements Serializable {}
  public record View(Actor admin, Instant lastActivityAt, Instant idleExpiresAt, Instant serverTime) {}
}
