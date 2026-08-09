# rotrack Development Backlog

**Architecture and contracts:** [`arch.plan.md`](arch.plan.md)
**Backlog reviewed:** 2026-08-09
**Current release target:** Production-ready personal timer and dashboard MVP

## 1. Operating Rules

### Source of truth

- `arch.plan.md` owns product invariants, architecture, trust boundaries, and API/data contracts.
- `todo.md` owns sequencing, dependencies, status, acceptance criteria, and verification evidence.
- Repository source and recorded command output outrank historical completion claims.
- When implementation reveals a contract conflict, update the architecture decision before building around an assumption.

### Orchestration

- The primary agent owns integration, final decisions, and cross-file consistency.
- Ordinary sub-agents may be used for independent research, audits, tests, and reviews when parallel work is useful.
- Assign one writer per file or isolated subsystem; parallel agents must not edit overlapping files.
- The primary agent reviews sub-agent output and repository diffs before accepting changes.
- User requests and repository instructions override this default orchestration policy.

### Status vocabulary

Use exactly these task states:

- **Not started** — no implementation exists.
- **In progress** — active work exists but acceptance criteria are incomplete.
- **Implemented—unverified** — relevant code exists, but required proof is missing or stale.
- **Blocked** — a named external dependency prevents progress; record the owner and unblock condition.
- **Verified** — all acceptance criteria passed and evidence is recorded.

Only **Verified** satisfies a milestone gate. A file existing or code compiling once is not enough.

### Task completion standard

Every completed task must record:

1. Deliverable and affected boundary
2. Dependency status
3. Acceptance checks, including failure/authorization cases
4. Automated tests added or updated
5. Exact verification commands or manual scenario
6. Evidence: concise output, date, environment, and known limitations

Do not record secrets, bearer tokens, database passwords, complete environment files, or private note content in evidence.

### Code and learning standards

- Add concise teaching comments to materially changed boundary or domain files: authentication, JWT-to-API flow, database authorization, timers, timezones, rich-text persistence, privacy projections, and group roles.
- Explain why an invariant exists and what fails if it is violated; do not comment trivial syntax or generated shadcn code.
- Preserve the `ROT | WORK` two-bucket rule across TypeScript, Java, SQL, tests, and documentation.
- Use Tailwind and shadcn for application controls. Third-party chart/editor APIs may use their required rendering props while respecting design tokens and accessibility.
- Analyze existing files and dirty state before editing. Never overwrite unrelated user changes.

## 2. Current Truth

| Area | State | Evidence / gap |
|---|---|---|
| Documentation currency | **Verified** | The startup/health runbook is canonical under `docs/operations/`, README links and limitations match current evidence, and a clean-candidate setup/build/startup walkthrough passes. |
| Frontend routes and auth UI | **Implemented—unverified** | Static build and real sign-in/signup browser checks pass, and M2.3 is verified by the combined technical matrix and operator-attested fresh first sign-in. Deployed authenticated UI evidence remains open under M3. |
| Tracker start/restore/stop UI | **Verified** | M1.3 source, unit tests, and recorded authenticated browser/API evidence cover explicit start, restore, and stop behavior. |
| Automatic unload stopping | **Verified** | M1.3 removed production unload/keepalive handling; source search finds only the negative unit test. |
| Dashboard UI and API | **Verified** | M1.4 replaces fixed server-time/minute timelines with validated IANA-zone ranges, timestamp-derived seconds, local daily buckets, tested DST/clipping, and explicit UI states. |
| Spring Boot API core | **Verified** | Recorded live Supabase JWT sign-in, Spring ownership isolation, timer lifecycle, dashboard flow, health, readiness, TLS, and CORS evidence pass. |
| Initial schema hardening | **Verified** | Empty-database apply and migrated-database rollback/repository verification pass against isolated PostgreSQL targets. |
| Supabase development integration | **Verified** | Migration, runtime role, Data API RLS, signup trigger, Spring ownership, and two-user technical evidence pass. The fresh confirmed-user first-sign-in acceptance step is operator-attested as complete; deployed authenticated E2E remains an M3 boundary. |
| Automated test suites | **Verified** | The 2026-08-09 candidate run reports frontend 19 passed plus lint/typecheck/build and backend 90 discovered, 86 passed, four expected opt-in integration skips, and zero failures/errors. Migration apply/verify and authenticated Playwright 4/4 remain recorded from prior local/development evidence. |
| Pull-request CI source | **Implemented—unverified** | Five credential-free jobs use isolated disposable PostgreSQL and do not touch hosted Supabase. The authenticated workflow source now uses trusted-default-branch `repository_dispatch`, logical `nonproduction`, two Supabase project identities, exact provider/host binding, and strict external storage-state handling. Hosted green/blocking checks and repository/environment protection remain unobserved/open; no authenticated environment secrets may be added before that protection exists. |
| Backend container artifact | **Verified** | The non-root Java 21 image passed local liveness/readiness/SIGTERM and sensitive-content inspection. An immutable Linux/amd64 OCI-compatible image was published to non-production ACR and its registry digest/media type/architecture were read back; production has no published candidate. |
| Production-separated non-production deployment | **Implemented—unverified** | On 2026-08-09 the authorized `eastus2` non-production resource group, managed environment, ACR/identity, Log Analytics cap/retention, budget alerts, digest-bound Container App, and one Vercel Preview were created. HTTPS health/readiness and exact allow/deny CORS passed against the shared non-production Supabase project. Vercel SSO remains enabled and no bypass secret remains. Authenticated 4/4, alert delivery, ten cold starts, backup/restore, and rollback are still required. No production deployment/environment, `rotrack-prod`, or production Azure mutation occurred; the shared Vercel project protection returned to its prior SSO/no-bypass state. |
| Release safeguards and observability | **In progress** | Commit `40d4376` adds a bounded process-local authenticated mutation limiter, allowlisted structured request logging, and fail-closed staging logger wiring. Fleet-wide/authentication-adjacent edge limiting, observed collector redaction/telemetry/alerts/routing, staffing, staging smoke, and rollback rehearsal remain incomplete. |
| Notes, logs, friends, groups | **Not started** | Architecture defined; implementation follows the verified M3 release gate. |

**Non-production deployment residual gate:** The Azure/Vercel non-production boundary is configured and health/readiness/CORS evidence is recorded in [`docs/operations/azure-nonproduction.md`](docs/operations/azure-nonproduction.md). The local-only credential-object finding was purged and the non-production runtime password rotation/local-and-ACA redeployment passed; all three historical personal-domain author addresses will be retained by explicit owner decision. Remaining owner/unblock conditions are the solo-maintainer/public-or-paid GitHub protection policy, deployed authenticated 4/4, alert delivery, cold-start trials, backup/restore or risk acceptance, and rollback rehearsal. Production remains `rotrack-production-env` / `rotrack-production` / `rotrack-api-production` / `rotrack-prod` / Vercel Production and was not mutated.

