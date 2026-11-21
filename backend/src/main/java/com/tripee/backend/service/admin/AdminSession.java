package com.tripee.backend.service.admin;

import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import com.tripee.backend.common.exception.ApiException;
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

  public synchronized View view(Instant now, boolean activity) {
    if (expired(now)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "세션이 만료되었습니다.");
    }
    if (activity && now.isAfter(lastActivityAt)) lastActivityAt = now;
    return new View(admin, lastActivityAt, lastActivityAt.plus(Duration.ofHours(1)), now);
  }

  public record Actor(String id, String displayName) implements Serializable {}
  public record View(Actor admin, Instant lastActivityAt, Instant idleExpiresAt, Instant serverTime) {}
}
