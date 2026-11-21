package com.tripee.backend.repository.admin.jdbc;

import com.tripee.backend.common.exception.ApiException;
import com.tripee.backend.model.admin.CityModels.City;
import java.math.BigDecimal;
import java.sql.Types;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** 도시 생성·삭제의 지연 제약조건과 감사 스냅샷 SQL을 관리함. */
@Repository
@Profile("postgres")
public class JdbcAdminCityCommands {
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public JdbcAdminCityCommands(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public long[] create(long countryId, long actorId, String nameKo, String nameEn, String tagline,
                BigDecimal latitude, BigDecimal longitude) {
    boolean countryExists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM country WHERE id = :id)")
        .param("id", countryId).query(Boolean.class).single();
    if (!countryExists) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "국가를 확인해 주세요.");
    }
    long cityId = jdbc.sql("INSERT INTO city(country_id) VALUES (:countryId) RETURNING id")
        .param("countryId", countryId).query(Long.class).single();
    long revisionId = jdbc.sql("""
        INSERT INTO city_revision(city_id, revision_no, name_ko, name_en, latitude, longitude, tagline,
                                  created_by, updated_by)
        VALUES (:cityId, 1, :nameKo, :nameEn, :latitude, :longitude, :tagline, :actorId, :actorId)
        RETURNING id
        """).param("cityId", cityId).param("nameKo", nameKo, Types.VARCHAR)
        .param("nameEn", nameEn, Types.VARCHAR).param("latitude", latitude, Types.NUMERIC)
        .param("longitude", longitude, Types.NUMERIC).param("tagline", tagline, Types.VARCHAR)
        .param("actorId", actorId).query(Long.class).single();
    jdbc.sql("UPDATE city SET draft_revision_id = :revisionId WHERE id = :cityId")
        .param("revisionId", revisionId).param("cityId", cityId).update();
    jdbc.sql("""
        INSERT INTO audit_event(actor_id, city_ref_id, revision_ref_id, action, after_data)
        VALUES (:actorId, :cityId, :revisionId, 'CREATE_DRAFT',
                jsonb_build_object('cityId', CAST(:cityId AS text),
                  'countryId', CAST(:countryId AS text), 'revisionId', CAST(:revisionId AS text),
                  'nameKo', CAST(:nameKo AS text), 'nameEn', CAST(:nameEn AS text),
                  'latitude', CAST(:latitude AS numeric), 'longitude', CAST(:longitude AS numeric),
                  'tagline', CAST(:tagline AS text)))
        """).param("actorId", actorId).param("cityId", cityId).param("revisionId", revisionId)
        .param("countryId", countryId).param("nameKo", nameKo, Types.VARCHAR)
        .param("nameEn", nameEn, Types.VARCHAR).param("latitude", latitude, Types.NUMERIC)
        .param("longitude", longitude, Types.NUMERIC).param("tagline", tagline, Types.VARCHAR).update();
    return new long[]{cityId, revisionId};
  }

  public void setVisibility(long cityId, String visibility) {
    jdbc.sql("UPDATE city SET visibility=:visibility WHERE id=:id")
        .param("visibility", visibility).param("id", cityId).update();
  }

  public void recordVisibility(long cityId, long actorId, City before, City after) {
    jdbc.sql("""
        INSERT INTO audit_event(actor_id, city_ref_id, action, before_data, after_data)
        VALUES (:actor,:city,:action,CAST(:before AS jsonb),CAST(:after AS jsonb))
        """).param("actor", actorId).param("city", cityId)
        .param("action", "PUBLIC".equals(before.visibility()) && "HIDDEN".equals(after.visibility())
            ? "UNPUBLISH" : "SET_VISIBILITY")
        .param("before", json.writeValueAsString(before)).param("after", json.writeValueAsString(after)).update();
  }

  public void delete(long cityId, long actorId) {
    String snapshot = jdbc.sql("""
        SELECT jsonb_build_object('city',to_jsonb(c),'country',to_jsonb(k),
          'revisions',COALESCE((SELECT jsonb_agg(to_jsonb(r) ORDER BY r.id) FROM city_revision r WHERE r.city_id=c.id),'[]'),
          'safetyRevisions',COALESCE((SELECT jsonb_agg(to_jsonb(s) ORDER BY s.id) FROM safety_revision s WHERE s.city_id=c.id),'[]'),
          'tags',COALESCE((SELECT jsonb_agg(jsonb_build_object('revisionId',rt.revision_id::text,'tag',to_jsonb(t)))
            FROM revision_tag rt JOIN city_revision r ON r.id=rt.revision_id JOIN tag t ON t.id=rt.tag_id
            WHERE r.city_id=c.id),'[]'),
          'photos',COALESCE((SELECT jsonb_agg(to_jsonb(a)-'storage_key' ORDER BY a.id)
            FROM photo_asset a WHERE a.city_id=c.id),'[]'),
          'photoLinks',COALESCE((SELECT jsonb_agg(to_jsonb(p)) FROM revision_photo p
            JOIN city_revision r ON r.id=p.revision_id WHERE r.city_id=c.id),'[]'),
          'contentItems',COALESCE((SELECT jsonb_agg(to_jsonb(i)) FROM content_item i
            JOIN city_revision r ON r.id=i.revision_id WHERE r.city_id=c.id),'[]'),
          'contentSources',COALESCE((SELECT jsonb_agg(jsonb_build_object('link',to_jsonb(cs),'source',to_jsonb(sd)))
            FROM content_source cs JOIN content_item i ON i.id=cs.content_item_id
            JOIN city_revision r ON r.id=i.revision_id JOIN source_document sd ON sd.id=cs.source_document_id
            WHERE r.city_id=c.id),'[]'),
          'safetyItems',COALESCE((SELECT jsonb_agg(to_jsonb(i)) FROM safety_item i
            JOIN safety_revision r ON r.id=i.safety_revision_id WHERE r.city_id=c.id),'[]'),
          'safetySources',COALESCE((SELECT jsonb_agg(jsonb_build_object('link',to_jsonb(ss),'source',to_jsonb(sd)))
            FROM safety_source ss JOIN safety_item i ON i.id=ss.safety_item_id
            JOIN safety_revision r ON r.id=i.safety_revision_id JOIN source_document sd ON sd.id=ss.source_document_id
            WHERE r.city_id=c.id),'[]'))::text
        FROM city c JOIN country k ON k.id=c.country_id WHERE c.id=:id
        """).param("id", cityId).query(String.class).single();
    jdbc.sql("""
        INSERT INTO audit_event(actor_id,city_ref_id,action,before_data)
        VALUES (:actor,:city,'DELETE',CAST(:snapshot AS jsonb))
        """).param("actor", actorId).param("city", cityId).param("snapshot", snapshot).update();
    jdbc.sql("""
        INSERT INTO photo_cleanup_job(storage_key)
        SELECT storage_key FROM photo_asset WHERE city_id=:city ON CONFLICT DO NOTHING
        """).param("city", cityId).update();
    jdbc.sql("""
        UPDATE city SET visibility='HIDDEN',published_revision_id=NULL,draft_revision_id=NULL,
          published_safety_revision_id=NULL WHERE id=:city
        """).param("city", cityId).update();
    jdbc.sql("DELETE FROM city_revision WHERE city_id=:city").param("city", cityId).update();
    jdbc.sql("DELETE FROM safety_revision WHERE city_id=:city").param("city", cityId).update();
    jdbc.sql("DELETE FROM photo_asset WHERE city_id=:city").param("city", cityId).update();
    jdbc.sql("DELETE FROM city WHERE id=:city").param("city", cityId).update();
  }
}