### Non-production cloud checkpoint — 2026-08-09

- Commits after `5a38d0d` add the Azure foundation/app Bicep split, safe CLI adapters and provider-shape tests, public-release GitHub workflow hardening, and exact OCI-digest service-version startup support. The final deployment image was built from clean revision `ea9b70b`.
- Required Azure providers were registered. The `eastus2` non-production boundary, Basic ACR, managed `AcrPull` identity, capped Log Analytics workspace, 15-unit monthly budget, and scale-`0..1` Container App were created. Registry and ACA readback proved Linux/amd64, immutable digest, exact secret references, probes, shutdown, and digest/service-version equality.
- The initial live revision reproduced and then verified a test-first fix for rejecting `sha256:<64-hex>` service versions. The corrected revision returned health/readiness `200`; HTTP redirected to HTTPS; exact final Vercel Preview CORS was allowed and an unrelated Origin was denied.
- One Vercel Preview built successfully with the non-production API URL. Vercel SSO remains enabled and readback shows no automation-bypass secret. GitHub remains private/unprotected; Dependabot alerts/automated fixes are enabled, Actions are restricted to GitHub-owned actions, and no environment auth secrets have been configured.
- See [`docs/operations/staging/2026-08-09-nonproduction-evidence.md`](docs/operations/staging/2026-08-09-nonproduction-evidence.md). No production deployment/environment, Supabase, or Azure resource was mutated; the shared Vercel project protection was restored to its prior SSO/no-bypass state.

### Current integrated checkpoint — 2026-08-08 / local workspace

- M3 delivery foundations were committed and pushed as `15997ea` (`Implement Milestone 3 delivery foundations`); the later `1347172` commit changes frontend typography from Migha to local Figtree and does not alter M3 contracts.
- M3 source adds five credential-free pull-request jobs using isolated PostgreSQL, a protected authenticated-E2E workflow converted to trusted-default-branch `repository_dispatch` and the two-project `nonproduction` boundary, isolated PostgreSQL migration apply/verify, prospective-tree/history secret scanning, a non-root Java 21 container, Azure Bicep/CLI deployment guards, and release/rollback/monitoring/incident contracts. Remote GitHub protections and authenticated execution remain residual work.
- Commit-time validation recorded frontend `npm ci`, high audit, lint, typecheck, 19 Vitest passes, and build; backend Java 21 `mvn clean test`/`mvn package` discovered 65 tests with 61 passes, four expected opt-in skips, and zero failures/errors.
- A disposable PostgreSQL 17.6 run recorded migration apply 1/1 and migrated verify/repository 4/4. The final clean committed container build recorded non-root UID/GID `10001:10001`, liveness/readiness `200`, SIGTERM exit `143`, and no source, `.env`, auth-state, token, or credential artifacts.
- Local action/workflow, sensitive-path, migration-order, container, staging, release, and Gitleaks guards passed, including deliberate negative fixtures. These are local/commit-time attestations, not hosted CI, registry, cloud, or deployed-staging evidence.
- Commit `40d4376` continues Lane C source preparation with a process-local authenticated mutation limiter, omission-based structured request logging, and staging template validation that binds enabled logging metadata to the immutable image digest. Java 21 `mvn clean package` discovers 88 tests: 84 pass, four opt-in PostgreSQL tests skip as expected, and none fail; staging/container positive and negative contract tests and `git diff --check` pass.
- Documentation is reconciled: the startup/health runbook remains under `docs/operations/`, README current-state limitations and environment guidance match the source/evidence, and local Markdown links pass.

### Pre-M3 audit — 2026-08-07 / historical local workspace

- Audited `main` at `2fc7cff`; frontend lint/typecheck/11 tests/build and backend Java 21 test/package with 32 tests passed at that checkpoint.
- No database-backed check was run in that audit. Its dependency findings and test counts were superseded by the 2026-08-08 integrated validation above.

### Historical baseline evidence captured during the 2026-08-01 audit

- Current tools: Node `v24.18.0`, npm `11.16.0`, Maven `3.9.12`, Java runtime `25.0.3`; the backend targets Java 21.
- `frontend/package-lock.json` exists, but `frontend/node_modules` is absent.
- `npm run lint` could not start because `eslint` was not installed. Historical frontend build checkmarks are therefore unverified.
- `backend/target/**` is committed and already dirty; it includes stale test output even though no backend test source exists.
- The root README is the default create-next-app document and does not describe the repository.

## 3. Milestone 0 — Truthful, Reproducible Baseline

**Goal:** Make the repository safe to develop and make all progress claims reproducible.
**Gate:** All tasks below are **Verified** before core correctness work is called complete.

### M0.1 — Reconcile architecture and backlog

**Status:** Verified
**Dependencies:** None

**Deliverable**

- Rewrite `arch.plan.md` as the current/target architecture and contract source of truth.
- Rewrite `todo.md` as this dependency-ordered, evidence-driven backlog.
- Lock explicit-session behavior, no automatic stopping, backend-owned data access, private social projections, Tiptap notes, daily logs, friends, presence, and groups.

**Acceptance and verification**

- All referenced current files/routes/endpoints exist or are clearly labeled target/future.
- No target requirement says idle time automatically becomes Rot or that hiding/leaving stops a timer.
- No executable task contains unresolved technology or hosting alternatives.
- Targeted searches confirm the only legacy-bucket mention is the architecture's explicit invalidation rule, and find no obsolete visibility-stop hook names or unresolved “tool A or tool B” instructions.
- Review links and the core endpoint list against source.

**Evidence — 2026-08-01 / local workspace**

- `git diff --check -- arch.plan.md todo.md` passed.
- Local Markdown links between `arch.plan.md` and `todo.md` resolve.
- Current routes, dependencies, migration, and controller mappings were cross-checked against repository source.
- Stale-target searches found only intentional current-state or explicit invalidation references.
- Independent architecture, backlog, and contract reviews were reconciled into the final documents.

### M0.2 — Remove generated artifacts from version control

**Status:** Verified
**Dependencies:** M0.1

**Deliverable**

- Add `backend/target/` to `.gitignore`.
- Remove already tracked target files from the Git index without deleting unrelated source or user work.
- Remove `backend/stuff.txt` only after inspecting it and confirming it is disposable.

**Acceptance and verification**

- `git ls-files backend/target` returns no files.
- A Maven build may recreate `backend/target/` without changing `git status`.
- Existing dirty artifacts are handled deliberately and their ownership is not overwritten silently.

**Evidence — 2026-08-02 / local workspace**

