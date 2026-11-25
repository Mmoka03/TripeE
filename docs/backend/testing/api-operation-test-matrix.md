# API 작업별 테스트 연결표

[테스트 문서 안내](README.md)

검토일 2026-11-24. 기준: [OpenAPI](../openapi-v1.json)·현재
컨트롤러/서비스·[실행 상세 명세](test-case-execution-draft.md). 경로는 `/api/v1` 기준이다. OP ID는
작업을 식별하는 문서 ID이며 행 순서 변경으로 재사용하지 않는다.

**39개 작업에 대한 설계 연결을 확인했다. 이는 전체 입력·상태 조합 검증률100%가 아니다.** 성공 상태는
OpenAPI에서 추출했으며, 각 경로의 명세상 오류 상태 전체가 실현 가능한지는 별도 분류한다. 400/403/500
등 공통 응답이 정의되어 있다는 이유로 같은 오류를 모든 작업에 억지로 생성하지 않는다.

기존 테스트 후보는 재사용을 검토할 클래스다. 클래스의 존재와 각 케이스의 검증 완료는 구분한다. 직접
Java 호출·목·DB fixture의 검증 범위도 함께 확인한다. **이번 검토의 모든 OP 실행 상태는 미실행**이다.

## 바로가기

- [공개 조회·이미지](#공개-조회이미지), [인증·세션](#인증세션), [사전·도시 관리](#사전도시-관리)
- [편집 잠금](#편집-잠금), [초안·게시·재검토](#초안게시재검토), [안전정보·사진](#안전정보사진)
- [출처·AI](#출처ai), [과거 버전·감사 이력](#과거-버전감사-이력),
  [작업마다 추가로 펼칠 검증](#작업마다-추가로-펼칠-검증)
- [출시 필수 검증 연결](#출시-필수-검증-연결), [계약·정책 쟁점 등록부](#계약정책-쟁점-등록부)
- [서비스 분리 시 검증 연결](#서비스-분리-시-검증-연결)

> 검토할 API를 찾고 **상세 케이스** 링크를 열면 입력값과 실행 절차로 이동한다. 기존 테스트 후보는
> 재사용 여부를 확인할 출발점이다.

## 공개 조회·이미지

### OP01 `GET /cities`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 국가 필터·PUBLIC/PREPARING·초안 비노출
- **상세 케이스:** [F05](test-case-execution-draft.md#f05-공개-도시-노출) ·
  [F09](test-case-execution-draft.md#f09-국가태그-분할)
- **기존 테스트 후보:**
  [CityListPostgresTest](../../../backend/src/test/java/com/tripee/backend/city/CityListPostgresTest.java)

### OP02 `GET /cities/{cityId}`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** PUBLIC/준비 객체·404·안전 DB 시각
- **상세 케이스:** [F01](test-case-execution-draft.md#f01-리소스-id) ·
  [F05](test-case-execution-draft.md#f05-공개-도시-노출) ·
  [F25](test-case-execution-draft.md#f25-공개-안전정보-시각)
- **기존 테스트 후보:**
  [CityDetailPostgresTest](../../../backend/src/test/java/com/tripee/backend/city/CityDetailPostgresTest.java)

### OP03 `GET /search`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 길이·limit·문자열 검색·정렬
- **상세 케이스:** [F06](test-case-execution-draft.md#f06-검색어-길이공백) ·
  [F07](test-case-execution-draft.md#f07-검색-결과-수-제한) ·
  [F08](test-case-execution-draft.md#f08-검색-순서리터럴)
- **기존 테스트 후보:**
  [CitySearchControllerTest](../../../backend/src/test/java/com/tripee/backend/city/CitySearchControllerTest.java)

### OP04 `GET /tags`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 활성 사전·문자열 ID
- **상세 케이스:** [F09](test-case-execution-draft.md#f09-국가태그-분할) ·
  [F35](test-case-execution-draft.md#f35-사전-조회)
- **기존 테스트 후보:**
  [TagControllerTest](../../../backend/src/test/java/com/tripee/backend/tag/TagControllerTest.java)

### OP05 `GET /media/photo-assets/{assetId}`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 이미지 바이트·공개/관리자·경로 이탈
- **상세 케이스:** [F29](test-case-execution-draft.md#f29-사진-형식파일-크기) ·
  [F31](test-case-execution-draft.md#f31-사진-공개파일-정리) ·
  [F38](test-case-execution-draft.md#f38-이미지204location-응답) ·
  [S11](test-case-execution-draft.md#s11-파일-경로-이탈)
- **기존 테스트 후보:**
  [PhotoFileServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/PhotoFileServiceTest.java)

## 인증·세션

### OP06 `GET /admin/csrf`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 익명 허용·세션 연결·no-store
- **상세 케이스:** [Q01](test-case-execution-draft.md#q01-세션-만료와-활동-보고) ·
  [S03](test-case-execution-draft.md#s03-csrf)
- **기존 테스트 후보:**
  [AdminBoundaryTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminBoundaryTest.java)

### OP07 `POST /admin/session`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 익명 허용이나 CSRF 필요·정상/자격 실패
- **상세 케이스:** [Q01](test-case-execution-draft.md#q01-세션-만료와-활동-보고) ·
  [S03](test-case-execution-draft.md#s03-csrf) ·
  [S05](test-case-execution-draft.md#s05-로그인-반복-시도) ·
  [L03](korea-legal-security-test-review-2026-11-23.md#l03-계정-회수최소-권한)
- **기존 테스트 후보:**
  [AdminSessionTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminSessionTest.java)

### OP08 `GET /admin/session`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·활동 갱신 안 함
- **상세 케이스:** [Q01](test-case-execution-draft.md#q01-세션-만료와-활동-보고) ·
  [S04](test-case-execution-draft.md#s04-세션-재사용로그아웃)
- **기존 테스트 후보:**
  [AdminSessionTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminSessionTest.java)

### OP09 `DELETE /admin/session`

- **성공 응답:** 204
- **선행 조건·핵심 판정:** CSRF·204·같은 계정 다른 세션 유지
- **상세 케이스:** [Q02](test-case-execution-draft.md#q02-잠금-만료소유권다른-세션-로그아웃) ·
  [S04](test-case-execution-draft.md#s04-세션-재사용로그아웃)
- **기존 테스트 후보:**
  [AdminSessionTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminSessionTest.java)

### OP10 `POST /admin/session/activity`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** INPUT/CLICK/NAVIGATION·만료 복구 불가
- **상세 케이스:** [Q01](test-case-execution-draft.md#q01-세션-만료와-활동-보고)
- **기존 테스트 후보:**
  [AdminSessionTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminSessionTest.java)

## 사전·도시 관리

### OP11 `GET /admin/countries`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·기준 사전
- **상세 케이스:** [F35](test-case-execution-draft.md#f35-사전-조회) ·
  [S01](test-case-execution-draft.md#s01-관리자-api-접근-통제)
- **기존 테스트 후보:**
  [AdminAccountsPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminAccountsPostgresTest.java)

### OP12 `GET /admin/tags`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·비활성 포함
- **상세 케이스:** [F09](test-case-execution-draft.md#f09-국가태그-분할) ·
  [F35](test-case-execution-draft.md#f35-사전-조회) ·
  [S01](test-case-execution-draft.md#s01-관리자-api-접근-통제)
- **기존 테스트 후보:**
  [AdminAccountsPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminAccountsPostgresTest.java)

### OP13 `GET /admin/cities`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·필터·cursor·limit
- **상세 케이스:** [F11](test-case-execution-draft.md#f11-목록커서-페이지) ·
  [F42](test-case-execution-draft.md#f42-커서-변조범위) ·
  [S01](test-case-execution-draft.md#s01-관리자-api-접근-통제)
- **기존 테스트 후보:**
  [AdminCityRepositoryPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminCityRepositoryPostgresTest.java)

### OP14 `POST /admin/cities`

- **성공 응답:** 201
- **선행 조건·핵심 판정:** CSRF·도시+초안+감사 원자성·Location
- **상세 케이스:** [F10](test-case-execution-draft.md#f10-도시-생성) ·
  [F27](test-case-execution-draft.md#f27-중간-실패-롤백) ·
  [F38](test-case-execution-draft.md#f38-이미지204location-응답) ·
  [F39](test-case-execution-draft.md#f39-http-본문content-type)
- **기존 테스트 후보:**
  [AdminCityServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminCityServiceTest.java)

### OP15 `GET /admin/cities/{cityId}`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·ETag·없는 도시404
- **상세 케이스:** [F12](test-case-execution-draft.md#f12-if-match) ·
  [S01](test-case-execution-draft.md#s01-관리자-api-접근-통제)
- **기존 테스트 후보:**
  [AdminCityControllerTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminCityControllerTest.java)

### OP16 `PATCH /admin/cities/{cityId}`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** CSRF·편집권·도시 If-Match
- **상세 케이스:** [F12](test-case-execution-draft.md#f12-if-match) ·
  [F13](test-case-execution-draft.md#f13-도시-상태-변경) ·
  [F41](test-case-execution-draft.md#f41-오류-우선순위)
- **기존 테스트 후보:**
  [AdminCityServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/AdminCityServiceTest.java)

### OP17 `DELETE /admin/cities/{cityId}`

- **성공 응답:** 204
- **선행 조건·핵심 판정:** CSRF·편집권·도시 If-Match·204
- **상세 케이스:** [F14](test-case-execution-draft.md#f14-도시-물리-삭제) ·
  [F27](test-case-execution-draft.md#f27-중간-실패-롤백) ·
  [F41](test-case-execution-draft.md#f41-오류-우선순위)
- **기존 테스트 후보:**
  [DraftWorkflowPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/DraftWorkflowPostgresTest.java)

## 편집 잠금

### OP18 `GET /admin/cities/{cityId}/edit-lock`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·토큰 미노출·view 비변경
- **상세 케이스:** [Q02](test-case-execution-draft.md#q02-잠금-만료소유권다른-세션-로그아웃) ·
  [F36](test-case-execution-draft.md#f36-편집-창-uuid)
- **기존 테스트 후보:**
  [EditLockControllerTest](../../../backend/src/test/java/com/tripee/backend/admin/EditLockControllerTest.java)

### OP19 `POST /admin/cities/{cityId}/edit-lock`

- **성공 응답:** 201
- **선행 조건·핵심 판정:** CSRF·UUID·201/409 경쟁
- **상세 케이스:** [Q02](test-case-execution-draft.md#q02-잠금-만료소유권다른-세션-로그아웃) ·
  [Q03](test-case-execution-draft.md#q03-동일-etag의-안전정보-동시-게시) ·
  [F36](test-case-execution-draft.md#f36-편집-창-uuid) ·
  [F38](test-case-execution-draft.md#f38-이미지204location-응답)
- **기존 테스트 후보:**
  [EditLockConcurrencyPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/EditLockConcurrencyPostgresTest.java)

### OP20 `DELETE /admin/cities/{cityId}/edit-lock`

- **성공 응답:** 204
- **선행 조건·핵심 판정:** CSRF·편집권·이미 없음/만료 no-op
- **상세 케이스:** [Q02](test-case-execution-draft.md#q02-잠금-만료소유권다른-세션-로그아웃) ·
  [F38](test-case-execution-draft.md#f38-이미지204location-응답) ·
  [S03](test-case-execution-draft.md#s03-csrf)
- **기존 테스트 후보:**
  [EditLockServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/EditLockServiceTest.java)

### OP21 `POST /admin/cities/{cityId}/edit-lock/heartbeats`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** CSRF·편집권·5분 갱신·사용자 활동과 구분
- **상세 케이스:** [Q01](test-case-execution-draft.md#q01-세션-만료와-활동-보고) ·
  [Q02](test-case-execution-draft.md#q02-잠금-만료소유권다른-세션-로그아웃)
- **기존 테스트 후보:**
  [EditLockServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/EditLockServiceTest.java)

## 초안·게시·재검토

### OP22 `POST /admin/cities/{cityId}/draft`

- **성공 응답:** 201
- **선행 조건·핵심 판정:** CSRF·편집권·중복409·복사·Location
- **상세 케이스:** [F18](test-case-execution-draft.md#f18-전체-교체초안-폐기) ·
  [F38](test-case-execution-draft.md#f38-이미지204location-응답)
- **기존 테스트 후보:**
  [DraftServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/DraftServiceTest.java)

### OP23 `GET /admin/cities/{cityId}/draft`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·편집권·초안404·ETag
- **상세 케이스:** [Q02](test-case-execution-draft.md#q02-잠금-만료소유권다른-세션-로그아웃) ·
  [S06](test-case-execution-draft.md#s06-다른-도시객체-접근)
- **기존 테스트 후보:**
  [DraftServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/DraftServiceTest.java)

### OP24 `PUT /admin/cities/{cityId}/draft`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** CSRF·편집권·초안 If-Match·전체 교체
- **상세 케이스:** [F03](test-case-execution-draft.md#f03-요청-구조) ·
  [F12](test-case-execution-draft.md#f12-if-match) ·
  [F15](test-case-execution-draft.md#f15-좌표-경계) ·
  [F16](test-case-execution-draft.md#f16-텍스트-경계) ·
  [F17](test-case-execution-draft.md#f17-배열-중복순서) ·
  [F19](test-case-execution-draft.md#f19-도시초안-소유권) ·
  [F20](test-case-execution-draft.md#f20-출처-확인-유지재검토) ·
  [F21](test-case-execution-draft.md#f21-출처-검토-입력) ·
  [F39](test-case-execution-draft.md#f39-http-본문content-type) ·
  [Q04](test-case-execution-draft.md#q04-안전-수정본의-저장충돌재검토) ·
  [Q05](test-case-execution-draft.md#q05-전체-교체출처-검토게시-불가의-독립성)
- **기존 테스트 후보:**
  [DraftRepositoryPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/DraftRepositoryPostgresTest.java)

### OP25 `DELETE /admin/cities/{cityId}/draft`

- **성공 응답:** 204
- **선행 조건·핵심 판정:** CSRF·편집권·초안 If-Match·공개본 보호
- **상세 케이스:** [F12](test-case-execution-draft.md#f12-if-match) ·
  [F18](test-case-execution-draft.md#f18-전체-교체초안-폐기) ·
  [F38](test-case-execution-draft.md#f38-이미지204location-응답)
- **기존 테스트 후보:**
  [DraftServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/DraftServiceTest.java)

### OP26 `POST /admin/cities/{cityId}/draft/preview`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** CSRF·편집권·expected 버전·200 미완료
- **상세 케이스:** [F22](test-case-execution-draft.md#f22-게시-준비필수-항목) ·
  [F40](test-case-execution-draft.md#f40-미리보기-비변경) ·
  [Q04](test-case-execution-draft.md#q04-안전-수정본의-저장충돌재검토)
- **기존 테스트 후보:**
  [PublicationValidatorTest](../../../backend/src/test/java/com/tripee/backend/admin/PublicationValidatorTest.java)

### OP27 `POST /admin/cities/{cityId}/draft/publications`

- **성공 응답:** 201
- **선행 조건·핵심 판정:** CSRF·편집권·expected 버전·201·전체 원자성
- **상세 케이스:** [F22](test-case-execution-draft.md#f22-게시-준비필수-항목) ·
  [F38](test-case-execution-draft.md#f38-이미지204location-응답) ·
  [Q04](test-case-execution-draft.md#q04-안전-수정본의-저장충돌재검토)
- **기존 테스트 후보:**
  [DraftWorkflowPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/DraftWorkflowPostgresTest.java)

### OP28 `POST /admin/cities/{cityId}/draft/safety-reviews`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** CSRF·편집권·expected/검토 안전 버전·ETag
- **상세 케이스:** [Q04](test-case-execution-draft.md#q04-안전-수정본의-저장충돌재검토) ·
  [F23](test-case-execution-draft.md#f23-안전정보-조합) ·
  [F24](test-case-execution-draft.md#f24-안전정보-기간-입력)
- **기존 테스트 후보:**
  [DraftServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/DraftServiceTest.java)

## 안전정보·사진

### OP29 `GET /admin/cities/{cityId}/safety`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·첫 공개본 null·안전 ETag
- **상세 케이스:** [F26](test-case-execution-draft.md#f26-안전정보-독립-게시) ·
  [S01](test-case-execution-draft.md#s01-관리자-api-접근-통제)
- **기존 테스트 후보:**
  [SafetyServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/SafetyServiceTest.java)

### OP30 `PUT /admin/cities/{cityId}/safety`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** CSRF·안전 If-Match·일반 편집권 불필요
- **상세 케이스:** [F23](test-case-execution-draft.md#f23-안전정보-조합) ·
  [F24](test-case-execution-draft.md#f24-안전정보-기간-입력) ·
  [F26](test-case-execution-draft.md#f26-안전정보-독립-게시) ·
  [F27](test-case-execution-draft.md#f27-중간-실패-롤백) ·
  [Q03](test-case-execution-draft.md#q03-동일-etag의-안전정보-동시-게시)
- **기존 테스트 후보:**
  [SafetyServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/SafetyServiceTest.java)

### OP31 `GET /admin/cities/{cityId}/photo-assets`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·편집권·도시 소속·빈 목록
- **상세 케이스:** [F37](test-case-execution-draft.md#f37-도시-사진-목록) ·
  [Q02](test-case-execution-draft.md#q02-잠금-만료소유권다른-세션-로그아웃)
- **기존 테스트 후보:**
  [DraftWorkflowPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/DraftWorkflowPostgresTest.java)

### OP32 `POST /admin/cities/{cityId}/photo-assets`

- **성공 응답:** 201
- **선행 조건·핵심 판정:** CSRF·편집권·multipart·Location
- **상세 케이스:** [F29](test-case-execution-draft.md#f29-사진-형식파일-크기) ·
  [F30](test-case-execution-draft.md#f30-이미지-해상도변환-실패) ·
  [F38](test-case-execution-draft.md#f38-이미지204location-응답) ·
  [S10](test-case-execution-draft.md#s10-파일-업로드-악용)
- **기존 테스트 후보:**
  [PhotoServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/PhotoServiceTest.java)

## 출처·AI

### OP33 `GET /admin/source-documents`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·q/limit/cursor
- **상세 케이스:** [F11](test-case-execution-draft.md#f11-목록커서-페이지) ·
  [F28](test-case-execution-draft.md#f28-출처-등록url길이) ·
  [F42](test-case-execution-draft.md#f42-커서-변조범위)
- **기존 테스트 후보:**
  [SourceServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/SourceServiceTest.java)

### OP34 `POST /admin/source-documents`

- **성공 응답:** 201
- **선행 조건·핵심 판정:** CSRF·URL·제목·publisher·Location
- **상세 케이스:** [F28](test-case-execution-draft.md#f28-출처-등록url길이) ·
  [F38](test-case-execution-draft.md#f38-이미지204location-응답) ·
  [S15](test-case-execution-draft.md#s15-url-자동-수집-방지)
- **기존 테스트 후보:**
  [SourceServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/SourceServiceTest.java)

### OP35 `GET /admin/source-documents/{sourceId}`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·존재/미존재·ID형식
- **상세 케이스:** [F28](test-case-execution-draft.md#f28-출처-등록url길이) ·
  [S01](test-case-execution-draft.md#s01-관리자-api-접근-통제)
- **기존 테스트 후보:**
  [JpaSourcePostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/JpaSourcePostgresTest.java)

### OP36 `POST /admin/cities/{cityId}/draft/experience-suggestions`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** CSRF·편집권·expected 버전·미설정502·timeout504
- **상세 케이스:** [F02](test-case-execution-draft.md#f02-초안-버전-형식) ·
  [F32](test-case-execution-draft.md#f32-ai-입력-한도) ·
  [F33](test-case-execution-draft.md#f33-ai-결과공급자-장애) ·
  [S16](test-case-execution-draft.md#s16-외부-ai-응답)
- **기존 테스트 후보:**
  [ExperienceSuggestionServiceTest](../../../backend/src/test/java/com/tripee/backend/admin/ExperienceSuggestionServiceTest.java)

## 과거 버전·감사 이력

### OP37 `GET /admin/cities/{cityId}/revisions/{revisionId}`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·게시된 해당 도시 버전·삭제 후404
- **상세 케이스:** [F34](test-case-execution-draft.md#f34-과거-버전감사-이력) ·
  [S06](test-case-execution-draft.md#s06-다른-도시객체-접근)
- **기존 테스트 후보:**
  [AuditRepositoryPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/AuditRepositoryPostgresTest.java)

### OP38 `GET /admin/audit-events`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·도시참조/커서·삭제 이력 보존
- **상세 케이스:** [F11](test-case-execution-draft.md#f11-목록커서-페이지) ·
  [F34](test-case-execution-draft.md#f34-과거-버전감사-이력) ·
  [F42](test-case-execution-draft.md#f42-커서-변조범위)
- **기존 테스트 후보:**
  [AuditRepositoryPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/AuditRepositoryPostgresTest.java)

### OP39 `GET /admin/audit-events/{eventId}`

- **성공 응답:** 200
- **선행 조건·핵심 판정:** 인증·독립 snapshot·없는 이벤트404
- **상세 케이스:** [F34](test-case-execution-draft.md#f34-과거-버전감사-이력) ·
  [S01](test-case-execution-draft.md#s01-관리자-api-접근-통제)
- **기존 테스트 후보:**
  [AuditRepositoryPostgresTest](../../../backend/src/test/java/com/tripee/backend/admin/AuditRepositoryPostgresTest.java)

## 작업마다 추가로 펼칠 검증

1. 정상 성공·빈 결과/초기 상태·없는 리소스·경계/입력 오류를 각 요청의 계약에 맞게 분리한다. JSON은
   HTTP-OK/ERR, 이미지/204는 F38을 적용한다.
2. 관리자 작업은 S01의 익명·만료 경계, 쓰기는 S03의 CSRF를 적용한다. 편집권 필요한 작업은 Q02/S06,
   If-Match는 F12를 별도로 적용한다. 로그인/CSRF 발급과 독립 안전 게시의 예외를 유지한다.
3. 쓰기는 정상 후조건과 거부 시 NO-WRITE를 모두 검사한다. 과거 버전/공개 데이터와 파일 같은 다른
   저장소의 영향도 포함한다.
4. 각 operation에 성공/오류 상태·요청 형태·세션/토큰·DB 상태별 leaf case를 할당한다. 시험 제외
   상태는 미지원/미구현/정책미정/도달불가로 구분하고 이유를 남긴다. 429 미구현 등을 도달불가로
   숨기지 않는다.
5. leaf case별 테스트 클래스/메서드·assertion 위치·실행 커밋/결과를 연결한 뒤 구현 완료로 갱신한다.
   기존 클래스에 없는 분기를 정상 경로가 있다는 이유로 완료 처리하지 않는다.

## 출시 필수 검증 연결

현재 [통합 계약 검사](../../../tools/api-contract/verify-integration.mjs)는 PostgreSQL 테스트의
`backend/build/contract/responses.jsonl`에서 응답을 50건 이상 읽고 메서드·경로·상태 코드 조합 16개를
필수로 확인한다. 수집한 응답의 상태·콘텐츠 타입·스키마와 선언된 헤더의 존재를 검사한다. 아래 표의
오류 코드, 동적 응답 값, DB 후조건은 케이스별 테스트에 연결해 실행한다.

| 필수 케이스 ID           | API와 기대 결과                                                      | 합격 증거                                                                                                               |
| ------------------------ | -------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| F12-STALE-DRAFT          | OP24, 이전 초안 ETag로 `412 RESOURCE_VERSION_MISMATCH`               | 응답 코드와 NO-WRITE. 기존 계약 검사의 412 응답 조합과 케이스 단언을 각각 연결                                          |
| Q01-EXPIRED              | OP10, 마지막 활동 후 3,600초에 `401 SESSION_EXPIRED`                 | 세션 무효화와 후속 세션 조회 결과. 계약 검사 필수 조합에 추가할 후보                                                    |
| S03-DRAFT-CSRF           | OP24, 나머지 입력이 정상일 때 CSRF 토큰 누락으로 `403 CSRF_INVALID`  | 동일 준비 상태의 정상 토큰 대조군과 NO-WRITE. 계약 검사 필수 조합에 추가할 후보                                         |
| Q04-PUBLICATION-CONFLICT | OP27, `409 SAFETY_CHANGED`와 `context.currentSafetyRevisionId == S1` | B가 실제 게시한 안전 버전 ID와 일치, NO-WRITE. [G10](#g10) 해결 후 계약 검사에 연결                                     |
| F38-PUBLICATION-LOCATION | OP27·OP37, 게시 `201`과 `Location` 재조회 `200`                      | Location의 도시·게시 버전 ID 일치와 같은 리비전의 조회 결과                                                             |
| R11-T01~T05              | 출시 범위에 관리자 게시가 포함되는 경우 실제 관리자·공개 UI 흐름     | 동일 후보로 수행한 스테이징 브라우저·HTTP·DB 증거. 절차는 [R11](test-case-execution-draft.md#r11-실제-사용자-흐름) 참고 |

출시 필수 케이스는 이름을 지정한 자동화 결과 또는 스테이징 기록에 ID·후보 커밋·판정·증거를 남긴다.
요구된 증거가 누락되거나 필수 케이스가 스킵·차단되면 해당 항목은 미통과다. 기존 통합 검사 합격
결과는 그 검사 범위의 증거로 첨부한다. 이 표에는 향후 자동화에 연결할 케이스도 포함한다.

## 서비스 분리 시 검증 연결

아래 OP는 기존 공개·관리자 API에서 결과를 확인할 지점이다. 내부 요청·응답과 이벤트의 계약이 확정되면
해당 계약 버전과 제공자·소비자 검사도 연결한다.

| 분리 시 검증할 흐름                   | 기존 API 확인 지점            | MSA 케이스                                                                                                                                                                                                         |
| ------------------------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 초안 저장·게시 결과·과거 게시본       | OP24·OP27·OP37                | [MS01 데이터 소유권](msa-test-spec-draft.md#ms01-데이터-소유권과-모듈-경계), [MS02 서비스 간 계약](msa-test-spec-draft.md#ms02-서비스-간-계약과-독립-배포)                                                         |
| 공개·비공개·사진·안전정보의 상태 일치 | OP01~OP03·OP05·OP16·OP27·OP30 | [MS04 정합성](msa-test-spec-draft.md#ms04-게시비공개-전환의-정합성), [MS05 이벤트 재처리](msa-test-spec-draft.md#ms05-이벤트-중복역순재처리)                                                                       |
| 서비스 장애·권한 회수                 | OP08·OP24·OP27·OP32·OP36      | [MS03 부분 장애](msa-test-spec-draft.md#ms03-부분-장애와-재시도), [MS06 서비스 호출 권한](msa-test-spec-draft.md#ms06-서비스-호출-권한과-사용자-세션)                                                              |
| 교체 배포·추적·데이터 이전            | OP02·OP05·OP27·OP37·OP38      | [MS07 배포와 롤백](msa-test-spec-draft.md#ms07-서비스별-배포와-롤백), [MS08 분산 추적](msa-test-spec-draft.md#ms08-분산-추적과-장애-탐지), [MS09 전환과 복구](msa-test-spec-draft.md#ms09-저장소-분리-전환과-복구) |

## 계약·정책 쟁점 등록부

아래는 문서 검토로 확인한 공백 또는 대조할 차이이며 모두 현재 열린 상태다. 제안값을 확정 기대값에
섞지 않는다. 결론은 각 담당이 계약 승인/구현 수정/비적용 근거 중 하나로 기록한다.

### G01

- **관찰·쟁점:** editVersion은 bigint지만 최댓값에서 다음 저장 시 증가·DB overflow 처리 정책 미명시
- **테스트·문서 처리:** F02의 최대 형식 검증과 실제 저장의 최대→증가를 분리. DB fixture 최대값에서
  부분 저장·감사 기록이 남지 않는지 확인
- **필요한 결정·담당 역할:** 최대 도달 시 거부/버전 정책·오류 계약 확정 / 백엔드·API 검토자

### G02

- **관찰·쟁점.** AI 요청은 버전의 숫자 문자열 형식과 bigint 범위를 검사한다. 미리보기·일반 게시의
  `expectedEditVersion`은 문자열 검사 후 현재 값과 대조한다. 안전 재검토는 기대 버전을 현재 JSON
  값과 대조하므로 JSON 숫자의 응답도 경로마다 다를 수 있다.
- **테스트 처리.** [F02](test-case-execution-draft.md#f02-초안-버전-형식)에서 타입·선행
  0·음수·overflow·유효한 이전 버전을 구분한다.
- **필요한 결정.** 백엔드와 API 검토자가 공통 형식 검증과 오류 우선순위를 확정한다.

### G03

- **관찰·쟁점:** 게시/입력422의 필드별 violations는 화면 정책에 필요하지만 현재 일반 메시지
  중심·스키마상 선택
- **테스트·문서 처리:** F22는 preview path와 게시 status/code를 별도 assertion. 게시 violations까지
  완료 판정 금지
- **필요한 결정·담당 역할:** 필수 필드·path 형식·화면 표시 계약 / 백엔드·프론트 담당

### G04

- **관찰·쟁점:** 로그인/AI429는 명세에 있으나 제한 기준·방어 구현 미확인
- **테스트·문서 처리:** S05/S12와 L04는 정책차단. 요청 수를 임의로 정해 출시 합격 처리 금지
- **필요한 결정·담당 역할:** N/W·계정/IP/동시성·회복·HTTP 계약 / 서비스 운영·백엔드

### G05

- **관찰·쟁점:** 세션의 관리자 신원/ROLE/계정 비활성화 후 회수 범위
- **테스트·문서 처리:** S02/L03은 현재 인증 통과와 회수 정책을 대조. 다른 인증방식이 실제 생성
  가능한지도 기록
- **필요한 결정·담당 역할:** 접근 정책·회수 반영 기한·세션 저장소 / 보안·백엔드

### G06

- **관찰·쟁점:** JSON 문자열의 공백/길이·URL length/Unicode 처리 차이 가능
- **테스트·문서 처리:** F16/F28에 ASCII·한글·보조평면·공백을 분리.
  U+0020/U+0009/U+3000/U+00A0/U+200B 시험, 모든 문자를 무조건 공백으로 단정하지 않음
- **필요한 결정·담당 역할:** 허용 공백·UTF-16/코드포인트·trim 정책 / API 검토자

### G07

- **관찰·쟁점:** 전체 본문 JSON null·중복 키·중복 쿼리·정수 강제변환의 parser/binding 정책 미명시
- **테스트·문서 처리:** F39에 raw bytes로 null/배열/빈/잘못된 JSON,
  `{"nameKo":"첫값","nameKo":"둘째값"}`, limit 두 개, `1.0` 시험. 중복 키를 JsonNode 생성으로
  표현하지 않음
- **필요한 결정·담당 역할:** 거부/허용·명확한 오류 code·Content-Type / 백엔드·API 검토자

### G08

- **관찰·쟁점:** cursor는 서명 토큰이 아니고 동시 변경 중 snapshot 페이지 정책 미정
- **테스트·문서 처리:** F42의 형식·필터 보호와 인증 범위 검사. 서명 의무나 동시 변경 중 무조건
  무누락을 현재 계약으로 가정하지 않음
- **필요한 결정·담당 역할:** 실시간/스냅샷 페이지·입력 위변조 보호 필요성 / API 검토자

### G09

- **관찰·쟁점:** SLO·RPO/RTO·보존·프록시/DB TLS·실제 위치/해외 전송 정책 미정
- **테스트·문서 처리:** R/L 항목은 적용 판정·목표·운영 환경 준비 후 실행. 연습값은 합격 기준과 분리
- **필요한 결정·담당 역할:** 출시 범위·운영 구조·법령 적용 / 서비스 운영·팀 책임자

### G10

- **관찰·쟁점.** API 정책은 일반 게시에서 안전 기반 충돌 시 `409 SAFETY_CHANGED`와 현재 안전 버전 ID
  반환을 요구한다. 소스 검토에서는 409와 오류 코드가 설정되지만 응답에 현재 버전 ID를 넣는 처리가
  확인되지 않았다. [안전정보 게시 정책](../api-v1.md#7-안전정보-독립-게시와-재검토)
- **테스트 처리.** [Q04](test-case-execution-draft.md#q04-안전-수정본의-저장충돌재검토)에서 오류
  본문의 `context.currentSafetyRevisionId`를 직전에 독립 게시한 안전 버전 ID와 대조한다. HTTP
  상태·오류 코드·ID·무변경 결과를 각각 기록한다.
- **필요한 결정.** 백엔드·프론트 담당이 오류 응답과 화면 재검토 흐름을 검토한다. 현재 계약을
  유지하면 응답 구현과 필수 케이스를 연결한다.

이 등록부는 구현 결함 확정 목록이 아니다. 요청 재현·명세 대조·영향 확인을 거친 뒤 분류한다. 문서
보강만으로 열린 쟁점을 해결됨으로 바꾸지 않는다.
