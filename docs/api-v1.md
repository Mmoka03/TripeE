# TripeE MVP API v1 — 승인된 기본안

작성일: 2026-09-28. API 계약 정의이며 서버 구현 완료를 뜻하지 않는다.

검수 상태: 2026-10-01 사용자 기본안 전체 승인. 지도·AI 제공자 등 명시적으로 미정인 기술 선택은 별도 확정한다.

- 기계 판독 명세: [openapi-v1.json](openapi-v1.json) — OpenAPI 3.1.1, Swagger Editor 등에서 확인 가능.
- 기존 정책: [ERD v2](erd-v2.md), [PostgreSQL 제약조건](db-constraints-v1.md).
- 작성 도구: [build.mjs](../tools/api-contract/build.mjs), 검증: [validate.mjs](../tools/api-contract/validate.mjs).
- 아래 기본값을 포함한 API 정책 검수가 완료되었다. 본문에 남은 '제안' 표현도 승인된 기본값을 의미한다.

## 1. 승인된 추가 기준

| 항목             | 승인된 기본값                                        | 이유와 영향                                                                              |
| ---------------- | ---------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| 초안 저장 단위   | 화면 전체 데이터를 PUT 한 번으로 저장                | 사진/태그/경험 일부만 저장되는 상태를 줄임. 항목 누락은 삭제가 될 수 있어 모든 배열 필수 |
| 오래된 편집 감지 | GET의 ETag를 PUT/PATCH/DELETE의 If-Match로 전달      | 같은 관리자라도 오래된 화면이 새 데이터를 덮어쓰지 못하게 함                             |
| 근거 재확인      | 항목 제목/본문/종류가 바뀌면 해당 근거 확인을 무효화 | 이전 문구의 확인 기록으로 바뀐 내용을 검증 완료 처리하지 않음. 순서만 바뀌면 유지        |
| 이미지 업로드    | JPEG/PNG/WebP, 장당 10 MiB, 해상도 4천만 픽셀 이하   | 실제 서버 처리 한도 제안. 사진 개수 제한과는 별개                                        |
| AI 적용 시점     | 경험 후보를 먼저 반환하고, 관리자가 적용·저장        | AI 응답이 작성 중 초안을 자동 덮어쓰지 않음. 서버 자동 임시저장은 후순위                 |
| AI 실행 범위     | 경험 후보 생성만, 동기 요청 최대 30초                | 새 작업 큐·작업 테이블 없이 MVP 구현. 안전정보 자동 생성·게시 제외                       |
| 국가 지도 이동   | 국가 코드로 프론트의 국가 경계 설정 사용             | 외부 지오코딩 API 없이 시작. 지도 공급원 선정 시 JP/IT/CN 전체 경계 확보                 |
| 다시 공개        | 공개본으로 초안 생성 후 게시 경로 사용               | 비공개 해제 전 게시 필수 조건과 안전 상태 재검증. 단순 PUBLIC 상태 변경 API 없음         |
| 관리자 목록      | 커서 페이지네이션, 기본 20개/최대 100개              | 이력 증가에 대응. 공개 지도 핀은 MVP에서 전체 반환                                       |

이 기준은 공통으로 강제되는 REST 표준이 아니라 이번 프로젝트를 위한 선택이다. 회원가입, 태그 필터 검색 UI, 버전 복원, 클라우드 배포 등 새로운 제품 기능을 추가하지 않는다.

## 2. 공통 규칙

