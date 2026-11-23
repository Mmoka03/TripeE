import { PGlite } from '@electric-sql/pglite';
import { readFile, writeFile } from 'node:fs/promises';

const db = new PGlite();
await db.exec(await readFile(new URL('../../backend/db/schema.sql', import.meta.url), 'utf8'));
await db.exec('SET search_path = tripee, public');
const version = (await db.query('SELECT version()')).rows[0].version;
await db.exec(`BEGIN;
INSERT INTO admin_account(id,login_id,password_hash,display_name) OVERRIDING SYSTEM VALUE VALUES
 (1,'admin_a','test-placeholder-not-a-real-hash','A'),(2,'admin_b','test-placeholder-not-a-real-hash','B');
INSERT INTO country(id,code,name_ko,name_en) OVERRIDING SYSTEM VALUE VALUES
 (1,'JP','일본','Japan'),(2,'IT','이탈리아','Italy');
INSERT INTO city(id,country_id) OVERRIDING SYSTEM VALUE VALUES (1,1),(2,2);
INSERT INTO safety_revision(id,city_id,created_by,published_at,published_by) OVERRIDING SYSTEM VALUE VALUES
 (201,1,1,CURRENT_TIMESTAMP,1),(202,2,1,CURRENT_TIMESTAMP,1);
INSERT INTO city_revision(id,city_id,revision_no,name_ko,name_en,latitude,longitude,tagline,created_by,updated_by)
 OVERRIDING SYSTEM VALUE VALUES
 (101,1,1,'교토','Kyoto',35,135,'역사 여행',1,1),(102,2,1,'나폴리','Naples',40,14,'항구 도시',1,1);
UPDATE city SET draft_revision_id=101,published_safety_revision_id=201 WHERE id=1;
UPDATE city SET draft_revision_id=102,published_safety_revision_id=202 WHERE id=2;
INSERT INTO tag(id,code,label_ko,label_en) OVERRIDING SYSTEM VALUE VALUES(1,'HISTORY','역사','History'),(2,'FOOD','미식','Food');
INSERT INTO revision_tag VALUES(101,1);
INSERT INTO photo_asset(id,city_id,storage_key,source_url,license_name,rights_checked_at) OVERRIDING SYSTEM VALUE VALUES
 (1,1,'cities/1/photo-a.jpg','https://example.com/a','test-license',CURRENT_TIMESTAMP),
 (2,1,'cities/1/photo-b.jpg','https://example.com/b','test-license',CURRENT_TIMESTAMP),
 (3,2,'cities/2/photo-c.jpg','https://example.com/c','test-license',CURRENT_TIMESTAMP);
INSERT INTO revision_photo(id,revision_id,photo_asset_id,display_order,alt_text) OVERRIDING SYSTEM VALUE VALUES
 (1,101,1,1,'교토 사진'),(2,101,2,2,'다른 교토 사진');
INSERT INTO source_document(id,url,title) OVERRIDING SYSTEM VALUE VALUES(1,'https://example.com/source','Test source');
INSERT INTO content_item(id,revision_id,kind,title,body,display_order,is_selected) OVERRIDING SYSTEM VALUE VALUES
 (1,101,'EXPERIENCE','첫 경험','설명',1,true),(2,101,'EXPERIENCE','다음 경험','설명',2,true);
INSERT INTO content_source VALUES(1,1,'근거 메모',CURRENT_TIMESTAMP,1);
INSERT INTO safety_item(id,safety_revision_id,kind,assessment,official_level,scope_type,original_scope_text,
 city_coverage,city_scope_note,title,body,display_order) OVERRIDING SYSTEM VALUE VALUES
 (1,201,'OFFICIAL_ADVISORY','NOT_ISSUED',NULL,'CITY','교토','WHOLE_CITY','도시 전역','테스트','실제 경보가 아닌 테스트 자료',1);
INSERT INTO safety_source VALUES(1,1,'테스트 근거',CURRENT_TIMESTAMP,1);
INSERT INTO audit_event(id,actor_id,city_ref_id,action,after_data) OVERRIDING SYSTEM VALUE
 VALUES(1,1,1,'PUBLISH','{"city":"교토","test_fixture":true}');
COMMIT;`);
// 테스트에서 ID를 직접 지정했으므로 자동 생성 시퀀스를 별도로 올려둠.
for (const table of [
  'admin_account',
  'country',
  'city',
  'safety_revision',
  'city_revision',
  'tag',
  'photo_asset',
  'revision_photo',
  'source_document',
  'content_item',
  'safety_item',
  'audit_event',
]) {
  await db.query("SELECT setval(pg_get_serial_sequence($1, 'id'), 1000, true)", [
    'tripee.' + table,
  ]);
}

