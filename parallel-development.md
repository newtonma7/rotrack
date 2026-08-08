# Parallel Development Guide

**Purpose:** Coordinate independent work now that the local MVP verification gates are complete and development can resume toward CI, staging, and release.

**Source of truth:** Read [`AGENTS.md`](AGENTS.md), [`arch.plan.md`](arch.plan.md), and [`todo.md`](todo.md) first. This file is an execution guide; it does not replace their contracts or acceptance criteria.

## Current baseline — 2026-08-08

- The verified local MVP includes schema hardening, JWT/error boundaries, explicit timer lifecycle, dashboard semantics, migration/repository coverage, startup/TLS/CORS/readiness, direct Data API RLS, the dedicated `rotrack_runtime` role, and the authenticated two-user browser flow.
- Final validation passed: frontend audit/lint/typecheck/Vitest/build, backend Java 21 tests/package, live health/readiness, and required-authenticated Playwright 4/4.
- M2.1–M2.3 have no remaining technical evidence gaps. Opening the fresh signup confirmation email is the only external inbox limitation; do not block code development on it unless the release gate explicitly requires it.
- M3.1–M3.3 are the next implementation stage. M4 feature work remains gated on the MVP release gate unless the product owner explicitly authorizes parallel feature development.
- The current main checkout is a committed, pushed checkpoint. Preserve unrelated user changes if the worktree becomes dirty.

## Coordination rules

1. One writer owns each lane and its files. Do not edit another lane's files.
2. Start every lane by inspecting `git status`, the current diff, `arch.plan.md`, and the relevant `todo.md` task.
3. Prefer a separate managed Git worktree for every implementation writer. A Herdr pane is visibility, not isolation.
4. Preserve unrelated dirty work. Never run `git reset --hard`, `git clean`, broad checkout, or destructive database commands in the shared checkout.
5. Behavior changes require tests. Every lane returns exact commands, results, changed files, and residual risks.
6. Never record tokens, credentials, database passwords, Playwright storage-state JSON, private notes, or complete environment files.
7. Keep `todo.md`, `arch.plan.md`, and final evidence updates parent-owned unless a lane is explicitly assigned a documentation change.
8. No lane may use production for testing, migration rehearsal, role changes, or browser automation.
9. Parent reviews and integrates lanes serially. Re-run the relevant full suites after integration.

## Worktree setup

Create a clean worktree from the current pushed checkpoint before starting parallel writers:

```text
m3.1-ci
m3.2-container
m3.2-staging-ops
m3.3-release-safeguards
```

Use one writer per worktree. Do not let lanes share `.github/`, deployment manifests, a Dockerfile, migration files, or the same evidence document. The parent owns cross-lane contract changes and the final merge.

## Immediate parallel lanes — Milestone 3

These lanes can start concurrently. M3.3 may prepare its runbooks and monitoring contracts immediately, but live smoke/rollback evidence waits for the staging artifact from M3.2.

### Lane A — M3.1 pull-request CI

**Task:** Add deterministic, credential-free CI and an explicit optional authenticated-job boundary.

**Owns:** `.github/workflows/`, CI helper scripts, CI-specific configuration, and CI documentation. Avoid application source, migrations, deployment manifests, `todo.md`, and `arch.plan.md`.

**Do:**

- Run frontend `npm ci`, audit, lint, typecheck, Vitest, and production build.
- Run backend Java 21 `mvn clean test` and `mvn package`.
- Run migration validation and the opt-in PostgreSQL `apply`/`verify` checks using an isolated PostgreSQL service or clearly documented CI fixture.
- Add secret scanning and ensure generated artifacts, credentials, and storage states cannot enter artifacts.
- Keep the external-auth Playwright suite quarantined by default; define a separately protected required-auth job only when CI has approved disposable auth states.
- Add a deliberately failing-check observation or test of branch protection behavior to the evidence.

**Done when:** A pull request produces a green credential-free pipeline, the migration check runs against PostgreSQL, secrets are scanned, and the optional authenticated boundary is explicit.

### Lane B — M3.2 backend container and deployment artifact

**Task:** Produce a staging-ready, non-root Spring Boot artifact with operational probes.

**Owns:** `backend/Dockerfile`, `.dockerignore`, container/deployment manifests, and deployment-specific backend configuration. Avoid shared README/backlog/evidence files unless assigned by the parent.

**Do:**

- Build a minimal non-root Java 21 image with no source or credential leakage.
- Expose the existing independent liveness and database readiness endpoints.
- Configure bounded Hikari settings, managed TLS CA injection, exact CORS origins, sanitized logs, and graceful shutdown.
- Add image/container tests for non-root execution, expected port, health probes, and absence of `.env`/storage-state files.
- Produce a reproducible image tag/digest and document required secret names without values.

