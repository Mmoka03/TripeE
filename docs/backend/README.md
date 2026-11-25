# 백엔드 문서

API·DB 설계, 실행 환경, 평가 자료를 모은다. 실행 명령은 [백엔드 실행 안내](../../backend/README.md), 테스트 설계와 검증 절차는 [testing 안내](testing/README.md)를 따른다.

| 문서                                              | 내용                                                                      |
| ------------------------------------------------- | ------------------------------------------------------------------------- |
| [API 정책](api-v1.md)                             | 공개·관리자 API 계약, 인증·초안·게시·안전정보·오류 정책                   |
| [OpenAPI](openapi-v1.json)                        | 도구에서 사용하는 기계 판독 계약. 생성기는 `tools/api-contract/build.mjs` |
| [API 구현 범위와 연결 순서](backend-api-scope.md) | 프론트엔드 연동 대상과 의존 관계                                          |
| [ERD](erd-v2.md)                                  | 도시·버전·안전정보·편집권·감사 데이터 모델                                |
| [DB 제약조건](db-constraints-v1.md)               | PostgreSQL 제약과 DB/서비스의 책임                                        |
| [데이터 접근 결정](backend-database-access.md)    | JPA·Querydsl·JDBC의 사용 기준과 검증                                      |
| [개발 DB 실행](docker-development.md)             | Compose DB 실행·앱 계정 준비·볼륨 관리                                    |
| [프로젝트 평가](project-assessment-2026-11-20.md) | 백엔드 구조·테스트·명세·성능·운영 준비 평가                               |
| [테스트 명세 모음](testing/README.md)             | 동등 분할·조건/분기·보안·배포·법령 검증 초안                              |

프로젝트 전체 진행 상태, 디자인·지도·콘텐츠 조사와 관리자 프론트 시연 문서는 상위 `docs/`에서 관리한다. 평가·초안에 기록된 날짜와 실행 여부를 확인하며 설계 계획을 최신 테스트 결과로 취급하지 않는다.
