# PostgreSQL 데이터 접근 결정

## 현재 선택

TripeE 백엔드는 Spring Data JPA, Querydsl JPA와 Spring JDBC `JdbcClient`를 함께 사용함.
컨트롤러 → 서비스 → 저장소 인터페이스 → 구현체 구조를 사용하며, 이번에 전환한 저장소는
`repository/{admin,city}`에 계약을, `jpa`·`querydsl` 하위 패키지에 구현을 둠.
도시 변경 SQL은 `repository/admin/jdbc/JdbcAdminCityCommands`로 분리함.

관리자 계정, 국가·태그, 출처의 기존 JPA 구현은 유지함. 도시 조회·검색과 감사 이력은
Querydsl JPA, 사진 메타데이터와 정리 작업은 Spring Data JPA로 전환했음.
초안·게시·안전정보·편집 잠금 및 도시 생성·삭제의 일부 쓰기는 JDBC SQL을 유지함.
모든 방식은 같은 PostgreSQL DataSource와 HikariCP 연결 풀을 사용함.
JPA와 Querydsl JPA도 내부적으로 JDBC 드라이버를 사용하므로 `jdbc:postgresql:` 연결 URL은 유지됨.

## 2026-11-20 재평가 및 적용

평가는 코드의 유지보수성, 조건의 복잡성, SQL 고유 기능 의존도, 동작 보존 비용을 기준으로 한
정성 판단임. 성능 벤치마크 점수가 아니며, 전환으로 처리 속도가 개선됐다는 의미는 아님.

