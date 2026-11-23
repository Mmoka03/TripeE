package com.tripee.backend.repository.admin;

import com.tripee.backend.common.exception.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 안전정보 버전과 출처 변경을 기존 도시 트랜잭션 안에서 처리함. */
@Repository
@Profile("postgres")
class DraftSafetyStore {
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  DraftSafetyStore(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  void replaceSafety(long cityId, long revision, long actorId, JsonNode input) {
    var state = jdbc.sql("""
        SELECT c.published_safety_revision_id, r.base_safety_revision_id, r.draft_safety_revision_id
        FROM city c JOIN city_revision r ON r.id=c.draft_revision_id WHERE c.id=:city
        """).param("city", cityId).query((r, n) -> new SafetyPointers(
            r.getObject("published_safety_revision_id", Long.class), r.getObject("base_safety_revision_id", Long.class),
            r.getObject("draft_safety_revision_id", Long.class))).single();
    if (state.draft() == null && !java.util.Objects.equals(state.base(), state.published())) {
      throw new ApiException(HttpStatus.CONFLICT, "SAFETY_CHANGED", "공개 안전정보가 변경되어 재검토가 필요합니다.");
    }
    Long previousRevision = state.draft() == null ? state.published() : state.draft();
    var items = prepareSafety(previousRevision, input);
    long safetyId;
    if (state.draft() == null) {
      safetyId = jdbc.sql("""
          INSERT INTO safety_revision(city_id, base_revision_id, review_state, created_by)
          VALUES (:city, :base, :state, :actor) RETURNING id
          """).param("city", cityId).param("base", state.base(), java.sql.Types.BIGINT)
          .param("state", input.get("reviewState").asString()).param("actor", actorId).query(Long.class).single();
      jdbc.sql("UPDATE city_revision SET draft_safety_revision_id=:safety WHERE id=:revision")
          .param("safety", safetyId).param("revision", revision).update();
    } else {
      safetyId = state.draft();
      jdbc.sql("UPDATE safety_revision SET review_state=:state WHERE id=:id AND published_at IS NULL")
          .param("state", input.get("reviewState").asString()).param("id", safetyId).update();
      jdbc.sql("DELETE FROM safety_item WHERE safety_revision_id=:id").param("id", safetyId).update();
    }
    insertSafetyItems(safetyId, actorId, items);
  }

  private java.util.List<SafetyInput> prepareSafety(Long previousRevision, JsonNode input) {
    var items = new java.util.ArrayList<SafetyInput>();
    for (JsonNode item : input.get("items")) {
      var sources = new java.util.ArrayList<SafetySource>();
      for (JsonNode source : item.get("sources")) {
        long sourceId = Long.parseLong(source.get("sourceDocumentId").asString());
        boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM source_document WHERE id=:id)")
            .param("id", sourceId).query(Boolean.class).single();
        if (!exists) throw invalid("안전정보 출처를 확인해 주세요.");
        SafetySource previous = previousSafetySource(previousRevision, item, sourceId, source.get("evidenceNote"));
        sources.add(new SafetySource(sourceId, nullable(source.get("evidenceNote")),
            source.get("reviewAction").asString().equals("CONFIRM"), previous.checkedAt(), previous.checkedBy()));
      }
      items.add(new SafetyInput(item, sources));
    }
    return items;
  }

  long createPublishedSafety(long cityId, long actorId, Long previousRevision, JsonNode input) {
    var items = prepareSafety(previousRevision, input);
    long safetyId = jdbc.sql("""
        INSERT INTO safety_revision(city_id,base_revision_id,review_state,created_by,published_by,published_at)
        VALUES (:city,:base,:state,:actor,:actor,CURRENT_TIMESTAMP) RETURNING id
        """).param("city", cityId).param("base", previousRevision, java.sql.Types.BIGINT)
        .param("state", input.get("reviewState").asString()).param("actor", actorId).query(Long.class).single();
    insertSafetyItems(safetyId, actorId, items);
    jdbc.sql("UPDATE city SET published_safety_revision_id=:safety WHERE id=:city")
        .param("safety", safetyId).param("city", cityId).update();
    return safetyId;
  }

  void auditSafety(long cityId, long actorId, long safetyId, JsonNode before, JsonNode after) {
    jdbc.sql("""
        INSERT INTO audit_event(actor_id,city_ref_id,safety_revision_ref_id,action,before_data,after_data)
        VALUES (:actor,:city,:safety,'SAFETY_PUBLISH',CAST(:before AS jsonb),CAST(:after AS jsonb))
        """).param("actor", actorId).param("city", cityId).param("safety", safetyId)
        .param("before", json.writeValueAsString(before)).param("after", json.writeValueAsString(after)).update();
  }

