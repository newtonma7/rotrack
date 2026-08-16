# Parallel Development Guide

**Purpose:** Coordinate work after the integrated Milestone 4 preferences/history implementation without weakening security, privacy, migration, or API contracts.

**Source of truth:** Read [`AGENTS.md`](AGENTS.md), [`arch.plan.md`](arch.plan.md), [`todo.md`](todo.md), and [`frontend/DESIGN.md`](frontend/DESIGN.md). This guide defines execution order and file ownership only.

## Current checkpoint — 2026-08-12

- M3-P is **Verified** for the 0–20-user pre-user scope. Full M3 production readiness remains open; see `todo.md` for the accepted and unresolved operational risks.
- M4 preferences, completed history/manual corrections, and handwritten typed contracts are integrated and locally accepted.
- The shared hosted database was explicitly remediated for legacy usernames, then migrations 003–005 applied in order; schema, RLS, runtime grants, and overlap constraints passed verification.
- Authenticated local and hosted acceptance passed settings defaults/persistence/isolation and history create/list/delete/overlap/two-user checks; the broader local run also covers pagination/edit/active-exclusion/mobile behavior.
- M4 is **Verified**. M5.1 is Verified locally and M5.2 is Implemented—unverified. The dated M5.2 shared-hosted rollout is authorized and evidence-gated; unresolved M3 risks and broad production-readiness claims remain open.

## Coordination rules

1. One writer owns each worktree and file set; reviewers remain read-only.
2. Check status/diff and read the relevant architecture/backlog sections before editing.
3. Preserve unrelated work. Do not use destructive cleanup or broad checkout commands in a shared checkout.
4. Behavior changes require a failing test first and the relevant full checks afterward.
5. Never record credentials, tokens, certificates, storage-state JSON, private notes, or populated environment files.
6. Reserve migration numbers and shared API contracts before parallel implementation.
7. Integrate lanes serially when they touch API types, navigation, error handling, migrations, or evidence.
8. Existing shared primitives and handwritten DTOs are the default. Do not add OpenAPI generation unless a later decision replaces the current M4 contract policy.
9. No milestone becomes **Verified** from source existence, skipped external checks, or local evidence relabeled as hosted evidence.

## Immediate stage — M5 source planning after verified M4

### Lane A — Source/PR review

**Owns:** Read-only review findings and narrowly scoped fixes in the subsystem that owns the defect.

- Keep M4 contracts aligned across Java records, TypeScript interfaces, `frontend/src/lib/api.ts`, and [`docs/specs/m4-contracts.md`](docs/specs/m4-contracts.md).
- Preserve ownership scoping, completed-only history, fixed 20-row pagination, opaque cursor pass-through, timestamp-derived duration, and private defaults.
- Run frontend lint/typecheck/Vitest/build, Java 21 clean test/package, migration/staging guards, and `git diff --check` after fixes.

### Lane B — Hosted migration/deployment plan (narrowly authorized by dated override)

**Owns:** Target-data preflight plan, release checklist, and redacted evidence template. It does not mutate hosted resources outside the exact dated override scope.

- Confirm the applicable M4 release checks and exact scope of the dated product-owner override before hosted action; the full M3 gate remains open and its unresolved operational risks remain unverified.
- Inspect migration 005 preflight against the target: `btree_gist` availability, notes over 280 characters, and same-user overlapping completed/active ranges.
- Apply migrations 004/005 database-first with an administrative migration identity.
- Verify `rotrack_runtime` has `SELECT/INSERT/UPDATE/DELETE` on `time_entries`, exactly `SELECT/INSERT/UPDATE` on `user_preferences`, and no schema-management privileges.
- Deploy the reviewed backend/frontend artifacts, then run authenticated settings/history acceptance and rollback-compatible smoke.
- Never auto-truncate notes, delete overlaps, rewrite UTC instants, or infer user-approved preference values.

**Done:** Hosted evidence is recorded in `todo.md`; M4 is Verified. This lane is closed unless a rollback or regression requires reopening it.

### Lane C — M5.2 shared-hosted rollout (authorized 2026-08-16)

**Owns:** migration `006_notes.sql`, reviewed immutable backend/frontend artifacts, stable hosted-only HMAC secret injection, Notes writes, hosted disposable-user acceptance/cleanup, rollback, and redacted evidence.

- Start only after final local verification, independent review, protected CI, and merge.
- Apply migration 006 before the dependent application and retain the additive schema during application rollback.
- Verify exact ACA secretRef/digest/service-version/CORS/readiness, then private Notes ownership, checklist persistence, timer restoration, cleanup, and log omission with disposable users.
- Stop on migration drift, secretRef mismatch, writes disabled, health/readiness failure, ownership/privacy regression, or incomplete cleanup.

## Later feature lanes

M5.3 logs and M6 social work may be designed only after their own architecture/API/privacy gates. The M5.2 authorization does not cover M5.3 or M6.

### M5 notes/logs

- Reserve migrations and rich-text schema/version rules before coding.
- Keep notes/reflections private and validate document nodes, marks, links, and size server-side.
- Reuse M4 saved-timezone semantics for generated daily logs; never duplicate mutable timer totals.

### M6 social/privacy

- Stabilize friendship/blocking storage before summaries or presence.
- Build explicit privacy-safe projections; never serialize persistence entities.
- Sharing flags are opt-in. Never expose Rot, raw sessions, timestamps, notes, or reflections.

## Parent acceptance checklist

- [ ] Changed files stay inside assigned ownership.
- [ ] Success, failure, authorization, concurrency, timezone, and privacy cases are tested.
- [ ] Schema changes pass clean apply and upgrade verification on isolated PostgreSQL.
- [ ] `git diff --check`, candidate secret scanning, and relevant full suites pass.
- [ ] No production/private data or credentials were used.
- [ ] Residual risks and external blockers have owners and unblock conditions.
- [ ] Status/evidence documents distinguish local, hosted, and production proof.
