# Docker 개발 DB 실행

## 목적

로컬 개발에서 PostgreSQL 18 환경을 통일하기 위한 Compose 구성임. 현재 Spring Boot 서버와 프론트엔드는 컨테이너에 포함하지 않음. 프론트는 기존 Vite 실행 방식을 유지하고, 백엔드 구현 뒤에 별도 서비스를 추가함.

## 시작 전 준비

- Docker Desktop 또는 Docker Engine과 Docker Compose v2가 필요함.
- 저장소 루트에서 `.env.example`을 `.env`로 복사한 뒤 로컬 비밀번호를 설정함.
- `.env`는 Git에 올리지 않음.

## 실행

```sh
cp .env.example .env
docker compose up -d db
docker compose ps
docker compose logs -f db
```

기본 호스트 포트는 `5433`이며, 컨테이너 내부 PostgreSQL 포트는 `5432`임. 따라서 로컬 클라이언트와 이후 Spring Boot 개발 설정의 연결 정보는 아래와 같음.

```text
host: localhost
port: 5433
database: tripee
username: tripee_app
password: .env의 POSTGRES_PASSWORD 값
```

초기 DDL은 기본 `public`이 아닌 `tripee` 스키마에 테이블을 생성함. 생성 여부는 아래처럼 확인함.

```sh
docker compose exec db psql -U tripee_app -d tripee -c '\\dt tripee.*'
```

포트가 이미 사용 중이면 `.env`의 `POSTGRES_HOST_PORT`만 변경함. 팀 전체가 같은 비밀번호를 Git으로 공유하지 않으며, 필요한 경우 별도 안전한 채널에서 합의함.

## 초기 DDL 동작

`db/schema.sql`은 새 PostgreSQL 데이터 볼륨이 만들어질 때 한 번만 실행됨. 이는 해당 파일이 비어 있는 전용 DB의 초기 DDL이기 때문임. 이미 초기화된 볼륨에 DDL을 다시 실행하거나 기존 데이터를 지우지 않음.

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
