# PostgreSQL 제약조건 정의 v1

작성일: 2026-09-28. PostgreSQL 채택 확정. 사용자 검수를 반영한 정책 기준이다. 정책 승인과 구현·통합 검증 완료는 구분한다.

- 논리 모델: [ERD v2](erd-v2.md)
- 실행 가능한 초기 DDL: [db/schema.sql](../db/schema.sql)
- 검증 코드: [validate.mjs](../tools/db-validation/validate.mjs)
- 대상: PostgreSQL 18 계열. 실제 배포 마이너 버전은 구현 시작 시 지원 중인 패치 버전으로 고정한다.
- 검증: PGlite 0.5.8 / PostgreSQL 18.3 WASM 엔진에서 DDL 생성 및 72개 동작 사례 통과. 네이티브 PostgreSQL/Docker 및 다중 연결 동시성 검증은 아직 하지 않았다.
- 서비스 구현은 아직 없다. 아래의 '서비스' 규칙은 정의이며 DDL만으로 시행되지 않는다.

## 1. 사용자 검수 완료 정책

남은 여섯 항목(노출 순서, 태그 영문명, 게시 가능한 콘텐츠, 사진 대체 텍스트, 안전정보 확인 상태, 근거 메모 길이)은 모두 기본안으로 승인되었다. 현재 제약조건의 정책 검수는 완료했으며, 서비스 구현 및 네이티브 PostgreSQL 다중 연결 검증은 아직 남아 있다. 이번 승인은 새로운 서비스 구현 착수나 공개 배포를 의미하지 않는다.

| 결정               | 확정 기준                                                             | 의미                                                    |
| ------------------ | --------------------------------------------------------------------- | ------------------------------------------------------- |
| 초안 저장          | 이름·좌표·소개·본문 등 미완성 NULL 허용                               | 편집 중에도 게시 필수 조건을 강제하지 않음              |
| 빈 문자열          | 비어 있으면 NULL, 공백뿐인 문자열 거부                                | 프론트/백엔드에서 빈 입력을 NULL로 정리                 |
| 도시명             | 한국어/영문 각각 150자, 중복 허용                                     | 이름만으로 도시를 식별하지 않음                         |
| 한 줄 소개         | 200자 이내                                                            | 화면에서는 더 짧게 작성하도록 안내 가능                 |
| 본문               | 콘텐츠/안전 항목당 3,000자 이내                                       | 사용자 검수 반영; DB 저장 상한이며 추천 글 길이가 아님  |
| 태그               | 코드·한국어명·영문명 각각 유일, 영문명은 대소문자 구분 없이 중복 검사 | 비활성 태그도 같은 코드/라벨 재사용 불가                |
| 사진               | 저장 경로 전역 유일, 같은 버전에 같은 사진 중복 불가                  | 다른 도시의 사진 연결은 DB에서도 차단                   |
| 노출 순서          | 1 이상의 정수, 그룹 안에서 유일                                       | 1, 10, 20처럼 빈 번호 허용; 연속 번호 강제 안 함        |
| 콘텐츠 개수        | 사진/경험 저장 개수의 임의 상한 없음                                  | 경험 최대 3개는 조회 규칙이지 저장 제약이 아님          |
| 관리자 ID          | 3~64자의 소문자 영문·숫자·점·밑줄·하이픈, 첫 글자는 영문/숫자         | 대소문자 혼동 방지; 표시 이름은 별도                    |
| 사진 권리 확인     | 확인 시각이 있으면 라이선스 이름 또는 이용 근거 메모 필요             | 내용의 실제 적법성은 관리자 검토                        |
| 출처 확인          | 확인일·확인자를 함께 기록하고 근거 메모 필수                          | 아직 검토 전인 출처 연결은 둘 다 NULL                   |
| 삭제               | 하위 내용을 정리한 뒤 도시 삭제, 감사 이벤트는 삭제 금지              | DB가 자동으로 완전한 삭제 이력을 작성해 주지는 않음     |
| 게시 가능한 콘텐츠 | 선정한 모든 콘텐츠에 제목·본문·검토한 출처 필요                       | 4번째 이후 경험도 동일. 미완성 후보는 미선정으로 저장   |
| 사진 대체 텍스트   | 게시 사진마다 필수                                                    | 초안 저장 시에는 누락 허용                              |
| 안전정보 확인 상태 | UNVERIFIED / PARTIAL / CHECKED                                        | 아래 S02 기준 적용. 확인 완료가 안전 보장을 뜻하지 않음 |
| 근거 메모 길이     | 출처 근거·안전 적용 범위 설명 4,000자                                 | 사용자용 본문 3,000자와 구분                            |

