# 협업 규칙

Gitflow를 기본으로 사용함. 브랜치는 영문 소문자와 숫자, 하이픈으로 작성하고 커밋 설명은 한국어로 작성 가능함.

## 브랜치

| 브랜치          | 용도                     | 분기 기준 | PR 대상 |
| --------------- | ------------------------ | --------- | ------- |
| main            | 릴리스 기준              | —         | —       |
| develop         | 기능 통합                | main      | main    |
| feature/작업명  | 기능 추가                | develop   | develop |
| fix/작업명      | 일반 오류 수정           | develop   | develop |
| refactor/작업명 | 동작 변경 없는 구조 개선 | develop   | develop |
| docs/작업명     | 문서 수정                | develop   | develop |
| test/작업명     | 테스트 추가·수정         | develop   | develop |
| chore/작업명    | 설정·도구·의존성 정리    | develop   | develop |
| release/0.1.0   | 릴리스 준비              | develop   | main    |
| hotfix/작업명   | 배포 버전 긴급 수정      | main      | main    |

이슈가 있으면 작업명 앞에 번호를 붙임. 예: feature/12-city-search, fix/edit-lock. 버전 브랜치는 release/숫자.숫자.숫자로 작성함.

Codex 작업은 codex/chore-git-conventions처럼 codex/유형-작업명으로 작성함. 유형은 feature, fix, refactor, docs, test, chore 중 하나이며 PR 대상은 develop임.

release·hotfix 병합 후에는 같은 브랜치에서 develop으로도 PR을 보내 변경을 반영함. main에서 develop으로 동기화 PR을 보내는 방식도 허용함. 초기 MVP는 release 브랜치 없이 develop → main PR로 릴리스 가능함.

## 커밋

제목은 다음 형식으로 작성함. 괄호의 범위는 필요한 경우에만 붙임. 제목 전체는 100자 이하이며 본문은 자유롭게 작성함.

```text
유형(범위): 변경 내용

feat(map): 도시 검색 추가
fix(admin): 편집 잠금 재개 처리
refactor: 관리자 입력 양식 분리
docs: 로컬 실행 안내 수정
```

| 유형     | 용도                     |
| -------- | ------------------------ |
| feat     | 기능 추가                |
| fix      | 오류 수정                |
| docs     | 문서                     |
| style    | 공백·포맷 변경           |
| refactor | 동작 변경 없는 구조 개선 |
| test     | 테스트                   |
| build    | 빌드·의존성              |
| ci       | 자동 검증·배포 설정      |
| chore    | 기타 관리 작업           |
| perf     | 성능 개선                |
| revert   | 기존 변경 되돌리기       |

범위는 map, admin, api, db 등 영문 소문자로 작성함. 호환성이 깨지는 변경은 feat(api)!: 응답 구조 변경처럼 !를 붙이고 본문에 영향과 대응 방법을 기록함. Git이 생성하는 병합 커밋은 제목 검사에서 제외함.

한 커밋에는 설명 가능한 하나의 변경을 담음. 관계없는 포맷 수정과 기능 변경은 분리함. 공유한 브랜치의 기록을 강제로 덮어쓰지 않음.

## 작업과 PR

1. develop을 최신 상태로 받은 뒤 작업 브랜치를 생성함.
2. 작업 후 관련 테스트·포맷·빌드를 확인하고 커밋함.
3. PR 제목도 커밋 제목과 같은 형식으로 작성함.
4. 변경 이유·확인 방법·관련 이슈를 짧게 기록함. 화면 변경은 캡처를 첨부함.
5. 상대방 승인 1개와 자동 검사 통과 후 병합하는 것을 팀 규칙으로 함.

기능 PR은 Squash merge를 기본으로 하며 최종 제목도 규칙에 맞춤. Squash 후 작업 브랜치는 삭제함. develop·release·hotfix의 통합은 Merge commit을 사용해 Gitflow의 이력을 유지함. release·hotfix 브랜치는 main과 develop 양쪽 반영 후 삭제함.

main·develop 직접 push는 초기 설정을 제외하고 사용하지 않음. GitHub에서 이를 강제하려면 별도 브랜치 보호 설정이 필요함. Actions나 로컬 훅만으로 직접 push를 완전히 막을 수는 없음.

## 검사 설치

각자 저장소를 clone한 뒤 한 번 실행함. Git 훅은 clone만으로 자동 활성화되지 않음.

```sh
pnpm git:setup
```

커밋 전 브랜치 이름, 커밋 시 제목을 검사함. Node.js가 필요하며 의존성 설치 없이 검사 가능함. 로컬 훅은 우회할 수 있으므로 PR에서 Git conventions 검사도 실행함. PR 검사에서는 브랜치 이름·대상·제목·일반 커밋 제목을 확인함.

```sh
pnpm test:git
pnpm format:check
pnpm test
pnpm build
```

Actions는 Git 규칙을 검사함. 백엔드·인프라 변경 PR에서는 Backend 검사도 실행해 Java 21 빌드와 PostgreSQL 앱 계정 연결·권한을 검증함. Git 규칙 변경 시 이 문서와 tools/git-conventions/check.mjs, 테스트를 함께 수정함.

## 코드와 문서

- pnpm format으로 포맷을 맞춤.
- 복잡한 처리의 이유와 제약을 주석으로 설명함. ~함, ~임, ~할 예정 등의 문체를 사용함.
- 시연 코드와 실제 API 구현의 경계를 유지함.
- API 변경 시 생성기·OpenAPI·관련 테스트를 함께 수정함.
- .env.local, 키, node_modules, 빌드 결과, 로컬 보관 자료는 커밋하지 않음.

코드의 공개 라이선스는 아직 정하지 않았음.