- 기본 경로: `/api/v1`. JSON 필드는 camelCase, URL 리소스는 복수형·소문자·하이픈 사용.
- GET 조회, POST 생성/명시적 작업, PUT 전체 교체, PATCH 제한된 속성 변경, DELETE 제거/해제.
- 게시·검토 같은 도메인 작업은 `/publications`, `/safety-reviews`로 표현한다. 테이블마다 CRUD API를 기계적으로 만들지 않는다.
- 성공은 리소스 객체를 직접 반환한다. 목록은 `{ "items": [...] }`, 관리자 페이지 목록은 `nextCursor`를 추가한다.
- 오류는 HTTP 상태와 `application/problem+json`을 사용한다. 오류를 200으로 감싸지 않는다.
- DB bigint ID와 editVersion은 JSON **문자열**로 전달한다. 예: `"101"`. 브라우저 Number 정밀도 문제를 피한다. 서버는 PostgreSQL bigint 범위도 검사한다.
- 시각은 ISO 8601 UTC, 예: `2026-09-28T03:00:00Z`. 클라이언트가 checkedAt/publishedAt/updatedBy 같은 신뢰 필드를 쓰게 하지 않는다.
- 좌표는 숫자, 빈 값은 null, 빈 목록은 []. 빈 문자열/공백 입력은 클라이언트가 null로 정리해 전송한다. 허용되지 않은 필드와 잘못된 형식은 거부한다.
- 초안의 제목/본문 등은 null로 저장 가능하지만, 게시 기준을 통과해야 공개된다. title 최대 200자, body 3,000자, 근거 메모 4,000자.
- 같은 도시라도 공개 응답과 관리자 응답 DTO를 분리한다. 공개 응답에는 관리자·초안·잠금 토큰·실제 저장 경로·검토 메모를 넣지 않는다.
- 공개 조회도 일단 `Cache-Control: no-store`로 시작한다. 안전정보/비공개 전환의 오래된 캐시 문제를 피하고, 캐시 최적화는 후속으로 검토한다.
- 모든 관리자 응답은 no-store. URL/요청 로그에 비밀번호·세션·잠금 토큰·CSRF 토큰을 남기지 않는다.
- GET은 편집 잠금 획득, 활동 시각 연장, 이력 변경을 수행하지 않는다.
- 조건 검사 실패는 부분 반영하지 않는다. 쓰기 작업은 서버 트랜잭션 단위로 처리한다.

## 3. 인증·CSRF·활동 시각

서버 세션 쿠키를 사용한다. 예시 쿠키 이름은 `TRIPEE_SESSION`이며 Spring 기본값이 아닌 프로젝트 설정안이다. HttpOnly, SameSite=Lax, HTTPS에서는 Secure를 사용한다. 로컬 HTTP에서는 개발 설정만 별도로 둔다. 프론트/API는 같은 출처로 프록시하여 CORS 복잡성을 줄이는 것을 권한다.

1. 로그인 전 `GET /admin/csrf` → CSRF 토큰 받기.
2. `POST /admin/session`에 `X-CSRF-TOKEN`과 loginId/password 전송.
3. 서버는 인증 성공 시 세션 ID를 교체하고 쿠키를 발급.
4. 로그인 뒤 CSRF 토큰을 다시 가져온다. 이후 모든 관리자 POST/PUT/PATCH/DELETE에 토큰 전송.
5. `GET /admin/session`으로 로그인 상태 확인. 실패/만료는 401이며 관리자 API를 HTML 로그인 페이지로 리다이렉트하지 않는다.
6. 실제 입력/클릭/페이지 이동이 있을 때 `POST /admin/session/activity`. 기본 제안은 최초 활동 시 및 활동 중 최대 1분 간격으로 묶어 보고.
7. 서버가 마지막 활동 보고를 받은 뒤 1시간이면 만료. 폴링·heartbeat·자동저장 자체는 활동 보고를 발생시키지 않는다. 활동 보고 간격에 따른 작은 오차는 허용한다.

세션에 편집권과 편집 창의 연결 정보를 보관한다. DB 잠금 행의 관리자 ID만 확인하지 않고, 해당 인증 세션에서 발급받은 토큰/창인지도 확인한다. 다른 세션의 로그아웃이 같은 계정의 다른 편집권을 지우지 않도록 한다. 프레임워크 세션 저장소 구현은 후속 작업이며 도메인 테이블 추가를 확정한 것은 아니다.

## 4. 공개 조회 API

경로는 모두 `/api/v1` 기준이다.

| Method | 경로                            | 응답/동작                                    |
| ------ | ------------------------------- | -------------------------------------------- |
| GET    | `/cities`                       | 지도 핀용 도시 전체 목록. 선택 `countryCode` |
| GET    | `/cities/{cityId}`              | 공개 상세 또는 정보 준비 중 객체             |
| GET    | `/search?q=교토&limit=10`       | CITY/COUNTRY를 구분한 검색 결과              |
| GET    | `/tags`                         | 활성 태그 사전                               |
| GET    | `/media/photo-assets/{assetId}` | 이미지 바이트. 공개 자산만 익명 허용         |