**Done when:** The image builds reproducibly, runs as non-root, passes local health/readiness checks, and has a reviewed staging configuration contract.

### Lane C — M3.2 staging operations

**Task:** Prepare and verify isolated staging infrastructure.

**Owns:** staging-only operations documents and environment checklists under `docs/operations/` or a dedicated `deploy/` subdirectory assigned by the parent. Do not commit secrets, production values, or shared application configuration without coordination.

**Do:**

- Create or identify a separate Supabase staging project and repeat CA, migration, runtime-role, RLS, and disposable-user setup there.
- Prepare Vercel frontend and ECS/Fargate backend configuration with staging-only CORS and secret names.
- Verify database-first migration order, connection limits, TLS, liveness, readiness, and rollback boundaries.
- Record image digest, staging URLs, redacted configuration names, and exact smoke commands.

**Done when:** Staging is demonstrably separate from development and production, the artifact is deployable, and redacted health/configuration evidence exists.

### Lane D — M3.3 release safeguards and observability

**Task:** Prepare release, rollback, monitoring, and incident-response safeguards.

**Owns:** release/rollback/monitoring runbooks and infrastructure-neutral alert contracts in an assigned operations directory. Avoid changing application behavior or CI workflow files owned by Lane A.

**Do:**

- Document database-first migration, backward-compatible application rollout, application rollback, and migration rollback limitations.
- Define health, latency, error-rate, restart, authentication-failure, and connection-exhaustion alerts with owners and thresholds.
- Define structured-log redaction rules; never log tokens, credentials, note content, or private reflections.
- Prepare staging smoke and rollback rehearsal scripts. Execute them only after Lane B/C staging is available.
- Add frontend/API error-monitoring setup with environment separation and retention rules.

**Done when:** Staging smoke, rollback rehearsal, alert identifiers, and incident contacts are recorded; no production promotion occurs without a passing staging artifact.

## M3 integration order

```text
M3.1 CI -------------------------┐
M3.2 container + staging --------┼─> staging smoke + rollback rehearsal ─> M3.3 release gate
M3.3 safeguards/monitoring prep --┘
```

CI, container work, staging provisioning, and safeguards preparation may proceed
in parallel. Staging smoke, rollback evidence, and production approval remain
sequential after a deployable artifact exists.

## Later feature parallelization after the MVP release gate

Do not merge M4 feature migrations before the M3 release gate unless the product
owner explicitly changes the dependency decision. Once M3 is accepted:

### M4 — History and preferences

- **M4.1 history/manual corrections** and **M4.2 profile/preferences** can proceed in parallel because they have separate migrations, APIs, services, and UI.
- Keep `M4.3` OpenAPI/typed-client generation behind the stabilized M4 API contracts; it may review both lanes but should not generate from moving endpoints.

```text
M4.1 history/manual corrections ─┐
                                 ├─> M4 milestone acceptance
M4.2 preferences/timezone -------┘
                 \
                  └─> M4.3 OpenAPI/types after contracts stabilize
```

### M5 and M6 — Notes and social privacy

After M4 preferences/contracts are accepted:

- **M5.1 notes data/API** and **M6.1 friendship/blocking** can proceed in parallel; they use separate tables and ownership boundaries.
- `M5.2` editor waits for the M5.1 notes document contract.
- `M5.3` daily logs/reflection waits for M5.1 plus M4.2 timezone/preferences.
- `M6.2` privacy-safe summaries and `M6.3` active-study presence wait for M6.1 and M4.2, and can then proceed in parallel.

```text
M5.1 notes/API ------------------┬─> M5.2 editor ─┐
                                 └─> M5.3 logs ---┼─> M5 acceptance
M4.2 preferences ------------------------------┘

M6.1 friendship/blocking --------┬─> M6.2 summaries ─┐
                                 └─> M6.3 presence --┼─> M6 acceptance
M4.2 preferences ------------------------------┘
```

### M7 — Private groups

- `M7.1` group model/invitations must stabilize first.
- `M7.2` group UI and `M7.3` group summaries/presence can proceed in parallel after M7.1.

## Parent acceptance checklist

Before accepting a lane:

- [ ] Changed files are within the lane boundary.
- [ ] Tests cover success, failure, authorization, and privacy cases relevant to the lane.
- [ ] Exact commands and results are recorded without secrets.
- [ ] No production database, credentials, or private content were used.
- [ ] `todo.md` status/evidence is updated only after parent review.
- [ ] `git diff --check` passes.
- [ ] The parent reviewed cross-lane API, migration, deployment, and privacy contracts.
- [ ] The integrated branch passes the relevant full frontend/backend suites.
