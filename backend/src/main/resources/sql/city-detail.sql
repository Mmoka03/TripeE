-- 한 스냅샷에서 현재 공개 본문과 현재 공개 안전정보를 함께 조회함.
WITH visible_city AS (
    SELECT c.id, c.visibility, c.published_revision_id, c.published_safety_revision_id,
           r.tagline, r.published_at,
           jsonb_build_object(
               'id', c.id::text, 'visibility', c.visibility, 'nameKo', r.name_ko, 'nameEn', r.name_en,
               'country', jsonb_build_object('id', k.id::text, 'code', k.code, 'nameKo', k.name_ko, 'nameEn', k.name_en),
               'coordinates', jsonb_build_object('latitude', r.latitude, 'longitude', r.longitude)
           ) AS summary
    FROM city c
    JOIN country k ON k.id = c.country_id
    JOIN city_revision r ON r.id = COALESCE(c.published_revision_id, c.draft_revision_id)
    WHERE c.id = :cityId AND c.visibility IN ('PREPARING', 'PUBLIC')
      AND r.name_ko IS NOT NULL AND r.name_en IS NOT NULL
      AND r.latitude IS NOT NULL AND r.longitude IS NOT NULL
), public_city AS (
    SELECT * FROM visible_city WHERE visibility = 'PUBLIC'
), selected_content AS (
    SELECT i.id, i.kind, i.display_order, i.title, i.body,
           row_number() OVER (PARTITION BY i.kind ORDER BY i.display_order) AS position
    FROM public_city c JOIN content_item i ON i.revision_id = c.published_revision_id
    WHERE i.is_selected
), content AS (
    SELECT i.kind, i.display_order,
           jsonb_build_object('id', i.id::text, 'kind', i.kind, 'title', i.title, 'body', i.body,
               'displayOrder', i.display_order, 'sources', (
                   SELECT jsonb_agg(jsonb_build_object(
                       'sourceDocumentId', d.id::text, 'url', d.url, 'title', d.title, 'publisher', d.publisher,
                       'checkedAt', to_char(s.checked_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
                   ) ORDER BY d.id)
                   FROM content_source s JOIN source_document d ON d.id = s.source_document_id
                   WHERE s.content_item_id = i.id AND s.checked_at IS NOT NULL
               )) AS item
    FROM selected_content i
    WHERE i.kind <> 'EXPERIENCE' OR i.position <= 3
), safety_items AS (
    SELECT i.display_order, jsonb_build_object(
        'id', i.id::text, 'kind', i.kind, 'assessment', i.assessment, 'officialLevel', i.official_level,
        'scopeType', i.scope_type, 'originalScopeText', i.original_scope_text, 'cityCoverage', i.city_coverage,
        'cityScopeNote', i.city_scope_note, 'title', i.title, 'body', i.body, 'displayOrder', i.display_order,
        'effectiveFrom', to_char(i.effective_from AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        'effectiveUntil', to_char(i.effective_until AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        'temporalStatus', CASE WHEN i.effective_from > CURRENT_TIMESTAMP THEN 'UPCOMING'
                               WHEN i.effective_until <= CURRENT_TIMESTAMP THEN 'EXPIRED'
                               ELSE 'CURRENT' END,
        'sources', (
            SELECT jsonb_agg(jsonb_build_object(
                'sourceDocumentId', d.id::text, 'url', d.url, 'title', d.title, 'publisher', d.publisher,
                'checkedAt', to_char(s.checked_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
            ) ORDER BY d.id)
            FROM safety_source s JOIN source_document d ON d.id = s.source_document_id
            WHERE s.safety_item_id = i.id AND s.checked_at IS NOT NULL
        )) AS item
    FROM public_city c JOIN safety_item i ON i.safety_revision_id = c.published_safety_revision_id
)
SELECT c.summary || CASE c.visibility
    WHEN 'PREPARING' THEN jsonb_build_object('message', '도시 정보를 준비 중이에요.')
    WHEN 'PUBLIC' THEN jsonb_build_object(
        'revisionId', c.published_revision_id::text, 'tagline', c.tagline,
        'publishedAt', to_char(c.published_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        'tags', COALESCE((
            SELECT jsonb_agg(jsonb_build_object('id', t.id::text, 'code', t.code, 'labelKo', t.label_ko,
                'labelEn', t.label_en, 'isActive', t.is_active) ORDER BY t.id)
            FROM revision_tag rt JOIN tag t ON t.id = rt.tag_id WHERE rt.revision_id = c.published_revision_id
        ), '[]'),
        'photos', COALESCE((
            SELECT CASE WHEN bool_and(a.rights_checked_at IS NOT NULL) THEN
                jsonb_agg(jsonb_build_object('assetId', a.id::text, 'imageUrl', '/api/v1/media/photo-assets/' || a.id,
                'displayOrder', p.display_order, 'altText', p.alt_text, 'caption', p.caption, 'sourceUrl', a.source_url,
                'creatorName', a.creator_name, 'licenseName', a.license_name, 'licenseUrl', a.license_url)
                ORDER BY p.display_order) END
            FROM revision_photo p JOIN photo_asset a ON a.id = p.photo_asset_id WHERE p.revision_id = c.published_revision_id
        ), '[]'),
        'experiences', COALESCE((SELECT jsonb_agg(item ORDER BY display_order) FROM content WHERE kind = 'EXPERIENCE'), '[]'),
        'visitPeriods', COALESCE((SELECT jsonb_agg(item ORDER BY display_order) FROM content WHERE kind = 'VISIT_PERIOD'), '[]'),
        'arrival', COALESCE((SELECT jsonb_agg(item ORDER BY display_order) FROM content WHERE kind = 'ARRIVAL'), '[]'),
        'localTransport', COALESCE((SELECT jsonb_agg(item ORDER BY display_order) FROM content WHERE kind = 'LOCAL_TRANSPORT'), '[]'),
        'considerations', COALESCE((SELECT jsonb_agg(item ORDER BY display_order) FROM content WHERE kind = 'CONSIDERATION'), '[]'),
        'safety', (
            SELECT jsonb_build_object('revisionId', s.id::text, 'reviewState', s.review_state,
                'publishedAt', to_char(s.published_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
                'items', COALESCE((SELECT jsonb_agg(item ORDER BY display_order) FROM safety_items), '[]'))
            FROM safety_revision s WHERE s.id = c.published_safety_revision_id
        )
    ) END AS detail
FROM visible_city c
