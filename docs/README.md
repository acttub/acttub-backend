# Docs

## Architecture

- [Layer flow](architecture/layer-flow.html): coaching package layers, current dependencies, and clean architecture notes.

## Process

- [Branching](branching.md): branch flow and contribution rules.

## Local / Smoke Tests

기본 테스트는 외부 DB나 Gemini API를 호출하지 않습니다.

```bash
./gradlew test
```

prod 프로필 설정과 Gemini API 키가 실제로 동작하는지만 확인하려면 아래처럼 smoke 테스트를 명시적으로 켭니다.

```bash
RUN_PROD_SMOKE_TESTS=true \
DB_URL='jdbc:mysql://localhost:3306/acttub?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
DB_USERNAME=root \
DB_PASSWORD='' \
VIDEO_STORAGE_ROOT="$HOME/Desktop/acttub-video-storage" \
GEMINI_API_KEY='your-gemini-api-key' \
./gradlew test --tests '*ProdProfileSmokeTests' --tests '*GeminiApiPingTests'
```

## Reviews

- [PR #3 Codex 리뷰 대응 정리](reviews/pr-3-codex-review-response.html)

## Specs

- [SOMA 98 coaching API design](superpowers/specs/2026-06-11-soma-98-coaching-api-design.md)
- [SOMA 98 코칭 API 설계 HTML](superpowers/specs/2026-06-11-soma-98-coaching-api-design.html)
