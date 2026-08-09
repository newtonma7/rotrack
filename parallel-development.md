# Parallel Development Guide

**Purpose:** Coordinate the remaining Milestone 3 release-gate work and the next feature stage, Milestone 4, without allowing parallel lanes to weaken security, privacy, migration, or API contracts.

**Source of truth:** Read [`AGENTS.md`](AGENTS.md), [`arch.plan.md`](arch.plan.md), [`todo.md`](todo.md), and [`frontend/DESIGN.md`](frontend/DESIGN.md) before implementation. This file defines execution order and ownership only; it does not replace those contracts or acceptance criteria.

## Current checkpoint — 2026-08-09

- The local documentation clean-candidate walkthrough and the timer, ownership/RLS, migration, readiness, and authenticated browser technical matrix pass. The product owner/operator attested that the already-confirmed fresh disposable user completed first sign-in and reached `/dashboard`; together with the previously recorded technical matrix, M2.3 is **Verified**. The attestation is manual evidence and is not the still-open deployed authenticated 4/4 run.
- The earlier dependency exception allowed M3 source preparation while the inbox step was blocked. That blocker is now resolved, but the exception never waived an M3 release gate or authorized production promotion.
- M3 delivery foundations are integrated: credential-free pull-request workflows with isolated PostgreSQL, isolated migration checks, secret/workflow guards, a non-root Java 21 OCI-compatible image, historical AWS-era staging templates, release scripts, and monitoring/incident contracts. The product-owner-approved target is Azure Container Apps Consumption with separate managed environments/resource groups/apps, one Vercel project, and two Supabase Free projects.
- M3 is **not yet Verified**: M3.1 remains **Implemented—unverified**, while M3.2 and M3.3 remain **In progress**. The authenticated workflow and Azure deployment adapter now match logical `nonproduction` and the two-project topology; an authorized Azure/Vercel Preview deployment passed digest readback, HTTPS health/readiness, and exact CORS. Hosted required-check evidence, public/paid branch and environment protection, authenticated 4/4, observed alert routing, ten cold-start trials, Free pause/backup safeguards, and rollback rehearsal remain open; the owner and unblock conditions are recorded in `todo.md`.
- M4 is the next feature milestone, but it remains gated on the M3 MVP release gate unless the product owner explicitly records an exception in `todo.md` and `arch.plan.md`.
- Never use production credentials or production infrastructure for development, migration tests, browser automation, smoke tests, or rehearsals.

## Coordination rules

1. One implementation writer owns each managed worktree and file set. Read-only reviewers may inspect any lane.
2. Start every lane by checking `git status`, the current diff, the relevant architecture/backlog sections, and its assigned ownership boundary.
3. Preserve unrelated work. Never use `git reset --hard`, `git clean`, broad checkout commands, or destructive database commands in the shared checkout.
4. Parent-owned files are `todo.md`, `arch.plan.md`, this coordination guide, shared contract hotspots, and final evidence unless explicitly delegated.
5. Behavior changes require tests. A lane handoff must include exact changed files, commands/results, tests, residual risks/blockers, and `git diff --check` output.
6. Never record tokens, passwords, connection strings, certificates, Playwright storage-state JSON, private content, or populated environment files.
7. Reserve migration numbers and shared API contracts before branching. Parallel lanes must not independently choose the same migration identifier or edit the same generated contract.
8. Integrate lanes serially. The parent resolves cross-lane API, migration, navigation, and error-contract conflicts and then runs the combined suites.
9. Generated DTOs and OpenAPI output have one owner. Do not hand-edit generated files after generation policy is established.
10. No later milestone starts merely because preparation exists; the preceding milestone gate must have recorded verification evidence.

## Stage 1 — Close the M3 release gate

The immediate stage is operational verification and the remaining production safeguards. Lane A's credential-free hosted CI and repository-protection work may run concurrently with Lanes B and C. Its authenticated E2E execution must wait for Lane B's explicit non-production Preview/API pair and the approved environment controls. Lane D may review commands and prerequisites concurrently, but live smoke and rollback execution must wait for the non-production deployment and observability setup.

### Lane A — Hosted CI and repository protection

**Owns:** `.github/workflows/`, CI helper files when a hosted defect requires a source fix, and an assigned redacted CI evidence document. Repository-setting changes require authorized repository administration and must never be inferred from YAML alone.

