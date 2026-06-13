# Branching Policy

Use `dev` as the default integration branch. Create feature branches from `dev`, open pull requests back into `dev`, and keep each pull request focused on one logical change.

Use `main` as the stable release branch. Merge `dev` into `main` only when the accumulated changes are release-ready.

## Branch Names

Every working branch must include the real Jira ticket key after the branch type:

```text
<type>/<JIRA_PROJECT_KEY>-<issue_number>-<description>
```

Examples below use `PROJ` only as a placeholder. Replace it with the actual Jira project key:

- `feature/PROJ-123-add-login-api`
- `fix/PROJ-124-handle-invalid-token`
- `chore/PROJ-125-update-gradle-wrapper`
- `docs/PROJ-126-update-branching-policy`

Allowed branch types are `feature`, `fix`, `chore`, `docs`, `refactor`, and `test`. Use lowercase kebab-case for the description. The `dev` and `main` branches are the only exceptions.

## Commit Messages

Use a lightweight Conventional Commits style:

```text
<type>(<scope>): <subject>
```

The scope is optional for repository-wide changes:

```text
<type>: <subject>
```

Allowed commit types are:

- `feat`: user-facing feature or new capability
- `fix`: bug fix
- `docs`: documentation-only change
- `test`: test-only change
- `refactor`: code restructuring without behavior change
- `chore`: build, config, tooling, or maintenance change

Write commit messages in Korean. Keep the subject short, imperative, and specific. Keep Conventional Commit `type` and `scope` in English so tooling can parse them, and write the subject/body in Korean.

Examples:

- `feat(coaching): 코칭 API 구현`
- `feat(swagger): Swagger UI 연결`
- `fix(coaching): Gemini 파일 처리 대기 추가`
- `docs: 브랜치 정책 문서화`
- `test(coaching): 코칭 통합 테스트 추가`
- `chore(config): 로컬 secret 파일 제외`

## Pull Requests

Write pull request titles, descriptions, review responses, and merge commit messages in Korean.

Pull request descriptions should include:

- 변경 요약
- 주요 판단 이유
- 테스트 결과
- 관련 이슈 또는 Jira 티켓

Use English only for code identifiers, commands, API paths, branch names, labels, and quoted error messages.

## Codex Reviews

Write Codex review prompts, review comments, and review response comments in Korean.

When requesting Codex review, include the review focus in Korean and prioritize actual bugs, runtime risks, architecture violations, missing tests, and maintenance risks over style-only feedback.

Example:

```text
@codex review

다음 관점에 집중해서 리뷰해줘:

- 클린 아키텍처 계층 의존성이 깨진 부분이 있는지
- API 요청/응답 계약이 일관적인지
- 운영 환경에서 실패할 수 있는 설정이나 예외 처리가 있는지
- 테스트에서 빠진 실패 케이스나 회귀 위험이 있는지

스타일 취향보다 실제 버그, 운영 리스크, 유지보수 리스크를 우선해서 봐줘.
```

## Protected Branches

Direct pushes to both `dev` and `main` are blocked.

- `dev`: pull request required; no approval requirement.
- `main`: pull request required, one approval required, and conversations must be resolved.

Force pushes and branch deletion are disabled for both protected branches. Administrators are also subject to these rules.