### 지도와 검색

- `/cities`는 PUBLIC/PREPARING만 반환한다. HIDDEN과 삭제된 도시는 제외한다.
- 3개 도시 MVP에서는 모든 핀을 받는다. 확대/축소·겹침·묶음 핀은 프론트에서 처리하며 서버 클러스터 API는 만들지 않는다.
- 데이터가 커지면 화면 범위 기반 조회를 새로 설계한다. 현재 응답을 설명 없이 잘라내지는 않는다.
- 검색은 등록된 공개/준비 중 도시와 그 소속 국가의 한국어·영문명에 한정한다. 전 세계 모든 지명을 검색하는 외부 API가 아니다.
- 앞뒤 공백 제거 후 1~100자. 결과 없음은 200과 items:[]. q 누락/공백뿐인 q는 400.
- 정확히 일치 → 앞부분 일치 → 부분 일치 순으로 우선하며 같은 우선순위에는 type/id로 안정적인 순서를 적용한다.
- CITY 선택은 반환된 좌표로 이동 후 상세 열기. COUNTRY 선택은 country.code로 해당 국가 전체 범위를 맞추기.
- 국가 전체 경계는 지도 데이터 선정 시 클라이언트 설정으로 확보한다. 등록된 도시 좌표만으로 국가 전체 범위라고 표시하지 않는다.
- 지도 이동은 사용자 검색 선택 때만 실행한다. 데이터 재조회 완료만으로 카메라를 이동하지 않는다.

### 공개 응답 분리

준비 중 도시 예:

```json
{
  "id": "1",
  "country": { "id": "1", "code": "JP", "nameKo": "일본", "nameEn": "Japan" },
  "nameKo": "교토",
  "nameEn": "Kyoto",
  "coordinates": { "latitude": 35.0, "longitude": 135.0 },
  "visibility": "PREPARING",
  "message": "도시 정보를 준비 중이에요."
}
```

PUBLIC은 사진·태그·경험(최대 3개)·방문 시기·접근·현지 이동·고려사항·현재 안전정보를 포함한다. 내용 형식은 OpenAPI PublishedCity를 따른다. HIDDEN/삭제/존재하지 않는 ID는 공개 상세에서 모두 404다. 과거 도시 상태나 내부 초안을 드러내지 않는다.

안전 항목에는 CURRENT/UPCOMING/EXPIRED를 반환한다. EXPIRED/UPCOMING을 현재 경보 배지로 표시하지 않는다. 과거 경보가 끝났다고 자동으로 '미발령'으로 바꾸지 않는다. 유효한 공식 정보가 없으면 '현재 상태 재확인 필요'로 표시하고 출처·확인일을 보여준다.

## 5. 관리자 API 목록

### 세션·기준 정보

| Method              | 경로                      | 역할                             |
| ------------------- | ------------------------- | -------------------------------- |
| GET                 | `/admin/csrf`             | 로그인 전에도 허용되는 CSRF 발급 |
| POST / GET / DELETE | `/admin/session`          | 로그인 / 상태 조회 / 로그아웃    |
| POST                | `/admin/session/activity` | 실제 사용자 활동 기록            |
| GET                 | `/admin/countries`        | 설정한 국가 기준 정보            |
| GET                 | `/admin/tags`             | 비활성 항목을 포함한 태그 사전   |

국가/태그 사전은 초기 데이터로 관리하며 사전 편집 UI/API는 MVP에 추가하지 않는다. 이미 연결된 비활성 태그는 읽을 수 있지만 신규 선택은 거부한다. 기존 연결 유지 여부는 단순 재저장 때 바뀌지 않는다.

### 도시·잠금·초안

