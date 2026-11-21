package com.tripee.backend.admin;

import com.tripee.backend.PostgresIntegrationTestBase;
import com.tripee.backend.repository.admin.AdminCityRepository;
import com.tripee.backend.repository.admin.PhotoRepository;
import com.tripee.backend.repository.admin.PhotoCleanupRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("postgres")
@Tag("repository")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TRIPEE_TEST_ISOLATED_DB", matches = "true")
@Transactional
class PhotoRepositoryPostgresTest extends PostgresIntegrationTestBase {
  @Autowired PhotoRepository photos;
  @Autowired PhotoCleanupRepository cleanup;
  @Autowired AdminCityRepository cities;
  @Autowired JdbcClient jdbc;

  @Test
  void savesNullableMetadataAndUsesDatabaseTransactionTimeForConfirmation() {
    long city = cities.create(1, 1, null, null, null, null, null)[0];
    var first = photos.insert(city, "unconfirmed.png", "https://example.org/a", null, null, null, null, false);
    var second = photos.insert(city, "confirmed.png", "https://example.org/b", "작가", "CC0",
        "https://example.org/license", "출처 확인", true);
    var dbTime = jdbc.sql("select current_timestamp").query(OffsetDateTime.class).single().toInstant();
    assertThat(first.rightsCheckedAt()).isNull();
    assertThat(first.creatorName()).isNull();
    assertThat(first.licenseName()).isNull();
    assertThat(second.rightsCheckedAt()).isEqualTo(dbTime);
    assertThat(second.imageUrl()).isEqualTo("/api/v1/media/photo-assets/" + second.id());
    assertThat(photos.list(city)).containsExactly(first, second);
    assertThat(photos.list(Long.MAX_VALUE)).isEmpty();
    assertThat(photos.storageKey(Long.MAX_VALUE, true)).isEmpty();
  }

  @Test
  void publicAccessRequiresPublishedLinkAndDatabaseRejectsCrossCityLinks() {
    long[] ids = cities.create(1, 1, "교토", "Kyoto", "소개", BigDecimal.ZERO, BigDecimal.ZERO);
    var photo = photos.insert(ids[0], "published.png", "https://example.org/a", null, "CC0", null, null, true);
    long id = Long.parseLong(photo.id());
    assertThat(photos.storageKey(id, true)).contains("published.png");
    assertThat(photos.storageKey(id, false)).isEmpty();
    jdbc.sql("insert into revision_photo(revision_id,photo_asset_id,display_order) values (:revision,:photo,1)")
        .param("revision", ids[1]).param("photo", id).update();
    assertThat(photos.storageKey(id, false)).isEmpty();
    RepositoryFixtures.publish(jdbc, ids[0], ids[1]);
    assertThat(photos.storageKey(id, false)).contains("published.png");
    var unlinked = photos.insert(ids[0], "unlinked.png", "https://example.org/b", null, null, null, null, false);
    assertThat(photos.storageKey(Long.parseLong(unlinked.id()), false)).isEmpty();
    long otherCity = cities.create(1, 1, null, null, null, null, null)[0];
    var foreign = photos.insert(otherCity, "other-city.png", "https://example.org/c", null, null, null, null, false);
    long foreignId = Long.parseLong(foreign.id());
    assertThat(photos.storageKey(foreignId, false)).isEmpty();
    assertThat(photos.storageKey(foreignId, true)).contains("other-city.png");
    cities.changeVisibility(ids[0], 1, "PREPARING", cities.find(ids[0]));
    assertThat(photos.storageKey(id, false)).isEmpty();
    assertThat(photos.storageKey(id, true)).contains("published.png");
    // DB가 잘못된 소유 관계 자체를 거부함. 실패 후 테스트 트랜잭션은 롤백됨.
    assertThatThrownBy(() -> jdbc.sql("""
        insert into revision_photo(revision_id,photo_asset_id,display_order) values (:revision,:photo,2)
        """).param("revision", ids[1]).param("photo", foreignId).update())
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
        .hasMessageContaining("photo asset belongs to a different city");
  }

  @Test
  void cleanupExcludesReferencedFilesLimitsBatchAndDeletesOnlyCompletedKey() {
    long city = cities.create(1, 1, null, null, null, null, null)[0];
    photos.insert(city, "referenced.png", "https://example.org/a", null, null, null, null, false);
    jdbc.sql("insert into photo_cleanup_job(storage_key) values ('referenced.png')").update();
    jdbc.sql("""
        insert into photo_cleanup_job(storage_key,created_at)
        select 'pending-' || n || '.png', current_timestamp + n * interval '1 second'
        from generate_series(1,101) n
        """).update();
    var batch = cleanup.pending();
    assertThat(batch).hasSize(100).startsWith("pending-1.png").endsWith("pending-100.png")
        .doesNotContain("referenced.png", "pending-101.png");
    cleanup.completed("pending-1.png");
    cleanup.completed("missing.png");
    assertThat(cleanup.pending()).hasSize(100).startsWith("pending-2.png").endsWith("pending-101.png");
    assertThat(jdbc.sql("select count(*) from photo_cleanup_job where storage_key='referenced.png'")
        .query(Integer.class).single()).isEqualTo(1);
  }
}
