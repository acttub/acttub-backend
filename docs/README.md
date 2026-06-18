# 문서

## 아키텍처

- [레이어 흐름](architecture/layer-flow.html): 코칭 패키지의 레이어, 현재 의존 관계, 클린 아키텍처 관점 정리

## 프로세스

- [Git 워크플로우](git-workflow.md): 브랜치 흐름, 커밋 메시지, PR, Codex 리뷰 규칙

## 로컬 / 스모크 테스트

기본 테스트는 외부 DB나 Gemini API를 호출하지 않습니다.

```bash
./gradlew test
```

로컬 실행에 필요한 secret은 프로젝트 루트의 `.env`에 둡니다. `.env`는 Git에 올라가지 않습니다.

예시:

```env
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL=gemini-3.5-flash
IDE_DB_URL=jdbc:mysql://localhost:3306/acttub?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
IDE_DB_USERNAME=root
IDE_DB_PASSWORD=
VIDEO_STORAGE_ROOT=/Users/insung/Desktop/acttub-video-storage
```

`.env`의 `GEMINI_API_KEY`에 실제 키를 넣은 뒤 `local` 프로필로 실행합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

IntelliJ 또는 Gradle로 직접 실행하는 `local` 프로필은 `IDE_DB_URL`, `IDE_DB_USERNAME`, `IDE_DB_PASSWORD`를 사용합니다. Docker Compose용 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`와 분리되어 있으므로 직접 실행 앱과 Compose 앱이 서로 다른 MySQL을 사용할 수 있습니다.

`prod` 프로필 설정과 Gemini API 키가 실제로 동작하는지만 확인하려면 아래처럼 스모크 테스트를 명시적으로 켭니다.

```bash
RUN_PROD_SMOKE_TESTS=true \
DB_URL='jdbc:mysql://localhost:3306/acttub?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
DB_USERNAME=root \
DB_PASSWORD='' \
VIDEO_STORAGE_ROOT="$HOME/Desktop/acttub-video-storage" \
GEMINI_API_KEY='your-gemini-api-key' \
./gradlew test --tests '*ProdProfileSmokeTests' --tests '*GeminiApiPingTests'
```

Docker Compose 배포 형태를 로컬에서 확인할 때는 로컬 전용 Compose 파일을 사용합니다. 이 구성은 실제 Gemini analyzer를 사용하고, 운영 volume과 분리된 로컬 volume을 사용합니다. Compose는 프로젝트 루트의 `.env`를 자동으로 읽습니다.

로컬 Compose는 운영 Compose와 같은 `.env` 변수명을 사용합니다. 운영과 다른 값만 로컬용으로 넣습니다. 이 값들은 Docker Compose 전용이며, IntelliJ 직접 실행의 `IDE_DB_*` 값과 분리됩니다.

```env
APP_PORT=8081
DB_HOST_PORT=3307
DB_NAME=acttub
DB_USERNAME=acttub
DB_PASSWORD=local-password
MYSQL_ROOT_PASSWORD=local-root-password
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL=gemini-3.5-flash
GEMINI_TEMPERATURE=0
GEMINI_PROMPT_VERSION=analysis-v0.2
GEMINI_BASE_URL=https://generativelanguage.googleapis.com
GEMINI_FILE_PROCESSING_TIMEOUT_MILLIS=180000
COACHING_STUB_ANALYZER_ENABLED=false
```

`COACHING_STUB_ANALYZER_ENABLED=true`로 실행할 때는 `GEMINI_API_KEY`를 비워 두거나 생략할 수 있습니다. 실제 Gemini analyzer를 쓰는 기본 모드에서는 애플리케이션 시작 시 `GEMINI_API_KEY`가 비어 있으면 실패합니다.

```bash
docker compose -f docker-compose.local.yml up -d --build
```

기본 앱 포트는 `8081`입니다. 다른 포트를 쓰려면 `.env`의 `APP_PORT`를 바꾸거나 실행 시 지정합니다.

```bash
APP_PORT=18081 docker compose -f docker-compose.local.yml up -d --build
```

로컬 MySQL은 DataGrip 같은 DB 클라이언트에서 접속할 수 있도록 이 PC의 `127.0.0.1:3307`에만 노출합니다.

```text
Host: localhost
Port: 3307
User: acttub
Password: local-password
Database: acttub
```

위 접속 정보는 예시값 기준입니다. `.env`에서 `DB_HOST_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `DB_NAME`을 바꿨다면 DataGrip에도 같은 값을 입력합니다.

