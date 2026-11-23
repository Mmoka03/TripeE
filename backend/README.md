# 백엔드 실행과 테스트

JDK 21을 준비하고 저장소 루트에서 실행함. 첫 실행에는 Gradle과 의존성을 다운로드할 인터넷 연결이 필요함.

```sh
cd backend
./gradlew test
./gradlew bootRun
```

기본 포트는 8080이며 Windows에서는 `gradlew.bat`를 사용함. DB 없이 서버 기동과 관리자 CSRF 발급을 확인할 수 있음. 공개 조회 4종은 아래 `postgres` 프로필에서 실행함. 화면별 경로와 남은 관리자 기능은 [API 연결 순서](../docs/backend-api-scope.md)에 정리함.

## PostgreSQL 연결

[개발 DB 실행 안내](../docs/docker-development.md)에 따라 DB를 시작하고 앱 계정 `tripee_backend`를 준비함. 기존 개발 DB도 `infra/`에서 `docker compose run --rm --no-deps db-provision`을 한 번 다시 실행해 상세·태그 조회 권한을 적용함. `postgres` 프로필은 기본 스키마를 `tripee`로 지정하며 DDL 초기화는 Compose가 담당함.

Mac의 zsh에서 `backend/`로 이동한 뒤 아래 명령을 실행함. 첫 줄에서 `infra/.env`에 설정한 앱 비밀번호를 입력함. 호스트에서 실행하는 Gradle은 Compose의 환경 파일을 자동으로 읽지 않음.

```zsh
read -rs 'TRIPEE_DB_PASSWORD?앱 DB 비밀번호: ' && echo
export TRIPEE_DB_PASSWORD
./gradlew postgresTest
./gradlew bootRun --args='--spring.profiles.active=postgres'
```

기본 접속 주소는 `127.0.0.1:5433/tripee`임. DB 이름이나 매핑 포트를 바꾸었다면 같은 셸에 `POSTGRES_DB`와 `POSTGRES_HOST_PORT`를 해당 값으로 설정함. 다른 호스트는 `TRIPEE_DB_HOST`로 지정함. IDE에서도 같은 환경 변수와 활성 프로필을 사용함.

## API 개발과 테스트

관리자 API는 `postgres` 프로필에서 사용함. `GET /api/v1/admin/csrf`로 받은 세션 쿠키와
`X-CSRF-TOKEN`으로 `POST /api/v1/admin/session`에 `loginId`, `password`를 전송함.
로그인 성공 후 CSRF 토큰을 다시 받아 같은 세션의 활동 보고와 로그아웃에 사용함.
활동 보고는 `POST /api/v1/admin/session/activity`에 `{"event":"CLICK"}`처럼 전송함.
`INPUT`, `CLICK`, `NAVIGATION`을 지원하며 서버가 수신한 시각으로 한 시간 만료를 계산함.
`GET /api/v1/admin/session`은 현재 상태 조회, `DELETE /api/v1/admin/session`은 로그아웃임.

초기 관리자는 DB 관리 계정으로 `admin_account`에 등록함. `password_hash`는
Spring Security의 `BCryptPasswordEncoder.encode`로 만든 BCrypt 해시를 사용함.
계정의 `is_active`가 true일 때 로그인할 수 있음. 기존 앱 계정에는 프로비저닝 명령을
다시 실행해 로그인에 필요한 열 조회 권한을 부여함.
국가 사전은 `GET /api/v1/admin/countries`, 비활성 항목까지 포함한 태그 사전은
`GET /api/v1/admin/tags`로 조회함.

편집권은 로그인 세션과 편집 창 UUID에 연결됨. `GET /api/v1/admin/cities/{cityId}/edit-lock`으로
상태를 조회하고, `POST`에 `{"editorInstanceId":"<UUID>"}`를 전달해 획득함.
획득 응답의 `leaseToken`을 이후 `X-Edit-Lock-Token` 헤더에 전달함. 같은 경로의
`DELETE`로 반납하고, `POST /heartbeats`로 5분 유효 시간을 갱신함. 변경 요청에는
CSRF 토큰도 필요함. 앱 계정 권한을 갱신하려면 DB 안내의 프로비저닝을 다시 실행함.
편집권 획득·갱신·반납과 초안 변경·안전정보 게시는 DB 행 잠금을 얻은 뒤 세션 유효성을
다시 확인함. 잠금 대기 중 세션이 만료되거나 무효화되면 `401 SESSION_EXPIRED`를 반환함.
편집권 연결 확인은 관리자 활동으로 취급하지 않으며 한 시간 비활동 만료를 연장하지 않음.

사진 파일 조회는 기본적으로 백엔드 실행 디렉터리의 `data/photos/`를 사용함. 다른 위치는
`TRIPEE_PHOTOS_DIRECTORY` 환경 변수로 설정함. 이미지 제공 API는 공개 도시의 현재 게시본에
연결된 사진 또는 유효한 관리자 세션에서 접근하는 보존 자산을 반환함. 관리자는 편집권을 사용해
JPEG·PNG·WebP 파일과 출처·권리 정보를 업로드할 수 있음. 파일은 이미지 메타데이터를 제거한
PNG로 저장하며 장당 입력과 저장 결과는 각각 10 MiB, 해상도는 4천만 픽셀 이하로 제한함.

