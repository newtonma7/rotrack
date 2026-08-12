# M4 API contracts

M4 uses handwritten DTOs and the existing authenticated native `fetch` client. Every route is under `/api/v1` and derives ownership from the validated JWT `sub`; request bodies never contain `userId` or duration.

## Preferences

- `GET /preferences` → `200 { data: { timeZone: string | null, dailyWorkGoalMinutes: number | null, shareStudySummary: boolean, shareActiveStudyStatus: boolean } }`
- `PUT /preferences` → the same `200` shape. `timeZone` is `null` or a valid IANA identifier, and `dailyWorkGoalMinutes` is `null` or an integer from 1 through 1440.

## Completed history

- `GET /time-entries/history` → `200 { data: { entries: HistoryEntry[], nextCursor: string | null } }`.
- The API returns only completed owned entries, in `(startTime DESC, id DESC)` order, with at most 20 entries. `nextCursor` is opaque and must be sent back unchanged as `?cursor=...`.
- `POST /time-entries` creates a completed entry; `PUT /time-entries/{id}` edits it; `DELETE /time-entries/{id}` returns `204`.
- `HistoryEntry` is `{ id, activityType, startTime, endTime, durationSeconds, notes }`; `durationSeconds` is derived by the server from timestamps. Inputs contain only `activityType`, `startTime`, `endTime`, and `notes` (maximum 280 characters).
- Ranges are half-open for overlap checks: adjacent entries are valid, overlapping entries are rejected, including overlap with an active session.

## Stable errors

Errors use the shared envelope `{ error: { code, message, fieldErrors }, timestamp, path }`:

- `VALIDATION_ERROR` — malformed input or an invalid range, timezone, activity, goal, or note; field-level messages are in `fieldErrors`.
- `INVALID_CURSOR` — a blank, padded, malformed, or noncanonical history cursor.
- `TIME_ENTRY_OVERLAP` — the requested completed range overlaps an owned entry.
- `ACTIVE_SESSION_EXISTS` — a start races or duplicates an active entry.
- `NOT_FOUND` — the requested entry is not owned or is not completed.
- `429 RATE_LIMITED` — an authenticated mutation limit was exceeded.

Authentication failures remain `401`; ownership misses do not reveal whether another user's resource exists. The API does not expose framework details or stack traces.
