# Pull-request CI

## Required credential-free checks

`.github/workflows/pull-request-ci.yml` runs for every pull request and can also
be dispatched manually. It grants only `contents: read` and does not use
repository or environment secrets.

Configure branch protection to require these job checks after the workflow has
run successfully on GitHub:

- `Guards and secret scan`
- `Frontend`
- `Backend`
- `Backend container artifact`
- `PostgreSQL migrations`

The repository cannot prove branch-protection settings or a hosted run by
itself. A maintainer must capture the first green pull-request URL and observe a
blocked merge or failing check before M3.1 is marked verified.

## CI boundary distinction

The credential-free pull-request jobs use isolated disposable PostgreSQL services and public/nonfunctional placeholders; they do not connect to either hosted Supabase project and do not mutate shared Supabase data. The candidate authenticated workflow uses trusted-default-branch `repository_dispatch`, protected logical environment `nonproduction`, exact provider/host binding, and disposable state from the shared non-production Supabase project. These are different CI boundaries. At the 2026-08-09 pre-push review checkpoint, `origin/main` still hosted the superseded legacy workflow; configure no auth secrets and dispatch nothing until the protected candidate source is hosted and read back.

## What each check proves

- **Guards and secret scan:** validates workflow syntax with actionlint, requires
  third-party actions to use immutable full commit SHAs, rejects
  `pull_request_target`, forbids artifact-upload actions, rejects sensitive or
  generated paths, validates contiguous ordered migration names, and runs
  Gitleaks across full Git history. The downloaded actionlint and Gitleaks
  archives are version-pinned and SHA-256 verified in `scripts/ci/`.
- **Frontend:** reads Node from `.nvmrc`, restores only npm's dependency cache,
  runs `npm ci`, `npm audit --audit-level=high`, lint, typecheck, Vitest, and a
  production build with public nonfunctional placeholders.
- **Backend:** uses Temurin Java from `.java-version`, restores Maven's dependency
  cache, and runs both `mvn clean test` and `mvn package`.
- **PostgreSQL migrations:** starts a digest-pinned PostgreSQL 17.6 service with
  trust authentication confined to the ephemeral runner. It first executes the
  repository's opt-in `apply` test against an empty database (rolled back), then
  applies migrations in checked order and runs the opt-in `verify` migration and
  Spring Data repository checks. The helper refuses non-loopback targets.

Dependency caches contain downloaded npm/Maven packages only. Build output,
Maven `target`, `.next`, Playwright output, credentials, and auth state are never
uploaded. `scripts/ci/check-workflow-policy.sh` deliberately rejects adding an
artifact-upload action so this remains a reviewed policy rather than convention.

## Local validation

From the repository root:

```bash
scripts/ci/test-guards.sh
scripts/ci/guard-sensitive-paths.sh
scripts/ci/check-workflow-policy.sh
scripts/ci/validate-migration-order.sh
scripts/ci/run-actionlint.sh
scripts/ci/run-secret-scan.sh
```

A reproducible deliberate failure for the sensitive-path guard is:

```bash
tmp=$(mktemp)
printf 'frontend/.env\n' > "$tmp"
ROTRACK_CI_PATH_MANIFEST="$tmp" scripts/ci/guard-sensitive-paths.sh
status=$? # expected: nonzero (currently 1)
rm -f "$tmp"
```

`test-guards.sh` wraps the same negative fixture, asserts that it returns
nonzero with the expected diagnostic, and then exits successfully. Never create
a real credential file to test this guard.

To reproduce the PostgreSQL job, start an isolated local PostgreSQL 17 service
named `rotrack_ci` with a trusted local `postgres` role. Export only the
credential-free test contract shown in the workflow, run the opt-in apply test,
run `scripts/ci/apply-migrations-to-local-postgres.sh`, then run the opt-in
verify command documented in `backend/src/test/README.md`. The apply helper
will not connect to a non-loopback host.

## Protected authenticated E2E

Authenticated external-auth Playwright is **not** part of pull-request CI. The candidate `.github/workflows/authenticated-e2e.yml` uses `repository_dispatch` type `rotrack-authenticated-e2e`, which GitHub resolves from the trusted default branch; it is not the hosted workflow until these reviewed commits are pushed. The fixed non-secret payload confirmation is `nonproduction-authenticated-e2e-only`.

Before adding any authenticated values, make the repository public or use a plan that supports the required controls, protect `main`, and configure logical environment `nonproduction` with a required reviewer and a deployment-branch restriction to protected `main`. Read back those controls. Then configure only environment-scoped:

- variables `ROTRACK_E2E_APPROVED_FRONTEND_HOST` and `ROTRACK_E2E_APPROVED_API_HOST`, copied from authoritative Vercel Preview/ACA readback;
- secrets `ROTRACK_E2E_BASE_URL` and `ROTRACK_E2E_EXPECTED_API_URL`;
- distinct `ROTRACK_NONPRODUCTION_SUPABASE_PROJECT_REF` and `ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF` identities;
- `ROTRACK_E2E_USER_A_STORAGE_STATE_JSON` and `ROTRACK_E2E_USER_B_STORAGE_STATE_JSON` for two distinct disposable non-production users.

Never configure personal or production user state in this environment. The workflow permits only the exact approved `.vercel.app` / `.azurecontainerapps.io` hosts, zero cookies, one exact frontend origin, and one matching non-production Supabase token entry per storage state. It parses the Supabase session JSON and requires distinct nonempty user IDs and access tokens, then exactly four passed tests with zero skipped/unexpected/flaky results, removes all temporary state/output, disables trace/video, and forbids artifact upload. The Azure adapter and workflow now match the two-project topology; legacy AWS files under `deploy/staging/` are not the active path.

Environment approval, branch protection, a real GitHub-hosted run, and external
non-production availability cannot be validated from a local checkout. Treat
`nonproduction` and `production` as existing logical GitHub environments whose
protection settings still require independent observation. Required reviewers, branch restrictions,
and environment-secret availability depend on repository visibility and plan; verify
them against the [GitHub Environments documentation](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments). If the required controls are unavailable, production remains stopped until the plan/visibility changes or an independently reviewed equivalent gate is approved. Credential-free PR CI remains isolated
from hosted Supabase; only approved environment-scoped authenticated E2E may use
disposable users/data in the shared non-production project. Treat each as a
release-side requirement, not as evidence supplied by these files.
