package com.tripee.backend.repository.admin;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tripee.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

@Repository
@Profile("postgres")
public class DraftRepository {
  private final JdbcClient jdbc;
  private final DraftSafetyStore safetyStore;
  private final DraftContentStore contents;
  private final DraftPublicationStore publications;
  private final ObjectMapper json;
  private final String draftDetailSql;

  public DraftRepository(JdbcClient jdbc, ObjectMapper json, DraftSafetyStore safetyStore, DraftPublicationStore publications, DraftContentStore contents,
      @org.springframework.beans.factory.annotation.Value("classpath:sql/draft-detail.sql")
      org.springframework.core.io.Resource sql) throws java.io.IOException {
    this.jdbc = jdbc;
    this.safetyStore = safetyStore;
    this.contents = contents;
    this.publications = publications;
    this.json = json;
    this.draftDetailSql = sql.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  }

  public Optional<JsonNode> find(long cityId) {
    return jdbc.sql(draftDetailSql).param("cityId", cityId).query((row, n) -> json.readTree(row.getString("draft"))).optional();
  }

  public void create(long cityId, long actorId) {
    var city = jdbc.sql("""
        SELECT published_revision_id, draft_revision_id, published_safety_revision_id
        FROM city WHERE id=:cityId
        """).param("cityId", cityId).query((r, n) -> new CityPointers(
            r.getObject("published_revision_id", Long.class), r.getObject("draft_revision_id", Long.class),
            r.getObject("published_safety_revision_id", Long.class))).single();
    if (city.draft() != null) {
      throw new ApiException(HttpStatus.CONFLICT, "DRAFT_ALREADY_EXISTS", "이미 초안이 있습니다.");
    }
    if (city.published() == null) {
      throw new ApiException(HttpStatus.CONFLICT, "DRAFT_NOT_AVAILABLE", "복사할 공개본이 없습니다.");
    }
    long revision = jdbc.sql("""
        INSERT INTO city_revision(city_id, revision_no, name_ko, name_en, latitude, longitude, tagline,
          base_safety_revision_id, created_by, updated_by)
        SELECT :cityId, GREATEST(COALESCE((SELECT max(revision_no) FROM city_revision WHERE city_id=:cityId),0),
          COALESCE((SELECT max((after_data->>'revisionNo')::integer) FROM audit_event
            WHERE city_ref_id=:cityId AND after_data->>'revisionNo' ~ '^[0-9]+$'),0)) + 1,
          name_ko, name_en, latitude, longitude, tagline, :safety, :actor, :actor
        FROM city_revision WHERE id=:published RETURNING id
        """).param("cityId", cityId).param("safety", city.safety(), java.sql.Types.BIGINT)
        .param("actor", actorId).param("published", city.published()).query(Long.class).single();
    jdbc.sql("INSERT INTO revision_tag SELECT :newId, tag_id FROM revision_tag WHERE revision_id=:oldId")
        .param("newId", revision).param("oldId", city.published()).update();
    jdbc.sql("""
        INSERT INTO revision_photo(revision_id, photo_asset_id, display_order, alt_text, caption)
        SELECT :newId, photo_asset_id, display_order, alt_text, caption FROM revision_photo WHERE revision_id=:oldId
        """).param("newId", revision).param("oldId", city.published()).update();
    var ids = jdbc.sql("SELECT id FROM content_item WHERE revision_id=:oldId ORDER BY id")
        .param("oldId", city.published()).query(Long.class).list();
    for (long oldId : ids) {
      long newId = jdbc.sql("""
          INSERT INTO content_item(revision_id, kind, title, body, display_order, is_selected)
          SELECT :revision, kind, title, body, display_order, is_selected FROM content_item WHERE id=:oldId
          RETURNING id
          """).param("revision", revision).param("oldId", oldId).query(Long.class).single();
      jdbc.sql("""
          INSERT INTO content_source(content_item_id, source_document_id, evidence_note, checked_at, checked_by)
          SELECT :newId, source_document_id, evidence_note, checked_at, checked_by
          FROM content_source WHERE content_item_id=:oldId
          """).param("newId", newId).param("oldId", oldId).update();
    }
    jdbc.sql("UPDATE city SET draft_revision_id=:revision WHERE id=:cityId")
        .param("revision", revision).param("cityId", cityId).update();
    JsonNode snapshot = find(cityId).orElseThrow();
    jdbc.sql("""
        INSERT INTO audit_event(actor_id, city_ref_id, revision_ref_id, action, after_data)
        VALUES (:actor, :cityId, :revision, 'CREATE_DRAFT', CAST(:snapshot AS jsonb))
        """).param("actor", actorId).param("cityId", cityId).param("revision", revision)
        .param("snapshot", json.writeValueAsString(snapshot)).update();
  }

