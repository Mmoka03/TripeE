package com.tripee.backend.repository.admin;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 게시본 전환과 감사 기록은 호출자의 도시 잠금 트랜잭션에 참여함. */
@Repository
@Profile("postgres")
class DraftPublicationStore {
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  DraftPublicationStore(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  long publish(long cityId, long actorId, JsonNode draft, JsonNode effectiveSafety) {
    long revision = Long.parseLong(draft.get("revisionId").asString());
    Long safetyId;
    if (!draft.get("safetyDraft").isNull()) {
      safetyId = Long.parseLong(draft.get("safetyDraft").get("revisionId").asString());
      jdbc.sql("""
          UPDATE safety_revision SET published_at=CURRENT_TIMESTAMP,published_by=:actor
          WHERE id=:id AND city_id=:city AND published_at IS NULL
          """).param("actor", actorId).param("id", safetyId).param("city", cityId).update();
    } else if (!effectiveSafety.get("revisionId").isNull()) {
      safetyId = Long.parseLong(effectiveSafety.get("revisionId").asString());
    } else {
      safetyId = jdbc.sql("""
          INSERT INTO safety_revision(city_id,review_state,created_by,published_by,published_at)
          VALUES (:city,'UNVERIFIED',:actor,:actor,CURRENT_TIMESTAMP) RETURNING id
          """).param("city", cityId).param("actor", actorId).query(Long.class).single();
    }
    jdbc.sql("""
        UPDATE city_revision SET draft_safety_revision_id=NULL,safety_revision_at_publish_id=:safety,
          published_at=CURRENT_TIMESTAMP,published_by=:actor
        WHERE id=:revision AND city_id=:city AND published_at IS NULL
        """).param("safety", safetyId).param("actor", actorId).param("revision", revision).param("city", cityId).update();
    jdbc.sql("""
        UPDATE city SET visibility='PUBLIC',published_revision_id=:revision,draft_revision_id=NULL,
          published_safety_revision_id=:safety WHERE id=:city
        """).param("revision", revision).param("safety", safetyId).param("city", cityId).update();
    jdbc.sql("""
        INSERT INTO audit_event(actor_id,city_ref_id,revision_ref_id,safety_revision_ref_id,action,after_data)
        VALUES (:actor,:city,:revision,:safety,'PUBLISH',
          jsonb_build_object('schemaVersion',1,'draft',CAST(:draft AS jsonb),'safety',CAST(:safetyView AS jsonb)))
        """).param("actor", actorId).param("city", cityId).param("revision", revision)
        .param("safety", safetyId).param("draft", json.writeValueAsString(draft))
        .param("safetyView", json.writeValueAsString(effectiveSafety)).update();
    jdbc.sql("DELETE FROM city_edit_lock WHERE city_id=:city").param("city", cityId).update();
    return revision;
  }

}