대표 경험 후보 API `POST /api/v1/admin/cities/{cityId}/draft/experience-suggestions`는
등록된 출처, 편집권, 초안 버전을 확인하고 공급자 응답을 검증함. AI 제공자는 아직 정해지지 않아
기본 설정에서는 `502 AI_UPSTREAM_ERROR`를 반환하며 후보를 생성하지 않음. 제공자 결정 후
`ExperienceSuggestionProvider` 구현을 Spring 빈으로 등록해야 실제 생성할 수 있음. 구현체는 입력 자료를
명령이 아닌 신뢰할 수 없는 데이터로 취급하고 외부 서비스 연결 및 취소를 안전하게 처리해야 함.
AI 호출은 DB 잠금 밖에서 최대 30초 동안 기다리며 응답은 자동으로 저장하거나 게시하지 않음.

요청 DTO에는 Jakarta Validation 제약을 적용하고 MVC 테스트로 입력·응답을 확인함. 공통 설정은 DTO에 선언되지 않은 JSON 필드를 거부함. 공개 조회와 관리자 접근 오류에는 Problem Details의 `type`, `status`, `instance`와 프로젝트 필드 `code`, `traceId`를 담음. 관리자 쓰기 API의 422 오류 응답에 필드별 `violations`를 제공하는 작업은 아직 남아 있음.

백엔드는 하나의 애플리케이션으로 배포하고 `controller`, `service`, `repository`, `config` 계층 아래에
`admin`, `city`, `tag` 기능별 패키지를 둠. `controller`는 HTTP 요청·응답, `service`는 업무 규칙과
트랜잭션, `repository`는 DB 조회·저장을 담당함. 관리자 인증 설정은 `config/admin`, 공통 예외와
오류 응답은 `common/exception`, 공통 요청 필터는 `common/filter`, JPA 엔티티는 `entity`에 둠.
도시 검색·조회와 감사 이력은 `repository/*/querydsl`, 사진·정리 작업은
`repository/admin/jpa`에서 구현함. Querydsl Q 타입은 Gradle 컴파일 시 자동 생성함.
PostgreSQL 데이터 접근 방식과 변경 기준은 [데이터 접근 결정](../docs/backend-database-access.md)을 참고함.

기능을 추가할 때는 계약에서 기대 동작 하나를 골라 실패하는 테스트를 먼저 실행하고, 통과하는 최소 구현을 작성한 뒤 리팩터링함. 예를 들어 준비 중인 도시가 지도 핀에 포함되는지는 `./gradlew test --tests '*CityVisibilityTest'`로 확인함.

- `./gradlew build`는 DB 없이 도메인 규칙, MVC 요청 처리, 서버 기동을 테스트하고 실행 JAR를 생성함.
  Windows에서 심볼릭 링크 생성 권한이 없으면 링크 경로 이탈 검사 1건만 건너뜀.
  일반 파일·상위 경로 접근 검사는 계속 실행하며, Linux CI에서는 링크 검사도 실행함.
- `./gradlew postgresTest`는 실행 중인 PostgreSQL에 앱 계정으로 접속해 기본 스키마, 도시 조회와 접근 권한을 확인함. 매번 현재 DB를 대상으로 실행함.
- `./gradlew controllerTest`는 HTTP 경로·검증·인증 경계를 검사함. 세션 테스트는 서비스와 보안 필터를 함께 사용함.
- `./gradlew serviceTest`는 DB 없이 업무 규칙을 검사함.
- `./gradlew repositoryTest`는 실행 중인 PostgreSQL의 저장소 조회와 권한을 검사함. 테스트용 임시 테이블은 트랜잭션 종료 시 롤백함.
- `postgresTest`는 저장소 테스트와 게시 상세 서비스·SQL 통합 테스트를 함께 실행함.
- `./gradlew postgresContainerTest`는 Testcontainers로 격리된 PostgreSQL을 생성하고 실제 스키마와
  제한된 앱 계정을 준비한 뒤 관리자 A/B 동시 잠금 및 도시 생성·게시·삭제 흐름을 검사함.
  Docker 실행 환경이 필요하며 개발 DB를 사용하지 않음. CI에서도 이 작업을 실행함.
- `postgresContainerTest` 실행 후 저장소 루트에서 `pnpm --dir tools/api-contract install --frozen-lockfile`과
  `pnpm --dir tools/api-contract test:integration`을 실행하면 격리 DB의 MockMvc 응답을 OpenAPI와 비교함.
  상태 코드, JSON 스키마, Content-Type, 명세 헤더와 필수 시나리오를 검사하며 CI에서도 실행함.
  테스트 결과는 `backend/build/contract/`에 임시 저장되므로 실제 데이터나 토큰을 넣지 않음.
  모든 API 조합과 실제 TCP·프론트엔드 연동을 검증하는 것은 아님.
- `python3 backend/tools/run-postgres-tests.py`는 기존 호환용 실행 경로이며 같은 목적의 독립 스크립트임.
  신규 테스트는 `postgresContainerTest`를 사용함.
- 위 테스트와 `./gradlew test`를 실행한 뒤 `./gradlew combinedContainerCoverageReport`로
  두 테스트의 커버리지를 합산할 수 있음.
- 실제 서버가 기동하면 저장소 루트에서 `pnpm --dir tools/api-contract test:live`로 HTTP 응답을 OpenAPI와 대조함. 다른 포트에서는 `TRIPEE_API_ORIGIN`에 서버 주소를 설정함. 상세 응답은 DB에 해당 상태의 도시가 있을 때 검사함.