const cases = [];
const reject = (name, sql, code, constraint) => cases.push({ name, sql, code, constraint });
const accept = (name, sql, assertion) => cases.push({ name, sql, assertion });

reject(
  '국가 코드 중복',
  `INSERT INTO country(code,name_ko,name_en) VALUES('JP','일본','Japan')`,
  '23505',
  'uq_country_code',
);
reject('국가 코드 형식', `UPDATE country SET code='jp' WHERE id=1`, '23514', 'ck_country_code');
reject(
  '공백 이름 거부',
  `UPDATE country SET name_ko=E' \t\n ' WHERE id=1`,
  '23514',
  'ck_country_names',
);
reject(
  '존재하지 않는 국가',
  `UPDATE city SET country_id=999 WHERE id=1`,
  '23514',
  'ck_stable_ownership',
);
reject('신규 도시 국가 FK', `INSERT INTO city(country_id) VALUES(999)`, '23503');
reject(
  '관리자 대문자 로그인 거부',
  `UPDATE admin_account SET login_id='Admin_A' WHERE id=1`,
  '23514',
  'ck_admin_login',
);
reject(
  '관리자 로그인 중복',
  `UPDATE admin_account SET login_id='admin_a' WHERE id=2`,
  '23505',
  'uq_admin_login',
);
reject(
  '공백 비밀번호 해시 거부',
  `UPDATE admin_account SET password_hash=' ' WHERE id=1`,
  '23514',
  'ck_admin_hash',
);
reject('태그 코드 중복', `UPDATE tag SET code='HISTORY' WHERE id=2`, '23505', 'uq_tag_code');
reject('태그 영문명 필수', `UPDATE tag SET label_en=NULL WHERE id=1`, '23502');
reject(
  '태그 영문명 공백 금지',
  `UPDATE tag SET label_en='   ' WHERE id=1`,
  '23514',
  'ck_tag_label_en',
);
reject(
  '태그 영문명 대소문자 중복',
  `UPDATE tag SET label_en='HISTORY' WHERE id=2`,
  '23505',
  'uq_tag_label_en',
);
reject('태그 연결 중복', `INSERT INTO revision_tag VALUES(101,1)`, '23505');
reject('참조 태그 삭제 차단', `DELETE FROM tag WHERE id=1`, '23001');
reject(
  '위도 범위',
  `UPDATE city_revision SET latitude=91 WHERE id=101`,
  '23514',
  'ck_revision_latitude',
);
reject(
  '경도 범위',
  `UPDATE city_revision SET longitude=-181 WHERE id=101`,
  '23514',
  'ck_revision_longitude',
);
reject(
  '좌표 NaN 거부',
  `UPDATE city_revision SET latitude='NaN' WHERE id=101`,
  '23514',
  'ck_revision_latitude',
);
reject(
  '좌표 한쪽 누락',
  `UPDATE city_revision SET latitude=NULL WHERE id=101`,
  '23514',
  'ck_revision_coordinate_pair',
);
reject(
  '빈 소개 문자열 거부',
  `UPDATE city_revision SET tagline='' WHERE id=101`,
  '23514',
  'ck_revision_tagline',
);
accept(
  '미완성 초안 허용',
  `UPDATE city_revision SET tagline=NULL,name_en=NULL,latitude=NULL,longitude=NULL WHERE id=101`,
);
reject(
  '도시당 초안 하나',
  `INSERT INTO city_revision(city_id,revision_no,created_by,updated_by) VALUES(1,2,1,1)`,
  '23505',
  'uq_revision_one_draft',
);
reject(
  '버전 번호 중복',
  `INSERT INTO city_revision(city_id,revision_no,created_by,updated_by) VALUES(1,1,1,1)`,
  '23505',
  'uq_revision_number',
);
reject(
  '음수 편집 버전',
  `UPDATE city_revision SET edit_version=-1 WHERE id=101`,
  '23514',
  'ck_revision_edit_version',
);
reject(
  '다른 도시의 초안 포인터',
  `UPDATE city SET draft_revision_id=102 WHERE id=1`,
  '23503',
  'fk_city_draft',
);
reject(
  '다른 도시의 안전 포인터',
  `UPDATE city SET published_safety_revision_id=202 WHERE id=1`,
  '23503',
  'fk_city_safety',
);
reject(
  '다른 도시의 안전 기반',
  `UPDATE city_revision SET base_safety_revision_id=202 WHERE id=101`,
  '23503',
  'fk_revision_base_safety',
);
reject(
  '자기 안전 버전 기반 금지',
  `UPDATE safety_revision SET base_revision_id=id WHERE id=201`,
  '23514',
  'ck_safety_not_self',
);
reject(
  '공개 포인터 없는 PUBLIC',
  `UPDATE city SET visibility='PUBLIC' WHERE id=1`,
  '23514',
  'ck_city_public_pointers',
);
reject(
  '동일 초안 공개 포인터',
  `UPDATE city SET published_revision_id=101 WHERE id=1`,
  '23514',
  'ck_city_distinct_pointers',
);
reject(
  '미게시 버전을 공개 포인터로 지정',
  `UPDATE city SET published_revision_id=101,draft_revision_id=NULL WHERE id=1`,
  '23514',
  'ck_city_pointer_states',
);
reject(
  '고아 초안 포인터',
  `UPDATE city SET draft_revision_id=NULL WHERE id=1`,
  '23514',
  'ck_city_pointer_states',
);
reject(
  '게시 시각 작업자 쌍',
  `UPDATE safety_revision SET published_by=NULL WHERE id=201`,
  '23514',
  'ck_safety_publication_pair',
);
reject(
  '안전 공개 포인터 상태 역변경',
  `UPDATE safety_revision SET published_at=NULL,published_by=NULL WHERE id=201`,
  '23514',
  'ck_city_pointer_states',
);
reject(
  '사진 다른 도시 연결',
  `INSERT INTO revision_photo(revision_id,photo_asset_id,display_order) VALUES(101,3,3)`,
  '23514',
  'ck_revision_photo_city',
);
reject(
  '사진 소유 도시 변경',
  `UPDATE photo_asset SET city_id=2 WHERE id=1`,
  '23514',
  'ck_stable_ownership',
);
reject(
  '사진 중복 연결',
  `INSERT INTO revision_photo(revision_id,photo_asset_id,display_order) VALUES(101,1,3)`,
  '23505',
  'uq_revision_photo_asset',
);
reject(
  '사진 순서 중복',
  `UPDATE revision_photo SET display_order=1 WHERE id=2`,
  '23505',
  'uq_revision_photo_order',
);
accept(
  '순서 교환 지연 검사',
  `SET CONSTRAINTS uq_revision_photo_order DEFERRED;
 UPDATE revision_photo SET display_order=2 WHERE id=1; UPDATE revision_photo SET display_order=1 WHERE id=2`,
);
reject(
  '순서 교환 후 중복 남으면 거부',
  `SET CONSTRAINTS uq_revision_photo_order DEFERRED;
 UPDATE revision_photo SET display_order=2 WHERE id=1`,
  '23505',
  'uq_revision_photo_order',
);
reject(
  '음수 콘텐츠 순서',
  `UPDATE content_item SET display_order=0 WHERE id=1`,
  '23514',
  'ck_content_order',
);
reject(
  '콘텐츠 종류 오타',
  `UPDATE content_item SET kind='EXPERIENCES' WHERE id=1`,
  '23514',
  'ck_content_kind',
);
accept(
  '대표 경험 3개 초과 저장 가능',
  `INSERT INTO content_item(revision_id,kind,display_order) VALUES
 (101,'EXPERIENCE',3),(101,'EXPERIENCE',4),(101,'EXPERIENCE',5)`,
);
accept('콘텐츠 본문 3000자 허용', `UPDATE content_item SET body=repeat('가',3000) WHERE id=1`);
reject(
  '콘텐츠 본문 3001자 거부',
  `UPDATE content_item SET body=repeat('가',3001) WHERE id=1`,
  '22001',
);
accept('안전 본문 3000자 허용', `UPDATE safety_item SET body=repeat('가',3000) WHERE id=1`);
reject(
  '안전 본문 3001자 거부',
  `UPDATE safety_item SET body=repeat('가',3001) WHERE id=1`,
  '22001',
);
reject(
  '출처 URL 스킴',
  `UPDATE source_document SET url='javascript:alert(1)' WHERE id=1`,
  '23514',
  'ck_source_url',
);
reject(
  '출처 URL 공백',
  `UPDATE source_document SET url='https://example.com/a b' WHERE id=1`,
  '23514',
  'ck_source_url',
);
reject(
  '확인일 확인자 쌍',
  `UPDATE content_source SET checked_by=NULL WHERE content_item_id=1`,
  '23514',
  'ck_content_checked_pair',
);
reject(
  '확인 완료 근거 누락',
  `UPDATE content_source SET evidence_note=NULL WHERE content_item_id=1`,
  '23514',
  'ck_content_checked_evidence',
);
reject(
  '경보 발령에 단계 누락',
  `UPDATE safety_item SET assessment='ISSUED' WHERE id=1`,
  '23514',
  'ck_safety_assessment_shape',
);
reject(
  '경보 미발령에 단계 존재',
  `UPDATE safety_item SET official_level='LEVEL_1' WHERE id=1`,
  '23514',
  'ck_safety_assessment_shape',
);
accept(
  '특별여행주의보 별도 값',
  `UPDATE safety_item SET assessment='ISSUED',official_level='SPECIAL' WHERE id=1`,
);
reject(
  '현지 주의에 공식 단계 혼합',
  `UPDATE safety_item SET kind='LOCAL_CAUTION',assessment='CAUTION',official_level='LEVEL_1' WHERE id=1`,
  '23514',
  'ck_safety_assessment_shape',
);
reject(
  '잘못된 안전 적용 범위',
  `UPDATE safety_item SET city_coverage='SAFE' WHERE id=1`,
  '23514',
  'ck_safety_coverage',
);
reject(
  '안전 종료 이전 시작',
  `UPDATE safety_item SET effective_from='2026-09-28Z',effective_until='2026-09-27Z' WHERE id=1`,
  '23514',
  'ck_safety_effective',
);
const lease = `INSERT INTO city_edit_lock VALUES(1,1,'00000000-0000-4000-8000-000000000001',repeat('a',64),
 CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+interval '5 minutes')`;