  private void insertSafetyItems(long safetyId, long actorId, java.util.List<SafetyInput> items) {
    for (SafetyInput entry : items) {
      JsonNode item = entry.item();
      long id = jdbc.sql("""
          INSERT INTO safety_item(safety_revision_id,kind,assessment,official_level,scope_type,original_scope_text,
            city_coverage,city_scope_note,title,body,display_order,effective_from,effective_until)
          VALUES (:revision,:kind,:assessment,:level,:scope,:original,:coverage,:scopeNote,:title,:body,:position,
            CAST(:effectiveFrom AS timestamptz),CAST(:effectiveUntil AS timestamptz)) RETURNING id
          """).param("revision", safetyId).param("kind", item.get("kind").asString())
          .param("assessment", item.get("assessment").asString())
          .param("level", nullable(item.get("officialLevel")), java.sql.Types.VARCHAR)
          .param("scope", nullable(item.get("scopeType")), java.sql.Types.VARCHAR)
          .param("original", nullable(item.get("originalScopeText")), java.sql.Types.VARCHAR)
          .param("coverage", nullable(item.get("cityCoverage")), java.sql.Types.VARCHAR)
          .param("scopeNote", nullable(item.get("cityScopeNote")), java.sql.Types.VARCHAR)
          .param("title", nullable(item.get("title")), java.sql.Types.VARCHAR)
          .param("body", nullable(item.get("body")), java.sql.Types.VARCHAR)
          .param("position", item.get("displayOrder").asInt())
          .param("effectiveFrom", nullable(item.get("effectiveFrom")), java.sql.Types.VARCHAR)
          .param("effectiveUntil", nullable(item.get("effectiveUntil")), java.sql.Types.VARCHAR)
          .query(Long.class).single();
      for (SafetySource source : entry.sources()) {
        jdbc.sql("""
            INSERT INTO safety_source(safety_item_id,source_document_id,evidence_note,checked_at,checked_by)
            VALUES (:item,:source,:note,CASE WHEN :confirm THEN CURRENT_TIMESTAMP ELSE CAST(:checkedAt AS timestamptz) END,
              CASE WHEN :confirm THEN :actor ELSE :checkedBy END)
            """).param("item", id).param("source", source.id())
            .param("note", source.note(), java.sql.Types.VARCHAR).param("confirm", source.confirm())
            .param("checkedAt", source.checkedAt(), java.sql.Types.VARCHAR).param("actor", actorId)
            .param("checkedBy", source.checkedBy(), java.sql.Types.BIGINT).update();
      }
    }  }

  private SafetySource previousSafetySource(Long revision, JsonNode item, long sourceId, JsonNode note) {
    if (revision == null) return new SafetySource(sourceId, null, false, null, null);
    var matches = jdbc.sql("""
        SELECT s.checked_at, s.checked_by FROM safety_item i JOIN safety_source s ON s.safety_item_id=i.id
        WHERE i.safety_revision_id=:revision AND s.source_document_id=:source
          AND s.evidence_note IS NOT DISTINCT FROM :note AND s.checked_at IS NOT NULL
          AND i.kind=:kind AND i.assessment=:assessment AND i.official_level IS NOT DISTINCT FROM :level
          AND i.scope_type IS NOT DISTINCT FROM :scope AND i.original_scope_text IS NOT DISTINCT FROM :original
          AND i.city_coverage IS NOT DISTINCT FROM :coverage AND i.city_scope_note IS NOT DISTINCT FROM :scopeNote
          AND i.title IS NOT DISTINCT FROM :title AND i.body IS NOT DISTINCT FROM :body
          AND i.effective_from IS NOT DISTINCT FROM CAST(:effectiveFrom AS timestamptz)
          AND i.effective_until IS NOT DISTINCT FROM CAST(:effectiveUntil AS timestamptz)
        LIMIT 2
        """).param("revision", revision).param("source", sourceId)
        .param("note", nullable(note), java.sql.Types.VARCHAR)
        .param("kind", item.get("kind").asString()).param("assessment", item.get("assessment").asString())
        .param("level", nullable(item.get("officialLevel")), java.sql.Types.VARCHAR)
        .param("scope", nullable(item.get("scopeType")), java.sql.Types.VARCHAR)
        .param("original", nullable(item.get("originalScopeText")), java.sql.Types.VARCHAR)
        .param("coverage", nullable(item.get("cityCoverage")), java.sql.Types.VARCHAR)
        .param("scopeNote", nullable(item.get("cityScopeNote")), java.sql.Types.VARCHAR)
        .param("title", nullable(item.get("title")), java.sql.Types.VARCHAR)
        .param("body", nullable(item.get("body")), java.sql.Types.VARCHAR)
        .param("effectiveFrom", nullable(item.get("effectiveFrom")), java.sql.Types.VARCHAR)
        .param("effectiveUntil", nullable(item.get("effectiveUntil")), java.sql.Types.VARCHAR)
        .query((row, n) -> new SafetySource(sourceId, nullable(note), false,
            row.getObject("checked_at", java.time.OffsetDateTime.class).toString(),
            row.getObject("checked_by", Long.class))).list();
    return matches.size() == 1 ? matches.getFirst() : new SafetySource(sourceId, nullable(note), false, null, null);
  }

  private record SafetyPointers(Long published, Long base, Long draft) {}
  private record SafetyInput(JsonNode item, java.util.List<SafetySource> sources) {}
  private record SafetySource(long id, String note, boolean confirm, String checkedAt, Long checkedBy) {}

  private static String nullable(JsonNode value) { return value.isNull() ? null : value.asString(); }
  private static ApiException invalid(String message) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message);
  }
}
