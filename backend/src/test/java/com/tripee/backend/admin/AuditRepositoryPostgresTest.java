package com.tripee.backend.admin;

import com.tripee.backend.PostgresIntegrationTestBase;
import com.tripee.backend.repository.admin.AdminCityRepository;
import com.tripee.backend.repository.admin.AuditRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@Tag("repository")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TRIPEE_TEST_ISOLATED_DB", matches = "true")
@Transactional
class AuditRepositoryPostgresTest extends PostgresIntegrationTestBase {
  @Autowired AdminCityRepository cities;
  @Autowired AuditRepository audit;
  @Autowired JdbcClient jdbc;

  @Test
  void filtersPaginatesAndPreservesNullReferencesAndJsonTypes() {
    long[] ids = cities.create(1, 1, null, null, null, null, null);
    long first = Long.parseLong(audit.list(ids[0], null, 1).getFirst().get("id").asString());
    long second = jdbc.sql("""
        insert into audit_event(actor_id,city_ref_id,safety_revision_ref_id,action,after_data)
        values (2,:city,9007199254740993,'SAFETY_PUBLISH',
          '{"text":"한글","number":3,"enabled":true,"nullable":null,"items":[1,2]}'::jsonb) returning id
        """).param("city", ids[0]).query(Long.class).single();
    var latest = audit.list(ids[0], null, 1);
    assertThat(latest).hasSize(1);
    assertThat(latest.getFirst().get("id").asString()).isEqualTo(Long.toString(second));
    assertThat(latest.getFirst().has("afterData")).isFalse();
    assertThat(latest.getFirst().has("snapshotSchemaVersion")).isFalse();
    assertThat(audit.list(ids[0], second, 10)).extracting(n -> n.get("id").asString())
        .containsExactly(Long.toString(first));
    assertThat(audit.list(null, second, 100)).extracting(n -> n.get("id").asString()).contains(Long.toString(first));
    assertThat(audit.list(Long.MAX_VALUE, null, 100)).isEmpty();
    assertThat(audit.list(ids[0], first, 1)).isEmpty();
    var detail = audit.find(second).orElseThrow();
    assertThat(detail.at("/actor/id").asString()).isEqualTo("2");
    assertThat(detail.at("/actor/displayName").asString()).isEqualTo("B");
    assertThat(detail.get("revisionRefId").isNull()).isTrue();
    assertThat(detail.get("safetyRevisionRefId").asString()).isEqualTo("9007199254740993");
    assertThat(detail.get("beforeData").isNull()).isTrue();
    assertThat(detail.at("/afterData/text").asString()).isEqualTo("한글");
    assertThat(detail.at("/afterData/number").asInt()).isEqualTo(3);
    assertThat(detail.at("/afterData/enabled").asBoolean()).isTrue();
    assertThat(detail.at("/afterData/nullable").isNull()).isTrue();
    assertThat(detail.at("/afterData/items").size()).isEqualTo(2);
    assertThat(detail.get("snapshotSchemaVersion").asInt()).isEqualTo(1);
    assertThat(Instant.parse(detail.get("occurredAt").asString())).isNotNull();
    assertThat(audit.find(Long.MAX_VALUE)).isEmpty();
  }

  @Test
  void historicalPublicationSelectsLatestPublishSnapshotForExactCityAndRevision() {
    long[] ids = cities.create(1, 1, "교토", "Kyoto", "소개", BigDecimal.ZERO, BigDecimal.ZERO);
    assertThat(audit.historicalPublication(ids[0], ids[1])).isEmpty();
    RepositoryFixtures.publish(jdbc, ids[0], ids[1]);
    assertThat(audit.historicalPublication(ids[0], ids[1])).isEmpty();
    for (String marker : java.util.List.of("old", "latest")) {
      jdbc.sql("""
          insert into audit_event(actor_id,city_ref_id,revision_ref_id,action,after_data)
          values (1,:city,:revision,'PUBLISH',jsonb_build_object('marker',cast(:marker as text)))
          """).param("city", ids[0]).param("revision", ids[1]).param("marker", marker).update();
    }
    jdbc.sql("""
        insert into audit_event(actor_id,city_ref_id,revision_ref_id,action,before_data,after_data)
        values (1,:city,:revision,'UPDATE_DRAFT','{}','{"marker":"not a publication"}')
        """).param("city", ids[0]).param("revision", ids[1]).update();
    var history = audit.historicalPublication(ids[0], ids[1]).orElseThrow();
    assertThat(history.at("/snapshot/marker").asString()).isEqualTo("latest");
    assertThat(history.get("revisionNo").asInt()).isEqualTo(1);
    assertThat(history.at("/publishedBy/id").asString()).isEqualTo("1");
    assertThat(Instant.parse(history.get("publishedAt").asString())).isNotNull();
    assertThat(audit.historicalPublication(Long.MAX_VALUE, ids[1])).isEmpty();
    assertThat(audit.historicalPublication(ids[0], Long.MAX_VALUE)).isEmpty();
  }

  @Test
  void auditSurvivesPhysicalCityDeletionWithSnapshotIntact() {
    long[] ids = cities.create(1, 1, null, null, null, null, null);
    cities.delete(ids[0], 1);
    var event = audit.list(ids[0], null, 1).getFirst();
    var detail = audit.find(Long.parseLong(event.get("id").asString())).orElseThrow();
    assertThat(detail.get("action").asString()).isEqualTo("DELETE");
    assertThat(detail.get("afterData").isNull()).isTrue();
    assertThat(detail.at("/beforeData/city/id").asLong()).isEqualTo(ids[0]);
    assertThat(detail.at("/beforeData/revisions").size()).isEqualTo(1);
    assertThat(audit.historicalPublication(ids[0], ids[1])).isEmpty();
  }
}