**Do:**

- Open a pull request and record all required credential-free checks passing.
- Deliberately make one required check fail on a disposable branch/commit and verify merge is blocked; restore the green state afterward.
- Configure and verify branch protection for the documented required check names.
- Preserve the trusted-default-branch `repository_dispatch` boundary for authenticated E2E. Do not add storage-state secrets until public/paid `main` and `nonproduction` protections, exact approved host variables, and a required reviewer are configured and read back.
- After Lane B supplies the explicit non-production frontend/API pair, verify the environment's required reviewers and branch restrictions, then run the authenticated job only against that pair; require exactly four passed tests and no skips, flaky results, or unexpected API targets.

**Done when:** Hosted green and deliberate-red evidence and required-check enforcement are recorded; after Lane B is deployed, protected-environment restrictions and the authenticated staging run are also recorded with URLs/identifiers that reveal no secrets.

### Lane B — Production-separated non-production deployment

**Owns:** `deploy/staging/`, non-production deployment documentation/checklists, and one redacted evidence file. It may use authorized provider consoles/CLIs but must not alter production resources.

**Do:**

- Prove that the shared non-production Supabase project, Vercel Preview, target logical GitHub `nonproduction`, managed environment `rotrack-nonproduction-env` inside resource group `rotrack-nonproduction`, and Container App `rotrack-api-nonproduction` are the selected boundary and that production's managed environment/resource group/app remain separate.
- Obtain the official shared-project Supabase CA through an authenticated provider channel and record redacted provenance.
- Apply ordered migrations with an administrative migration identity; configure and audit the narrower runtime identity separately.
- Build the platform-neutral backend OCI-compatible image; read back its registry manifest media type, architecture, and immutable digest; then deploy that exact digest to the non-production Azure Container App. If ACR is selected, use managed identity for pulls.
- Deploy the frontend to Vercel Preview in the one Vercel project with the exact non-production API URL.
- Verify connection budgeting across maximum Container App replicas and revision overlap; verify exact HTTPS CORS origins, TLS/CA injection, liveness, readiness, service-version-to-digest binding, ACA hardening controls, at least 10 scale-from-zero trials and the production minimum-replica decision, Free pause/resume ownership, encrypted logical-export retention/restore rehearsal or explicit product-owner risk acceptance, and budget/credit-expiry notifications.
- Complete the non-production checklist with names/IDs redacted where disclosure would be sensitive. Never commit actual secret values.

**Done when:** The immutable candidate is deployed to demonstrably separate non-production Azure/Vercel/GitHub boundaries while intentionally using the shared non-production Supabase project, and redacted CA, migration, runtime-role, CORS, connection, health, cold-start, and alert evidence is complete.

### Lane C — Active release safeguards and observability

**Owns:** rate-limit implementation/tests, structured application logging/redaction implementation, frontend/API telemetry integration, monitoring configuration, and assigned operational tests. Coordinate required deployment variables with Lane B through names and contracts only.

**Do:**

- Implement rate limiting for authentication-adjacent and mutation endpoints with `429`, bounded recovery, and trusted-proxy/bypass tests.
- Implement structured-log allowlisting and redaction; prove tokens, credentials, note/reflection content, authorization headers, and private payload fields are absent.
- Configure logically separated non-production frontend/API telemetry with bounded retention and least-privilege access; do not infer a separate provider project from this requirement.
- Materialize health, latency, error-rate, restart, authentication-failure, and connection-exhaustion dashboards/alerts from the checked-in contract.
- Assign named incident roles and alert owners through the approved operational channel; do not commit private contact details.
- Send a harmless redaction sentinel and test alert, then record redacted ingestion/routing evidence.

**Done when:** Safeguards are active in staging, failure/recovery tests pass, telemetry and alert routing are observed, and production STOP conditions in the runbooks are cleared except those requiring Lane D.

### Lane D — Staging smoke and rollback rehearsal

**Depends on:** Lanes B and C integrated and deployed.

**Owns:** release/rollback execution evidence and fixes limited to `scripts/release/` or release documentation. It does not provision infrastructure or change application behavior.

**Do:**