| Method | 경로                                          | 성공 | 조건                                                              |
| ------ | --------------------------------------------- | ---- | ----------------------------------------------------------------- |
| GET    | `/admin/cities`                               | 200  | 목록, 커서·검색·상태 필터                                         |
| POST   | `/admin/cities`                               | 201  | HIDDEN 도시+초안 생성; countryId 필수, 나머지 기본 내용 null 가능 |
| GET    | `/admin/cities/{cityId}`                      | 200  | 관리 상태와 ETag, 초안 본문 없음                                  |
| PATCH  | `/admin/cities/{cityId}`                      | 200  | HIDDEN/PREPARING만; 편집권+If-Match                               |
| DELETE | `/admin/cities/{cityId}`                      | 204  | 편집권+If-Match; 이력과 삭제를 함께 처리                          |
| GET    | `/admin/cities/{cityId}/edit-lock`            | 200  | 소유자·만료 시각 조회, 토큰 비공개                                |
| POST   | `/admin/cities/{cityId}/edit-lock`            | 201  | editorInstanceId로 새 편집권 획득                                 |
| POST   | `/admin/cities/{cityId}/edit-lock/heartbeats` | 200  | 토큰으로 1분 간격 생존 확인                                       |
| DELETE | `/admin/cities/{cityId}/edit-lock`            | 204  | 본인 편집권 반납                                                  |
| POST   | `/admin/cities/{cityId}/draft`                | 201  | 편집권 필요; 공개본을 복사해 초안 생성                            |
| GET    | `/admin/cities/{cityId}/draft`                | 200  | 본인 편집권 필요; 다른 관리자는 초안 진입 불가                    |
| PUT    | `/admin/cities/{cityId}/draft`                | 200  | 편집권+If-Match; 전체 초안 저장                                   |
| DELETE | `/admin/cities/{cityId}/draft`                | 204  | 편집권+If-Match; 초안 폐기, 공개본 유지                           |
| POST   | `/admin/cities/{cityId}/draft/preview`        | 200  | expectedRevisionId/editVersion 확인, 게시 준비 상태 반환          |
| POST   | `/admin/cities/{cityId}/draft/publications`   | 201  | 게시 요건 재검사, 전체 게시, 편집권 반납                          |
| POST   | `/admin/cities/{cityId}/draft/safety-reviews` | 200  | 최신 안전정보와 수정 내용을 재검토하여 초안에 저장                |

POST 도시 생성은 DB 트랜잭션으로 초안까지 생성한다. 편집 잠금은 별도로 획득하므로 생성 응답만으로 수정 권한이 생기지 않는다. 기존 초안이 있을 때 POST draft는 409 DRAFT_ALREADY_EXISTS를 반환하며 GET으로 이어서 편집한다.

편집 토큰은 X-Edit-Lock-Token 헤더로 전송하고 URL에 넣지 않는다. 만료 기준은 서버 시각이며 응답의 serverTime/expiresAt은 화면 안내에만 사용한다. 잠금 획득에 대한 응답을 잃어 원문 토큰을 받지 못했다면 해시에서 재발급하지 않는다. 잠금 상태를 확인하고 최대 5분 만료 후 다시 획득한다. 강제 탈취 API는 없다.

### 안전정보·사진·출처·AI·이력

| Method     | 경로                                                  | 역할                                                           |
| ---------- | ----------------------------------------------------- | -------------------------------------------------------------- |
| GET        | `/admin/cities/{cityId}/safety`                       | 현재 공개 안전정보와 ETag. 아직 없으면 revisionId:null         |
| PUT        | `/admin/cities/{cityId}/safety`                       | If-Match로 검증하고 새 안전 버전 독립 게시. 일반 편집권 불필요 |
| GET        | `/admin/cities/{cityId}/photo-assets`                 | 본인 편집권으로 도시 소유 사진 목록                            |
| POST       | `/admin/cities/{cityId}/photo-assets`                 | multipart 사진 업로드 및 출처·권리 메타데이터 등록             |
| GET / POST | `/admin/source-documents`                             | 출처 검색 / 등록                                               |
| GET        | `/admin/source-documents/{sourceId}`                  | 출처 문서 메타데이터 조회                                      |
| POST       | `/admin/cities/{cityId}/draft/experience-suggestions` | 입력 본문으로 AI 경험 후보 생성                                |
| GET        | `/admin/cities/{cityId}/revisions/{revisionId}`       | 게시된 과거 일반 버전과 당시 안전정보 조회                     |
| GET        | `/admin/audit-events?cityRefId=1`                     | 삭제된 도시 포함 감사 이력 목록                                |
| GET        | `/admin/audit-events/{eventId}`                       | 독립된 이력 스냅샷 조회                                        |

