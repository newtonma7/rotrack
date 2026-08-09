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

The credential-free pull-request jobs use isolated disposable PostgreSQL services and public/nonfunctional placeholders; they do not connect to either hosted Supabase project and do not mutate shared Supabase data. The separately dispatched authenticated workflow is an environment-scoped operation: after its current legacy checks pass, it may use disposable users/data in the shared non-production Supabase project. These are different CI boundaries.

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

Authenticated external-auth Playwright is **not** part of pull-request CI. It is
quarantined in `.github/workflows/authenticated-e2e.yml`, which runs only through
manual dispatch and fails closed unless all checks below are met:

1. The dispatcher explicitly confirms the disposable-staging input.
2. The `disposable-staging-auth` GitHub Environment approves the job. Configure
   required reviewers and restrict deployment branches in GitHub settings.
3. That environment provides all of these values (never repository-level PR
   secrets):
   - `ROTRACK_E2E_DISPOSABLE_STAGING_CONFIRMATION` with the exact value
     `disposable-staging-only`;
   - `ROTRACK_E2E_BASE_URL`, an HTTPS URL for the approved non-production Vercel Preview frontend;
   - `ROTRACK_E2E_EXPECTED_API_URL`, the approved HTTPS non-production API base ending in `/api/v1`;
   - `ROTRACK_STAGING_SUPABASE_PROJECT_REF`, `ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF`, and `ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF`, three distinct protected project identities required by the current legacy workflow;
   - `ROTRACK_PRODUCTION_FRONTEND_URL` and `ROTRACK_PRODUCTION_API_URL`, authoritative protected Production identities that must differ from the current staging targets;
   - `ROTRACK_E2E_USER_A_STORAGE_STATE_JSON` and
     `ROTRACK_E2E_USER_B_STORAGE_STATE_JSON`, valid external Playwright storage
     states for two distinct disposable staging users.

Never configure development, personal, or production users in this environment.
The workflow writes auth states with mode `0600` under `RUNNER_TEMP`, validates
their JSON shape, binds every captured API response to the approved API base, requires exactly four passed authenticated tests with zero skipped/unexpected/flaky results, and removes storage state plus Playwright failure/report output even when the test fails. Tracing and
video remain disabled by the Playwright configuration, and no artifact upload is
permitted.

The current workflow still targets the `disposable-staging-auth` GitHub Environment, the `disposable-staging-only` confirmation, and three Supabase project references; the checked-in release/staging scripts likewise retain three-reference and AWS-era assumptions. Converting this workflow and those scripts to the target logical `nonproduction` environment, two-project topology, one Vercel project, and Azure managed environments is residual work.

Environment approval, branch protection, a real GitHub-hosted run, and external
non-production availability cannot be validated from a local checkout. Treat
`nonproduction` and `production` as target logical GitHub environments whose
settings require independent observation. Required reviewers, branch restrictions,
and environment-secret availability depend on repository visibility and plan; verify
them against the [GitHub Environments documentation](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments). If the required controls are unavailable, production remains stopped until the plan/visibility changes or an independently reviewed equivalent gate is approved. Credential-free PR CI remains isolated
from hosted Supabase; only approved environment-scoped authenticated E2E may use
disposable users/data in the shared non-production project. Treat each as a
release-side requirement, not as evidence supplied by these files.