- Confirm the release target, candidate digest, prior digest, expected API URL, and approved external hooks before execution.
- Run health, CORS, authentication, Work/Rot lifecycle, restoration, dashboard delta, and two-user isolation smoke against staging.
- Require authenticated Playwright 4/4 with no skip, flaky result, or target mismatch.
- Rehearse rollback from the exact candidate digest to the exact prior digest, verify health/readiness and critical behavior, and restore the intended staging candidate if approved.
- Record elapsed recovery time, observed alerts, rollback limitations, approvals, and residual risks without secrets.

**Done when:** Staging smoke and rollback rehearsal pass against immutable artifacts and the M3 release evidence is sufficient to mark M3.1–M3.3 and the MVP release gate **Verified**.

### M3 dependency order

```text
A credential-free hosted CI/protection ----------------┐
B production-separated non-production deployment ─┬─> A authenticated E2E ┤
C active safeguards/telemetry ────────┴───────────────────────┴─> D smoke + rollback ─> M3 MVP gate
```

Do not promote to production or begin M4 implementation before this gate unless the product owner explicitly changes the dependency decision.

## Stage 2 — Milestone 4 contract seed

After the M3 gate passes, the parent creates and reviews one small shared contract seed before parallel feature worktrees are created.

The seed must decide and record:

- Decide the manual-correction trust boundary and record it in `arch.plan.md` before implementation, including whether clients may submit completed-entry start/end instants. Regardless of that decision, clients never select `user_id` or submit authoritative duration; the server validates ownership and derives durations.
- Completed-range semantics, including half-open adjacency, overlap with completed/active entries, mutation races, maximum editable range, and stable error codes.
- Cursor shape, deterministic `(start_time DESC, id DESC)` ordering, page-size limits, and invalid/stale cursor behavior.
- Preference representation: validated IANA timezone, the unit/range for an optional daily Work goal, private-default sharing flags, and how saved timezone becomes the default for future calendar rendering.
- Exact API DTOs and routes needed by both frontend lanes, with detailed draft OpenAPI schemas written before each vertical slice as required by `arch.plan.md`.
- Reserved, non-conflicting migration filenames and upgrade order.
- Ownership for shared files such as `frontend/src/lib/api.ts`, global navigation/layout, common error handling, and the OpenAPI root.

Changes to timer lifecycle or trust boundaries require the corresponding `arch.plan.md` decision before implementation. Branch M4 lanes only from the reviewed seed commit.

## Stage 3 — Milestone 4 parallel implementation

M4.1 and M4.2 are independent vertical slices after the shared seed. Give each one implementation writer and a separate managed worktree.

```text
m4.1-history
m4.2-preferences
```

### Lane E — M4.1 history and manual corrections

**Owns:** Its reserved migration, history-specific backend model/repository/service/controller/DTO files, `/history`, history feature components/hooks, and focused tests. It must not edit M4.2 files, the other reserved migration, or parent-owned shared contract files.

**Do:**

- Add ownership-scoped list/create/update/delete endpoints for completed entries only.
- Use opaque cursor pagination and deterministic reverse-chronological ordering with an ID tie-breaker.
- Enforce valid completed ranges and no overlap with completed or active entries in service validation and at the PostgreSQL boundary; adjacent half-open ranges remain valid.
- Make all ownership failures non-enumerating and ensure concurrent edits cannot bypass overlap protection.
- Keep duration timestamp-derived and reject client `user_id`, duration, active-state, or ownership fields.
- Build an accessible history/editor UI with loading, empty, validation, conflict, unauthorized/not-found, retry, and delete-confirmation states.
- Test clean migration apply and upgrade, database constraints, pagination boundaries/ties, ownership, overlap races, DST-offset instants, deletion, and browser recovery.

**Done when:** History CRUD is ownership-safe and deterministic, overlap protection survives concurrent database writes, UI states are accessible, and focused plus full affected suites pass.

### Lane F — M4.2 profile and preferences

**Owns:** Its reserved migration, preference-specific backend model/repository/service/controller/DTO files, `/settings`, settings feature components/hooks, and focused tests. It must not edit M4.1 files, the other reserved migration, or parent-owned shared contract files.

**Do:**

- Add one owned preference record per user with validated timezone, optional daily Work goal, and both sharing flags defaulting to `false` at the database and API boundaries.
- Define idempotent read/update behavior for users whose preference row does not yet exist.
- Validate IANA zones and goal bounds server-side; never trust hidden or disabled client controls.
- Make timezone changes affect future calendar rendering/bucketing without rewriting stored UTC time-entry instants.
- Build an accessible `/settings` UI with explicit save/saved/error states and clear privacy copy.
- Test defaults, partial/full updates, malformed values, ownership, concurrent updates, timezone/DST behavior, private-default migration behavior, reload, and error recovery.

