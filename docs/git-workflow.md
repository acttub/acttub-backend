# Git 워크플로우

`dev`를 기본 통합 브랜치로 사용합니다. 기능 브랜치는 `dev`에서 만들고, PR도 `dev`로 올립니다. 하나의 PR은 하나의 논리적 변경에 집중해야 합니다.

`main`은 안정 릴리스 브랜치로 사용합니다. 누적된 변경이 릴리스 가능한 상태일 때만 `dev`를 `main`으로 병합합니다.

## 브랜치 이름

작업 브랜치는 브랜치 타입 뒤에 실제 Jira 티켓 키를 포함해야 합니다.

```text
<type>/<JIRA_PROJECT_KEY>-<issue_number>-<description>
```

아래 예시의 `PROJ`는 자리표시자입니다. 실제 Jira 프로젝트 키로 바꿔 사용합니다.

- `feature/PROJ-123-add-login-api`
- `fix/PROJ-124-handle-invalid-token`
- `chore/PROJ-125-update-gradle-wrapper`
- `docs/PROJ-126-update-git-workflow`

허용되는 브랜치 타입은 `feature`, `fix`, `chore`, `docs`, `refactor`, `test`입니다. 설명 부분은 소문자 kebab-case를 사용합니다. `dev`와 `main` 브랜치만 예외입니다.

## 커밋 메시지

가벼운 Conventional Commits 형식을 사용합니다.

```text
<type>(<scope>): <subject>
```

저장소 전체에 해당하는 변경은 scope를 생략할 수 있습니다.

```text
<type>: <subject>
```

허용되는 커밋 타입은 다음과 같습니다.

- `feat`: 사용자에게 보이는 기능 또는 새 capability 추가
- `fix`: 버그 수정
- `docs`: 문서만 변경
- `test`: 테스트만 변경
- `refactor`: 동작 변경 없는 코드 구조 개선
- `chore`: 빌드, 설정, 도구, 유지보수 변경

커밋 메시지는 한글로 작성합니다. `type`과 `scope`는 도구가 파싱할 수 있도록 영어를 유지하고, subject/body는 한글로 작성합니다. subject는 짧고 구체적으로 작성합니다.

예시:

- `feat(coaching): 코칭 API 구현`
- `feat(swagger): Swagger UI 연결`
- `fix(coaching): Gemini 파일 처리 대기 추가`
- `docs: Git 워크플로우 문서화`
- `test(coaching): 코칭 통합 테스트 추가`
- `chore(config): 로컬 secret 파일 제외`

## PR

PR 제목, 본문, 리뷰 응답, merge commit message는 한글로 작성합니다.

PR 본문에는 다음 내용을 포함합니다.

- 변경 요약
- 주요 판단 이유
- 테스트 결과
- 관련 이슈 또는 Jira 티켓

코드 식별자, 명령어, API 경로, 브랜치명, 라벨, 인용한 에러 메시지는 영어를 그대로 사용할 수 있습니다.

## Codex 리뷰

Codex 리뷰 요청 프롬프트, 리뷰 코멘트, 리뷰 응답 코멘트는 한글로 작성합니다.

Codex 리뷰를 요청할 때는 리뷰 관점을 한글로 명시하고, 스타일 취향보다 실제 버그, 런타임 리스크, 아키텍처 위반, 누락된 테스트, 유지보수 리스크를 우선하도록 요청합니다.

예시:

```text
@codex review

다음 관점에 집중해서 리뷰해줘:

- 클린 아키텍처 계층 의존성이 깨진 부분이 있는지
- API 요청/응답 계약이 일관적인지
- 운영 환경에서 실패할 수 있는 설정이나 예외 처리가 있는지
- 테스트에서 빠진 실패 케이스나 회귀 위험이 있는지

스타일 취향보다 실제 버그, 운영 리스크, 유지보수 리스크를 우선해서 봐줘.
```

## 보호 브랜치

`dev`와 `main`에는 직접 push하지 않습니다.

- `dev`: PR 필수, 승인 요구 없음
- `main`: PR 필수, 승인 1개 필수, conversation resolve 필수

두 보호 브랜치 모두 force push와 브랜치 삭제를 금지합니다. 관리자도 이 규칙을 따릅니다.