accept('편집 잠금 5분 허용', lease);
reject('편집 잠금 중복 차단', `${lease}; ${lease}`, '23505');
reject(
  '편집 잠금 2분 거부',
  lease.replace("interval '5 minutes'", "interval '2 minutes'"),
  '23514',
  'ck_lock_times',
);
accept(
  '만료 행 보관 가능 - 시간 검사는 서비스 책임',
  `INSERT INTO city_edit_lock VALUES(1,1,
 '00000000-0000-4000-8000-000000000001',repeat('b',64),'2020-01-01Z','2020-01-01Z','2020-01-01 00:05Z')`,
);
reject(
  '감사 로그 수정 금지',
  `UPDATE audit_event SET action='DELETE' WHERE id=1`,
  '23514',
  'ck_audit_append_only',
);
reject(
  '감사 로그 삭제 금지',
  `DELETE FROM audit_event WHERE id=1`,
  '23514',
  'ck_audit_append_only',
);
reject('감사 로그 truncate 금지', `TRUNCATE audit_event`, '23514', 'ck_audit_append_only');
reject(
  '감사 JSON 배열 금지',
  `INSERT INTO audit_event(actor_id,city_ref_id,action,after_data) VALUES(1,1,'PUBLISH','[]')`,
  '23514',
  'ck_audit_after_object',
);
reject(
  '감사 JSON null 금지',
  `INSERT INTO audit_event(actor_id,city_ref_id,action,after_data) VALUES(1,1,'PUBLISH','null')`,
  '23514',
  'ck_audit_after_object',
);
reject(
  '삭제 이력 이전 데이터 필수',
  `INSERT INTO audit_event(actor_id,city_ref_id,action,after_data) VALUES(1,1,'DELETE','{}')`,
  '23514',
  'ck_audit_action_data',
);
reject('감사 작업자 삭제 차단', `DELETE FROM admin_account WHERE id=1`, '23001');
reject('도시 단독 삭제 제한', `DELETE FROM city WHERE id=1`, '23503');
accept(
  '게시 마커와 포인터를 한 트랜잭션에 변경',
  `UPDATE city_revision SET published_at=CURRENT_TIMESTAMP,
 published_by=1,safety_revision_at_publish_id=201 WHERE id=101;
 UPDATE city SET published_revision_id=101,draft_revision_id=NULL,visibility='PUBLIC' WHERE id=1`,
);
accept(
  '초안 폐기 하위 연결 제거 및 사진 유지',
  `UPDATE city SET draft_revision_id=NULL WHERE id=1;
 DELETE FROM city_revision WHERE id=101`,
  async () => {
    const r = (
      await db.query(`SELECT (SELECT count(*) FROM content_item WHERE revision_id=101)::int AS items,
 (SELECT count(*) FROM photo_asset WHERE city_id=1)::int AS assets,
 (SELECT count(*) FROM source_document WHERE id=1)::int AS sources`)
    ).rows[0];
    if (r.items !== 0 || r.assets !== 2 || r.sources !== 1) throw new Error(JSON.stringify(r));
  },
);
accept(
  '도시 물리 삭제 후 이력과 공용 자료 유지',
  `INSERT INTO audit_event(actor_id,city_ref_id,action,before_data)
 VALUES(1,1,'DELETE','{"city":"교토","archive":"test"}');
 UPDATE city SET published_revision_id=NULL,draft_revision_id=NULL,published_safety_revision_id=NULL WHERE id=1;
 DELETE FROM city_revision WHERE city_id=1;
 DELETE FROM safety_revision WHERE city_id=1;
 DELETE FROM photo_asset WHERE city_id=1;
 DELETE FROM city WHERE id=1`,
  async () => {
    const r = (
      await db.query(`SELECT (SELECT count(*) FROM city WHERE id=1)::int AS cities,
 (SELECT count(*) FROM audit_event WHERE city_ref_id=1)::int AS logs,
 (SELECT count(*) FROM source_document WHERE id=1)::int AS sources,
 (SELECT count(*) FROM tag)::int AS tags`)
    ).rows[0];
    if (r.cities !== 0 || r.logs !== 2 || r.sources !== 1 || r.tags !== 2)
      throw new Error(JSON.stringify(r));
  },
);