도시가 삭제되면 도시 하위 경로는 404지만 `/admin/audit-events`는 계속 사용 가능하다. 일반 이력에는 게시 당시 안전정보, 독립 안전 게시 이력에는 해당 변경 시점 안전정보가 나타난다. 복원/과거 포인터 되돌리기 API는 없다.

## 6. 초안 저장 계약

초안 GET의 ETag를 PUT에 그대로 전달한다. 예: `ETag: "draft-101-5"`. 문자열은 예시이며 클라이언트가 조립하지 않는다. 도시 관리 상태, 일반 초안, 공개 안전정보는 각각 별도 ETag다.

- PUT은 수정 가능한 초안의 전체 교체다. 배열 누락은 422, 빈 배열은 해당 연결을 비운다는 뜻.
- 기본 필드는 명시적으로 null 가능. tagIds는 중복 없이 전달한다.
- photos는 기존 photoAssetId, 순서, altText, caption을 전달한다. 제거는 연결만 삭제하며 파일은 유지한다.
- contentItems의 id=null은 새 항목이다. 기존 id는 현재 초안 소속이어야 하며 공개 버전/다른 초안 ID를 가져올 수 없다. 중복 id는 422.
- 기존 항목이 배열에서 빠지면 해당 초안에서 제거한다. 공개본에는 영향이 없다.
- displayOrder는 클라이언트 이동 UI가 계산하되 서버가 양수·그룹 내 유일성을 검사한다. 저장 시 필요한 UNIQUE만 지연 검사한다.
- 모든 콘텐츠 source link는 등록된 sourceDocumentId를 가리킨다. reviewAction=KEEP는 기존 검토 결과 유지, CONFIRM은 현재 관리자와 서버 시각으로 재확인.
- 새 근거나 변경된 evidenceNote에 KEEP을 사용하면 검토 미완료로 저장한다. 이전 checkedAt을 복사해 검토 완료처럼 만들지 않는다. 내용 변경 시 영향을 받는 근거는 재검토 대상으로 처리한다. 최소 기본안은 해당 항목 title/body/kind 변경 시 그 항목의 모든 근거 확인을 무효화한다.
- 단순 순서 변경/다른 항목 편집에서는 확인일을 갱신하지 않는다. CONFIRM에는 근거 메모가 필수다.
- 일반 초안 safetyDraft=null은 '안전정보 미수정'. 이미 안전 수정본이 생겼다면 null로 지워 바로 게시할 수 없고 409 SAFETY_DRAFT_CANNOT_BE_DROPPED다. 초안 전체 폐기는 별도 가능하다.
- 안전정보를 처음 편집할 때 기반이 달라졌다면 409 SAFETY_CHANGED 후 재검토 경로로 이동한다.
- 기존 안전 수정본의 내용은 보존·저장 가능하지만 오래된 기반으로 게시할 수 없다. 기반 포인터는 일반 PUT에서 클라이언트가 변경할 수 없다.
- 저장 성공 시 서버가 새 ID와 검토 결과, 증가한 editVersion 및 ETag를 반환한다. 프론트는 해당 응답을 새 기준으로 사용한다.

`저장하고 편집 종료`는 PUT 성공 후 DELETE edit-lock 순서다. PUT 실패 시 편집 종료 성공으로 표시하지 않는다. 두 요청 사이 연결이 끊겨도 저장은 유지되고 잠금은 정상 해제 또는 만료된다.

### 게시 요청 예

```http
POST /api/v1/admin/cities/1/draft/publications
Content-Type: application/json
X-CSRF-TOKEN: <token>
X-Edit-Lock-Token: <lease>

{"expectedRevisionId":"101","expectedEditVersion":"5"}
```

