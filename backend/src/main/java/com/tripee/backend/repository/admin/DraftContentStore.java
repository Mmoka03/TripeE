package com.tripee.backend.repository.admin;

import com.tripee.backend.common.exception.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

/** 태그·사진·콘텐츠 및 출처 교체를 호출자의 도시 잠금 트랜잭션에서 수행함. */
@Repository
@Profile("postgres")
class DraftContentStore {
  private final JdbcClient jdbc;
  DraftContentStore(JdbcClient jdbc) { this.jdbc = jdbc; }

  void replace(long cityId, long revision, long actorId, JsonNode before, JsonNode input) {
    for (JsonNode tag : input.get("tagIds")) {
      long tagId = Long.parseLong(tag.asString());
      boolean valid = jdbc.sql("""
          SELECT EXISTS(SELECT 1 FROM tag t WHERE t.id=:id AND
            (t.is_active OR EXISTS(SELECT 1 FROM revision_tag rt WHERE rt.revision_id=:revision AND rt.tag_id=t.id)))
          """).param("id", tagId).param("revision", revision).query(Boolean.class).single();
      if (!valid) throw invalid("태그를 확인해 주세요.");
    }
    for (JsonNode photo : input.get("photos")) {
      boolean valid = jdbc.sql("SELECT EXISTS(SELECT 1 FROM photo_asset WHERE id=:id AND city_id=:city)")
          .param("id", Long.parseLong(photo.get("photoAssetId").asString()))
          .param("city", cityId).query(Boolean.class).single();
      if (!valid) throw invalid("사진 소속 도시를 확인해 주세요.");
    }
    var existingIds = jdbc.sql("SELECT id FROM content_item WHERE revision_id=:revision")
        .param("revision", revision).query(Long.class).list();
    var keptIds = new java.util.HashSet<Long>();
    for (JsonNode item : input.get("contentItems")) {
      if (!item.get("id").isNull()) {
        long itemId = Long.parseLong(item.get("id").asString());
        if (!existingIds.contains(itemId)) throw invalid("초안 콘텐츠 ID를 확인해 주세요.");
        keptIds.add(itemId);
      }
      for (JsonNode source : item.get("sources")) {
        boolean valid = jdbc.sql("SELECT EXISTS(SELECT 1 FROM source_document WHERE id=:id)")
            .param("id", Long.parseLong(source.get("sourceDocumentId").asString())).query(Boolean.class).single();
        if (!valid) throw invalid("출처를 확인해 주세요.");
      }
    }
    jdbc.sql("SET CONSTRAINTS tripee.uq_content_order, tripee.uq_revision_photo_order DEFERRED").update();
    jdbc.sql("DELETE FROM revision_tag WHERE revision_id=:revision").param("revision", revision).update();
    for (JsonNode tag : input.get("tagIds")) {
      jdbc.sql("INSERT INTO revision_tag(revision_id,tag_id) VALUES (:revision,:tag)")
          .param("revision", revision).param("tag", Long.parseLong(tag.asString())).update();
    }
    jdbc.sql("DELETE FROM revision_photo WHERE revision_id=:revision").param("revision", revision).update();
    for (JsonNode photo : input.get("photos")) {
      jdbc.sql("""
          INSERT INTO revision_photo(revision_id,photo_asset_id,display_order,alt_text,caption)
          VALUES (:revision,:asset,:position,:alt,:caption)
          """).param("revision", revision).param("asset", Long.parseLong(photo.get("photoAssetId").asString()))
          .param("position", photo.get("displayOrder").asInt())
          .param("alt", nullable(photo.get("altText")), java.sql.Types.VARCHAR)
          .param("caption", nullable(photo.get("caption")), java.sql.Types.VARCHAR).update();
    }
    for (long old : existingIds) {
      if (!keptIds.contains(old)) jdbc.sql("DELETE FROM content_item WHERE id=:id").param("id", old).update();
    }
    for (JsonNode item : input.get("contentItems")) {
      Long oldId = item.get("id").isNull() ? null : Long.parseLong(item.get("id").asString());
      JsonNode old = null;
      if (oldId != null) {
        for (JsonNode candidate : before.get("contentItems")) {
          if (candidate.get("id").asString().equals(oldId.toString())) old = candidate;
        }
      }
      boolean unchanged = old != null && old.get("kind").equals(item.get("kind"))
          && old.get("title").equals(item.get("title")) && old.get("body").equals(item.get("body"));
      long itemId;
      if (oldId == null) {
        itemId = jdbc.sql("""
            INSERT INTO content_item(revision_id,kind,title,body,display_order,is_selected)
            VALUES (:revision,:kind,:title,:body,:position,:selected) RETURNING id
            """).param("revision", revision).param("kind", item.get("kind").asString())
            .param("title", nullable(item.get("title")), java.sql.Types.VARCHAR)
            .param("body", nullable(item.get("body")), java.sql.Types.VARCHAR)
            .param("position", item.get("displayOrder").asInt()).param("selected", item.get("isSelected").asBoolean())
            .query(Long.class).single();
      } else {
        itemId = oldId;
        jdbc.sql("""
            UPDATE content_item SET kind=:kind,title=:title,body=:body,display_order=:position,is_selected=:selected
            WHERE id=:id AND revision_id=:revision
            """).param("revision", revision).param("id", itemId).param("kind", item.get("kind").asString())
            .param("title", nullable(item.get("title")), java.sql.Types.VARCHAR)
            .param("body", nullable(item.get("body")), java.sql.Types.VARCHAR)
            .param("position", item.get("displayOrder").asInt()).param("selected", item.get("isSelected").asBoolean()).update();
      }
      jdbc.sql("DELETE FROM content_source WHERE content_item_id=:id").param("id", itemId).update();
      for (JsonNode source : item.get("sources")) {
        JsonNode oldSource = null;
        if (old != null) {
          for (JsonNode candidate : old.get("sources")) {
            if (candidate.get("sourceDocument").get("id").equals(source.get("sourceDocumentId"))) oldSource = candidate;
          }
        }
        boolean preserve = unchanged && oldSource != null
            && oldSource.get("evidenceNote").equals(source.get("evidenceNote"))
            && !oldSource.get("checkedAt").isNull();
        boolean confirm = source.get("reviewAction").asString().equals("CONFIRM");
        String checkedAt = preserve ? oldSource.get("checkedAt").asString() : null;
        Long checkedBy = preserve ? Long.parseLong(oldSource.get("checkedBy").get("id").asString()) : null;
        jdbc.sql("""
            INSERT INTO content_source(content_item_id,source_document_id,evidence_note,checked_at,checked_by)
            VALUES (:item,:source,:note,CASE WHEN :confirm THEN CURRENT_TIMESTAMP ELSE CAST(:checkedAt AS timestamptz) END,
              CASE WHEN :confirm THEN :actor ELSE :checkedBy END)
            """).param("item", itemId).param("source", Long.parseLong(source.get("sourceDocumentId").asString()))
            .param("note", nullable(source.get("evidenceNote")), java.sql.Types.VARCHAR).param("confirm", confirm)
            .param("checkedAt", checkedAt, java.sql.Types.VARCHAR).param("actor", actorId)
            .param("checkedBy", checkedBy, java.sql.Types.BIGINT).update();
      }
    }
  }

  private static String nullable(JsonNode value) { return value.isNull() ? null : value.asString(); }
  private static ApiException invalid(String message) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message);
  }
}
