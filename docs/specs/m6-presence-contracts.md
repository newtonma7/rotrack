# M6.3 active-study presence

**Status:** Implemented with isolated local acceptance. Independent Standards review found no issues; Spec review's HTTP validation coverage finding is fixed, with targeted closure recorded in `todo.md`. The subsequent 2026-10-22 operator request authorizes source commit/push/PR publication with M6.2. Merge, hosted migrations, deployment, and fresh Supabase acceptance remain outside this work unit.
**Baseline:** `02684d437ae6ec425fe833eaffad5f89c72e17e8`.

## API and privacy

`GET /api/v1/social/presence/{username}` returns `200 { data: { studying: boolean } }` for a distinct accepted Friend who enables `share_active_study_status`, with no block in either direction. The validated JWT subject selects the viewer. Username validation uses the existing shared validator. The viewer's sharing preferences and the peer's summary preference do not affect eligibility.

The sole field is `studying`. It is true exactly when the eligible peer has a Time Entry with `activity_type='WORK'` and `end_time IS NULL`. Otherwise it is false. False does not describe whether the peer is online, idle, tracking Rot, or has completed activity. No identity, activity type, label, timestamp, duration, Note, Reflection, or summary value is returned. No self-presence endpoint is added.

Self, missing peer, missing preferences, opt-out, either pending direction, removed friendship, unrelated viewer, and either block direction return the identical `404 NOT_FOUND` error with message `Study presence not found`. Authentication and invalid-username errors retain the existing contracts. All successes and errors have `Cache-Control: no-store` and sanitized route labels. No presence values or private activity enter logs.

One parameterized SQL statement authorizes and derives the boolean from the same snapshot. A subsequent snapshot observes committed revocation; an already authorized response cannot be recalled. The service uses the established five-second read-only transaction timeout. The SQL returns at most one boolean, never an entity or private text. No migration or browser-table grant change is needed. Existing runtime profile/time-entry reads and migration 004/008 grants suffice.

## Friends UI and lifecycle

Only accepted Friend rows on the Friends view load presence automatically. Each row uses the typed Spring API client. Pending, discovery, and blocked rows never poll presence. A compact text indicator distinguishes `Studying now`, `Not studying`, loading, generic unavailable, paused, and error with keyboard-accessible retry. False is not labelled offline or Rot. The page explains the opt-in meaning and 30-second freshness limit.

While the document is visible, fetch immediately and every 30 seconds. On hide or unmount, cancel timers, abort in-flight reads, invalidate late replies, and clear displayed booleans. On return, clear stale state and fetch immediately. Account, peer, or relationship-list replacement creates a fresh lifecycle. Each refresh clears old values; errors and unavailable responses never retain a previous true indicator. Overlapping reads use only the latest response. No persistent cache or localStorage is used.

Successful local start/stop API operations send a content-free same-origin BroadcastChannel invalidation. Visible Friends views in the same or other tabs refresh immediately; hidden views wait until visible. Browsers without BroadcastChannel use a same-tab event and retain normal polling for other tabs. Failed mutations and session restoration do not announce a change. This notification never starts or stops a session and carries no activity or user data. A remote friend's changes become visible on the next poll.

## Implementation shape

Follow the existing Study Summary boundary with `StudyPresenceDTO`, repository, service, and controller. Keep authorization inside the projection query rather than trusting the loaded friend list. Use a keyed `StudyPresenceControl` with one effect owning the timer, abort controller, visibility listener, invalidation subscription, and response generation. Keep the existing summary reveal independent of polling. Start/stop notifications live in the typed API methods so all callers share the behavior without changing timer lifecycle.

## Verification

Add failing behavior tests before implementing each boundary. Cover the SQL privacy matrix using runtime-equivalent grants against disposable PostgreSQL, active Work versus completed Work/active Rot/no session, preference independence, and revocation on later reads. Controller tests assert JWT ownership, the exact one-field DTO, no-store on success and errors, sanitized paths, and validation. Frontend tests cover cadence, hide/show cleanup, unmount, account/peer replacement, overlapping replies, unavailable/error recovery, abort, and successful versus failed start/stop notifications. Browser acceptance uses real local Spring/PostgreSQL and disposable signed JWTs, checks exact payloads and privacy revocation, and drives responsive keyboard controls. Local JWTs do not prove Supabase issuance. Run relevant full suites, typecheck/build, and an independent review before recording source/local acceptance. Keep the dependency audit and merge block unchanged.
