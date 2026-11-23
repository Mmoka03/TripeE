package com.tripee.backend.repository.admin;

import com.tripee.backend.common.exception.ApiException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class EditLockRepository {
  private final JdbcClient jdbc;

  public EditLockRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void lockCity(long cityId) {
    boolean exists = jdbc.sql("SELECT id FROM city WHERE id = :id FOR UPDATE")
        .param("id", cityId).query(Long.class).optional().isPresent();
    if (!exists) throw new ApiException(HttpStatus.NOT_FOUND, "CITY_NOT_FOUND", "도시를 찾을 수 없어요.");
  }

  public void lockCityIfPresent(long cityId) {
    jdbc.sql("SELECT id FROM city WHERE id = :id FOR UPDATE")
        .param("id", cityId).query(Long.class).optional();
  }

  public void deleteIfTokenMatches(long cityId, String hash) {
    jdbc.sql("DELETE FROM city_edit_lock WHERE city_id = :id AND lease_token_hash = :hash")
        .param("id", cityId).param("hash", hash).update();
  }

  public Optional<Lock> find(long cityId) {
    return jdbc.sql("""
        SELECT l.owner_admin_id, a.display_name, l.editor_instance_id, l.lease_token_hash, l.expires_at
        FROM city_edit_lock l JOIN admin_account a ON a.id = l.owner_admin_id
        WHERE l.city_id = :id
        """).param("id", cityId).query((r, n) -> new Lock(r.getLong("owner_admin_id"),
            r.getString("display_name"), r.getObject("editor_instance_id", UUID.class),
            r.getString("lease_token_hash"), r.getObject("expires_at", OffsetDateTime.class).toInstant())).optional();
  }

  public void save(long cityId, long actorId, UUID editorId, String tokenHash, Instant now) {
    jdbc.sql("""
        INSERT INTO city_edit_lock(city_id, owner_admin_id, editor_instance_id, lease_token_hash,
          acquired_at, last_heartbeat_at, expires_at)
        VALUES (:city, :actor, :editor, :hash, :now, :now, :expires)
        ON CONFLICT (city_id) DO UPDATE SET owner_admin_id = EXCLUDED.owner_admin_id,
          editor_instance_id = EXCLUDED.editor_instance_id, lease_token_hash = EXCLUDED.lease_token_hash,
          acquired_at = EXCLUDED.acquired_at, last_heartbeat_at = EXCLUDED.last_heartbeat_at,
          expires_at = EXCLUDED.expires_at
        """).param("city", cityId).param("actor", actorId).param("editor", editorId)
        .param("hash", tokenHash).param("now", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
        .param("expires", OffsetDateTime.ofInstant(now.plusSeconds(300), java.time.ZoneOffset.UTC)).update();
  }

  public void renew(long cityId, Instant now) {
    jdbc.sql("""
        UPDATE city_edit_lock SET last_heartbeat_at = :now, expires_at = :expires WHERE city_id = :id
        """).param("id", cityId).param("now", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
        .param("expires", OffsetDateTime.ofInstant(now.plusSeconds(300), java.time.ZoneOffset.UTC)).update();
  }

  public void delete(long cityId) {
    jdbc.sql("DELETE FROM city_edit_lock WHERE city_id = :id").param("id", cityId).update();
  }

  public record Lock(long actorId, String displayName, UUID editorId, String tokenHash, Instant expiresAt) {}
}