## 2. DB와 서비스의 책임

| 계층                     | 보장하는 것                             | 예                                                    |
| ------------------------ | --------------------------------------- | ----------------------------------------------------- |
| PK / UNIQUE / FK / CHECK | 행의 형태, 중복, 참조 존재 및 소속      | 다른 도시 버전 참조 금지, 경보/단계 조합              |
| 소규모 DB 트리거         | 단순 CHECK로 표현할 수 없는 제한적 관계 | 사진 소속 도시 일치, 포인터 상태, 감사 로그 변경 금지 |
| 서비스 트랜잭션          | 사용자 권한·시간·업무 절차·게시 완성도  | 잠금 만료, 출처 확인, 초안만 수정, 안전 충돌 재검토   |

PostgreSQL CHECK는 다른 테이블을 참조하는 지속적 무결성 검사 용도로 쓰지 않는다. NULL 결과는 CHECK 통과로 간주되므로 필수 값에는 NOT NULL 또는 명시적 IS NOT NULL을 사용했다. 시각이 흐르는 것만으로 CHECK가 재실행되지는 않으므로 만료 판단에 CHECK(now())를 사용하지 않았다.

공식 참고: [Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html), [CREATE TRIGGER](https://www.postgresql.org/docs/current/sql-createtrigger.html).

## 3. 자료형·NULL·기본값

DDL이 자료형과 기본값의 최종 명세다. 별도 ID는 bigint GENERATED ALWAYS AS IDENTITY, 시각은 timestamptz, 감사 스냅샷은 jsonb를 사용한다. CHECK 문자열은 허용 목록 방식으로 두고 PostgreSQL ENUM은 사용하지 않았다. 상태 확장 시 마이그레이션으로 CHECK를 수정한다.

아래 'NULL 허용' 이외의 필드는 필수다. identity와 명시된 DEFAULT가 있는 값은 INSERT에서 생략할 수 있다.

| 테이블          | NULL 허용 필드                                                                                                                | 주요 기본값/제약                                                                       |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| admin_account   | 없음                                                                                                                          | is_active=true; password_hash는 해시만 저장                                            |
| country         | 없음                                                                                                                          | code는 대문자 알파벳 2자리, 실제 국가 여부는 기준 데이터로 검증                        |
| tag             | 없음                                                                                                                          | label_ko/label_en 필수, is_active=true, code는 대문자 영문으로 시작하며 영문/숫자/밑줄 |
| source_document | publisher                                                                                                                     | URL 4096자, 제목 500자, created_at=현재 트랜잭션 시각                                  |
| city            | published_revision_id, draft_revision_id, published_safety_revision_id                                                        | visibility=HIDDEN                                                                      |
| city_revision   | name_ko, name_en, latitude, longitude, tagline, 세 가지 안전 버전 참조, published_at, published_by                            | edit_version=0, 생성/수정 시각 기본값                                                  |
| safety_revision | base_revision_id, published_at, published_by                                                                                  | review_state=UNVERIFIED                                                                |
| revision_tag    | 없음                                                                                                                          | (revision_id, tag_id) 복합 PK                                                          |
| photo_asset     | creator_name, license_name, license_url, usage_note, rights_checked_at                                                        | source_url 필수. 자체 촬영은 출처 식별용 URL과 사용 근거를 정해 등록                   |
| revision_photo  | alt_text, caption                                                                                                             | 순서는 명시적으로 입력                                                                 |
| content_item    | title, body                                                                                                                   | is_selected=false; 미완성 후보는 기본 비선정                                           |
| content_source  | evidence_note, checked_at, checked_by                                                                                         | 확인 시 evidence_note 필수                                                             |
| safety_item     | official_level, scope_type, original_scope_text, city_coverage, city_scope_note, title, body, effective_from, effective_until | kind/assessment는 생성 시 지정, 공식 발령은 단계 필수                                  |
| safety_source   | evidence_note, checked_at, checked_by                                                                                         | 확인 시 evidence_note 필수                                                             |
| city_edit_lock  | 없음                                                                                                                          | 시각을 서버가 명시적으로 설정                                                          |
| audit_event     | revision_ref_id, safety_revision_ref_id, before_data, after_data                                                              | action에 따라 before/after 필수 여부 달라짐                                            |

### 길이 상한

- 도시·국가명 150자, 관리자 표시 이름 80자, 태그 한국어/영문 라벨 각각 64자.
- 한 줄 소개/콘텐츠 제목/안전 제목 200자.
- 콘텐츠·안전 본문 3,000자, 출처 근거 및 안전 적용 범위 설명 4,000자. 후자는 사용자용 본문이 아니라 검토 근거 메모이므로 기존 상한을 유지한다.
- 사진 alt 500자, 설명 1,000자, 권리 메모 2,000자, 저장 키 512자.
- URL 4,096자. http/https 및 공백 부재 정도만 DB 검사; 실제 URL 구문·접근 가능 여부·권리는 서비스/관리자 검사.
- PostgreSQL varchar(n)은 문자 길이 기준이다. 대용량 이미지 원본이나 출처 원문 전체는 이 필드에 넣지 않는다.

## 4. 테이블별 무결성

### 이름 및 언어 필드 검수

- city_revision과 country에는 name_ko/name_en이 이미 있다.
- tag에 label_en을 추가했다. code=HISTORY와 label_en=History는 내부 식별 코드와 화면 표시명으로 역할이 다르다.
- 일반 제목·한 줄 소개·본문에 UNIQUE를 추가하지 않는다. 문구 중복 자체는 데이터 오류가 아니다. UI에서 경고를 추가할 수 있지만 핵심 유일성/권한 검증을 클라이언트만으로 대체하지 않는다.
- content_item/safety_item 제목과 본문, 사진 설명/대체 텍스트는 현재 한국어 콘텐츠다. 영문 필드를 자동 추가하지 않는다. 영문 콘텐츠 서비스는 별도 다국어 요구사항이다.
- source_document의 제목/발행자, 사진 촬영자/라이선스명은 원본 표기를 보존한다. 원본 자료가 영어일 수도 있으며 임의 번역명을 필수화하지 않는다.
- admin_account.display_name은 관리자 표시 이름이므로 영문명 필수 대상이 아니다.

### 도시·버전

- 이름은 중복 허용. country_id는 필수이며 도시 생성 후 일반 편집에서 변경하지 않는다.
- 도시 visibility는 HIDDEN / PREPARING / PUBLIC.
- PUBLIC은 일반 공개 포인터와 안전 공개 포인터가 모두 필요하다. 미확인 안전정보도 UNVERIFIED 안전 버전으로 표시한다.
- PREPARING은 일반 공개본 또는 초안이 필요하다. 사용 가능한 이름/좌표 확인은 서비스 책임이다.
- 일반 공개본과 초안 포인터는 서로 달라야 한다.
- UNIQUE(city_id, revision_no); revision_no > 0. 같은 도시의 버전 번호는 재사용하지 않는 서비스 정책을 둔다. 초안 폐기 후에도 감사에 남은 번호와 혼동하지 않도록 번호 할당 시 이력을 고려하거나 독립 증가 시퀀스를 사용한다. 행 ID는 항상 별도로 식별한다.
- UNIQUE(city_id, id)는 '같은 도시 버전만 참조'하는 복합 FK의 참조 키다.
- published_at IS NULL인 city_revision에는 도시별 부분 UNIQUE 인덱스를 두어 초안 두 개 생성을 DB에서 차단한다.
- edit_version >= 0. 서비스가 저장할 때 이전 값 비교 후 1 증가시킨다. DB CHECK만으로 비교 갱신을 자동 수행하지 않는다.
- 위도 -90~90, 경도 -180~180, 소수점 6자리. 두 좌표는 함께 있거나 함께 NULL이다. NaN도 범위를 통과하지 못한다.
- 도시 버전의 이름/좌표/소개는 초안에서 NULL 가능하나 published_at을 기록할 때 모두 있어야 한다.
- 게시 시각·게시자는 함께 NULL이거나 함께 존재하며 게시 시각은 생성 시각보다 이르지 않아야 한다.
- 일반 버전 게시 시 안전 스냅샷 포인터는 필수이고, 안전 수정본 포인터는 NULL로 정리한다.
- 공개/초안 포인터의 존재 및 동일 도시 소속은 지연 FK로 검사한다. 커밋 시 일반 공개 포인터는 게시된 버전, 초안 포인터는 미게시 버전, 안전 공개 포인터는 게시된 안전 버전을 가리켜야 한다.
- 고아 일반 초안을 커밋할 수 없다. 새 초안 INSERT와 도시 포인터 설정은 반드시 같은 트랜잭션으로 수행한다.
- 일반/안전 버전·사진 자산의 소유 도시는 수정 불가. 이동이 필요하면 새 레코드를 만든다.

### 사진·태그·본문

- 태그 연결 중복 불가. 사진 자산은 저장 키가 유일하며 한 도시에 속한다.
- 사진 연결은 버전/자산 조합과 버전/순서가 각각 유일하다.
- 사진 소속 도시는 트리거로 검사한다. 같은 소속을 보장하려고 revision_photo에 city_id를 반복 저장하지 않았다.
- CONTENT_ITEM 종류: EXPERIENCE / CONSIDERATION / VISIT_PERIOD / ARRIVAL / LOCAL_TRANSPORT.
- 콘텐츠 순서는 (버전, 종류) 안에서, 안전 항목 순서는 안전 버전 안에서 유일하다.
- 순서 UNIQUE는 DEFERRABLE INITIALLY IMMEDIATE. 일반 저장에서는 즉시 검사하고 순서 바꾸기 작업만 해당 제약을 DEFERRED로 바꾼다. 커밋 시 중복은 반드시 없어야 한다.
- 사진/경험 저장 개수 제한은 없다. 공개 쿼리에서 선정된 경험을 순서대로 LIMIT 3 한다.
- 태그 라벨·문서/사진 메타데이터의 불변 운영 원칙은 서비스에서 시행한다. 현재 DDL은 공개 자산 메타데이터 덮어쓰기까지 자동 차단하지 않는다.

### 안전정보

| kind              | assessment | official_level                  |
| ----------------- | ---------- | ------------------------------- |
| OFFICIAL_ADVISORY | ISSUED     | LEVEL_1/2/3/4 또는 SPECIAL 필수 |
| OFFICIAL_ADVISORY | NOT_ISSUED | NULL                            |
| LOCAL_CAUTION     | CAUTION    | NULL                            |

- 조합 외 값은 DB가 거부한다. SPECIAL을 2.5 같은 수치로 환산하지 않는다.
- scope_type: COUNTRY_WIDE / REGION / CITY / SPECIFIC_PLACE.
- city_coverage: WHOLE_CITY / PART_OF_CITY. 미확인=안전으로 해석하지 않는다.
- review_state=UNVERIFIED/PARTIAL/CHECKED는 전체 검토 상태다. CHECKED가 위험 없음이나 안전 보장을 뜻하지 않는다.
- 초안 안전 항목의 범위/본문은 NULL 가능하지만 게시 전 검증에서 필수다.
- effective_from/until은 원본에 명시된 경우만 저장. 둘 다 있으면 종료가 시작보다 늦어야 한다. 일 단위 종료일은 서비스에서 현지 시간대 기준 종료 다음 날 0시 등으로 변환하여 배타적 종료 시각으로 저장한다.
- 안전 버전은 자신을 기반으로 참조할 수 없다. 기반이 게시된 버전인지, 순환이 없는지, 최신인지와 수정본 소유권은 서비스 검증이다.

### 출처

- 같은 URL을 새 문서 메타데이터 버전으로 다시 등록할 수 있도록 URL UNIQUE는 두지 않는다.
- 콘텐츠/안전 항목과 문서의 연결은 복합 PK로 중복 차단.
- checked_at과 checked_by는 함께 존재해야 한다. 확인 완료에는 evidence_note가 필요하다.
- checked_at <= 현재 시각 같은 동적 CHECK는 두지 않는다. 확인 작업에서 서버 시각을 사용한다.
- 문서 삭제는 참조 중이면 RESTRICT. 공용 문서를 도시 삭제와 함께 무조건 삭제하지 않는다.

### 편집 잠금·감사

- 잠금은 city_id가 PK. 한 도시에 하나이며 owner_admin_id와 편집 창 UUID를 기록한다.
- 무작위 토큰은 원문 대신 SHA-256 결과를 소문자 64자리 hex로 저장하는 기본안이다. 토큰은 재획득 시 새로 발급하고 감사에 넣지 않는다.
- last_heartbeat_at >= acquired_at, expires_at = last_heartbeat_at + 5분.
- 1분 요청 주기, 만료된 토큰 거부, 사용자 활동 1시간 만료는 DB가 스스로 수행하지 않는다. 서비스에서 매 요청 검사한다.
- 감사 city_ref_id/revision_ref_id/safety_revision_ref_id는 과거 ID이므로 FK 없음. 양수만 허용한다.
- 감사 before/after는 JSON 객체여야 하며 JSON null과 배열은 거부한다. 실제 스냅샷 필드 완전성은 서비스 검증이다.
- CREATE_DRAFT/PUBLISH/SAFETY_PUBLISH: after 필수. DELETE/DISCARD_DRAFT: before 필수·after SQL NULL. UPDATE_DRAFT/UNPUBLISH/SET_VISIBILITY: 둘 다 필수.
- UPDATE/DELETE/TRUNCATE는 감사 트리거로 거부한다. DB 소유자가 트리거를 제거하는 것까지 방지하는 변조 방지 장치가 아니다.

## 5. 삭제 참조 정책

| 관계                                   | ON DELETE      | 이유                                                 |
| -------------------------------------- | -------------- | ---------------------------------------------------- |
| 도시 → 일반/안전 버전·사진 자산        | NO ACTION      | 도시 단독 삭제로 조용히 전체 자료가 사라지지 않게 함 |
| 일반 버전 → 사진 연결·태그 연결·콘텐츠 | CASCADE        | 도시 삭제/초안 폐기 시 전용 하위 데이터 정리         |
| 콘텐츠/안전 항목 → 출처 연결           | CASCADE        | 연결만 제거, 원본 문서는 유지                        |
| 안전 버전 → 안전 항목                  | CASCADE        | 버전 전용 행 정리                                    |
| 도시 → 편집 잠금                       | CASCADE        | 도시가 없으면 잠금 불필요                            |
| 태그·사진 자산·출처·관리자 참조        | RESTRICT       | 살아 있는 참조를 깨뜨리는 삭제 금지                  |
| 도시 포인터 및 버전 사이 안전 FK       | 지연 NO ACTION | 같은 트랜잭션의 포인터 교체·다중 버전 삭제 허용      |
| 감사 → 도시/버전                       | FK 없음        | 삭제 후에도 기록 유지                                |

물리 삭제는 서비스 트랜잭션에서 다음 순서로 수행한다.

현재 schema.sql은 구조/제약 DDL이다. 아래 전체 삭제 업무 SQL과 완전한 스냅샷 작성 로직은 서비스 구현 단계에 작성한다. validate.mjs에는 최소 테스트 스냅샷 INSERT와 실제 DELETE를 한 트랜잭션에 실행하는 검증 SQL이 있으나, 이를 운영용 삭제 구현 완료로 간주하지 않는다. 'DB 자동 작성 아님'은 이력 저장을 생략한다는 뜻이 아니라 서비스가 이력 INSERT와 삭제 SQL을 명시적으로 함께 실행한다는 뜻이다.

1. CITY 행 잠금, 관리자 세션/편집권 확인, 모든 이력 스냅샷 준비.
2. DELETE 감사 이벤트 저장. 실패하면 전체 롤백.
3. visibility=HIDDEN과 세 포인터 NULL을 한 UPDATE로 적용.
4. 일반 버전 전체 삭제(연결/본문 자동 정리), 안전 버전 전체 삭제(항목/연결 자동 정리), 사진 자산 삭제, CITY 삭제.
5. 커밋 시 지연 FK/트리거 검증. 도시가 삭제되었으므로 공개 포인터 상태 검사는 종료.
6. DB 커밋 후 실제 이미지 파일 정리. 실패 파일은 별도 재시도.

공개 버전의 safety_revision_at_publish_id를 먼저 NULL로 UPDATE하지 않는다. 이는 공개 행 형태 CHECK를 위반한다. 해당 버전 자체를 삭제하고 안전 버전도 같은 트랜잭션에서 제거하면 된다.

## 6. 서비스에서 반드시 구현할 제약조건

DDL을 실행했다고 다음 정책이 자동 구현된 것은 아니다. 앱이 사용할 DB 계정에는 스키마 소유자 권한을 주지 않고, 인증된 서비스 코드의 경로로만 업무 변경을 수행한다. 실제 역할/권한 생성은 배포 구성 단계다.

### S01. 편집과 상태 변경

- 모든 일반 수정: 활성 관리자·유효 세션·잠금 소유자/창/토큰·서버 시각 < expires_at 확인.
- 서버가 클라이언트의 잠금 시각, 확인 시각, 게시 시각을 그대로 신뢰하지 않는다.
- 현재 draft_revision_id와 요청 revision_id가 일치해야 한다. 공개/과거 버전과 그 하위 행은 수정 불가.
- 관련 자산/문서도 공개 이력에서 쓰인 값은 덮어쓰지 않고 새로 생성한다.
- 하위 변경에도 부모 edit_version을 비교·증가시키고 updated_by/updated_at을 갱신한다. 같은 잠금 토큰 안의 늦은 요청도 차단한다.
- 일반 저장/게시/삭제 및 안전 독립 게시의 DB 행 잠금 순서는 CITY부터 시작하여 일관되게 유지한다.
- 감사 로그 저장과 업무 변경은 같은 트랜잭션이다. 로그인 실패나 다른 보안 로그는 별도 범위다.

### S02. 게시 필수 검사

- 이름/좌표/소개, 권리 검토한 사진 최소 1장 및 사진별 alt 텍스트.
- 선정된 경험 최소 1개, 방문 시기/도시 접근/현지 이동 각각 최소 1개.
- 선정된 모든 콘텐츠(첫 3개 외 후보 포함)는 제목/본문과 검토 완료한 출처 최소 1개를 가져야 한다. 미완성 후보는 is_selected=false로 둔다.
- 공개 안전 항목은 제목/본문/적용 범위/도시 적용 설명과 확인된 출처가 있어야 한다. UNVERIFIED 버전은 항목 0개로 게시 가능하나 미확인으로 표시한다. UNVERIFIED에 항목이 있으면 먼저 검토 후 PARTIAL/CHECKED로 바꾼다.
- PARTIAL은 확인된 항목을 제공하되 전체 확인이 끝나지 않은 상태이며, 최소 하나의 검토된 항목이 필요하다.
- CHECKED는 최소 하나의 공식 경보 또는 공식 미발령 확인 항목과 관리자 범위 검토를 요구한다. 안전 문제가 없다는 뜻은 아니다.
- 만료되었거나 미래에 효력이 시작되는 안전 안내를 현재 경보처럼 표시하지 않는다. 게시 시/공개 조회 시 효력과 확인 시점을 구분한다.
- 게시 전에 사진/본문 전체 미리보기와 최종 검토를 수행한다. 필드 존재만으로 사실 검증이 끝나지는 않는다.

### S03. 안전 충돌

- 일반 초안의 draft_safety_revision_id가 NULL이면 게시 시 현재 안전 공개 포인터를 유지한다.
- 수정 안전 버전이 있으면 그 소속·미게시 상태·기반 버전 일치를 검사한다. 현재 공개 버전과 다른 경우 도시 게시 전체를 거부한다.
- 안전 독립 게시도 화면에서 확인한 기반 버전과 현재 안전 공개 버전을 NULL까지 포함하여 비교한다.
- 기반 비교와 공개 포인터 변경은 CITY 행을 잠근 같은 트랜잭션에서 처리한다. 사전 SELECT만 하고 별도 UPDATE를 실행하면 안 된다.
- 충돌 시 입력 유지, 최신 정보와 수정본 재검토 필수. 다시 게시할 때도 버전 비교. 안전 수정본을 제외하고 즉시 게시하는 우회 기능 없음.
- 게시된 안전/일반 버전은 불변으로 관리하고, 공개 포인터를 과거 버전으로 되돌리는 API는 제공하지 않는다.

### S04. 삭제·감사 스냅샷

- 삭제 로그는 ID만이 아니라 당시 본문/국가명/태그/출처/확인일/사진 메타데이터를 포함해야 한다.
- 개인정보/비밀번호/세션/편집 토큰/API 키를 스냅샷에 포함하지 않는다.
- 최초 게시 이후 초안 수정도 이력에 남긴다. 최초 게시 전 삭제도 기록한다.
- 초안 폐기는 공유 자산이나 현재 안전 공개본을 삭제하지 않는다.
- 국가/태그/출처/관리자는 도시 삭제 때 제거하지 않는다. 관리자 삭제 대신 비활성화한다.
- 감사 트리거는 '변경 기록이 작성됐는지'를 자동 보장하지 않는다. 이벤트 누락을 서비스 통합 테스트로 검사한다.

## 7. 인덱스와 실행 예

PK/UNIQUE가 생성한 인덱스를 중복 생성하지 않았다. 버전/순서 조회, 출처 역참조, 도시 국가, 감사 이력 시간순, 잠금 만료 조회를 우선 지원한다. 관리자 FK 모두에 무조건 인덱스를 추가하지 않았다. 관리자는 소수이며 삭제하지 않는 정책이다.

초기 도시명 검색은 소량 데이터에서 시작한다. 부분 문자열 검색 성능을 확인하지 않고 trigram/전문 검색 확장을 추가하지 않는다.

순서 교환 예:

```sql
BEGIN;
SET CONSTRAINTS tripee.uq_revision_photo_order DEFERRED;
-- 실제 서비스에서는 먼저 CITY 및 편집권/버전을 확인한다.
UPDATE tripee.revision_photo SET display_order = 2 WHERE id = :photo_row_a;
UPDATE tripee.revision_photo SET display_order = 1 WHERE id = :photo_row_b;
COMMIT; -- 최종 중복이면 전체 실패
```

초안 편집 버전 비교 예:

```sql
UPDATE tripee.city_revision
SET edit_version = edit_version + 1,
    updated_at = clock_timestamp(), updated_by = :admin_id
WHERE id = :revision_id AND edit_version = :expected_version
  AND published_at IS NULL;
-- 영향 행 0이면 실패. 하위 변경도 같은 트랜잭션 안에서 처리한다.
```

`:...`는 설명용 매개변수이며 그대로 실행하는 SQL 파일이 아니다. 애플리케이션에서 바인딩한다.

## 8. 검증 결과와 남은 검증

- PGlite의 PostgreSQL 18.3 WASM 엔진에서 schema.sql을 실제 실행했다.
- 72개 사례 통과: 정상 초안/포인터 게시, PK·FK·CHECK 오류, 다른 도시 사진, 지연 순서 교환, 잘못된 안전 단계, 감사 변경 금지, 초안 폐기, 공개 버전과 안전 기반 참조를 포함한 도시 삭제 후 이력 보존, 태그 영문명 제약, 두 본문 필드의 3,000자 경계 등.
- 일부 테스트는 의도적으로 최소 구조만 게시한다. 서비스 게시 검증이 아직 없으므로 테스트 fixture를 실제 게시 가능한 콘텐츠로 간주하지 않는다.
- PGlite의 엔진 버전은 검증 환경일 뿐 실제 배포 패치 버전 추천이 아니다.
- 네이티브 PostgreSQL 18 Docker 적용/덤프·복원, 여러 연결의 잠금 경합, 서비스 권한·게시 완성도·스냅샷 내용·API 롤백 검증은 미실행이다.
- 새 스키마에 한 번 적용하도록 작성했다. 기존 데이터베이스를 DROP하거나 기존 스키마를 수정하지 않는다. 향후 첫 마이그레이션으로 편입할 수 있다.

실행:

```sh
# 네이티브 PostgreSQL을 별도로 준비한 경우, 새 전용 DB에 실행
psql -v ON_ERROR_STOP=1 -d tripee -f db/schema.sql

# Node.js와 pnpm이 있는 환경에서 메모리 내 검증
cd tools/db-validation
pnpm install --frozen-lockfile --ignore-scripts
node validate.mjs
```

검증 도구는 매번 별도의 메모리 DB를 만들어 종료한다. 실제 사용자 데이터베이스를 연결하거나 삭제하지 않는다.