- Added `backend/target/` to `.gitignore` and removed 24 tracked generated files from Git while preserving local build output.
- Inspected and removed the empty disposable `backend/stuff.txt`.
- Verified `git ls-files backend/target` returns no files and `git diff --check` passes after cleanup.

### M0.3 — Create the development runbook

**Status:** Verified
**Dependencies:** M0.1

**Deliverable**

- Replace the root README with repository purpose, directory map, prerequisites, Supabase setup, environment-variable names, install/run/test commands, and troubleshooting.
- Explain how environment variables enter Spring: shell/IDE/container injection, not implicit `.env` loading.
- Add exact frontend, backend, and combined local URLs without printing secret values.

**Acceptance and verification**

- A new developer can start from a clean clone using only the README plus access to a Supabase development project.
- `.env.example` files contain every required variable and no secret defaults.
- Runbook commands match package scripts, Maven configuration, and `/api/v1` paths.

**Evidence — 2026-08-02 / local workspace**

- Replaced the create-next-app README with repository setup, Supabase configuration, environment handling, run commands, API routes, conventions, and known limitations.
- Verified README links, package scripts, Maven/API mappings, and `git diff --check`.
- **Revalidation — 2026-08-08 / clean candidate:** Built a credential-free candidate tree from the current index, confirmed it initially contained no ignored environment files or build output, then followed the README with private ignored development configuration. Frontend `npm ci`, lint, typecheck, 19 tests, build, and startup passed; Java 21 backend clean package discovered 88 tests with 84 passes and four expected opt-in skips, then liveness/readiness returned `200`. Isolated ports 13000/18080 were used because an existing healthy Java process owned 8080.

### M0.4 — Pin and verify toolchains

**Status:** Verified
**Dependencies:** M0.3

**Deliverable**

- Pin a supported Node LTS release and Java 21 using repository-visible version files or documented tooling.
- Install frontend dependencies with `npm ci`.
- Establish baseline scripts for lint, typecheck, unit tests, and build.

**Acceptance and verification**

- `npm ci`, `npm run lint`, `npm test`, and `npm run build` run deterministically.
- `mvn test` and `mvn package` run using Java 21.
- Failures are fixed or recorded as explicit blockers; they are not converted into checkmarks.

**Evidence — 2026-08-02 / local workspace**

- Toolchain pins: Node `24.18.0` in `.nvmrc`; Java `21` in `.java-version`; Maven `3.9.12`.
- Frontend: `npm ci`, `npm run lint`, `npm run typecheck`, `npm test`, and `npm run build` passed. Vitest reported 1 test passed.
- Backend: `mvn test` and `mvn package` passed under Temurin Java `21.0.12`; Maven reported no source tests yet.
- Added the initial Vitest runner/configuration and a focused `cn` utility test. Broader frontend and backend coverage remains part of M1.5.
- `npm install` reports 11 dependency audit findings and pending install-script approvals; these are recorded for follow-up rather than hidden.

**Revalidation — 2026-08-06 / local workspace**

- A fresh `npm ci`, frontend lint/typecheck/test/build, and backend `mvn test`/`mvn package` under Temurin Java `21.0.12` passed. The current suites report 11 frontend and 32 backend tests; no database-backed check was run.

### M0.5 — Reconcile current-state documentation

**Status:** Verified
**Dependencies:** None

**Deliverable**

- Refresh `arch.plan.md` current-state sections and README known limitations to match the checked-in M1.1–M1.3 source without weakening target invariants.
- Resolve the documented `POST /time-entries/start` `201` response versus the controller's actual `200` response through an explicit contract/implementation decision and matching test.

**Acceptance and verification**

- Current-state documentation accurately names the single ID-based stop endpoint, tracked tests, migration hardening, generated-artifact state, and remaining gaps.
- README limitations distinguish historical evidence from unresolved migration/RLS, dashboard, test, CI, and deployment work.
- The chosen start-response status is consistent across architecture, controller, tests, frontend assumptions, and evidence.

**Evidence — 2026-08-06 / local workspace**

- Reconciled `arch.plan.md` and README current-state/limitation sections with the single ID-based stop endpoint, migration hardening, source-controlled tests, dashboard contract, and remaining database/JWT/two-user/CI gaps.
- Kept the architecture's `201 Created` decision and added `@ResponseStatus(HttpStatus.CREATED)` plus a failing-first MockMvc status test.
- Updated the shared time-entry DTO from transitional `durationMinutes` to timestamp-derived `durationSeconds` across Java, TypeScript, and tests.
- `mvn clean package`, frontend lint/typecheck/test/build, contract searches, and `git diff --check` passed; current suites report 33 backend and 11 frontend tests.

**Revalidation — 2026-08-08:** Restored the canonical startup/health runbook under `docs/operations/`, updated its rate-limit/logging environment contract, and reconciled README limitations with completed empty-database, Data API RLS, authenticated Playwright, managed-CA, degraded-readiness, and CORS evidence. Clean-candidate setup/build/startup, Markdown links, source/API/script checks, and `git diff --check` pass.

## 4. Milestone 1 — Secure and Correct Timer MVP

**Goal:** Correct the current implementation before applying its baseline migration remotely.
**Dependencies:** Milestone 0
**Gate:** Migration, timer lifecycle, dashboard, security, and automated tests are all **Verified**.

### M1.1 — Harden the initial schema before application

**Status:** Verified

**Deliverable**

- Amend the unapplied development baseline or add the next ordered migration if any environment has already applied `001`.
- Enforce one active entry per user with a partial unique index.
- Add `(user_id, start_time)` reporting index and timestamp-based constraints.
- Define timestamps as duration source of truth; plan removal of transitional `duration_minutes`.

**Acceptance and verification**

- Concurrent inserts cannot create two `end_time IS NULL` rows for one user.
- Invalid ranges are rejected by PostgreSQL.
- Migration applies cleanly to an empty development database and has a documented path for any already-migrated environment.
- Repository integration tests cover the constraints.

**Evidence — 2026-08-02 / local workspace**

- The configured Supabase development database already had `001_initial_schema.sql` applied, so hardening was added as ordered migration `002_harden_time_entries.sql`.
- Applied `002_harden_time_entries.sql` through Supabase CLI direct database mode using the transaction pooler; catalog inspection confirmed `idx_time_entries_one_active_per_user` is partial on `end_time IS NULL` and `idx_time_entries_user_start_time` covers `(user_id, start_time)`.
- A rollback-cleanup SQL probe confirmed duplicate active inserts fail while active rows for different users remain allowed; an invalid timestamp range failed its check constraint.
- A timestamp-arithmetic probe inserted `duration_minutes = 999` for a one-hour entry and confirmed the derived duration is 60 minutes; probe rows were removed and the final probe count was zero.
- Backend aggregation and DTO mapping derive duration from `start_time`/`end_time`; the transitional column is not authoritative.
- Failing-first tests were added for the migration contract and active-session duration behavior. `mvn test` and `mvn package` pass under Temurin Java `21.0.12` with 2 tests passing.

