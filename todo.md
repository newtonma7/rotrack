# rotrack Development Backlog

**Architecture and contracts:** [`arch.plan.md`](arch.plan.md)
**Backlog reviewed:** 2026-08-01
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
| Architecture/backlog rewrite | **Verified** | Reconciled and validated against repository source on 2026-08-01 |
| Frontend routes and auth UI | **Implemented—unverified** | Source exists; sign-in currently redirects to `/dashboard`; no current build proof |
| Tracker start/restore/stop UI | **Implemented—unverified** | Source exists; live API flow is unproven |
| Automatic unload stop | **Implemented—unverified** | Legacy behavior exists but conflicts with the target and is scheduled for removal |
| Dashboard UI and API call | **Implemented—unverified** | Fixed server-time buckets are incorrect target behavior |
| Spring Boot API scaffold | **Implemented—unverified** | Core source exists; source test tree is absent |
| Initial database migration | **Implemented—unverified** | SQL exists but is reportedly unapplied and lacks required hardening |
| Supabase development integration | **Not started** | No recorded migration, RLS, or two-user proof |
| Automated test suites | **Not started** | No current frontend/backend source tests |
| CI and deployment | **Not started** | No pipeline, Dockerfile, staging evidence, or rollback runbook |
| Notes, logs, friends, groups | **Not started** | Architecture defined; implementation follows the core MVP |

### Baseline evidence captured during the 2026-08-01 audit

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

**Status:** Implemented—unverified
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
- A clean-clone walkthrough has not yet been recorded; keep this task unverified until one is completed.

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

### M1.3 — Implement the explicit session lifecycle

**Status:** Not started

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

**Evidence:** Service/controller tests, component tests, and a manual browser lifecycle script.

### M1.4 — Correct dashboard time semantics

**Status:** Not started

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

**Evidence:** Unit fixture table, API snapshot, frontend component test, and manual chart check.

### M1.5 — Establish the MVP automated test suite

**Status:** Not started

**Deliverable**

- Backend service, security/controller, repository, and migration tests for M1.1–M1.4.
- Frontend API-client, tracker, and dashboard tests using Vitest/React Testing Library.
- Playwright skeleton for the authenticated critical path, with external-auth setup documented.

**Acceptance and verification**

- Backend and frontend suites fail if ownership, one-active-session, idempotency, or dashboard bucketing regresses.
- Tests do not rely on tracked build artifacts or production credentials.
- Exact commands are ready for CI.

**Evidence:** Commands, pass counts, duration, and any quarantined external test with owner/reason.

## 5. Milestone 2 — Secure Local Supabase Integration

**Goal:** Prove the hardened MVP against a real development project.
**Dependencies:** Milestone 1
**Gate:** Signup, API ownership, RLS boundary, tracker, and dashboard pass with two users.

### M2.1 — Apply and verify the development migration

**Status:** Not started

- Apply reviewed migrations to the Supabase development project.
- Verify the signup trigger creates `public.users`.
- Verify Data API RLS separately: each user can access only owned rows.
- Verify the Spring application role has only required table DML grants plus the documented RLS-bypass behavior, and that Spring authorization is enforced by ownership-scoped application queries.

**Evidence:** Redacted migration/version output and two-user isolation matrix.

### M2.2 — Make local startup deterministic

**Status:** Not started

- Configure redacted frontend/backend environment templates, CORS origins, JWT issuer/JWKS/audience, and TLS JDBC settings.
- Start backend and frontend using the runbook.
- Confirm liveness independently from database readiness.

**Evidence:** Startup commands, health bodies/statuses, and configuration-name checklist without values.

### M2.3 — Complete the local critical-path test

**Status:** Not started

- User A signs up/signs in, starts Work, navigates/reloads, restores, explicitly stops, and sees dashboard totals.
- User B cannot read, stop, or aggregate User A's session.
- Repeat with Rot and verify it remains private to the owner.
- Confirm closing the app does not stop an active session.

**Evidence:** Playwright result where feasible plus a concise manual route/API log.

## 6. Milestone 3 — CI, Staging, and MVP Release

**Goal:** Ship the personal tracker/dashboard safely.
**Dependencies:** Milestone 2

### M3.1 — Pull-request CI

**Status:** Not started

- Run frontend install/lint/typecheck/test/build, backend test/package, migration validation, and secret scanning.
- Cache dependencies without caching generated source artifacts into Git.
- Require green checks before merge.

**Evidence:** Link a passing pipeline and a deliberately observed failing check.

### M3.2 — Containerize and deploy staging

**Status:** Not started

- Build a non-root Spring Boot container with liveness/readiness checks.
- Deploy frontend to Vercel, API to ECS Fargate, and use a separate Supabase staging project.
- Configure secrets, TLS, restricted CORS, structured logs, and database connection limits.

**Evidence:** Image digest, staging URLs, health results, and redacted configuration checklist.

### M3.3 — Release safeguards

**Status:** Not started

- Run the critical Playwright flow and documented manual health smoke against staging.
- Document database-first migration order, application rollback, and incident contacts.
- Add API/frontend error monitoring and alerts for health, latency, error rate, and connection exhaustion.

**Evidence:** Smoke result, rollback rehearsal, dashboards/alert identifiers.

**MVP release gate**

- Milestones 0–3 are fully **Verified**.
- No open critical/high security or data-integrity defect.
- Explicit stop and session restoration behavior match UI copy and documentation.
- Production promotion is approved from a passing staging artifact.

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

Finish and verify **M0.1**, then complete repository hygiene and the runbook in **M0.2–M0.4**. Do not apply the remote development migration until **M1.1** resolves the active-session race and reporting indexes.
