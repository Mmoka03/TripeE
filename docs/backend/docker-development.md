# Docker 개발 DB 실행

## 목적

로컬 개발에서 PostgreSQL 18 환경을 통일하기 위한 Compose 구성임. 현재 Spring Boot 서버와 프론트엔드는 컨테이너에 포함하지 않음. 프론트는 기존 Vite 실행 방식을 유지하고, 백엔드 구현 뒤에 별도 서비스를 추가함.

## 시작 전 준비

- Docker Desktop 또는 Docker Engine과 Docker Compose v2가 필요함.
- Compose 설정과 PostgreSQL 환경 파일은 `infra/`에 모아 둠. 기존에 루트 `.env`를 사용했다면 PostgreSQL 설정값을 `infra/.env`로 옮겨 기존 접속 정보를 유지함.
- 기본 Compose 프로젝트명은 기존과 같은 `tripee`임. 예전에 다른 프로젝트명으로 DB를 시작했다면 같은 이름을 `-p`로 지정해 기존 볼륨에 연결함.
- `.env`는 Git에 올리지 않음.

## 실행

저장소 루트에서 아래 명령으로 시작함. 이후 이 문서의 Compose 명령은 모두 `infra/`에서 실행함.

```sh
cd infra
cp -n .env.example .env
# 처음 만들었다면 .env에서 로컬 비밀번호를 설정한 뒤 실행함.
docker compose up -d --wait db
docker compose ps
docker compose logs -f db
```

호스트는 `127.0.0.1:5433`으로 제한하며, 컨테이너 내부 PostgreSQL 포트는 `5432`임. 로컬 DB 관리 도구의 연결 정보는 아래와 같음.

```text
host: localhost
port: 5433
database: tripee
username: tripee_app
password: .env의 POSTGRES_PASSWORD 값
```

초기 DDL은 기본 `public`이 아닌 `tripee` 스키마에 테이블을 생성함. 생성 여부는 아래처럼 확인함.

```sh
docker compose exec db psql -U tripee_app -d tripee -c '\dt tripee.*'
```

포트가 이미 사용 중이면 `.env`의 `POSTGRES_HOST_PORT`만 변경함. 팀 전체가 같은 비밀번호를 Git으로 공유하지 않으며, 필요한 경우 별도 안전한 채널에서 합의함.

이미지는 기존 볼륨을 생성한 OS 계열과 동일한 `postgres:18.6-trixie`로 고정함. 버전 변경 시 별도 검증 후 팀에 공유함. 필수 환경변수가 비어 있으면 Compose 단계에서 오류로 중단함. `--wait`는 DB healthcheck 완료를 기다리며, healthcheck 자체가 스키마 생성 성공까지 보장하지 않으므로 위 테이블 확인도 수행함.

기존 볼륨에서는 `POSTGRES_USER`와 `POSTGRES_PASSWORD`에 처음 DB를 생성할 때의 관리 계정과 비밀번호를 사용함.

PowerShell에서는 `infra/`에서 최초 한 번 `Copy-Item .env.example .env`로 환경 파일을 준비함. 기존 `.env`가 있으면 복사를 생략함. 위 psql 예시의 계정·DB 이름은 예시값이므로 직접 변경했다면 함께 맞춤.

## Spring Boot 계정 준비

`POSTGRES_USER`는 DB를 초기화한 관리용 슈퍼유저임. Spring Boot에는 별도 로그인 `tripee_backend`를 사용함. 기존 볼륨과 새 볼륨 모두 위 단계로 DB가 준비된 뒤 아래 순서로 설정함.

1. `infra/.env`의 `TRIPEE_DB_PASSWORD`에 앱 전용 비밀번호를 입력함. 기존 환경 파일이면 이 키를 추가함.
2. `infra/`에서 `docker compose run --rm --no-deps db-provision`을 실행함. 일회성 컨테이너가 관리 계정으로 앱 로그인을 준비하고, 마지막에 앱 계정으로 새 TCP 연결을 열어 도시 테이블 읽기를 확인함.
3. Spring Boot에서 같은 앱 비밀번호를 사용함. 서버 설정과 검증 명령은 [백엔드 안내](../../backend/README.md)를 참고함.

앱 계정은 `tripee` 스키마에서 지도 핀·검색·태그·도시 상세에 필요한 데이터를 읽을 수 있음. 상세 조회에는 게시본의 사진 메타데이터, 콘텐츠, 안전정보와 공개 출처가 포함됨. 기존 계정은 위 준비 명령을 다시 실행해 새 조회 권한을 적용함. 허용 열은 [계정 설정 SQL](../../infra/provision-backend.sql)에서 확인함.

같은 계정 준비 명령을 다시 실행하면 기존 비밀번호를 유지하고 권한과 로그인을 재확인함. 비밀번호를 교체할 때는 관리자로 `psql`에 접속해 `\password tripee_backend`로 바꾼 후 `infra/.env`와 백엔드 실행 환경의 값을 맞춤.

현재 프로비저닝은 기존 개발 DB에 사진 정리 대기 테이블 `tripee.photo_cleanup_job`도 생성함. 도시 삭제 시 사진 파일 정리 요청이 이 테이블에 저장되며 백엔드가 정리 실패 항목을 재시도함. 기존 볼륨에서는 이 명령을 다시 실행해 테이블과 권한을 적용함.

## 초기 DDL 동작

[초기 DDL](../../backend/db/schema.sql)은 새 PostgreSQL 데이터 볼륨이 만들어질 때 한 번만 실행됨. 이는 해당 파일이 비어 있는 전용 DB의 초기 DDL이기 때문임. 이미 초기화된 볼륨에 DDL을 다시 실행하거나 기존 데이터를 지우지 않음.

스키마 변경은 향후 Spring Boot 구현과 함께 Flyway 등 마이그레이션 도구를 도입해 버전 파일로 관리함. `schema.sql`을 수정한 뒤 기존 볼륨을 삭제하는 방식은 공동 개발 DB에 사용하지 않음.

## 중지와 데이터 보존

```sh
docker compose stop db
docker compose down
```

두 명령 모두 named volume `tripee-postgres-data`를 유지하므로 데이터도 보존됨. 초기화가 꼭 필요한 개인 개발 DB일 때만 팀과 확인한 뒤 다음 명령을 사용함.

```sh
docker compose down --volumes
```

이 명령은 해당 Compose 프로젝트의 DB 데이터를 삭제하므로 공유 DB나 필요한 로컬 데이터를 대상으로 실행하지 않음.

## 다음 단계

Spring Boot 프로젝트를 추가할 때 `backend` 서비스를 Compose에 포함함. 앱 컨테이너에서는 DB 호스트를 `db`, 포트를 `5432`로 연결함. 브라우저 개발 환경은 Vite 프록시를 통해 API를 같은 출처처럼 접근하도록 구성해 세션 쿠키와 CORS 처리를 단순화함.