**Current verification — 2026-08-07 / configured development PostgreSQL**

- Added opt-in executable migration and Spring Data repository tests with an explicit isolated-target acknowledgement, redacted environment contract, and rollback-only cleanup.
- `ROTRACK_TEST_DATABASE_MODE=verify mvn -Drotrack.postgres.integration=true -Dtest='PostgresMigrationIntegrationTest,TimeEntryRepositoryPostgresIntegrationTest' test` passed under Temurin Java 21 against PostgreSQL 17.6: 4 tests, 0 failures/errors/skips.
- The run proved the actual constraints/indexes, same-user active rejection (`23505`), different-user active sessions, invalid-range rejection (`23514`), timestamp-derived 3,600 seconds despite `duration_minutes=999`, signup-trigger fixture profiles, and real repository flush/owned-active reads. See `database/verification/2026-08-07-postgres-verify.md`.
- A temporary isolated PostgreSQL 18.4 cluster was initialized under `/tmp` because Docker/Podman was unavailable. `ROTRACK_TEST_DATABASE_MODE=apply mvn -Drotrack.postgres.integration=true -Dtest=PostgresMigrationIntegrationTest test` passed 1/1 with no failures, errors, or skips; the cluster was stopped and removed afterward.

### M1.2 — Harden JWT authentication and API errors

**Status:** Verified

**Deliverable**

- Validate issuer, audience, time claims, accepted asymmetric signing algorithm, and UUID `sub`.
- Disable legacy symmetric fallback in production.
- Return the documented error shape for auth, validation, malformed JSON, missing resources, conflicts, and unexpected errors.
- Scope every repository access using the authenticated UUID and never accept client `user_id`.

**Acceptance and verification**

- Missing, expired, wrong-issuer/audience, bad-signature, and non-UUID-subject tokens return stable `401` responses.
- User A receives `404` when requesting User B's resource.
- Validation returns field-level errors; internal exceptions do not leak details.
- Security/controller tests cover success and failure cases.

**Evidence — 2026-08-02 / local workspace**

- Implemented asymmetric ES256/JWKS validation with issuer, time, audience, and UUID `sub` validators; removed the legacy HS256 fallback.
- Added structured `401/403` security responses, validation/malformed-JSON/constraint/unexpected error handling, domain conflict/not-found exceptions, and frontend structured API error parsing.
- Added MockMvc security/controller tests and a frontend API-error parser test.
- Backend `mvn test` and `mvn package` pass under Temurin Java `21.0.12` with 15 tests passing; frontend lint, typecheck, test, and build also pass.
- A live Spring Boot probe against the configured Supabase database confirmed `/health` is public and missing/invalid bearer requests return stable JSON `401` envelopes with path and error codes.
- A real Supabase-signed valid token, bad-signature token, and two-user authenticated API isolation flow remain untested; those belong to the authenticated integration flow in M2.2–M2.3.

**Current verification — 2026-08-07 / local workspace**

- Added generated-key tests through the production JWT decoder/security filter for valid ES256 plus bad signature, unsupported trusted RS256, wrong issuer/audience, expired/not-before, missing subject, and malformed UUID subject; all assert the stable `401` envelope.
- Added controller tests using the real ownership-scoped services and an owner-sensitive repository boundary: User B sees empty active/dashboard views and receives `404 NOT_FOUND` when stopping User A's entry.
- These tests materially strengthen the cryptographic and application authorization boundary. The real Supabase sign-in/browser flow then passed the Spring API ownership path for both disposable users; the direct Data API RLS matrix is recorded under M2.1.

### M1.3 — Implement the explicit session lifecycle

**Status:** Verified

**Deliverable**

- Remove `usePageUnloadStop`, `stopActiveSessionBeacon`, related keepalive behavior, and auto-stop UI copy.
- Preserve active sessions across reload, navigation, tab changes, minimization, and browser closure.
- Make session start concurrency-safe and stop idempotent.
- Migrate the frontend to the ID-based stop endpoint and retire `/time-entries/active/stop` after compatibility tests pass.

**Acceptance and verification**

- A started session remains active after route navigation and a browser close/reopen.
- Reload restores the original ID and start timestamp.
- A concurrent second start returns `409 ACTIVE_SESSION_EXISTS` and leaves one row.
- Repeated stop requests return the unchanged entry and original end time.
- Start accepts only `WORK` or `ROT`; idle time creates no row.

**Evidence — 2026-08-02 / local workspace**