**Done when:** Preferences are owner-scoped, timezone behavior is deterministic, both sharing flags remain private by default, and focused plus full affected suites pass.

### Parallel ownership hotspots

The parent must assign these before branching; two lanes may not write them concurrently:

- `frontend/src/lib/api.ts` and shared DTO exports
- global navigation, protected-route lists, or app layout
- shared backend exception/error mapping
- migration ordering validation fixtures
- `arch.plan.md`, `todo.md`, and combined evidence

Prefer adding the shared client signatures and navigation slots in the contract seed. If integration changes are still needed, lanes return a small requested-change note and the parent applies it after serial integration.

## Stage 4 — M4.3 OpenAPI and typed-client policy

Start M4.3 generation only after the integrated M4.1 and M4.2 HTTP contracts are stable. The detailed schemas originate in the pre-slice contract seed; this stage reconciles them with the implementation and adds deterministic generation. Use one writer and do not run it concurrently with API-shape changes.

**Owns:** OpenAPI source, generator configuration, generated frontend DTO files, contract snapshots, and contract-drift CI checks.

**Do:**

- Finalize and publish only implemented routes, schemas, stable error codes, authentication requirements, pagination, and validation constraints.
- Pin the generator/toolchain and make regeneration deterministic from a clean checkout.
- Generate DTO types while keeping bearer-token acquisition, refresh, error normalization, and native `fetch` orchestration hand-written.
- Replace duplicated manual DTO definitions without weakening runtime validation or auth behavior.
- Add CI that regenerates and fails on a diff, then prove the check with a deliberate local drift fixture.
- Verify generated output contains no server URLs, credentials, example tokens, or private example content.

**Done when:** OpenAPI matches both implemented slices, clean regeneration is stable, frontend typechecking/tests pass against generated DTOs, and CI detects contract drift.

## M4 integration order

```text
M3 MVP gate
    │
    └─> parent contract seed
            ├─> E M4.1 history --------┐
            └─> F M4.2 preferences ----┴─> parent integration/full verification
                                                └─> M4.3 OpenAPI/types
                                                        └─> M4 gate
```

Integrate M4.1 and M4.2 serially. After resolving shared contracts, run:

- frontend clean install/audit, lint, typecheck, Vitest, build, and relevant Playwright flows;
- backend Java 21 clean test/package;
- clean-database and upgrade-path migrations against isolated PostgreSQL;
- ownership, overlap/concurrency, timezone/DST, and privacy-default verification;
- OpenAPI regeneration and contract-drift checks after M4.3;
- `git diff --check` and secret scanning over the candidate tree.

Mark M4 **Verified** only when history, overlap protection, timezone preferences, private-default sharing flags, and contract drift are all evidenced. Preparation or skipped external tests are not verification.

## Later parallelization after M4

- **M5.1 notes data/API** and **M6.1 friendship/blocking** may proceed in parallel only after M4 acceptance and separate architecture/API/privacy contract seeds.
- `M5.2` waits for the M5.1 document contract. `M5.3` waits for M5.1 and M4.2 timezone/preferences.
- `M6.2` privacy-safe summaries and `M6.3` presence wait for M6.1 and M4.2; they may then proceed in parallel with separate payload-allowlist tests.
- M7.1 group membership/invitations stabilizes before M7.2 management UI and M7.3 summaries/presence run in parallel.

## Parent acceptance checklist

Before accepting any lane:

- [ ] Changed files stay within the assigned ownership boundary.
- [ ] Success, failure, authorization, concurrency, timezone, and privacy cases relevant to the lane are tested.
- [ ] Exact commands/results and changed files are reported without secrets.
- [ ] No production resource, credential, or private content was used.
- [ ] Clean apply and upgrade migration paths are verified where schema changed.
- [ ] `git diff --check` and candidate secret scanning pass.
- [ ] Residual risks and external blockers have owners and unblock conditions.
- [ ] Parent-owned status/evidence files are updated only after combined review.
- [ ] Cross-lane API, migration, DTO, UI navigation, and privacy contracts are reconciled.
- [ ] The integrated branch passes the relevant full suites before the milestone status changes.
