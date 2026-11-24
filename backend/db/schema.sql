-- PostgreSQL 초기 DDL임. 빈 데이터베이스에 한 번 적용하는 용도로 작성했음.
-- 인증·게시·삭제 트랜잭션은 서비스에서 처리해야 함. docs/backend/db-constraints-v1.md를 참고함.
BEGIN;
CREATE SCHEMA tripee;
SET LOCAL search_path = tripee, public;

CREATE TABLE photo_cleanup_job (
    storage_key varchar(512) PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_account (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id varchar(64) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name varchar(80) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_admin_login UNIQUE (login_id),
    CONSTRAINT ck_admin_login CHECK (login_id ~ '^[a-z0-9][a-z0-9._-]{2,63}$'),
    CONSTRAINT ck_admin_hash CHECK (password_hash ~ '[^[:space:]]'),
    CONSTRAINT ck_admin_name CHECK (display_name ~ '[^[:space:]]')
);

CREATE TABLE country (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code varchar(2) NOT NULL,
    name_ko varchar(150) NOT NULL,
    name_en varchar(150) NOT NULL,
    CONSTRAINT uq_country_code UNIQUE (code),
    CONSTRAINT ck_country_code CHECK (code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_country_names CHECK (name_ko ~ '[^[:space:]]' AND name_en ~ '[^[:space:]]')
);

CREATE TABLE tag (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code varchar(50) NOT NULL,
    label_ko varchar(64) NOT NULL,
    label_en varchar(64) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_tag_code UNIQUE (code),
    CONSTRAINT uq_tag_label UNIQUE (label_ko),
    CONSTRAINT ck_tag_code CHECK (code ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_tag_label CHECK (label_ko = btrim(label_ko) AND label_ko ~ '[^[:space:]]'),
    CONSTRAINT ck_tag_label_en CHECK (label_en = btrim(label_en) AND label_en ~ '[^[:space:]]')
);
CREATE UNIQUE INDEX uq_tag_label_en ON tag(lower(label_en));

CREATE TABLE source_document (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    url varchar(4096) NOT NULL,
    title varchar(500) NOT NULL,
    publisher varchar(200),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_source_url CHECK (url ~ '^https?://[^[:space:]]+$'),
    CONSTRAINT ck_source_title CHECK (title ~ '[^[:space:]]'),
    CONSTRAINT ck_source_publisher CHECK (publisher IS NULL OR publisher ~ '[^[:space:]]')
);

CREATE TABLE city (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    country_id bigint NOT NULL REFERENCES country(id) ON DELETE RESTRICT,
    visibility varchar(12) NOT NULL DEFAULT 'HIDDEN',
    published_revision_id bigint,
    draft_revision_id bigint,
    published_safety_revision_id bigint,
    CONSTRAINT ck_city_visibility CHECK (visibility IN ('HIDDEN', 'PREPARING', 'PUBLIC')),
    CONSTRAINT ck_city_public_pointers CHECK (visibility <> 'PUBLIC' OR
        (published_revision_id IS NOT NULL AND published_safety_revision_id IS NOT NULL)),
    CONSTRAINT ck_city_preparing_pointer CHECK (visibility <> 'PREPARING' OR
        published_revision_id IS NOT NULL OR draft_revision_id IS NOT NULL),
    CONSTRAINT ck_city_distinct_pointers CHECK (published_revision_id IS NULL OR draft_revision_id IS NULL
        OR published_revision_id <> draft_revision_id)
);

CREATE TABLE safety_revision (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    city_id bigint NOT NULL REFERENCES city(id) ON DELETE NO ACTION,
    base_revision_id bigint,
    review_state varchar(12) NOT NULL DEFAULT 'UNVERIFIED',
    created_by bigint NOT NULL REFERENCES admin_account(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at timestamptz,
    published_by bigint REFERENCES admin_account(id) ON DELETE RESTRICT,
    CONSTRAINT uq_safety_city_id UNIQUE (city_id, id),
    CONSTRAINT ck_safety_review CHECK (review_state IN ('UNVERIFIED', 'PARTIAL', 'CHECKED')),
    CONSTRAINT ck_safety_not_self CHECK (base_revision_id IS NULL OR base_revision_id <> id),
    CONSTRAINT ck_safety_publication_pair CHECK ((published_at IS NULL) = (published_by IS NULL)),
    CONSTRAINT ck_safety_publication_time CHECK (published_at IS NULL OR published_at >= created_at),
    CONSTRAINT fk_safety_base FOREIGN KEY (city_id, base_revision_id)
        REFERENCES safety_revision(city_id, id) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE city_revision (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    city_id bigint NOT NULL REFERENCES city(id) ON DELETE NO ACTION,
    revision_no integer NOT NULL,
    name_ko varchar(150),
    name_en varchar(150),
    latitude numeric(9,6),
    longitude numeric(9,6),
    tagline varchar(200),
    base_safety_revision_id bigint,
    draft_safety_revision_id bigint,
    safety_revision_at_publish_id bigint,
    edit_version bigint NOT NULL DEFAULT 0,
    created_by bigint NOT NULL REFERENCES admin_account(id) ON DELETE RESTRICT,
    updated_by bigint NOT NULL REFERENCES admin_account(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at timestamptz,
    published_by bigint REFERENCES admin_account(id) ON DELETE RESTRICT,
    CONSTRAINT uq_revision_number UNIQUE (city_id, revision_no),
    CONSTRAINT uq_revision_city_id UNIQUE (city_id, id),
    CONSTRAINT ck_revision_number CHECK (revision_no > 0),
    CONSTRAINT ck_revision_edit_version CHECK (edit_version >= 0),
    CONSTRAINT ck_revision_names CHECK ((name_ko IS NULL OR name_ko ~ '[^[:space:]]')
        AND (name_en IS NULL OR name_en ~ '[^[:space:]]')),
    CONSTRAINT ck_revision_tagline CHECK (tagline IS NULL OR tagline ~ '[^[:space:]]'),
    CONSTRAINT ck_revision_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_revision_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_revision_coordinate_pair CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_revision_update_time CHECK (updated_at >= created_at),
    CONSTRAINT ck_revision_publication_pair CHECK ((published_at IS NULL) = (published_by IS NULL)),
    CONSTRAINT ck_revision_publication_time CHECK (published_at IS NULL OR published_at >= created_at),
    CONSTRAINT ck_revision_publication_shape CHECK (
        (published_at IS NULL AND safety_revision_at_publish_id IS NULL) OR
        (published_at IS NOT NULL AND safety_revision_at_publish_id IS NOT NULL
            AND draft_safety_revision_id IS NULL AND name_ko IS NOT NULL
            AND name_en IS NOT NULL AND latitude IS NOT NULL AND longitude IS NOT NULL
            AND tagline IS NOT NULL)),
    CONSTRAINT fk_revision_base_safety FOREIGN KEY (city_id, base_safety_revision_id)
        REFERENCES safety_revision(city_id, id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_revision_draft_safety FOREIGN KEY (city_id, draft_safety_revision_id)
        REFERENCES safety_revision(city_id, id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_revision_published_safety FOREIGN KEY (city_id, safety_revision_at_publish_id)
        REFERENCES safety_revision(city_id, id) DEFERRABLE INITIALLY DEFERRED
);
CREATE UNIQUE INDEX uq_revision_one_draft ON city_revision(city_id) WHERE published_at IS NULL;
CREATE UNIQUE INDEX uq_revision_safety_draft_owner ON city_revision(draft_safety_revision_id)
    WHERE draft_safety_revision_id IS NOT NULL;

ALTER TABLE city
    ADD CONSTRAINT fk_city_published FOREIGN KEY (id, published_revision_id)
        REFERENCES city_revision(city_id, id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_city_draft FOREIGN KEY (id, draft_revision_id)
        REFERENCES city_revision(city_id, id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_city_safety FOREIGN KEY (id, published_safety_revision_id)
        REFERENCES safety_revision(city_id, id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE revision_tag (
    revision_id bigint NOT NULL REFERENCES city_revision(id) ON DELETE CASCADE,
    tag_id bigint NOT NULL REFERENCES tag(id) ON DELETE RESTRICT,
    PRIMARY KEY (revision_id, tag_id)
);

CREATE TABLE photo_asset (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    city_id bigint NOT NULL REFERENCES city(id) ON DELETE NO ACTION,
    storage_key varchar(512) NOT NULL,
    source_url varchar(4096) NOT NULL,
    creator_name varchar(200),
    license_name varchar(200),
    license_url varchar(4096),
    usage_note varchar(2000),
    rights_checked_at timestamptz,
    CONSTRAINT uq_photo_storage UNIQUE (storage_key),
    CONSTRAINT ck_photo_storage CHECK (storage_key ~ '[^[:space:]]'),
    CONSTRAINT ck_photo_source_url CHECK (source_url ~ '^https?://[^[:space:]]+$'),
    CONSTRAINT ck_photo_license_url CHECK (license_url IS NULL OR license_url ~ '^https?://[^[:space:]]+$'),
    CONSTRAINT ck_photo_creator CHECK (creator_name IS NULL OR creator_name ~ '[^[:space:]]'),
    CONSTRAINT ck_photo_license CHECK (license_name IS NULL OR license_name ~ '[^[:space:]]'),
    CONSTRAINT ck_photo_usage CHECK (usage_note IS NULL OR usage_note ~ '[^[:space:]]'),
    CONSTRAINT ck_photo_rights CHECK (rights_checked_at IS NULL OR license_name IS NOT NULL OR usage_note IS NOT NULL)
);

CREATE TABLE revision_photo (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    revision_id bigint NOT NULL REFERENCES city_revision(id) ON DELETE CASCADE,
    photo_asset_id bigint NOT NULL REFERENCES photo_asset(id) ON DELETE RESTRICT,
    display_order integer NOT NULL,
    alt_text varchar(500),
    caption varchar(1000),
    CONSTRAINT uq_revision_photo_asset UNIQUE (revision_id, photo_asset_id),
    CONSTRAINT uq_revision_photo_order UNIQUE (revision_id, display_order) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT ck_revision_photo_order CHECK (display_order > 0),
    CONSTRAINT ck_photo_alt CHECK (alt_text IS NULL OR alt_text ~ '[^[:space:]]'),
    CONSTRAINT ck_photo_caption CHECK (caption IS NULL OR caption ~ '[^[:space:]]')
);

CREATE TABLE content_item (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    revision_id bigint NOT NULL REFERENCES city_revision(id) ON DELETE CASCADE,
    kind varchar(20) NOT NULL,
    title varchar(200),
    body varchar(3000),
    display_order integer NOT NULL,
    is_selected boolean NOT NULL DEFAULT false,
    CONSTRAINT ck_content_kind CHECK (kind IN ('EXPERIENCE','CONSIDERATION','VISIT_PERIOD','ARRIVAL','LOCAL_TRANSPORT')),
    CONSTRAINT ck_content_title CHECK (title IS NULL OR title ~ '[^[:space:]]'),
    CONSTRAINT ck_content_body CHECK (body IS NULL OR body ~ '[^[:space:]]'),
    CONSTRAINT ck_content_order CHECK (display_order > 0),
    CONSTRAINT uq_content_order UNIQUE (revision_id, kind, display_order) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE content_source (
    content_item_id bigint NOT NULL REFERENCES content_item(id) ON DELETE CASCADE,
    source_document_id bigint NOT NULL REFERENCES source_document(id) ON DELETE RESTRICT,
    evidence_note varchar(4000),
    checked_at timestamptz,
    checked_by bigint REFERENCES admin_account(id) ON DELETE RESTRICT,
    PRIMARY KEY (content_item_id, source_document_id),
    CONSTRAINT ck_content_evidence CHECK (evidence_note IS NULL OR evidence_note ~ '[^[:space:]]'),
    CONSTRAINT ck_content_checked_pair CHECK ((checked_at IS NULL) = (checked_by IS NULL)),
    CONSTRAINT ck_content_checked_evidence CHECK (checked_at IS NULL OR evidence_note IS NOT NULL)
);

CREATE TABLE safety_item (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    safety_revision_id bigint NOT NULL REFERENCES safety_revision(id) ON DELETE CASCADE,
    kind varchar(20) NOT NULL,
    assessment varchar(12) NOT NULL,
    official_level varchar(10),
    scope_type varchar(20),
    original_scope_text varchar(4000),
    city_coverage varchar(20),
    city_scope_note varchar(4000),
    title varchar(200),
    body varchar(3000),
    display_order integer NOT NULL,
    effective_from timestamptz,
    effective_until timestamptz,
    CONSTRAINT ck_safety_kind CHECK (kind IN ('OFFICIAL_ADVISORY','LOCAL_CAUTION')),
    CONSTRAINT ck_safety_assessment CHECK (assessment IN ('ISSUED','NOT_ISSUED','CAUTION')),
    CONSTRAINT ck_safety_level CHECK (official_level IN ('LEVEL_1','LEVEL_2','LEVEL_3','LEVEL_4','SPECIAL')),
    CONSTRAINT ck_safety_assessment_shape CHECK (
        (kind='OFFICIAL_ADVISORY' AND assessment='ISSUED' AND official_level IS NOT NULL) OR
        (kind='OFFICIAL_ADVISORY' AND assessment='NOT_ISSUED' AND official_level IS NULL) OR
        (kind='LOCAL_CAUTION' AND assessment='CAUTION' AND official_level IS NULL)),
    CONSTRAINT ck_safety_scope CHECK (scope_type IN ('COUNTRY_WIDE','REGION','CITY','SPECIFIC_PLACE')),
    CONSTRAINT ck_safety_coverage CHECK (city_coverage IN ('WHOLE_CITY','PART_OF_CITY')),
    CONSTRAINT ck_safety_original_scope CHECK (original_scope_text IS NULL OR original_scope_text ~ '[^[:space:]]'),
    CONSTRAINT ck_safety_city_scope CHECK (city_scope_note IS NULL OR city_scope_note ~ '[^[:space:]]'),
    CONSTRAINT ck_safety_title CHECK (title IS NULL OR title ~ '[^[:space:]]'),
    CONSTRAINT ck_safety_body CHECK (body IS NULL OR body ~ '[^[:space:]]'),
    CONSTRAINT ck_safety_effective CHECK (effective_from IS NULL OR effective_until IS NULL OR effective_until > effective_from),
    CONSTRAINT ck_safety_order CHECK (display_order > 0),
    CONSTRAINT uq_safety_order UNIQUE (safety_revision_id, display_order) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE safety_source (
    safety_item_id bigint NOT NULL REFERENCES safety_item(id) ON DELETE CASCADE,
    source_document_id bigint NOT NULL REFERENCES source_document(id) ON DELETE RESTRICT,
    evidence_note varchar(4000),
    checked_at timestamptz,
    checked_by bigint REFERENCES admin_account(id) ON DELETE RESTRICT,
    PRIMARY KEY (safety_item_id, source_document_id),
    CONSTRAINT ck_safety_evidence CHECK (evidence_note IS NULL OR evidence_note ~ '[^[:space:]]'),
    CONSTRAINT ck_safety_checked_pair CHECK ((checked_at IS NULL) = (checked_by IS NULL)),
    CONSTRAINT ck_safety_checked_evidence CHECK (checked_at IS NULL OR evidence_note IS NOT NULL)
);

CREATE TABLE city_edit_lock (
    city_id bigint PRIMARY KEY REFERENCES city(id) ON DELETE CASCADE,
    owner_admin_id bigint NOT NULL REFERENCES admin_account(id) ON DELETE RESTRICT,
    editor_instance_id uuid NOT NULL,
    lease_token_hash varchar(64) NOT NULL,
    acquired_at timestamptz NOT NULL,
    last_heartbeat_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT uq_lock_token UNIQUE (lease_token_hash),
    CONSTRAINT ck_lock_token CHECK (lease_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_lock_times CHECK (last_heartbeat_at >= acquired_at
        AND expires_at = last_heartbeat_at + interval '5 minutes')
);

CREATE TABLE audit_event (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id bigint NOT NULL REFERENCES admin_account(id) ON DELETE RESTRICT,
    city_ref_id bigint NOT NULL,
    revision_ref_id bigint,
    safety_revision_ref_id bigint,
    action varchar(24) NOT NULL,
    snapshot_schema_version integer NOT NULL DEFAULT 1,
    before_data jsonb,
    after_data jsonb,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_refs CHECK (city_ref_id > 0 AND (revision_ref_id IS NULL OR revision_ref_id > 0)
        AND (safety_revision_ref_id IS NULL OR safety_revision_ref_id > 0)),
    CONSTRAINT ck_audit_action CHECK (action IN ('CREATE_DRAFT','UPDATE_DRAFT','DISCARD_DRAFT','PUBLISH',
        'SAFETY_PUBLISH','UNPUBLISH','SET_VISIBILITY','DELETE')),
    CONSTRAINT ck_audit_schema CHECK (snapshot_schema_version > 0),
    CONSTRAINT ck_audit_before_object CHECK (before_data IS NULL OR jsonb_typeof(before_data)='object'),
    CONSTRAINT ck_audit_after_object CHECK (after_data IS NULL OR jsonb_typeof(after_data)='object'),
    CONSTRAINT ck_audit_has_data CHECK (before_data IS NOT NULL OR after_data IS NOT NULL),
    CONSTRAINT ck_audit_action_data CHECK (
        (action IN ('CREATE_DRAFT','PUBLISH','SAFETY_PUBLISH') AND after_data IS NOT NULL) OR
        (action IN ('DELETE','DISCARD_DRAFT') AND before_data IS NOT NULL AND after_data IS NULL) OR
        (action IN ('UPDATE_DRAFT','UNPUBLISH','SET_VISIBILITY') AND before_data IS NOT NULL AND after_data IS NOT NULL))
);

-- Targeted indexes, not an index on every column. PK/UNIQUE indexes already exist.
CREATE INDEX ix_city_country ON city(country_id);
CREATE INDEX ix_city_visible ON city(visibility) WHERE visibility <> 'HIDDEN';
CREATE INDEX ix_revision_tag_tag ON revision_tag(tag_id);
CREATE INDEX ix_photo_city ON photo_asset(city_id);
CREATE INDEX ix_revision_photo_asset ON revision_photo(photo_asset_id);
CREATE INDEX ix_content_source_document ON content_source(source_document_id);
CREATE INDEX ix_safety_source_document ON safety_source(source_document_id);
CREATE INDEX ix_safety_base ON safety_revision(base_revision_id) WHERE base_revision_id IS NOT NULL;
CREATE INDEX ix_revision_base_safety ON city_revision(base_safety_revision_id) WHERE base_safety_revision_id IS NOT NULL;
CREATE INDEX ix_revision_history_safety ON city_revision(safety_revision_at_publish_id) WHERE safety_revision_at_publish_id IS NOT NULL;
CREATE INDEX ix_audit_city_time ON audit_event(city_ref_id, occurred_at DESC, id DESC);
CREATE INDEX ix_audit_actor_time ON audit_event(actor_id, occurred_at DESC);
CREATE INDEX ix_lock_expiry ON city_edit_lock(expires_at);

-- Stable ownership is required for the cross-table photo check to remain valid.
CREATE FUNCTION prevent_identity_reassignment() RETURNS trigger
LANGUAGE plpgsql SET search_path = tripee, pg_temp AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR
       (to_jsonb(NEW)->TG_ARGV[0]) IS DISTINCT FROM (to_jsonb(OLD)->TG_ARGV[0]) THEN
        RAISE EXCEPTION 'identity/ownership cannot be reassigned on %', TG_TABLE_NAME
            USING ERRCODE='23514', CONSTRAINT='ck_stable_ownership';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER city_stable_identity BEFORE UPDATE ON city
    FOR EACH ROW EXECUTE FUNCTION prevent_identity_reassignment('country_id');
CREATE TRIGGER revision_stable_identity BEFORE UPDATE ON city_revision
    FOR EACH ROW EXECUTE FUNCTION prevent_identity_reassignment('city_id');
CREATE TRIGGER safety_stable_identity BEFORE UPDATE ON safety_revision
    FOR EACH ROW EXECUTE FUNCTION prevent_identity_reassignment('city_id');
CREATE TRIGGER photo_stable_identity BEFORE UPDATE ON photo_asset
    FOR EACH ROW EXECUTE FUNCTION prevent_identity_reassignment('city_id');

CREATE FUNCTION check_photo_city() RETURNS trigger
LANGUAGE plpgsql SET search_path = tripee, pg_temp AS $$
DECLARE revision_city bigint; photo_city bigint;
BEGIN
    SELECT city_id INTO revision_city FROM city_revision WHERE id=NEW.revision_id FOR KEY SHARE;
    SELECT city_id INTO photo_city FROM photo_asset WHERE id=NEW.photo_asset_id FOR KEY SHARE;
    IF revision_city IS NOT NULL AND photo_city IS NOT NULL AND revision_city <> photo_city THEN
        RAISE EXCEPTION 'photo asset belongs to a different city'
            USING ERRCODE='23514', CONSTRAINT='ck_revision_photo_city';
    END IF;
    RETURN NEW; -- missing rows are rejected by the ordinary FKs
END;
$$;
CREATE TRIGGER revision_photo_city BEFORE INSERT OR UPDATE ON revision_photo
    FOR EACH ROW EXECUTE FUNCTION check_photo_city();

-- Deferred so revision marking and pointer swapping can occur in one transaction.
-- This guards pointer states only, not content completeness or authenticated publication.
CREATE FUNCTION check_city_pointer_states() RETURNS trigger
LANGUAGE plpgsql SET search_path = tripee, pg_temp AS $$
DECLARE target_city bigint; c city%ROWTYPE;
BEGIN
    IF TG_TABLE_NAME='city' THEN target_city := COALESCE(NEW.id, OLD.id);
    ELSE target_city := COALESCE(NEW.city_id, OLD.city_id); END IF;
    SELECT * INTO c FROM city WHERE id=target_city;
    IF NOT FOUND THEN RETURN NULL; END IF; -- full physical deletion
    IF c.published_revision_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM city_revision WHERE id=c.published_revision_id AND city_id=c.id AND published_at IS NOT NULL) THEN
        RAISE EXCEPTION 'published pointer must reference a published city revision'
            USING ERRCODE='23514', CONSTRAINT='ck_city_pointer_states';
    END IF;
    IF c.draft_revision_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM city_revision WHERE id=c.draft_revision_id AND city_id=c.id AND published_at IS NULL) THEN
        RAISE EXCEPTION 'draft pointer must reference an unpublished city revision'
            USING ERRCODE='23514', CONSTRAINT='ck_city_pointer_states';
    END IF;
    IF EXISTS (SELECT 1 FROM city_revision WHERE city_id=c.id AND published_at IS NULL
        AND id IS DISTINCT FROM c.draft_revision_id) THEN
        RAISE EXCEPTION 'unpublished city revision must be the current draft'
            USING ERRCODE='23514', CONSTRAINT='ck_city_pointer_states';
    END IF;
    IF c.published_safety_revision_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM safety_revision WHERE id=c.published_safety_revision_id AND city_id=c.id AND published_at IS NOT NULL) THEN
        RAISE EXCEPTION 'safety pointer must reference a published safety revision'
            USING ERRCODE='23514', CONSTRAINT='ck_city_pointer_states';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER city_pointer_states AFTER INSERT OR UPDATE ON city
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_city_pointer_states();
CREATE CONSTRAINT TRIGGER revision_pointer_states AFTER INSERT OR UPDATE OR DELETE ON city_revision
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_city_pointer_states();
CREATE CONSTRAINT TRIGGER safety_pointer_states AFTER INSERT OR UPDATE OR DELETE ON safety_revision
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_city_pointer_states();

CREATE FUNCTION reject_audit_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit events are append-only'
        USING ERRCODE='23514', CONSTRAINT='ck_audit_append_only';
END;
$$;
CREATE TRIGGER audit_no_update_delete BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
CREATE TRIGGER audit_no_truncate BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_mutation();

COMMENT ON TABLE audit_event IS 'Historical snapshots. city_ref_id and revision refs intentionally have no FK.';
COMMENT ON COLUMN city_revision.edit_version IS 'Service must use compare-and-swap and increment on every draft mutation.';
COMMENT ON TABLE city_edit_lock IS '1-minute heartbeat / 5-minute lease. Expiry and session validity checked at request time, not by a time-dependent CHECK.';
COMMIT;
