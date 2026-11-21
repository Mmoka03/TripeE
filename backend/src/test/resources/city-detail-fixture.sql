-- 권한이 있는 실제 열을 복사하고 테스트용 행은 해당 연결의 임시 스키마에만 작성함.
CREATE TEMP TABLE country AS SELECT id, code, name_ko, name_en FROM tripee.country WITH NO DATA;
CREATE TEMP TABLE city AS
    SELECT id, country_id, visibility, published_revision_id, draft_revision_id, published_safety_revision_id
    FROM tripee.city WITH NO DATA;
CREATE TEMP TABLE city_revision AS
    SELECT id, name_ko, name_en, latitude, longitude, tagline, published_at FROM tripee.city_revision WITH NO DATA;
CREATE TEMP TABLE tag AS SELECT id, code, label_ko, label_en, is_active FROM tripee.tag WITH NO DATA;
CREATE TEMP TABLE revision_tag AS SELECT revision_id, tag_id FROM tripee.revision_tag WITH NO DATA;
CREATE TEMP TABLE photo_asset AS
    SELECT id, source_url, creator_name, license_name, license_url, rights_checked_at FROM tripee.photo_asset WITH NO DATA;
CREATE TEMP TABLE revision_photo AS
    SELECT revision_id, photo_asset_id, display_order, alt_text, caption FROM tripee.revision_photo WITH NO DATA;
CREATE TEMP TABLE content_item AS
    SELECT id, revision_id, kind, title, body, display_order, is_selected FROM tripee.content_item WITH NO DATA;
CREATE TEMP TABLE source_document AS SELECT id, url, title, publisher FROM tripee.source_document WITH NO DATA;
CREATE TEMP TABLE content_source AS
    SELECT content_item_id, source_document_id, checked_at FROM tripee.content_source WITH NO DATA;
CREATE TEMP TABLE safety_revision AS SELECT id, review_state, published_at FROM tripee.safety_revision WITH NO DATA;
CREATE TEMP TABLE safety_item AS
    SELECT id, safety_revision_id, kind, assessment, official_level, scope_type, original_scope_text,
           city_coverage, city_scope_note, title, body, display_order, effective_from, effective_until
    FROM tripee.safety_item WITH NO DATA;
CREATE TEMP TABLE safety_source AS SELECT safety_item_id, source_document_id, checked_at FROM tripee.safety_source WITH NO DATA;

INSERT INTO pg_temp.country VALUES (9, 'JP', '일본', 'Japan');
INSERT INTO pg_temp.city VALUES (1, 9, 'PUBLIC', 10, 11, 21), (2, 9, 'PREPARING', NULL, 11, NULL),
    (3, 9, 'HIDDEN', 10, 11, 21);
INSERT INTO pg_temp.city_revision VALUES (10, '교토', 'Kyoto', 35, 135, '공개 소개', '2026-11-18T09:00:00+09:00'),
    (11, '준비 중 도시', 'Preparing city', 40, 140, '비공개 초안 소개', NULL);
INSERT INTO pg_temp.tag VALUES (12, 'NATURE', '자연', 'Nature', true);
INSERT INTO pg_temp.revision_tag VALUES (10, 12);
INSERT INTO pg_temp.photo_asset VALUES (9007199254740993, 'https://example.org/photo', NULL, 'CC0', NULL, CURRENT_TIMESTAMP);
INSERT INTO pg_temp.revision_photo VALUES (10, 9007199254740993, 1, '거리', NULL);
INSERT INTO pg_temp.content_item VALUES
    (1, 10, 'EXPERIENCE', '경험 1', '본문', 1, true), (2, 10, 'EXPERIENCE', '경험 2', '본문', 2, true),
    (3, 10, 'EXPERIENCE', '경험 3', '본문', 3, true), (4, 10, 'EXPERIENCE', '경험 4', '본문', 4, true),
    (5, 10, 'EXPERIENCE', '미선정', '본문', 5, false), (6, 11, 'CONSIDERATION', '초안', '본문', 1, true),
    (7, 10, 'VISIT_PERIOD', '계절', '본문', 1, true), (8, 10, 'ARRIVAL', '도착', '본문', 1, true),
    (9, 10, 'LOCAL_TRANSPORT', '이동', '본문', 1, true), (10, 10, 'CONSIDERATION', '미선정', '본문', 1, false);
INSERT INTO pg_temp.source_document VALUES
    (1, 'https://example.org/a', '확인한 출처', NULL), (2, 'https://example.org/b', '미확인 출처', NULL);
INSERT INTO pg_temp.content_source SELECT id, 1, CURRENT_TIMESTAMP FROM pg_temp.content_item;
INSERT INTO pg_temp.content_source VALUES (1, 2, NULL);
INSERT INTO pg_temp.safety_revision VALUES
    (20, 'UNVERIFIED', CURRENT_TIMESTAMP - INTERVAL '1 day'), (21, 'CHECKED', CURRENT_TIMESTAMP);
INSERT INTO pg_temp.safety_item VALUES
    (1, 21, 'OFFICIAL_ADVISORY', 'ISSUED', 'LEVEL_1', 'CITY', '교토', 'WHOLE_CITY', '도시 전역',
     '이전 안내', '본문', 1, NULL, CURRENT_TIMESTAMP),
    (2, 21, 'LOCAL_CAUTION', 'CAUTION', NULL, 'CITY', '교토', 'WHOLE_CITY', '도시 전역',
     '현재 안내', '본문', 2, CURRENT_TIMESTAMP, NULL),
    (3, 21, 'LOCAL_CAUTION', 'CAUTION', NULL, 'CITY', '교토', 'WHOLE_CITY', '도시 전역',
     '예정 안내', '본문', 3, CURRENT_TIMESTAMP + INTERVAL '1 day', NULL);
INSERT INTO pg_temp.safety_source SELECT id, 1, CURRENT_TIMESTAMP FROM pg_temp.safety_item;
