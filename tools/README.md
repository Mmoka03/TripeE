# 프로젝트 보조 도구

이 폴더는 애플리케이션 기능 자체가 아니라 명세, 스키마, 협업 규칙과 디자인 산출물을 검증하거나 생성한다.
명령은 별도 표시가 없다면 저장소 루트에서 실행한다.

| 폴더                | 역할                                                                | 주요 명령                                                                       |
| ------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `api-contract/`     | OpenAPI 문서 생성·계약 검사 및 실행 중인 서버의 공개 API 일부 검사  | `pnpm api:generate`, `pnpm test:api`, `pnpm --dir tools/api-contract test:live` |
| `db-validation/`    | PGlite에서 초기 스키마를 적용하고 제약조건 사례를 검사              | `pnpm test:db`                                                                  |
| `git-conventions/`  | 브랜치, 커밋 제목, PR 대상 규칙 검사                                | `pnpm test:git`                                                                 |
| `wireframe-export/` | 화면 데이터와 이미지를 Figma 가져오기 패키지로 생성하고 패키지 검증 | `pnpm test:wireframe`, `node tools/wireframe-export/build.mjs <출력 폴더>`      |

## 파일별 역할

- `api-contract/build.mjs`: `docs/backend/openapi-v1.json`을 생성하므로 실행 시 해당 파일을 덮어쓴다.
- `api-contract/validate.mjs`: OpenAPI 구조와 계약 사례를 검사하고 `results.json`을 작성한다.
- `api-contract/live-public.test.mjs`: 실행 중인 서버의 공개 API 일부를 실제 HTTP로 확인한다.
  서버와 필요한 게시 데이터가 별도로 준비돼야 하며, 관리자 API 전체를 검증하지 않는다.
- `db-validation/validate.mjs`: PGlite의 격리된 메모리 DB에서 스키마와 제약을 검사하고 `results.json`을 작성한다.
  실제 Docker PostgreSQL의 다중 연결·권한 테스트를 대체하지 않는다.
- `git-conventions/check.mjs`: Git 훅과 CI에서 사용할 규칙 검사기이고 `check.test.mjs`는 그 검사기의 테스트다.
- `wireframe-export/build.mjs`: 이미지와 와이어프레임을 지정한 출력 폴더에 패키징한다.
  `code.js`와 `ui.html`은 가져오기 도구의 구성 요소이며 `build.test.mjs`는 결과를 검사한다.

`api-contract`와 `db-validation`은 각 폴더의 `package.json`과 잠금 파일로 의존성을 관리한다.
두 `results.json`은 생성 결과이며 Git에서 제외된다. 백엔드 PostgreSQL 통합 테스트는
`backend/`에서 `./gradlew postgresContainerTest`로 실행한다. 기존 Python 실행 스크립트는
`backend/tools/`에 호환용으로 남아 있다. 자세한 실행 안내는 [백엔드 README](../backend/README.md)를 참고한다.