서버는 CITY 행을 잠근 뒤 편집권·초안 버전·게시 요건·안전 기반을 다시 검사한다. 성공 시 새 일반 공개본과 필요한 안전 공개본, 감사 스냅샷을 한 번에 반영하고 편집 잠금을 해제한다.

## 7. 안전정보 독립 게시와 재검토

### 독립 게시

1. GET safety로 현재 내용과 ETag를 받는다. 일반 초안 잠금이 없어도 가능하다.
2. 화면에서 수정·검토한다. 이 임시 입력은 브라우저 메모리에 있다.
3. PUT safety에 If-Match, CSRF와 전체 안전 콘텐츠를 보낸다.
4. 서버가 최신 ETag를 트랜잭션 안에서 다시 확인한다. 다르면 412 SAFETY_VERSION_MISMATCH.
5. 같으면 검토 조건을 확인하고 새 안전 버전·감사 이벤트를 저장한다. 일반 초안/잠금/도시 노출 상태는 바꾸지 않는다.

### 일반 초안의 안전 충돌

- 일반 게시가 오래된 기반을 사용하면 409 SAFETY_CHANGED와 현재 안전 버전 ID를 반환한다.
- 프론트는 일반/안전 입력을 보존하고 GET safety로 최신 정보를 표시한다.
- 관리자가 확인·수정한 결과를 POST safety-reviews로 제출한다. expectedRevisionId, expectedEditVersion, reviewedPublicSafetyRevisionId, safety 본문이 필요하다.
- 서버는 검토한 공개 버전이 여전히 최신인지 확인하고 초안/기반을 갱신한다. 다시 바뀌었으면 409.
- 이 작업은 게시가 아니다. 미리보기 후 일반 게시 시에도 충돌을 다시 검사한다.
- 안전정보를 한 번도 수정하지 않은 일반 초안은 최신 안전 공개본을 유지하면서 게시 가능하다.

## 8. 사진과 AI 입력

### 사진

- 한 요청에 파일 1개와 metadata JSON을 multipart/form-data로 전송한다.
- 한 장 최대 10 MiB, JPEG/PNG/WebP만 허용하는 기본안. 선언된 MIME뿐 아니라 실제 디코딩 형식·해상도를 확인한다. 픽셀 수 제한은 압축 파일의 과도한 메모리 사용을 막기 위한 제안이다.
- 파일명은 서버가 생성하고 사용자가 보낸 경로를 사용하지 않는다. 불필요한 위치정보 등 EXIF는 제거하고 실제 이용 조건에 필요한 출처 표기는 메타데이터로 유지한다.
- sourceUrl을 입력했다고 서버가 그 URL의 이미지를 가져오지는 않는다. 원격 이미지 다운로드 기능은 범위에 없다.
- rightsConfirmed=true는 관리자 확인 의사표시다. 라이선스/이용 근거가 있어야 하며 rightsCheckedAt은 서버가 기록한다. false이면 검토 전 자산으로 저장할 수 있으나 게시할 수 없다.
- 업로드된 자산은 자동으로 초안에 붙지 않는다. PUT draft의 photos에 선택하여 추가한다.
- 기존 자산의 권리 메타데이터를 수정하는 API는 없다. 현재 MVP에서는 새 자산으로 등록한다. 편집 UI에서 업로드 전 권리 항목 확인을 안내한다.
- 익명 이미지 GET은 현재 PUBLIC 일반 버전에서 사용하는 사진만 허용한다. 관리자는 보존 자산 조회 가능. 삭제된 원본은 이력에서도 재생을 보장하지 않는다.

### AI

