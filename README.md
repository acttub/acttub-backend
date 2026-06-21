# acttub-backend

Spring Boot 기반 Acttub 백엔드입니다.

## 요구 사항

- Java 21
- PostgreSQL
- Gradle Wrapper 사용

## 테스트

기본 테스트는 외부 DB나 Gemini API를 호출하지 않습니다.

```bash
./gradlew test
```

## 로컬 Docker 실행

로컬 실행에 필요한 secret은 프로젝트 루트의 `.env`에 둡니다. `.env`는 Git에 올리지 않습니다.

```env
DB_URL=jdbc:postgresql://localhost:5432/acttub_db
DB_USERNAME=insung
DB_PASSWORD=
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL=gemini-3.5-flash
GEMINI_PROMPT_VERSION=analysis-v0.3
COACHING_STUB_ANALYZER_ENABLED=false
VIDEO_STORAGE_ROOT=/Users/insung/Desktop/acttub-video-storage
```

PostgreSQL을 먼저 실행한 뒤 이미지를 빌드하고 컨테이너를 실행합니다. Docker 실행 시 DB URL은 스크립트에서 `host.docker.internal`로 덮어쓰고, 영상 저장 경로는 컨테이너 밖 호스트 디렉터리에 마운트합니다. 앱 시작 시 Flyway가 마이그레이션을 적용합니다.

현재 앱은 PostgreSQL 전용 Flyway location인 `classpath:db/migration/postgresql`을 사용합니다. 기존 `db/migration`의 MySQL migration 파일은 이미 적용된 이력의 checksum을 보존하기 위해 수정하지 않습니다.

```bash
docker build -t acttub-backend .
```

```bash
./scripts/docker-run-local.sh
```

Gemini 대신 stub analyzer로 실행할 때는 `.env`에서 `COACHING_STUB_ANALYZER_ENABLED=true`로 바꿉니다.

## 운영 실행

운영 서버에서는 PostgreSQL을 별도로 설치/운영하고, 앱은 jar로 실행합니다.

```bash
./gradlew bootJar
```

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL='jdbc:postgresql://127.0.0.1:5432/acttub' \
DB_USERNAME=acttub \
DB_PASSWORD='your-db-password' \
VIDEO_STORAGE_ROOT=/var/lib/acttub/videos \
GEMINI_API_KEY='your-gemini-api-key' \
java -jar build/libs/acttub-backend-0.0.1-SNAPSHOT.jar
```

운영 PostgreSQL은 가능하면 서버의 `127.0.0.1:5432`에만 노출하고, 외부에서 접근할 때는 SSH 터널을 사용합니다.

```bash
ssh -L 5432:127.0.0.1:5432 <server-user>@<server-host>
```

## 문서

- [문서 인덱스](docs/README.md)
- [API 요청/응답 명세](docs/specs/current-api-contract.html)
- [테이블 구조](docs/specs/database-schema.html)
