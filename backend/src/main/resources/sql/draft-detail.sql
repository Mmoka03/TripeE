SELECT jsonb_build_object(
  'cityId', c.id::text, 'revisionId', r.id::text, 'revisionNo', r.revision_no,
  'editVersion', r.edit_version::text, 'nameKo', r.name_ko, 'nameEn', r.name_en,
  'coordinates', CASE WHEN r.latitude IS NULL THEN NULL ELSE jsonb_build_object(
    'latitude', r.latitude, 'longitude', r.longitude) END,
  'tagline', r.tagline, 'baseSafetyRevisionId', r.base_safety_revision_id::text,
  'tagIds', COALESCE((SELECT jsonb_agg(t.tag_id::text ORDER BY t.tag_id)
    FROM revision_tag t WHERE t.revision_id = r.id), '[]'::jsonb),
  'photos', COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'asset', jsonb_build_object('id', a.id::text, 'cityId', a.city_id::text,
      'imageUrl', '/api/v1/media/photo-assets/' || a.id, 'sourceUrl', a.source_url,
      'creatorName', a.creator_name, 'licenseName', a.license_name, 'licenseUrl', a.license_url,
      'usageNote', a.usage_note, 'rightsCheckedAt', a.rights_checked_at),
    'displayOrder', p.display_order, 'altText', p.alt_text, 'caption', p.caption)
    ORDER BY p.display_order) FROM revision_photo p JOIN photo_asset a ON a.id=p.photo_asset_id
    WHERE p.revision_id = r.id), '[]'::jsonb),
  'contentItems', COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'id', i.id::text, 'kind', i.kind, 'title', i.title, 'body', i.body,
    'displayOrder', i.display_order, 'isSelected', i.is_selected,
    'sources', COALESCE((SELECT jsonb_agg(jsonb_build_object(
      'sourceDocument', jsonb_build_object('id', d.id::text, 'url', d.url, 'title', d.title,
        'publisher', d.publisher, 'createdAt', d.created_at),
      'evidenceNote', s.evidence_note, 'checkedAt', s.checked_at,
      'checkedBy', CASE WHEN a.id IS NULL THEN NULL ELSE
        jsonb_build_object('id', a.id::text, 'displayName', a.display_name) END) ORDER BY d.id)
      FROM content_source s JOIN source_document d ON d.id=s.source_document_id
      LEFT JOIN admin_account a ON a.id=s.checked_by WHERE s.content_item_id=i.id), '[]'::jsonb))
    ORDER BY i.kind, i.display_order) FROM content_item i WHERE i.revision_id=r.id), '[]'::jsonb),
  'safetyDraft', CASE WHEN r.draft_safety_revision_id IS NULL THEN NULL ELSE (
    SELECT jsonb_build_object('revisionId', sr.id::text, 'reviewState', sr.review_state,
      'items', COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'id', si.id::text, 'kind', si.kind, 'assessment', si.assessment,
        'officialLevel', si.official_level, 'scopeType', si.scope_type,
        'originalScopeText', si.original_scope_text, 'cityCoverage', si.city_coverage,
        'cityScopeNote', si.city_scope_note, 'title', si.title, 'body', si.body,
        'displayOrder', si.display_order, 'effectiveFrom', si.effective_from,
        'effectiveUntil', si.effective_until,
        'sources', COALESCE((SELECT jsonb_agg(jsonb_build_object(
          'sourceDocument', jsonb_build_object('id', d.id::text, 'url', d.url,
            'title', d.title, 'publisher', d.publisher, 'createdAt', d.created_at),
          'evidenceNote', ss.evidence_note, 'checkedAt', ss.checked_at,
          'checkedBy', CASE WHEN a.id IS NULL THEN NULL ELSE
            jsonb_build_object('id', a.id::text, 'displayName', a.display_name) END) ORDER BY d.id)
          FROM safety_source ss JOIN source_document d ON d.id=ss.source_document_id
          LEFT JOIN admin_account a ON a.id=ss.checked_by WHERE ss.safety_item_id=si.id), '[]'::jsonb))
      ORDER BY si.display_order) FROM safety_item si WHERE si.safety_revision_id=sr.id), '[]'::jsonb))
    FROM safety_revision sr WHERE sr.id=r.draft_safety_revision_id) END,
  'updatedAt', r.updated_at, 'updatedBy',
    jsonb_build_object('id', updater.id::text, 'displayName', updater.display_name)) AS draft
FROM city c JOIN city_revision r ON r.id=c.draft_revision_id
JOIN admin_account updater ON updater.id=r.updated_by
WHERE c.id=:cityId AND r.published_at IS NULL