- 출처 URL/제목을 먼저 등록하고, 사용 가능한 자료 본문을 요청에 명시적으로 입력한다. URL 자동 수집은 하지 않는다.
- 입력 1~5개 자료, 자료당 10,000자, 전체 30,000자 이하 제안. 출력 본문 3,000자 이하 경험 후보 최대 5개.
- AI에는 서버에서만 호출한다. API 키는 프론트로 보내지 않는다. 외부 자료의 지시문을 관리자 명령으로 취급하지 않는다.
- DB 트랜잭션이나 행 잠금을 외부 AI 호출 동안 유지하지 않는다. 요청 시작 시 세션/편집권/버전을 확인한다.
- 응답에는 시작 시 revisionId/editVersion과 persisted:false를 반환한다. 호출 사이 편집권이 만료되어도 자동 저장하지 않으므로 기존 초안을 덮어쓰지 않는다. 결과 적용 전 현재 초안을 다시 확인하고 유효한 편집권/If-Match로 저장한다.
- 모델이 입력에 없던 source ID를 만들거나 스키마를 어기면 502 AI_INVALID_RESPONSE. 후보는 검토 전이며 checkedAt을 자동 설정하지 않는다.
- 제한 시간 30초를 넘기면 504. 실패 시 기존 초안은 그대로다. AI 실패가 일반 초안 편집을 막지 않는다.
- AI 모델/제공자는 아직 미정이다. 이 API 계약이 특정 모델의 성능이나 응답 시간을 보장하지 않는다.

## 9. 오류 계약

