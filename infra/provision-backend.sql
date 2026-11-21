-- 동시 실행해도 같은 역할을 한 번만 생성하도록 이 트랜잭션 안에서 직렬화함.
SELECT pg_advisory_xact_lock(hashtextextended('tripee_backend provisioning', 0));

DO $$
BEGIN
    IF EXISTS (
        SELECT FROM pg_roles
        WHERE rolname = 'tripee_backend'
          AND (shobj_description(oid, 'pg_authid') IS DISTINCT FROM 'TripeE local backend login'
               OR rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'tripee_backend already exists with a different owner or role configuration';
    END IF;
END $$;

SELECT NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'tripee_backend') AS create_role \gset
\if :create_role
    \getenv app_password TRIPEE_DB_PASSWORD
    CREATE ROLE tripee_backend LOGIN PASSWORD :'app_password';
    COMMENT ON ROLE tripee_backend IS 'TripeE local backend login';
\endif

-- 공개 조회에 필요한 열을 허용함. 저장 키와 내부 근거 메모는 DB 권한에서도 제외함.
GRANT USAGE ON SCHEMA tripee TO tripee_backend;
CREATE TABLE IF NOT EXISTS tripee.photo_cleanup_job (
    storage_key varchar(512) PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GRANT SELECT, INSERT, DELETE ON tripee.photo_cleanup_job TO tripee_backend;
GRANT SELECT (id, login_id, display_name, password_hash, is_active)
    ON tripee.admin_account TO tripee_backend;
GRANT SELECT ON tripee.city_revision, tripee.content_source, tripee.source_document,
    tripee.safety_revision, tripee.safety_source TO tripee_backend;
GRANT SELECT (city_id, usage_note) ON tripee.photo_asset TO tripee_backend;
GRANT SELECT (storage_key) ON tripee.photo_asset TO tripee_backend;
GRANT INSERT ON tripee.photo_asset TO tripee_backend;
-- 사진 소속 검증 트리거가 FOR KEY SHARE로 행을 잠글 수 있도록 함.
-- city_id 변경은 ck_stable_ownership 트리거에서 거부함.
GRANT UPDATE (city_id) ON tripee.photo_asset TO tripee_backend;
GRANT USAGE ON SEQUENCE tripee.photo_asset_id_seq TO tripee_backend;
GRANT INSERT, UPDATE ON tripee.city, tripee.city_revision TO tripee_backend;
GRANT INSERT ON tripee.audit_event TO tripee_backend;
GRANT SELECT ON tripee.audit_event TO tripee_backend;
GRANT INSERT ON tripee.source_document TO tripee_backend;
GRANT USAGE ON SEQUENCE tripee.source_document_id_seq TO tripee_backend;
GRANT INSERT, DELETE ON tripee.revision_tag, tripee.revision_photo,
    tripee.content_item, tripee.content_source TO tripee_backend;
GRANT UPDATE ON tripee.content_item TO tripee_backend;
GRANT INSERT, UPDATE ON tripee.safety_revision TO tripee_backend;
GRANT INSERT, DELETE ON tripee.safety_item, tripee.safety_source TO tripee_backend;
GRANT USAGE ON SEQUENCE tripee.safety_revision_id_seq, tripee.safety_item_id_seq TO tripee_backend;
GRANT DELETE ON tripee.city_revision, tripee.safety_revision, tripee.city, tripee.photo_asset TO tripee_backend;
GRANT USAGE ON SEQUENCE tripee.revision_photo_id_seq, tripee.content_item_id_seq TO tripee_backend;
GRANT USAGE ON SEQUENCE tripee.city_id_seq, tripee.city_revision_id_seq, tripee.audit_event_id_seq TO tripee_backend;
GRANT SELECT ON tripee.city, tripee.country, tripee.tag TO tripee_backend;
GRANT SELECT, INSERT, UPDATE, DELETE ON tripee.city_edit_lock TO tripee_backend;
GRANT SELECT (id, city_id, name_ko, name_en, latitude, longitude, tagline, published_at, edit_version)
    ON tripee.city_revision TO tripee_backend;
GRANT SELECT ON tripee.revision_tag, tripee.content_item, tripee.safety_item TO tripee_backend;
GRANT SELECT (id, source_url, creator_name, license_name, license_url, rights_checked_at)
    ON tripee.photo_asset TO tripee_backend;
GRANT SELECT (id, revision_id, photo_asset_id, display_order, alt_text, caption)
    ON tripee.revision_photo TO tripee_backend;
GRANT SELECT (content_item_id, source_document_id, checked_at)
    ON tripee.content_source TO tripee_backend;
GRANT SELECT (id, url, title, publisher) ON tripee.source_document TO tripee_backend;
GRANT SELECT (id, review_state, published_at) ON tripee.safety_revision TO tripee_backend;
GRANT SELECT (safety_item_id, source_document_id, checked_at)
    ON tripee.safety_source TO tripee_backend;