다른 DB 포트를 쓰려면 `.env`의 `DB_HOST_PORT`를 바꾸거나 실행 시 지정합니다.

```bash
DB_HOST_PORT=13307 docker compose -f docker-compose.local.yml up -d --build
```

상태와 로그를 확인합니다.

```bash
docker compose -f docker-compose.local.yml ps
docker compose -f docker-compose.local.yml logs -f app
```

컨테이너만 내리려면 아래 명령을 사용합니다.

```bash
docker compose -f docker-compose.local.yml down
```

로컬 MySQL과 영상 volume까지 초기화하려면 `-v`를 추가합니다.

```bash
docker compose -f docker-compose.local.yml down -v
```

## 우분투 홈서버 배포

운영 서버에서는 프로젝트 루트에 `.env`를 직접 만들고 Docker Compose가 읽게 합니다. `.env`는 Git에 올리지 않습니다.

예시:

```env
APP_PORT=8080
DB_NAME=acttub
DB_USERNAME=acttub
DB_PASSWORD=your-db-password
MYSQL_ROOT_PASSWORD=your-root-db-password
DB_HOST_PORT=3307
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL=gemini-3.5-flash
GEMINI_TEMPERATURE=0
GEMINI_PROMPT_VERSION=analysis-v0.2
COACHING_STUB_ANALYZER_ENABLED=false
```

운영에서도 `COACHING_STUB_ANALYZER_ENABLED=true`로 스택 기동만 점검할 때는 `GEMINI_API_KEY`를 비워 두거나 생략할 수 있습니다. 실제 Gemini analyzer를 쓰는 기본 모드에서는 유효한 `GEMINI_API_KEY`를 넣어야 합니다.

운영 Docker Compose 배포에서는 영상 저장 경로를 `/app/storage/videos`로 고정합니다. 이 경로는 `video_storage` Docker volume에 마운트되므로 `.env`에서 `VIDEO_STORAGE_ROOT`를 따로 지정하지 않습니다.

운영 MySQL은 서버의 `127.0.0.1:${DB_HOST_PORT:-3307}`에만 노출합니다. 외부 네트워크에 MySQL 포트를 직접 공개하지 말고, 이 PC의 DataGrip에서는 SSH 터널을 통해 접속합니다.

```bash
ssh -L 3307:127.0.0.1:3307 <server-user>@<server-host>
```

터널을 연 뒤 DataGrip에는 아래처럼 입력합니다.

```text
Host: localhost
Port: 3307
User: acttub
Password: 운영 DB_PASSWORD
Database: acttub
```

파일 권한은 소유자만 읽을 수 있게 제한합니다.

```bash
chmod 600 .env
```

앱과 MySQL을 함께 실행합니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

상태와 로그를 확인합니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps
docker compose -f docker-compose.prod.yml --env-file .env logs -f app
```

운영 데이터는 Docker volume에 저장됩니다.

- `mysql_data`: MySQL 데이터
- `video_storage`: 업로드 영상 파일, 컨테이너 내부 `/app/storage/videos`에 마운트

## 스펙

- [SOMA 98 코칭 API 설계](specs/2026-06-11-soma-98-coaching-api-design.html): 현재 기준 스펙. 앞으로 스펙 문서는 HTML로 작성합니다.