  public void replace(long cityId, long actorId, JsonNode before, JsonNode input) {
    long revision = Long.parseLong(before.get("revisionId").asString());
    if (input.get("safetyDraft").isNull() && !before.get("safetyDraft").isNull()) {
      throw new ApiException(HttpStatus.CONFLICT, "SAFETY_DRAFT_CANNOT_BE_DROPPED", "수정 중인 안전정보를 유지해 주세요.");
    }
    contents.replace(cityId, revision, actorId, before, input);
    if (!input.get("safetyDraft").isNull()) {
      safetyStore.replaceSafety(cityId, revision, actorId, input.get("safetyDraft"));
    }
    JsonNode coords = input.get("coordinates");
    jdbc.sql("""
        UPDATE city_revision SET name_ko=:ko,name_en=:en,tagline=:tagline,latitude=:latitude,longitude=:longitude,
          edit_version=edit_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=:actor WHERE id=:revision
        """).param("ko", nullable(input.get("nameKo")), java.sql.Types.VARCHAR)
        .param("en", nullable(input.get("nameEn")), java.sql.Types.VARCHAR)
        .param("tagline", nullable(input.get("tagline")), java.sql.Types.VARCHAR)
        .param("latitude", coords.isNull() ? null : coords.get("latitude").decimalValue(), java.sql.Types.NUMERIC)
        .param("longitude", coords.isNull() ? null : coords.get("longitude").decimalValue(), java.sql.Types.NUMERIC)
        .param("actor", actorId).param("revision", revision).update();
    JsonNode after = find(cityId).orElseThrow();
    jdbc.sql("""
        INSERT INTO audit_event(actor_id,city_ref_id,revision_ref_id,action,before_data,after_data)
        VALUES (:actor,:city,:revision,'UPDATE_DRAFT',CAST(:before AS jsonb),CAST(:after AS jsonb))
        """).param("actor", actorId).param("city", cityId).param("revision", revision)
        .param("before", json.writeValueAsString(before)).param("after", json.writeValueAsString(after)).update();
  }

  public long createPublishedSafety(long cityId, long actorId, Long previousRevision, JsonNode input) {
    return safetyStore.createPublishedSafety(cityId, actorId, previousRevision, input);
  }

  public void auditSafety(long cityId, long actorId, long safetyId, JsonNode before, JsonNode after) {
    safetyStore.auditSafety(cityId, actorId, safetyId, before, after);
  }

  public void reviewSafety(long cityId, long actorId, JsonNode before, JsonNode safety, Long reviewedId) {
    long revision = Long.parseLong(before.get("revisionId").asString());
    jdbc.sql("UPDATE city_revision SET base_safety_revision_id=:base WHERE id=:revision")
        .param("base", reviewedId, java.sql.Types.BIGINT).param("revision", revision).update();
    JsonNode existing = before.get("safetyDraft");
    if (!existing.isNull()) {
      jdbc.sql("UPDATE safety_revision SET base_revision_id=:base WHERE id=:id AND published_at IS NULL")
          .param("base", reviewedId, java.sql.Types.BIGINT)
          .param("id", Long.parseLong(existing.get("revisionId").asString())).update();
    }
    safetyStore.replaceSafety(cityId, revision, actorId, safety);
    jdbc.sql("""
        UPDATE city_revision SET edit_version=edit_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=:actor
        WHERE id=:revision
        """).param("actor", actorId).param("revision", revision).update();
    JsonNode after = find(cityId).orElseThrow();
    jdbc.sql("""
        INSERT INTO audit_event(actor_id,city_ref_id,revision_ref_id,action,before_data,after_data)
        VALUES (:actor,:city,:revision,'UPDATE_DRAFT',CAST(:before AS jsonb),CAST(:after AS jsonb))
        """).param("actor", actorId).param("city", cityId).param("revision", revision)
        .param("before", json.writeValueAsString(before)).param("after", json.writeValueAsString(after)).update();
  }

  private static String nullable(JsonNode value) {
    return value.isNull() ? null : value.asString();
  }

  public void discard(long cityId, long actorId, JsonNode before) {
    long revision = Long.parseLong(before.get("revisionId").asString());
    var pointers = jdbc.sql("SELECT visibility, published_revision_id FROM city WHERE id=:id")
        .param("id", cityId).query((r, n) -> new DiscardState(
            r.getString("visibility"), r.getObject("published_revision_id", Long.class))).single();
    if ("PREPARING".equals(pointers.visibility()) && pointers.published() == null) {
      throw new ApiException(HttpStatus.CONFLICT, "PREPARING_REQUIRES_REVISION", "먼저 도시를 비공개로 전환해 주세요.");
    }
    jdbc.sql("""
        INSERT INTO audit_event(actor_id, city_ref_id, revision_ref_id, action, before_data)
        VALUES (:actor, :cityId, :revision, 'DISCARD_DRAFT', CAST(:snapshot AS jsonb))
        """).param("actor", actorId).param("cityId", cityId).param("revision", revision)
        .param("snapshot", json.writeValueAsString(before)).update();
    Long safety = jdbc.sql("SELECT draft_safety_revision_id FROM city_revision WHERE id=:revision")
        .param("revision", revision).query((r, n) -> new SafetyReference(r.getObject(1, Long.class)))
        .single().id();
    jdbc.sql("UPDATE city SET draft_revision_id=NULL WHERE id=:cityId").param("cityId", cityId).update();
    jdbc.sql("DELETE FROM city_revision WHERE id=:revision").param("revision", revision).update();
    if (safety != null) {
      jdbc.sql("DELETE FROM safety_revision WHERE id=:id").param("id", safety).update();
    }
  }

  public long publish(long cityId, long actorId, JsonNode draft, JsonNode effectiveSafety) {
    return publications.publish(cityId, actorId, draft, effectiveSafety);
  }

  private record CityPointers(Long published, Long draft, Long safety) {}
  private record DiscardState(String visibility, Long published) {}
  private record SafetyReference(Long id) {}
}
