package com.tripee.backend.admin;

import com.tripee.backend.service.admin.AdminSession;
import com.tripee.backend.service.admin.EditLockService;
import com.tripee.backend.common.exception.ApiException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@Tag("integration")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TRIPEE_TEST_ISOLATED_DB", matches = "true")
class EditLockConcurrencyPostgresTest extends com.tripee.backend.PostgresIntegrationTestBase {
  @Autowired EditLockService locks;
  @Autowired JdbcClient jdbc;

  @Test
  void releasingASessionDoesNotReleaseAnotherSessionsLock() {
    long cityA = jdbc.sql("insert into city(country_id) values (1) returning id").query(Long.class).single();
    long cityB = jdbc.sql("insert into city(country_id) values (1) returning id").query(Long.class).single();
    var sessionA = new MockHttpSession();
    var sessionB = new MockHttpSession();
    sessionA.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("1", "A", Instant.now()));
    sessionB.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("2", "B", Instant.now()));
    var leaseA = locks.acquire(cityA, 1, UUID.randomUUID(), sessionA);
    var leaseB = locks.acquire(cityB, 2, UUID.randomUUID(), sessionB);
    assertThat(locks.view(cityA).isLocked()).isTrue();
    locks.releaseSession(sessionA);
    assertThat(locks.view(cityA).isLocked()).isFalse();
    assertThat(locks.view(cityB).isLocked()).isTrue();
    assertThat(locks.heartbeat(cityB, 2, leaseB.leaseToken(), sessionB)).isNotNull();
    assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> locks.heartbeat(cityA, 1, leaseA.leaseToken(), sessionA)))
        .isInstanceOf(ApiException.class);
    locks.release(cityB, 2, leaseB.leaseToken(), sessionB);
    assertThat(locks.view(cityB).isLocked()).isFalse();
  }

  @Test
  void twoAdministratorsCompetingForTheSameCityHaveExactlyOneWinner() throws Exception {
    long cityId = Long.parseLong(System.getenv("TRIPEE_TEST_CITY_ID"));
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> editorA = attempt(cityId, 1, ready, start);
      Callable<Boolean> editorB = attempt(cityId, 2, ready, start);
      var a = pool.submit(editorA);
      var b = pool.submit(editorB);
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(locks.view(cityId).isLocked()).isTrue();
    } finally {
      pool.shutdownNow();
    }
  }

  private Callable<Boolean> attempt(long cityId, long actor, CountDownLatch ready, CountDownLatch start) {
    return () -> {
      var session = new MockHttpSession();
      session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession(Long.toString(actor), "test", Instant.now()));
      ready.countDown();
      if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent start timed out");
      try {
        locks.acquire(cityId, actor, UUID.randomUUID(), session);
        return true;
      } catch (ApiException conflict) {
        assertThat(conflict.code()).isEqualTo("EDIT_LOCK_HELD");
        return false;
      }
    };
  }
}