accept(
  '공개 이력과 안전 기반 참조가 있는 도시 물리 삭제',
  `
 UPDATE city_revision SET published_at=CURRENT_TIMESTAMP,published_by=1,
 safety_revision_at_publish_id=201 WHERE id=101;
 UPDATE city SET published_revision_id=101,draft_revision_id=NULL,visibility='PUBLIC' WHERE id=1;
 INSERT INTO safety_revision(id,city_id,base_revision_id,created_by,published_at,published_by)
 OVERRIDING SYSTEM VALUE VALUES(303,1,201,1,CURRENT_TIMESTAMP,1);
 UPDATE city SET published_safety_revision_id=303 WHERE id=1;
 INSERT INTO audit_event(actor_id,city_ref_id,action,before_data) VALUES(1,1,'DELETE','{"test":"published archive"}');
 UPDATE city SET visibility='HIDDEN',published_revision_id=NULL,draft_revision_id=NULL,published_safety_revision_id=NULL WHERE id=1;
 DELETE FROM city_revision WHERE city_id=1;
 DELETE FROM safety_revision WHERE city_id=1;
 DELETE FROM photo_asset WHERE city_id=1;
 DELETE FROM city WHERE id=1`,
  async () => {
    const r = (
      await db.query(`SELECT (SELECT count(*) FROM safety_revision WHERE city_id=1)::int AS safety,
 (SELECT count(*) FROM audit_event WHERE city_ref_id=1)::int AS logs`)
    ).rows[0];
    if (r.safety !== 0 || r.logs !== 2) throw new Error(JSON.stringify(r));
  },
);

