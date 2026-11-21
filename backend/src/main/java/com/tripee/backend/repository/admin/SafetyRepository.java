package com.tripee.backend.repository.admin;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
@Profile("postgres")
public class SafetyRepository {
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public SafetyRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public Optional<JsonNode> current(long cityId) {
    return jdbc.sql("""
        SELECT jsonb_build_object(
          'revisionId',sr.id::text,'reviewState',COALESCE(sr.review_state,'UNVERIFIED'),
          'publishedAt',sr.published_at,
          'publishedBy',CASE WHEN a.id IS NULL THEN NULL ELSE
            jsonb_build_object('id',a.id::text,'displayName',a.display_name) END,
          'items',COALESCE((SELECT jsonb_agg(jsonb_build_object(
            'id',i.id::text,'kind',i.kind,'assessment',i.assessment,'officialLevel',i.official_level,
            'scopeType',i.scope_type,'originalScopeText',i.original_scope_text,'cityCoverage',i.city_coverage,
            'cityScopeNote',i.city_scope_note,'title',i.title,'body',i.body,'displayOrder',i.display_order,
            'effectiveFrom',i.effective_from,'effectiveUntil',i.effective_until,
            'sources',COALESCE((SELECT jsonb_agg(jsonb_build_object(
              'sourceDocument',jsonb_build_object('id',d.id::text,'url',d.url,'title',d.title,
                'publisher',d.publisher,'createdAt',d.created_at),
              'evidenceNote',ss.evidence_note,'checkedAt',ss.checked_at,
              'checkedBy',CASE WHEN reviewer.id IS NULL THEN NULL ELSE
                jsonb_build_object('id',reviewer.id::text,'displayName',reviewer.display_name) END) ORDER BY d.id)
              FROM safety_source ss JOIN source_document d ON d.id=ss.source_document_id
              LEFT JOIN admin_account reviewer ON reviewer.id=ss.checked_by WHERE ss.safety_item_id=i.id),'[]'::jsonb))
            ORDER BY i.display_order) FROM safety_item i WHERE i.safety_revision_id=sr.id),'[]'::jsonb)
        ) AS result
        FROM city c LEFT JOIN safety_revision sr ON sr.id=c.published_safety_revision_id
        LEFT JOIN admin_account a ON a.id=sr.published_by WHERE c.id=:city
        """).param("city", cityId).query((r,n) -> json.readTree(r.getString("result"))).optional();
  }
}
