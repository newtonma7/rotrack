# Pull-request CI

## Required credential-free checks

`.github/workflows/pull-request-ci.yml` runs for every pull request and can also
be dispatched manually. It grants only `contents: read` and does not use
repository or environment secrets.

The public repository's protected `main` currently requires these exact app-bound contexts:

- `Guards and secret scan`
- `Frontend`
- `Backend`
- `Backend container artifact`
- `PostgreSQL migrations`
- `Analyze (actions)`
- `Analyze (java-kotlin)`
- `Analyze (javascript-typescript)`

The first five are the Pull request CI jobs. The final three are the sole
chosen scanner: GitHub default CodeQL setup. The checked-in advanced CodeQL
workflow was removed after temporary PR #17 showed that its `CodeQL (java)` and
`CodeQL (javascript-typescript)` jobs fail when default setup is enabled.

Historical PR #17 (temporary, credential-free, empty commit) showed all eight
required contexts appearing and succeeding, while also exposing the conflict
between the removed advanced CodeQL workflow and default setup. PR #18
(credential-free documentation PR) then showed all eight required contexts
appearing and succeeding, and merged through the protected rebase path as
`12dfdffb62ee51f6912b290eecc651444e797b90`. Its read-only protection metadata
confirmed pull requests, zero human approvals, strict checks, administrator
enforcement, linear history, no force pushes/deletion, and advisory
`CODEOWNERS`. PR #19 then used one isolated temporary TypeScript type-error file;
the exact required `Frontend` context failed under GitHub Actions app ID
`15368`, the other seven required contexts succeeded, and the open PR metadata
reported `mergeStateStatus: BLOCKED`. PR #19 was closed unmerged and all
temporary artifacts were removed. M3.1 is **Verified**.

## CI boundary distinction

The credential-free pull-request jobs use isolated disposable PostgreSQL services and public/nonfunctional placeholders; they do not connect to either hosted Supabase project and do not mutate shared Supabase data. The candidate authenticated workflow uses trusted-default-branch `repository_dispatch`, protected logical environment `nonproduction`, exact provider/host binding, and disposable state from the shared non-production Supabase project. These are different CI boundaries. The public-readiness controls are now read back: repository/nonproduction/production auth-secret inventories are empty, `ROTRACK_AUTHENTICATED_E2E_ENABLED` is absent/default-disabled, `nonproduction` allows exactly protected `main`, and the hosted authenticated workflow remains disabled. No authenticated dispatch is performed here.

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

Authenticated external-auth Playwright is **not** part of pull-request CI. `.github/workflows/authenticated-e2e.yml` uses `repository_dispatch` type `rotrack-authenticated-e2e`, which GitHub resolves from the trusted default branch. The fixed non-secret payload confirmation is `nonproduction-authenticated-e2e-only`. A job-level check of administrator-controlled variable `ROTRACK_AUTHENTICATED_E2E_ENABLED` defaults the job off before GitHub assigns a runner, evaluates the protected environment, or exposes secrets.

The product owner approved a solo-maintainer equivalent because no second human reviewer is currently available. Under this policy:

- keep public `main` protected with pull requests, zero human approvals, strict required contexts listed above, administrator enforcement, linear history, and blocked force pushes/deletion;
- keep logical environment `nonproduction` restricted to exactly protected `main`, with no reviewers or secrets;
- keep `ROTRACK_AUTHENTICATED_E2E_ENABLED` absent/default-disabled and all GitHub repository/nonproduction/production auth-secret inventories empty;
- treat `CODEOWNERS` as advisory rather than claim an impossible self-approval;
- retain enabled vulnerability reporting, Dependabot security fixes, secret scanning, and push protection;
- run authenticated non-production Playwright from a trusted local operator context with disposable users and storage states outside Git, binding the exact approved Vercel Preview/ACA hosts and requiring four passes with no skips, unexpected targets, or flaky results.

The dormant hosted workflow retains exact `.vercel.app` / `.azurecontainerapps.io` host validation, distinct non-production/production project identities, distinct user IDs/access tokens, temporary-state cleanup, disabled trace/video, and no artifact upload. Do not enable it or configure its variables/secrets without a second trusted reviewer and independently read-back environment approval. Personal or production user state is forbidden.

Branch protection and external non-production availability cannot be validated from a local checkout. Read back available controls against the [GitHub Environments documentation](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments). Credential-free PR CI remains isolated from hosted Supabase. Production remains stopped until all release-side requirements are observed; source files alone are not evidence.
