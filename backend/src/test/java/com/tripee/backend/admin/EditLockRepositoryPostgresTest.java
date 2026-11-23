package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.EditLockRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("repository")
@Tag("postgres")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Sql(statements = {
    "CREATE TEMP TABLE city AS SELECT id FROM tripee.city WITH NO DATA",
    "CREATE UNIQUE INDEX ON pg_temp.city (id)",
    "CREATE TEMP TABLE admin_account AS SELECT id, display_name FROM tripee.admin_account WITH NO DATA",
    "CREATE TEMP TABLE city_edit_lock AS SELECT * FROM tripee.city_edit_lock WITH NO DATA",
    "CREATE UNIQUE INDEX ON pg_temp.city_edit_lock (city_id)",
    "INSERT INTO pg_temp.city VALUES (1)",
    "INSERT INTO pg_temp.admin_account VALUES (7, 'A')"
})
class EditLockRepositoryPostgresTest extends com.tripee.backend.PostgresIntegrationTestBase {
  @Autowired EditLockRepository locks;

  @Test
  void storesAndReleasesTheHashedLeaseUsingServerTimestamps() {
    var editor = UUID.randomUUID();
    var now = Instant.parse("2026-11-19T00:00:00Z");
    locks.lockCity(1);
    locks.save(1, 7, editor, "a".repeat(64), now);
    assertThat(locks.find(1)).get().satisfies(lock -> {
      assertThat(lock.actorId()).isEqualTo(7);
      assertThat(lock.editorId()).isEqualTo(editor);
      assertThat(lock.expiresAt()).isEqualTo(now.plusSeconds(300));
    });
    locks.renew(1, now.plusSeconds(60));
    assertThat(locks.find(1).orElseThrow().expiresAt()).isEqualTo(now.plusSeconds(360));
    locks.deleteIfTokenMatches(1, "b".repeat(64));
    assertThat(locks.find(1)).isPresent();
    locks.deleteIfTokenMatches(1, "a".repeat(64));
    assertThat(locks.find(1)).isEmpty();
  }
}
