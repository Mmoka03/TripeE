# TripeE

세계지도에서 도시를 선택하고 여행 전 필요한 정보를 확인하는 서비스.
React와 Spring Boot를 사용하는 2인 포트폴리오 프로젝트로 진행 중임.

사용자 지도·검색·도시 상세는 Spring Boot API와 연결했음. 기존 교토 검수본을 개발 DB에 등록하는 도구를 제공함. 관리자 화면은 아직 브라우저에 시연 데이터를 저장하며 API 연결은 후속 작업임.

## 실행

Node.js 24, pnpm 11.25.0을 사용함. 저장소 루트에서 실행함.

```sh
pnpm install --frozen-lockfile
pnpm --dir frontend install --frozen-lockfile
pnpm git:setup
pnpm dev
```

- 사용자 화면: http://127.0.0.1:5174/
- DB 없는 디자인 시연: http://127.0.0.1:5174/?demo=1
- 소개 페이지: http://127.0.0.1:5174/design/index.html
- 관리자 화면: http://127.0.0.1:5174/admin
- 사용자 와이어프레임: http://127.0.0.1:5174/wireframe/index.html

디자인 시연은 Docker 없이 실행 가능함. 실제 도시 정보는 개발 DB와 백엔드가 필요하며 `/api` 요청을 Vite가 백엔드 8080 포트로 전달함. 지도는 인터넷 연결이 필요하며, 소개 페이지의 사진·영상·Pretendard 글꼴은 저장소에 포함함.
지도 스타일을 바꾸려면 `frontend/.env.example`을 `frontend/.env.local`로 복사해 수정함.

PostgreSQL 개발 DB는 Docker Compose로 선택 실행할 수 있음. 설정과 초기화 주의점은 [Docker 개발 DB 실행](docs/docker-development.md)을 따름.

Spring Boot 서버 실행과 TDD 개발 방법은 [백엔드 안내](backend/README.md)를 따름.
교토 데이터 등록과 실제 화면 확인은 [개발 데이터 안내](tools/dev-data/README.md)를 따름.

## 현재 구현

- 지도 확대·축소, 도시 핀 선택, 겹친 핀 목록, 도시·국가 검색
- 도시 상세 패널, 사진 슬라이드 배치, 모바일 화면
- 소개 페이지 6개 섹션과 스크롤 전환, 새로고침 시 도시 예제 교체
- 다수 도시 묶음·사진 카드, 최근 본 도시, 지도 색상 시안과 모바일 탐색
- 사용자 와이어프레임 21개와 Figma 가져오기 도구
- 관리자 초안 편집·게시 검증, 안전정보 독립 게시·충돌 재검토
- 편집 잠금·본인 편집 재개, 비공개·삭제·감사 이력 시연
- Spring Boot의 공개 도시 목록·상세·검색·활성 태그 조회, 관리자 세션용 CSRF 토큰 발급
- PostgreSQL 초기 DDL과 API 계약 검증 도구

일반 지도는 API의 공개 도시만 표시하며 연결 실패를 시연 데이터로 대체하지 않음. 교토 상세는 검수 자료를 사용하며 안전정보는 미확인 상태임. 소개 페이지와 디자인 시연에는 예시 콘텐츠가 남아 있음. AI 정리와 관리자 화면의 인증·업로드·저장 연결은 후속 작업임. 관리자 시연 화면의 변경은 실제 지도에 반영되지 않음.

## 디렉터리

```text
backend/          Spring Boot 서버와 JUnit 테스트
  db/schema.sql   PostgreSQL 초기 DDL
frontend/         React 사용자·관리자 화면과 Vite 환경 설정
docs/             API 계약, ERD, 조사 자료와 개발 문서
infra/            로컬 Docker Compose와 PostgreSQL 환경 설정
tools/            API·DB·Git 규칙 검증과 Figma 내보내기 도구
```

## 검증

검증 도구의 의존성은 처음 한 번 설치함.

```sh
pnpm --dir tools/api-contract install --frozen-lockfile
pnpm --dir tools/db-validation install --frozen-lockfile
pnpm test
pnpm build
pnpm format:check
```

`pnpm test`는 관리자 시연·지도 묶음·소개 예제·공개 API 변환·교토 등록 자료·와이어프레임 생성·API 계약·DB 제약조건·Git 규칙을 검사함. PostgreSQL 다중 연결 검증은 백엔드의 `./gradlew postgresContainerTest`로 별도 실행함. 실행 중인 서버는 `pnpm --dir tools/api-contract test:live`로 확인함. 지도 번들의 크기 경고가 남아 있음.

각 검증·생성 도구의 역할과 실행 방법은 [tools 안내](tools/README.md)를 참고함.

## 문서

- [프로젝트 범위와 진행 상태](docs/project-status.md)
- [ERD](docs/erd-v2.md) · [DB 제약조건](docs/db-constraints-v1.md)
- [API 정책](docs/api-v1.md) · [OpenAPI](docs/openapi-v1.json)
- [백엔드 API 연결 순서와 테스트 범위](docs/backend-api-scope.md)
- [지도 설정](docs/map-decision-v1.md)
- [디자인 자산 출처와 화면 검수 기록](docs/design-assets.md)
- [관리자 구현 범위](docs/admin-implementation-v1.md)
- [협업 방법](CONTRIBUTING.md)