const results = [];
for (const test of cases) {
  await db.exec('BEGIN');
  let error;
  try {
    await db.exec(test.sql);
    await db.exec('SET CONSTRAINTS ALL IMMEDIATE');
    if (test.assertion) await test.assertion();
  } catch (e) {
    error = e;
  }
  await db.exec('ROLLBACK');
  const ok = test.code
    ? error?.code === test.code && (!test.constraint || error?.constraint === test.constraint)
    : !error;
  results.push({
    name: test.name,
    ok,
    ...(!ok
      ? {
          expected: test.code ?? 'success',
          expectedConstraint: test.constraint,
          actual: error?.code,
          actualConstraint: error?.constraint,
          message: error?.message ?? 'unexpected success',
        }
      : {}),
  });
  if (!ok) process.stdout.write(JSON.stringify(results.at(-1)) + '\n');
}
const report = {
  engine: version,
  adapter:
    'PGlite 0.5.8; isolated in-memory PostgreSQL/WASM; not native Docker or multi-session integration',
  total: results.length,
  passed: results.filter((r) => r.ok).length,
  results,
};
await writeFile(new URL('./results.json', import.meta.url), JSON.stringify(report, null, 2));
process.stdout.write(
  JSON.stringify({ engine: version, total: report.total, passed: report.passed }) + '\n',
);
await db.close();
if (report.passed !== report.total) process.exitCode = 1;
