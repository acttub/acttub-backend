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

## Protected Branches

Direct pushes to both `dev` and `main` are blocked.

- `dev`: pull request required; no approval requirement.
- `main`: pull request required, one approval required, and conversations must be resolved.

Force pushes and branch deletion are disabled for both protected branches. Administrators are also subject to these rules.
