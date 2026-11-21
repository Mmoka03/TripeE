package com.tripee.backend.admin;

import com.tripee.backend.repository.admin.SourceRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@Tag("integration")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TRIPEE_TEST_ISOLATED_DB", matches = "true")
class JpaJdbcTransactionPostgresTest extends com.tripee.backend.PostgresIntegrationTestBase {
  @Autowired SourceRepository sources;
  @Autowired com.tripee.backend.repository.admin.AdminCityRepository cities;
  @Autowired com.tripee.backend.repository.admin.PhotoRepository photos;
  @Autowired com.tripee.backend.repository.admin.AuditRepository audit;
  @Autowired com.tripee.backend.repository.admin.PhotoCleanupRepository cleanup;
  @Autowired JdbcClient jdbc;
  @Autowired TransactionTemplate transactions;

  @Test
  void jpaInsertAndJdbcReadShareTransactionAndRollbackTogether() {
    String id = transactions.execute(status -> {
      var created = sources.create("https://example.org/transaction-test", "트랜잭션 검증", null);
      assertThat(created.createdAt()).isNotNull();
      assertThat(jdbc.sql("select count(*) from source_document where id = :id")
          .param("id", Long.parseLong(created.id())).query(Integer.class).single()).isEqualTo(1);
      status.setRollbackOnly();
      return created.id();
    });
    assertThat(sources.find(Long.parseLong(id))).isEmpty();
  }

  @Test
  void jdbcCityQuerydslReadsJpaPhotoAndAuditRollbackTogether() {
    long[] ids = transactions.execute(status -> {
      long city = cities.create(1, 1, null, null, null, null, null)[0];
      assertThat(cities.find(city).id()).isEqualTo(Long.toString(city));
      long photo = Long.parseLong(photos.insert(city, "rollback.png", "https://example.org/photo",
          null, null, null, null, false).id());
      assertThat(jdbc.sql("select count(*) from photo_asset where id=:id")
          .param("id", photo).query(Integer.class).single()).isEqualTo(1);
      long event = Long.parseLong(audit.list(city, null, 1).getFirst().get("id").asString());
      status.setRollbackOnly();
      return new long[]{city, photo, event};
    });
    assertThat(jdbc.sql("select count(*) from city where id=:id").param("id", ids[0])
        .query(Integer.class).single()).isZero();
    assertThat(photos.storageKey(ids[1], true)).isEmpty();
    assertThat(audit.find(ids[2])).isEmpty();
  }

  @Test
  void mixedJpaPhotoInsertJdbcCityDeleteAndCleanupAreAtomic() {
    long[] ids = transactions.execute(status -> {
      long city = cities.create(1, 1, null, null, null, null, null)[0];
      long photo = Long.parseLong(photos.insert(city, "delete-in-transaction.png", "https://example.org/photo",
          null, null, null, null, false).id());
      cities.delete(city, 1);
      assertThat(photos.storageKey(photo, true)).isEmpty();
      assertThat(cleanup.pending()).contains("delete-in-transaction.png");
      status.setRollbackOnly();
      return new long[]{city, photo};
    });
    assertThat(photos.storageKey(ids[1], true)).isEmpty();
    assertThat(cleanup.pending()).doesNotContain("delete-in-transaction.png");
    assertThat(audit.list(ids[0], null, 10)).isEmpty();
  }
}
