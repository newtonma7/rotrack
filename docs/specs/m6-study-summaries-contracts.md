# M6.2 privacy-safe study summaries

**Status:** Implemented with isolated local acceptance and independent review at `135682d`. Fresh Supabase authentication acceptance and hosted rollout remain unverified. No publishing or hosted rollout authorization.
**Baseline:** `e8ba3900f7ed016f840d4cfe4ced4a084ed9920e`.
**Scope:** Opted-in accepted Friends only. No presence, groups, goals, public profiles, or editor changes.

## Privacy and authorization

`GET /api/v1/social/summaries/{username}` uses the validated JWT subject as viewer. Canonicalize/validate the username by the existing friendship rules. Do not accept a viewer ID, reporting range, or timezone from the caller.

Return `200 { data: { workSeconds: number, studyDayStreak: number, streakCapped: boolean } }` only when the distinct viewer and peer have an ACCEPTED friendship, neither has blocked the other, and the peer's `share_study_summary` is true. The viewer's own sharing preference is irrelevant. Absent preferences fail closed. Self, missing username, unrelated peer, either pending direction, removed friendship, either block direction, and opted-out peer all return the identical existing `404 NOT_FOUND` shape. Unauthenticated requests return the existing `401` shape; syntactically invalid usernames return the existing validation error.

Authorization and activity projection must use one database statement/snapshot, not an earlier cached friend list. Concurrent revocation affects subsequent snapshots; an already executing authorized read is not recalled. Do not cache summary responses: apply `Cache-Control: no-store` on successes and errors for this route. Existing general application logs must retain only their sanitized allowlist, never summary values, bodies, query results, or private activity data.

The three DTO fields are an exhaustive allowlist. Do not return identities, timezone, dates, exact timestamps, daily buckets, raw sessions, Rot, active-session information, labels, Notes, Reflections, goals, or persistence entities. Zero Work is a valid available summary, never an unavailable marker. Never invoke/serialize the personal dashboard for this route.

## Calculation

- Capture one server `now` per request. Only completed WORK entries contribute; active entries and ROT are excluded at query level.
- Use the peer's saved IANA timezone, with UTC for an unset timezone. The viewer/browser timezone cannot redefine the peer's study day. Do not disclose the timezone itself.
- `workSeconds` is completed Work elapsed seconds in the seven local calendar days including today, from local midnight six days ago through `now` (exclusive). Clip overlapping intervals at report boundaries; exclude future completed activity. Split at actual local midnights, preserving DST's 23/25-hour days and fractional-offset zones. Ignore stored duration columns.
- For each entry/day segment, count whole elapsed seconds (floor, then sum). A **Study Day** has at least one such completed Work second. Subsecond-only segments do not qualify. A cross-midnight entry can qualify both days.
- **Study-day Streak** counts consecutive Study Days ending today if today qualifies; otherwise it may end yesterday. If neither qualifies, return zero. Stop at the first nonqualifying day.
- Inspect a fixed 366-local-day window including today, never all history. `studyDayStreak` counts only qualifying days in that window. `streakCapped` is true exactly when the streak reaches the oldest inspected day without a gap; the true historical streak may be longer. The UI displays a `+` when capped (including a 365-day streak ending yesterday when today's window day is empty). No arbitrary range or unbounded backwards walk is permitted.
- Manual corrections and saved-timezone changes reproject future reads; summaries are not persisted snapshots.

Use an explicit minimal SQL projection/aggregate, returning at most 366 internal day totals, not Time Entry entities or private text. Bound database execution using the established database/transaction timeout conventions. No migration or new browser table privilege is needed.

## Friends UI

Use the typed Spring client on `/friends`. Each accepted Friend offers an explicit summary reveal/refresh control; do not fetch discovery/request/block results or introduce polling. Show “work · last 7 days” and a clearly labelled study-day streak; explain that days follow the friend's saved timezone (UTC if unset) and only completed Work counts. Show capped values with `+` and explain the bounded lookback. Include loading, zero, unavailable, error, and explicit retry states. Use generic “Summary unavailable”, not guessed opt-out/block reasons. Clear visible summary data while reloading and after relationship changes; suppress responses from stale relationships, accounts, unmounted components, or older requests. No localStorage persistence. Preserve existing lifecycle controls and responsive/keyboard behavior. Existing Settings remains the sharing opt-in control.

## Verification gates

Write failing behavior tests before implementation. Backend gates cover authenticated route/DTO/cache headers, the full friendship/preference/block matrix, zero versus unavailable, one snapshot authorization, clipping at both boundaries and now, seven-day windows, today's/yesterday's streak anchoring and gaps/caps, midnight crossing, DST/fractional zones, future/active/ROT exclusion, and minimal query projection. Exercise the SQL against disposable PostgreSQL with runtime-equivalent grants, not only mocks. Frontend gates cover typed API shape/errors, reveal/loading/zero/capped/unavailable/retry states, stale-response suppression and lifecycle refresh. Browser acceptance uses the real local Spring/PostgreSQL path with disposable users, captures exact allowlisted payload fields, verifies opt-in/revoke/block and responsive keyboard controls. Local signed JWTs do not prove Supabase issuance. Run focused tests, relevant full suites/typecheck/build, then fresh independent Standards and Spec review against the baseline. Retain the existing dependency audit/merge block; do not publish, merge, deploy, or apply hosted migrations.
