-- 실제 앱 권한과 열 자료형을 사용하며, 테스트 트랜잭션 안에서만 임시 테이블을 조회함.
CREATE TEMP TABLE country AS
    SELECT id, code, name_ko, name_en FROM tripee.country WITH NO DATA;
CREATE TEMP TABLE city AS
    SELECT id, country_id, visibility, published_revision_id, draft_revision_id
    FROM tripee.city WITH NO DATA;
CREATE TEMP TABLE city_revision AS
    SELECT id, name_ko, name_en, latitude, longitude FROM tripee.city_revision WITH NO DATA;

INSERT INTO pg_temp.country VALUES (1, 'JP', '일본', 'Japan'), (2, 'IT', '이탈리아', 'Italy');
INSERT INTO pg_temp.city VALUES
    (1, 1, 'PUBLIC', 10, 11), (2, 2, 'PREPARING', NULL, 20),
    (3, 1, 'HIDDEN', NULL, 30), (4, 1, 'PREPARING', NULL, 40);
INSERT INTO pg_temp.city_revision VALUES
    (10, '교토', 'Kyoto', 35.01, 135.77),
    (11, '미공개 이름', 'Unpublished name', 35, 135),
    (20, '나폴리', 'Naples', 40.85, 14.27),
    (30, '숨긴 도시', 'Hidden city', 35, 135),
    (40, NULL, NULL, NULL, NULL);