| 기존 JDBC 구간                                         | 전환 적합도    | 판단 및 적용                                                                                                                                                                                                                                                     |
| ------------------------------------------------------ | -------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AdminCityRepository.list/find/hasDisplayableRevision` | 높음           | Querydsl JPA로 교체함. 국가·상태·커서·문자열 조건 및 공개본/초안 조인을 타입으로 표현하고 중복 투영을 공유함.                                                                                                                                                    |
| `AdminCityRepository.create/changeVisibility/delete`   | 현재 보통 이하 | `JdbcAdminCityCommands`로 SQL을 분리해 유지함. 생성 시 도시/초안 상호 참조, 상태 변경 감사 기록, 삭제 전 전체 JSON 스냅샷과 파일 정리 예약을 같은 트랜잭션에서 수행함. JPA로도 구현 가능하지만 현재 범위에서는 여러 쓰기 모델과 flush 순서를 추가하는 비용이 큼. |
| `CityListRepository.list`                              | 높음           | Querydsl JPA로 교체함. 단순한 조인이며 관리자 조회용 도시/리비전 모델을 재사용함. 공개본 우선 선택과 표시 가능 필드 검사를 유지함.                                                                                                                               |
| `AuditRepository.list/find/historicalPublication`      | 높음           | Querydsl JPA로 모두 교체함. 목록은 JSON 스냅샷 열을 조회하지 않음. 상세의 JSON 조립은 Java에서 수행하고, 과거 게시 조회의 LATERAL 정렬/1건 제한은 해당 도시·리비전의 마지막 PUBLISH ID를 찾는 상관 서브쿼리로 대체함.                                            |
| `PhotoRepository.list/storageKey/insert`               | 높음           | Spring Data JPA로 교체함. 단일 엔티티 생성·조회에 적합함. 공개 파일 접근의 EXISTS 조건은 JPQL로 유지함.                                                                                                                                                          |
| `PhotoCleanupRepository.pending/completed`             | 높음           | Spring Data JPA로 교체함. NOT EXISTS, 생성 시각 순서, 100건 제한과 단일 키 삭제를 JPQL로 표현함. 작업 예약의 ON CONFLICT는 도시 삭제 JDBC 트랜잭션에 남김.                                                                                                       |
| `EditLockRepository` 전체                              | 현재 낮음      | JDBC 유지함. `FOR UPDATE`, lease 조건부 삭제, `ON CONFLICT` 갱신과 만료 시각을 기존 순서로 처리함. JPA 비관적 잠금으로도 구현할 수 있지만 잠금 순서·갱신 동작을 변경하는 데 비해 이점이 작음.                                                                    |
| `DraftRepository` 전체                                 | 현재 낮음      | JDBC 유지함. 여러 하위 테이블의 교체·복제, 편집 버전 검사, 지연 제약조건, 게시와 감사 기록이 결합돼 있음. Querydsl로 옮겨도 상당한 네이티브 SQL 또는 엔티티/flush 관리가 남음.                                                                                   |
| `SafetyRepository.current`                             | 현재 낮음      | JDBC 유지함. 안전정보·출처·확인자를 중첩 JSON 집계로 한 번에 조회함. JPA 전환에는 별도의 일괄 조회/조립 설계가 필요함.                                                                                                                                           |
| `CityDetailRepository.find`                            | 현재 낮음      | JDBC 유지함. 사진·태그·콘텐츠·안전정보의 필터와 정렬을 포함한 PostgreSQL JSON 집계를 사용함. 단순 연관 엔티티 순회로 바꾸면 추가 쿼리가 생길 수 있음.                                                                                                            |

직접 `JdbcClient`를 주입받는 운영 구현체는 **9개에서 5개**로 줄었음.
남은 구현체는 `JdbcAdminCityCommands`, `DraftRepository`, `EditLockRepository`,
`SafetyRepository`, `CityDetailRepository`임. 테스트 데이터 준비와 DB 권한 검증에서의 JDBC는
운영 데이터 접근 전환 대상이 아님.

## Querydsl 설정과 조회 모델

- OpenFeign Querydsl `7.7`의 `querydsl-jpa`와 `querydsl-apt:jpa`를 사용함.
  Spring Data 공식 문서가 설명하는 포크이며 지원은 best-effort임.
  현재 Java 21 / Spring Boot 4.1.2 조합에서 컴파일과 PostgreSQL 통합 테스트로 확인함.
- Gradle annotation processor가 `Q` 타입을 `backend/build/generated/sources/annotationProcessor/`
  아래에 생성함. 생성된 파일을 직접 편집하거나 커밋하지 않음. IDE에서는 Gradle 프로젝트를 새로고침함.
- `config/QuerydslConfig`에서 Spring이 관리하는 EntityManager를 사용하는 `JPAQueryFactory`를 등록함.
  기본 DB 없는 실행을 유지하기 위해 설정과 구현체는 `postgres` 프로필에서만 활성화함.
- 도시·리비전·감사 이벤트의 조회 엔티티는 `@Immutable`로 두고, 쓰기에 필요한 전체 엔티티 관계를
  새로 만들지 않았음. Querydsl은 스칼라 투영을 사용해 같은 트랜잭션의 JDBC 갱신 후에도
  영속성 컨텍스트에 남은 이전 엔티티 값을 반환하지 않도록 함.
- 감사 이력 ID는 문자열, 스냅샷은 JSON 객체로 반환함. 시각은 같은 시점을 나타내는 ISO-8601
  UTC `Z` 표기로 직렬화함. 기존 PostgreSQL JSON의 `+00:00` 표기와 문자열 형식은 다를 수 있음.

## 변경 시 지켜야 할 사항

- SQL 입력은 문자열 결합 대신 매개변수로 바인딩함. 관리자 도시 검색은 DB의 lower와
  부분 문자열 위치 연산을 사용해 `%`, `_`, 역슬래시를 와일드카드로 해석하지 않음.
- 편집권 확인과 도시 수정은 같은 트랜잭션에서 수행하며 행 잠금 순서를 유지함.
  Querydsl 도입 자체가 동시성이나 트랜잭션 문제를 해결하는 것은 아님.
- 외부 AI 호출과 파일 처리는 장시간 DB 잠금을 유지하지 않도록 분리함.
- 사진 생성은 `saveAndFlush`로 이후 JDBC 코드가 바로 참조할 수 있게 함.
  권리 확인 시각은 JVM 시각 대신 JPQL `current_timestamp`로 DB 트랜잭션 시각을 읽음.
  확인된 사진 저장에는 시각 조회가 1회 추가됨.
- JPA와 JDBC 쓰기가 함께 실행될 때 같은 트랜잭션을 사용하고 flush 및 영속성 컨텍스트의
  일관성을 확인함. JPA 관리 엔티티를 JDBC로 수정하는 새 경로를 추가한다면 refresh/clear
  또는 스칼라 조회를 검토해야 함. 무조건 clear하면 다른 미반영 변경이 유실될 수 있음.
- Hibernate 자동 DDL은 비활성화함. 스키마와 권한을 변경하지 않고 기존 DB를 사용함.
- 운영 배포 전에는 스키마 마이그레이션, 백업·복구, 연결 풀 크기와 실데이터 쿼리 계획을
  별도로 검증함. 초기 SQL 실행만으로 기존 운영 DB 변경이 자동 관리되는 것은 아님.

## 검증 항목

`./gradlew clean build postgresContainerTest --offline`으로 DB 없는 테스트와 격리된 PostgreSQL
통합 테스트를 실행함. 통합 테스트는 실제 스키마와 제한된 앱 계정을 사용함.

검증 결과: 일반 테스트 151개와 PostgreSQL 통합 테스트 30개가 모두 통과했으며,
실패·오류·건너뜀은 0개임. 전환 관련 통합 테스트 11개를 추가했음.

- 도시: 상태·국가·커서 조합, 한글/대소문자 검색, `%`·`_`·역슬래시, null 이름,
  공개본 우선 선택, 필수 표시 필드, JDBC 변경 직후 재조회, 존재하지 않는 ID를 확인함.
- 사진: nullable 메타데이터, DB 시각, 정렬, 관리자 접근, 초안/미연결/비공개 사진 차단을 확인함.
- 정리 작업: 사용 중인 파일 제외, 생성 시각 순서, 100/101건 경계, 완료된 키만 삭제를 확인함.
- 감사 이력: 커서 경계, 큰 ID의 문자열 보존, null 참조, JSON 내부 자료형,
  최신 PUBLISH 선택, 다른 도시/리비전 배제, 도시 삭제 후 이력 보존을 확인함.
- 혼합 트랜잭션: JDBC 도시 생성 → Querydsl 조회 → JPA 사진 저장 → JDBC 조회,
  JPA 사진 저장 → JDBC 도시 삭제/정리 예약의 동일 트랜잭션 롤백을 확인함.
- 기존 편집 잠금 동시성 및 사진 업로드부터 게시·비공개·도시 삭제까지의 HTTP 통합 흐름을 재검증함.

참고: [Spring Data Querydsl 지원](https://docs.spring.io/spring-data/jpa/reference/repositories/core-extensions.html),
[Querydsl JPA 사용법](https://openfeign.github.io/querydsl/tutorials/jpa/),
[OpenFeign Querydsl 릴리스](https://github.com/OpenFeign/querydsl/releases),
[Spring JDBC 접근 방식](https://docs.spring.io/spring-framework/reference/data-access/jdbc/choose-style.html),
[PostgreSQL 행 잠금](https://www.postgresql.org/docs/current/explicit-locking.html).
