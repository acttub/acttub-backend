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
DB_URL=jdbc:mysql://localhost:3306/acttub?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=
VIDEO_STORAGE_ROOT=/Users/insung/Desktop/acttub-video-storage
```

`.env`의 `GEMINI_API_KEY`에 실제 키를 넣은 뒤 `local` 프로필로 실행합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

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

## 우분투 홈서버 배포

운영 서버에서는 프로젝트 루트에 `.env`를 직접 만들고 Docker Compose가 읽게 합니다. `.env`는 Git에 올리지 않습니다.

예시:

```env
APP_PORT=8080
DB_NAME=acttub
DB_USERNAME=acttub
DB_PASSWORD=your-db-password
MYSQL_ROOT_PASSWORD=your-root-db-password
DB_URL=jdbc:mysql://mysql:3306/acttub?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL=gemini-3.5-flash
GEMINI_TEMPERATURE=0.2
GEMINI_PROMPT_VERSION=analysis-v0.2
VIDEO_STORAGE_ROOT=/app/storage/videos
COACHING_STUB_ANALYZER_ENABLED=false
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
- `video_storage`: 업로드 영상 파일

## 스펙

- [SOMA 98 코칭 API 설계](specs/2026-06-11-soma-98-coaching-api-design.html): 현재 기준 스펙. 앞으로 스펙 문서는 HTML로 작성합니다.