- Removed the unload hook, keepalive stop request, legacy active-stop client/server endpoint, and contradictory auto-stop copy; the tracker now uses the ID-based stop path only.
- Added frontend hook/API tests for active-session restoration, explicit ID-based stop, successful state clearing, and absence of an unload handler.
- Added backend service tests for unchanged repeated-stop responses and database-conflict translation to `ACTIVE_SESSION_EXISTS`; the controller suite covers ID-based stop responses. The partial unique index remains the database race authority.
- `JAVA_HOME=<java-21-home> PATH="$JAVA_HOME/bin:$PATH" mvn clean package` passed with 18 tests; `cd frontend && npm test` passed with 7 tests, and frontend lint, typecheck, and build passed.
- `git diff --check` passed and lifecycle searches found no production unload/keepalive implementation.
- Authenticated `agent-browser` verification used profile `rotrack-test` against isolated frontend/API ports 3001/8081. Work remained active through tracker → dashboard → tracker navigation, reload, and a browser close/reopen using browser restore; the reopened tracker displayed the same active session until explicit stop.
- Two simultaneous authenticated Work starts returned one success and one `409 ACTIVE_SESSION_EXISTS`; the active-session lookup matched the one successful entry, which was then explicitly stopped. ROT also started and stopped successfully. Two stops against the same entry both returned `200` with the identical server `endTime` and duration. (The historical success response preceded M0.5's correction to `201`.)
- `/api/v1/health` returned `200`, and unauthenticated `/time-entries/active` returned the structured `401` envelope. The pre-existing Spring process on 8080 was identified and deliberately stopped after the isolated API hit Supabase's session-pool client limit.

### M1.4 — Correct dashboard time semantics

**Status:** Verified

**Deliverable**

- Implement validated range and IANA timezone inputs for `/dashboard/stats`.
- Return daily buckets and totals using timestamp-derived seconds.
- Clip sessions to `[start, end)`, split across local days, handle DST, and exclude active sessions from completed totals.
- Update frontend types and charts to the documented contract.

**Acceptance and verification**

- Empty ranges return zero totals and complete empty-state UI.
- Cross-midnight, cross-range, and DST-transition fixtures produce correct buckets.
- No logic uses the server's system timezone or fixed 08:00–16:00 hours.
- Productivity score matches the documented formula.

**Evidence — 2026-08-07 / local workspace**

- Added a dedicated dashboard service with an injected UTC clock and ownership-scoped overlap query. Service fixtures cover empty defaults, range clipping, cross-midnight splitting, active-session exclusion, productivity rounding, and New York 23-hour/25-hour DST days.
- Added MockMvc coverage for authenticated query binding, the UTC-instant/local-date response contract, and stable missing-parameter errors. The API requires an IANA `timeZone`; optional paired `start`/`end` dates are end-exclusive and capped at 366 days.
- Replaced minute/hour timeline DTOs with `range`, `totalSeconds`, complete daily buckets, full recent-session DTOs, and `productivityScore`; the frontend API test proves timezone/date query encoding.
- Rebuilt the dashboard with seconds-based cards, Work/Rot daily bars, responsive distribution/recent-session panels, and distinct loading, empty, error, retry, and populated states. Vitest component tests cover those states and the accessible daily-data table.
- The final clean package passed with 32 backend tests; frontend lint, typecheck, 11 tests, and production build passed. Searches found no dashboard `ZoneId.systemDefault`, fixed-hour, legacy timeline, or minute-total logic.
- Manual chart inspection used a temporary non-production fixture route that was removed immediately afterward. `agent-browser` screenshots at 1440×1000 and 390×844 confirmed daily bars, seconds-derived labels, Tangerine Studio colors/type, and responsive stacking (`/tmp/rotrack-m14-dashboard-desktop.png`, `/tmp/rotrack-m14-dashboard-mobile.png`). Authenticated live-data browser proof remains part of M2.3, not this deterministic chart check.

### M1.5 — Establish the MVP automated test suite

**Status:** Verified

**Deliverable**

- Backend service, security/controller, repository, and migration tests for M1.1–M1.4.
- Frontend API-client, tracker, and dashboard tests using Vitest/React Testing Library.
- Playwright skeleton for the authenticated critical path, with external-auth setup documented.

**Acceptance and verification**

- Backend and frontend suites fail if ownership, one-active-session, idempotency, or dashboard bucketing regresses.
- Tests do not rely on tracked build artifacts or production credentials.
- Exact commands are ready for CI.

**Evidence — 2026-08-07 / local workspace**

- Tracked coverage includes six frontend test files (11 Vitest tests) and 15 backend test classes. Added executable PostgreSQL migration/repository coverage, production-filter signed-JWT failures, real-service ownership boundaries, readiness/TLS/CORS configuration tests, and a Spring constructor-wiring regression test. Under Temurin Java 21, `mvn clean test` and `mvn package` passed with 64 tests, including four expected default skips for the explicitly opt-in PostgreSQL classes.
- Added tracked Playwright configuration and four serialized external-auth scenarios for Work, Rot, browser-context close/reopen restoration, exact dashboard deltas, and two-user Work/Rot read/stop/aggregate isolation. Storage states must resolve to regular files outside the repository; traces/video are disabled and required-auth mode fails fast.
- Fresh `npm ci`, `npm audit --audit-level=high`, lint, typecheck, 11 Vitest tests, production build, and `npm run e2e -- --list` passed; Playwright listed four Chromium tests. A transitive `nanoid` advisory was resolved by updating only its lockfile resolution to 3.3.18. The opt-in migrated-PostgreSQL suite passed 4/4 with no skips. Exact credential-free and opt-in commands are documented in `frontend/e2e/README.md` and `backend/src/test/README.md`.
- Authenticated Playwright execution is now recorded under M2.3: two external storage states produced 4/4 passing Chromium tests. Real Supabase JWT/Data API evidence remains an M2.1–M2.3 operational boundary, not an M1.5 infrastructure claim.

## 5. Milestone 2 — Secure Local Supabase Integration

**Goal:** Prove the hardened MVP against a real development project.
**Dependencies:** Milestone 1
**Gate:** Signup, API ownership, RLS boundary, tracker, and dashboard pass with two users.

### M2.1 — Apply and verify the development migration

**Status:** Verified

- Apply reviewed migrations to the Supabase development project.
- Verify the signup trigger creates `public.users`.
- Verify Data API RLS separately: each user can access only owned rows.
- Verify the Spring application role has only required table DML grants plus the documented RLS-bypass behavior, and that Spring authorization is enforced by ownership-scoped application queries.

**Current state — 2026-08-07 / configured development PostgreSQL**

- Rollback-only verification re-attested the migrated catalog, RLS enabled on both application tables, all seven named policies, the enabled signup trigger/security-definer function, and profile creation for two fixture auth rows.
- A read-only redacted role audit first found the original backend identity overprivileged. A dedicated `rotrack_runtime` role was then created and re-audited: non-superuser, `BYPASSRLS`, required time-entry DML, no delete, no schema creation, and no role/database/replication privileges. The backend now starts with that role. See `database/verification/2026-08-07-application-role-audit.md` for the original finding and the local validation notes for the corrected runtime result.
- The recorded direct Supabase Data API matrix returned User A's 18 owned rows only, returned an empty owned view for User B, returned zero rows for both foreign filters, and rejected forged inserts as both users with `403`. The dedicated `rotrack_runtime` audit also passed.
- A fresh signup through the real UI returned `200` and reached the confirmation page. Redacted Supabase management SQL confirmed matching `auth.users` and `public.users` rows; the disposable row was removed afterward. The confirmation email itself was not opened because no inbox access was available.

**Remaining evidence:** None for the database/API technical gate. The later fresh confirmed-user first-sign-in acceptance evidence is recorded under M2.3; it is separate from migration, trigger, RLS, and ownership verification.

### M2.2 — Make local startup deterministic

**Status:** Verified

- Configure redacted frontend/backend environment templates, CORS origins, JWT issuer/JWKS/audience, and TLS JDBC settings.
- Start backend and frontend using the runbook.
- Confirm liveness independently from database readiness.

**Current state — 2026-08-07 / local workspace**

- Added validated exact CORS origins, explicit issuer/JWKS/audience documentation, bounded Hikari settings, `sslmode=verify-full` plus explicit provider-CA-path startup enforcement, and a loopback-only plaintext exception for the `local` profile.
- Added independent public liveness and sanitized database readiness. Readiness uses bounded validation, a configurable five-second cache, and single-flight synchronization to limit pool pressure.
- A clean frontend development start returned HTTP 200. After quoting the ignored JDBC URL and using the official CA path plus dedicated `rotrack_runtime` role, `mvn spring-boot:run` started successfully; live `/api/v1/health` returned 200 `{"status":"ok"}` and `/api/v1/readiness` returned 200 `{"status":"ready"}`. See `docs/operations/2026-08-07-local-verification.md`.
- Earlier fail-closed attempts exposed two shell/configuration issues: an unquoted `&` prevented `DATABASE_URL` from being exported, and `$HOME` was passed literally inside the JDBC URL. The checked-in example/checklist now quote JDBC URLs.

**Current verification — 2026-08-08:** A degraded probe-mode startup with an unreachable loopback database returned liveness `200 {"status":"ok"}` and readiness `503 {"status":"not_ready"}` with no dependency details. The normal `ddl-auto=validate` configuration fails fast before HTTP startup when the database is unavailable, preserving schema validation. A final clean Java 21/frontend run passed; health/readiness returned 200 against the configured database. Live CORS preflights passed: configured localhost origin returned exact credentialed CORS headers with HTTP 200; an unconfigured origin returned HTTP 403 without `Access-Control-Allow-Origin`.

**CORS recheck — 2026-08-09 / local workspace:** With the current ignored local configuration, a preflight from `http://localhost:3001` returned HTTP 200 with exact `Access-Control-Allow-Origin: http://localhost:3001`; `http://localhost:3000` returned HTTP 403 with no allow-origin header. The frontend therefore ran on port 3001 for the fresh-user acceptance step.

### M2.3 — Complete the local critical-path test

**Status:** Verified

- User A signs up/signs in, starts Work, navigates/reloads, restores, explicitly stops, and sees dashboard totals.
- User B cannot read, stop, or aggregate User A's session.
- Repeat with Rot and verify it remains private to the owner.
- Confirm closing the app does not stop an active session.

**Technical evidence — 2026-08-08 / local workspace**

- Added a tracked external-auth Playwright harness for Work/Rot start-stop, reload/navigation, browser-context close/reopen restoration, exact dashboard deltas, and User B read/stop/aggregate isolation for both activity types.
- The harness lists four Chromium tests and safely quarantines them when external storage state is absent; `ROTRACK_E2E_REQUIRE_AUTH=1` converts missing User A/User B state into a configuration failure.
- Two existing disposable development users signed in through the real frontend. Their storage states remained outside the repository, and `ROTRACK_E2E_REQUIRE_AUTH=1 npm run e2e` passed all four Chromium tests with zero skips on 2026-08-08. This proves authenticated sign-in, tracker lifecycle, restoration, dashboard deltas, and Spring API ownership isolation for Work and Rot.
- A fresh signup through the real UI returned `200`, reached `/signup/confirmation`, and sent a confirmation email. A redacted management query confirmed the Auth row and signup-trigger profile; the disposable row was removed. Email confirmation was not opened during that earlier technical run because no inbox access was available.
- Direct Data API RLS and the final redacted route evidence are recorded in `docs/operations/2026-08-07-local-verification.md`.

**Acceptance evidence — 2026-08-09 / local workspace:** The product owner/operator reported that the already-confirmed fresh disposable user signed in through `/signin` and reached `/dashboard`. The password and account details were entered privately and are not recorded. The frontend ran at `http://localhost:3001`; an independent preflight recheck returned exact allow-origin for port 3001 and denied port 3000. This attests the missing fresh-user first-sign-in step; the previously recorded Playwright 4/4 remains the automated local/development technical evidence.

## 6. Milestone 3 — CI, Staging, and MVP Release

**Goal:** Ship the personal tracker/dashboard safely.
**Dependencies:** Milestone 2
**Recorded dependency exception:** The product owner previously authorized M3 source preparation while M2.3 was externally blocked on the fresh first-sign-in step. M2.3 is now verified; the earlier exception did not waive any M3 or MVP release gate.

### M3.1 — Pull-request CI

**Status:** Implemented—unverified

- Run frontend install/lint/typecheck/test/build, backend test/package, migration validation, and secret scanning.
- Cache dependencies without caching generated source artifacts into Git.
- Require green checks before merge.

**Evidence — 2026-08-08 / committed M3 foundation (`15997ea`)**

- Added commit-pinned pull-request jobs for frontend install/high audit/lint/typecheck/Vitest/build, Java 21 backend test/package, isolated PostgreSQL apply/verify, workflow/secret/operational guards, and a credential-free container build/inspection. The initial authenticated workflow targeted legacy `disposable-staging-auth`; it is now superseded by trusted-default-branch `repository_dispatch` against logical `nonproduction`, with exact provider/host binding, two project identities, and strict storage-state validation. Required reviewer/branch restrictions remain external configuration.
- Local CI equivalents passed: frontend `npm ci`, `npm audit --audit-level=high`, lint, typecheck, 19 Vitest tests, build, and four-test Playwright listing; backend `mvn clean test` and `mvn package` each discovered 65 tests with 61 passes, four expected default integration skips, and zero failures/errors.
- Against a disposable PostgreSQL 17.6 container, migration `apply` passed 1/1, ordered migration application passed, and migrated `verify`/repository coverage passed 4/4. Actionlint, the workflow pin/policy guard, operational safeguard suites, and Gitleaks over 50 commits plus the prospective candidate tree passed.
- Deliberate local negative fixtures rejected a forbidden `.env`/certificate path, mutable reusable-workflow reference, privileged trigger, artifact upload, unresolved staging sentinel, malformed staging identities, skipped Playwright result, and release target mismatch.
- Missing required evidence: a hosted green PR URL, a deliberately failing required GitHub check that blocks merge, configured branch protection, and protected-environment reviewer/branch restrictions. Keep this task unverified until those external controls are observed.

### M3.2 — Containerize and deploy non-production

**Status:** In progress

- Build a platform-neutral non-root Spring Boot OCI-compatible image with liveness/readiness checks; verify registry manifest media type, architecture, and immutable digest before deployment.
- Deploy the frontend to Vercel Preview in the one Vercel project and the API to Azure Container Apps Consumption in managed environment `rotrack-nonproduction-env` inside `rotrack-nonproduction`, with app `rotrack-api-nonproduction`, using the shared non-production Supabase project for approved authenticated E2E.
- Keep production separate in `rotrack-prod`, Vercel Production, managed environment `rotrack-production-env` inside `rotrack-production`, and app `rotrack-api-production`.
- Configure secrets, TLS/CA injection, restricted CORS, structured logs, connection limits, scale-to-zero cold-start handling, budget/credit-expiry notifications, Supabase Free pause/resume ownership, encrypted off-site logical exports, retention, and restore rehearsal or explicit product-owner risk acceptance.

**Evidence — 2026-08-08 / committed M3 foundation (`15997ea`)**

- Historical evidence: added a digest-pinned multi-stage Java 21 image, fixed UID/GID `10001:10001`, read-only-root/ECS-era contract, Docker liveness and ALB database-readiness probes, SIGTERM shutdown, injected provider-CA materialization, immutable image references, AWS base inputs, and staging-only render/validation/checklist/evidence files. These remain checked-in legacy/unselected artifacts and were not converted to Azure.
- A no-cache image build from the clean committed revision passed inspection. Tag `localhost/rotrack-api:15997ea` produced local image ID `1dc95ecdc5519ccfd62ee10efb4561793906c7bf7069d550ed35b1b4cc549481`, local content digest `sha256:c5b24a5bc2603e8ac57355fc267d31b2aac6f9b4a758fe39049c8452da8592ea`, and OCI revision `15997ea40e7bbf8e4841ca58e0c4da81ddd77bfb`; none is a registry digest or release artifact.
- A disposable local TLS PostgreSQL 17.6 service accepted ordered migrations; the separate `rotrack_runtime` audit returned all true for identity, memberships, RLS bypass, relation/sequence/database/routine boundaries. The non-root backend container returned `200 {"status":"ok"}` liveness and `200 {"status":"ready"}` readiness, then stopped on SIGTERM with exit 143 rather than forced kill.
- Historical local validation accepted distinct project identities, distinct staging/production AWS accounts, staging-prefixed ECS names, separate task/execution roles, exact HTTPS origins, six staging secret ARNs, an immutable image digest, and a rollout-surge-aware connection budget. Those checks describe the unselected AWS artifacts, not the approved architecture.
- Missing required evidence: an authorized non-production Azure Container App deployment, registry digest/media-type readback, ACA hardening controls, service-version-to-digest deployment binding, Vercel Preview deployment plus same-commit environment-specific production-build provenance contract, public non-production URLs, official shared-project CA provenance, live managed-identity/CORS/health/browser evidence, ten-trial cold-start observations and production minimum-replica decision, budget/credit-expiry alert routing, and a redacted completed checklist. No remote deployment is claimed.

### M3.3 — Release safeguards

**Status:** In progress

- Run the critical Playwright flow and documented manual health smoke against staging.
- Document database-first migration order, application rollback, and incident contacts.
- Add API/frontend error monitoring and alerts for health, latency, error rate, and connection exhaustion.

**Evidence — 2026-08-08 / committed M3 foundation (`15997ea`)**

- Added migration-first rollout/application rollback contracts, migration rollback limits, staging-only smoke and rollback-rehearsal scripts, alert thresholds/windows/owners, structured-log allowlist/redaction rules, frontend/API monitoring separation/retention, and incident-response roles/escalation.
- Safe local checks passed Bash syntax, release static policy, API-target/inventory isolation, secret/path handling, exact Playwright result parsing, and fail-closed rollback approval. Staging smoke and rollback rehearsal were deliberately not executed because integrated staging does not exist.
- Production promotion is explicitly stopped until rate limiting, structured logging/redaction, dashboards/alerts/routing, named incident staffing, hosted staging smoke, and rollback rehearsal are implemented and observed.

**Current safeguard continuation — 2026-08-08 / committed and pushed as `40d4376`**

- Commit `40d4376` added a synchronized, bounded, process-local fixed-window limiter shared by each authenticated user's start/stop mutations. Stable `429 RATE_LIMITED`, `Retry-After`, recovery, key bounds, forwarding-header spoof resistance, cross-user isolation, route-alternation resistance, and concurrent capacity are tested. This is defense in depth, not a fleet-wide or authentication-adjacent edge control.
- Commit `40d4376` added one allowlisted request-completion JSON event with a generated 128-bit request ID, normalized route, status/class, latency, stable error code, and sanitized unexpected-exception category. Full-chain tests prove 401, 429, and 500 capture without bearer, cookie, query, resource UUID, or private exception message; the implementation omits request/response bodies, while body-sentinel capture evidence remains part of the open staging redaction checks.
- Commit `40d4376` leaves structured request logging disabled for local development and fails startup when enabled without staging/production metadata and a non-placeholder immutable release ID. Staging templates require it enabled, bind the release ID to the backend image digest, and reject missing, disabled, mutable, or mismatched values.
- Java 21 `mvn clean package` discovers 88 tests: 84 pass and four opt-in PostgreSQL tests skip as expected, with zero failures/errors. Staging synthetic validation, container contract checks, Bash/JSON validation, Markdown links, and `git diff --check` pass.
- Missing required evidence: a trusted fleet-wide/authentication-adjacent edge limiter and failure-mode tests; staging collector ingestion and second-layer redaction sentinel; dashboard/alert identifiers and routing test; measured threshold tuning; incident contacts; retention/access proof; staging smoke 4/4; exact candidate/prior rollback rehearsal; and an observation window.

**MVP release gate — Not met**

The gate requires all of the following:

- Milestones 0–3 are fully **Verified**; currently M3.1–M3.3 remain open.
- No open critical/high security or data-integrity defect.
- Explicit stop and session restoration behavior match UI copy and documentation.
- Hosted CI/protection, authorized immutable non-production deployment, fleet-wide/authentication-adjacent rate limiting, observed logging/telemetry/alerts, non-production smoke, and exact application rollback rehearsal pass with redacted evidence.
- Production promotion uses the exact passing backend digest; because `NEXT_PUBLIC_*` values are embedded at build time, the frontend is a separately recorded Vercel Production build from the same reviewed source commit and must pass production-safe verification.

## 7. Milestone 4 — History, Timezone, and Privacy Preferences

**Goal:** Deliver prerequisites for logs and social features as complete vertical slices.
**Dependencies:** MVP release

### M4.1 — Time-entry history and manual corrections

**Status:** Not started

- Add owned list/create/update/delete APIs with cursor pagination and deterministic reverse-chronological ordering.
- Reject overlapping completed entries and edits that conflict with an active entry; enforce completed-range overlap protection at the database boundary as well as in user-facing validation.
- Add accessible history/editor UI with validation and confirmation for deletion.
- Cover ownership, overlap, boundaries, empty state, and error recovery.

### M4.2 — Profile and preferences

**Status:** Not started

- Add `user_preferences`, owned API, and `/settings` UI.
- Validate IANA timezone and optional daily Work goal.
- Add `share_study_summary` and `share_active_study_status`, both `false` by default.
- Changing timezone affects future calendar rendering without rewriting stored UTC instants.

### M4.3 — OpenAPI and typed client generation policy

**Status:** Not started

- Publish the implemented API contract and stable error codes.
- Generate frontend DTO types from OpenAPI while keeping the authenticated native-`fetch` wrapper hand-written.
- Add contract-drift validation to CI.

**Milestone gate:** History, overlap protection, timezone preferences, and private-default sharing flags are **Verified**.

## 8. Milestone 5 — WYSIWYG Notes and Daily Study Logs

**Goal:** Add private study context beside the timer and a trustworthy daily record.
**Dependencies:** Milestone 4

### M5.1 — Notes data and API

**Status:** Not started

- Add owned `notes` migration/API with optional time-entry link, Tiptap JSON, derived plain text, size limits, and optimistic version.
- Use `ON DELETE SET NULL` for a deleted linked session so the user's note survives.
- Enforce at the database boundary that a note can link only to a session owned by the same user.
- Validate supported nodes/marks and safe link protocols; never accept arbitrary executable HTML.
- Test ownership, missing link, deleted session, version conflict, malformed document, and size limit.

### M5.2 — Timer-side WYSIWYG editor

**Status:** Not started

- Add a Tiptap StarterKit client component beside the timer with the formatting set defined in `arch.plan.md`.
- Allow standalone notes and optional attachment to the active/completed session.
- Autosave after about 750 ms and show saving/saved/conflict/offline-error states without losing local edits.
- Add keyboard navigation, accessible toolbar labels, responsive layout, and reload restoration tests.

### M5.3 — Generated daily logs and reflection

**Status:** Not started

- Add one owned reflection per local date and generate totals/timeline/session-note references from time entries.
- Build daily and calendar views; generated statistics are read-only.
- Reuse the safe rich-text document contract for reflections.
- Test empty days, cross-midnight/DST sessions, timezone changes, version conflicts, and privacy.

**Milestone gate:** Notes and reflections survive reloads, generated totals match authoritative entries, and cross-user/private-content tests pass.

## 9. Milestone 6 — Friends, Privacy, and Active Study Presence

**Goal:** Let users connect and see opted-in study progress without exposing sensitive behavior.
**Dependencies:** Milestone 4; may run after or independently from Milestone 5

### M6.1 — Friendship lifecycle and blocking

**Status:** Not started

- Add canonical mutual friendship/request storage with `PENDING` and `ACCEPTED` states plus a separate directional user-block table.
- Add unique case-normalized public handles and rate-limited handle search with a three-character minimum; never search or expose email addresses.
- Implement search/request/list/accept/decline/cancel/remove/block/unblock APIs and UI.
- Reject self, duplicate, and reversed duplicate requests.
- Blocking deletes friendship/direct-invitation state, prevents new direct requests, and suppresses pairwise activity visibility.
- Test both sides of every transition, authorization, enumeration resistance, and concurrent requests.

### M6.2 — Privacy-safe study summaries

**Status:** Not started

- Expose Work totals and study-day streak only when `share_study_summary` is enabled; do not expose goal progress until the goals feature defines its sharing contract.
- Build explicit projection DTOs; never serialize time-entry, note, reflection, or persistence entities directly.
- Ensure Rot totals, raw sessions, exact timestamps, note titles/content, and reflections never appear in responses or logs.
- Add allow/deny matrix tests for friend state, sharing preference, block state, and unrelated users.

### M6.3 — Active-study presence

**Status:** Not started

- Derive boolean `studying` only from an active `WORK` entry and `share_active_study_status=true`.
- Poll every 30 seconds only while the social screen is visible; stop on hide/unmount and refresh immediately after a local start/stop.
- Do not expose Rot activity, session labels, start timestamps, or attached note data.
- Test polling cleanup, stale UI recovery, opt-out, friendship removal, and block behavior.

**Milestone gate:** The privacy matrix passes and captured social payloads contain no forbidden fields.

## 10. Milestone 7 — Private Study Groups

**Goal:** Let friends form private groups and view permitted study summaries/presence.
**Dependencies:** Milestone 6

### M7.1 — Group, invitation, and membership model

**Status:** Not started

- Add private groups, invitation state, memberships, and `OWNER | ADMIN | MEMBER` roles.
- Limit initial invitations to accepted friends.
- Require ownership transfer before owner departure; enforce role transitions server-side.
- Unfriending and blocking retain third-party group membership/ownership. Blocking suppresses direct interaction and pairwise activity/presence while preserving the minimum membership/role metadata required for administration.
- Test invitation races, duplicate membership, last-owner protection, removal, leaving, and block behavior.

### M7.2 — Group management UI

**Status:** Not started

- Build group list/detail/create/edit/invite/member-management flows with role-aware controls.
- Provide explicit empty, pending, unauthorized, removed, archived, and error states.
- Verify keyboard navigation, responsive behavior, and stale-membership refresh.

### M7.3 — Group summaries and presence

**Status:** Not started

- Show opted-in member Work summaries, boolean active-study presence, and privacy-safe aggregate Work totals/streaks.
- Never include member Rot, raw sessions, timestamps, notes, or reflections.
- Poll presence at the same 30-second cadence and pause while hidden.
- Test group role plus privacy preference combinations and payload field allowlists.

**Milestone gate:** Membership authorization, role transitions, blocking, and privacy-safe aggregate tests are **Verified**.

## 11. Independent Future Epics

These items do not block the timer MVP, notes/logs, or first social release. Each requires discovery and its own architecture/API/data/privacy/test plan before implementation.

- Goal CRUD and goal-progress UX beyond the simple daily Work preference
- Group challenges and competitions
- Notifications and milestone reminders
- Advanced weekly/monthly analytics and pattern insights
- CSV/PDF export
- Collaborative or shared notes
- Search across private notes and logs
- Offline note editing and conflict reconciliation beyond optimistic concurrency

## 12. Evidence Template

Append evidence beneath the completed task; do not create unsupported global checkmarks.

```md
**Evidence — YYYY-MM-DD / environment**

- Commands: `...`
- Result: exit code, test count, or relevant status/body summary
- Manual scenario: routes/actions and observed outcome
- Security/privacy cases: identities and allow/deny result, with tokens redacted
- Known limitations/blockers: owner and unblock condition
- Commit/PR/deployment reference: ...
```

## 13. Next Action

The fresh confirmed-user first-sign-in step is complete. The local-only credential object was purged; the non-production runtime password was rotated, the old password was rejected, and local/Azure redeployment health/readiness passed. The product owner explicitly retained all three historical personal-domain author addresses. The next decision is the solo-maintainer protection policy before a deliberate public-or-paid GitHub transition followed immediately by branch/environment restrictions, required checks, CodeQL/secret scanning, and exact non-production host variables. No second human reviewer is currently available, so CODEOWNERS/environment approval remains an explicit release blocker unless an independently reviewed solo-maintainer equivalent is approved. Only after the available protections are observed may disposable authenticated-E2E secrets be configured and the trusted `repository_dispatch` run 4/4. In parallel, observe structured-log ingestion/redaction and alert routing, run ten cold-start trials, verify budget/credit-expiry and Supabase pause ownership, establish encrypted logical-export retention plus restore rehearsal or explicit data-loss risk acceptance, and rehearse exact-digest rollback. Do not start M4 or promote to production until the M3 MVP release gate is **Verified**.