형식은 RFC 9457 Problem Details를 사용하고 code/traceId/violations/context를 추가한다.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "안전정보가 갱신됐어요. 최신 내용과 수정본을 다시 검토해주세요.",
  "instance": "/api/v1/admin/cities/1/draft/publications",
  "code": "SAFETY_CHANGED",
  "traceId": "sample-trace-id",
  "context": { "expectedSafetyRevisionId": "201", "currentSafetyRevisionId": "205" }
}
```

about:blank 사용 시 title은 HTTP 상태의 일반 명칭이다. 사용자 문구는 detail을 사용한다. 프론트 분기는 detail 문자열이 아니라 code를 사용한다. 내부 SQL·스택·파일 경로를 노출하지 않는다.

| HTTP      | 대표 code                                                | 처리                                         |
| --------- | -------------------------------------------------------- | -------------------------------------------- |
| 400       | MALFORMED_REQUEST / INVALID_QUERY                        | JSON 구문·헤더·쿼리 형식 수정                |
| 401       | AUTH_REQUIRED / SESSION_EXPIRED / INVALID_CREDENTIALS    | 로그인. 입력은 화면에 유지                   |
| 403       | FORBIDDEN / CSRF_INVALID                                 | 권한 확인 또는 CSRF 재발급                   |
| 404       | CITY_NOT_FOUND / DRAFT_NOT_FOUND / RESOURCE_NOT_FOUND    | 존재 여부 확인. 공개 HIDDEN은 존재 정보 숨김 |
| 409       | EDIT_LOCK_HELD / EDIT_LOCK_EXPIRED / EDIT_LOCK_NOT_OWNED | 재획득 또는 편집 중 안내                     |
| 409       | DRAFT_ALREADY_EXISTS / DRAFT_VERSION_MISMATCH            | 최신 초안 조회, 입력 자동 덮어쓰기 금지      |
| 409       | SAFETY_CHANGED / SAFETY_DRAFT_CANNOT_BE_DROPPED          | 안전정보 재검토                              |
| 409       | PREPARING_REQUIRES_REVISION                              | 초안 폐기 전 HIDDEN으로 전환                 |
| 412       | RESOURCE_VERSION_MISMATCH / SAFETY_VERSION_MISMATCH      | If-Match 불일치, 최신 내용 재검토            |
| 413       | PAYLOAD_TOO_LARGE                                        | 파일/본문 전송 크기 축소                     |
| 415       | UNSUPPORTED_MEDIA_TYPE                                   | 지원 파일/요청 형식 사용                     |
| 422       | VALIDATION_FAILED / PUBLICATION_NOT_READY                | violations의 path별 오류 표시                |
| 428       | PRECONDITION_REQUIRED                                    | 필요한 If-Match 누락                         |
| 429       | RATE_LIMITED                                             | Retry-After 후 사용자가 다시 시도            |
| 502 / 504 | AI_UPSTREAM_ERROR / AI_INVALID_RESPONSE / AI_TIMEOUT     | 입력 유지, 선택적 재시도                     |
| 500       | INTERNAL_ERROR                                           | traceId로 확인. 재시도 전에 저장 결과 조회   |

필드 오류의 path는 `/contentItems/0/body` 같은 JSON Pointer다. DB 오류 문자열을 그대로 사용자에게 반환하지 않는다.

미리보기의 미완성은 정상 검사 결과이므로 200 canPublish:false다. 실제 게시의 미완성은 422다. If-Match 누락(428)과 값 불일치(412), 업무 단계 충돌(409)을 구분한다.

## 10. 동시성·재시도·일관성

- GET draft ETag는 초안 자체의 상태를 나타낸다. 외부 안전 공개본의 독립 변경으로 초안 ETag를 억지로 바꾸지 않는다. 안전 충돌은 별도 기반 검사로 잡는다.
- GET safety ETag는 현재 공개 안전 버전을 나타낸다. 아직 없을 때도 '비어 있음'에 대한 ETag를 준다.
- GET admin city ETag는 노출 상태, 공개/초안/안전 포인터와 초안 editVersion을 포함한다. 삭제 직전 다른 변경이 발생하면 412로 재확인하게 한다.
- If-Match는 따옴표 포함 단일 strong ETag만 지원한다. `*`, 약한 ETag, 여러 값은 400. 이를 계산하는 방식은 서버 내부다.
- 서버는 조건 검사와 변경을 같은 트랜잭션에서 처리한다. 헤더를 컨트롤러에서 한 번 비교하고 트랜잭션 밖에서 변경하지 않는다.
- 조건 불일치/잠금 만료 때 프론트는 입력을 유지하되 자동 재저장하지 않는다.
- 생성·AI·게시 POST는 네트워크 오류 후 자동 반복하지 않는다. 중복 클릭은 UI에서 차단하고, 응답을 잃었으면 관리 상태/초안/이력으로 결과를 확인한다.
- 안전/일반 게시의 이전 조건은 재실행 시 불일치하므로 이미 반영된 결과를 자동으로 다시 덮어쓰지 않는다. 다만 범용 Idempotency-Key 저장소를 구현한 것은 아니다.
- 삭제 재시도에서 404가 오면 이미 삭제됐는지 감사 이력으로 확인한다. 없는 도시를 생성하거나 다시 삭제 이력을 만들지 않는다.
- 파일 I/O·외부 AI 호출은 긴 DB 트랜잭션 밖에서 수행한다. 최종 등록/게시 직전에 세션과 편집권을 다시 확인한다.
- 일부 HTTP 응답 형식 검사만으로 실제 업무 원자성이 입증되지는 않는다. API 구현 후 다중 연결 테스트가 필요하다.

## 11. 구현 순서 제안

1. 공개 도시 목록·상세·검색과 기준 데이터.
2. 관리자 세션·CSRF·활동 만료.
3. 도시 생성·잠금·초안 읽기/저장/폐기.
4. 사진·출처 등록과 게시 미리보기/게시.
5. 안전정보 독립 게시·충돌 재검토.
6. 비공개·물리 삭제·이력 조회.
7. AI 경험 후보 생성.

26개 경로/39개 작업은 위 기능의 요청을 구분한 수이며, 39개의 독립 제품 기능을 추가한다는 뜻은 아니다. 태그/국가 사전 CRUD, 작업 큐, 복원 기능, 외부 지명 검색은 제외했다.

## 12. 검증 및 참고

- OpenAPI 구조 및 참조는 Swagger Parser로 검증한다.
- 스키마 경계·예제는 Ajv로 검증하고 공개/관리자 경계, CSRF/잠금/버전 조건도 계약 수준에서 검사한다.
- 현재 OpenAPI 검증 및 129개 계약 검사가 통과했다. 61개 재사용 스키마를 컴파일해 문법과 참조를 확인했다.
- 이러한 검증은 실행 중인 Spring API의 인증/트랜잭션/파일 처리 검증이 아니다.
- OpenAPI 3.1.1은 이번 문서 포맷 선택이다. 최신 표준이라는 주장을 하지 않는다.
- [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html), [HTTP 의미론 RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html), [Problem Details RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html).

생성/검증:

```sh
cd tools/api-contract
pnpm install --frozen-lockfile --ignore-scripts
node build.mjs
node validate.mjs
```
