# M5 contracts

**Status:** M5.1 Verified locally; M5.2 and M5.3 not started. The governing contract source is [`arch.plan.md`](../../arch.plan.md), the domain glossary is [`CONTEXT.md`](../../CONTEXT.md), and M4 conventions are in [`m4-contracts.md`](m4-contracts.md). If this document conflicts with the architecture, update the architecture decision before implementation.

M5 is defined as one domain model delivered sequentially: M5.1 Notes data/API, M5.2 rich-text editors and Notes workspace, then M5.3 Daily Logs and Reflections. Source/local work is authorized; M5 hosted migration or rollout is not.

Every API route is under `/api/v1`, derives ownership from the validated JWT `sub`, uses explicit handwritten DTOs through the existing native `fetch` client, and returns the existing error envelope. Request bodies never contain `userId`, generated totals, derived text, or server timestamps. Notes and Reflections are available only through Spring; Supabase browser roles receive no table privileges.

## Domain language

- A **Session Label** is the existing nullable `time_entries.notes` plain text under M4's 280-character contract. M5 does not change that field's semantics or validation. It is not rich text.
- A **Note** is an independent private rich-text document optionally attached to one owned active or completed Time Entry.
- A **Note Draft** exists only in the current editor until its first meaningful save.
- A **Note Summary** is the list projection without rich-text JSON.
- A **Reflection** is private rich-text writing identified by owner and its original local-date label.
- A **Daily Log** is generated on read from authoritative completed Time Entries, attached Note references, and an optional persisted Reflection. No Daily Log row or mutable statistics snapshot exists.
- A **Daily Log Summary** is the bounded calendar projection without labels, sessions, Note references, or rich content.

## Shared rich-text document

`RichTextDocument` is stored in `content_json` and returned as an object, never a JSON string:

```ts
type RichTextDocument = {
  schemaVersion: 1;
  document: {
    type: "doc";
    content: RichTextNode[]; // may be empty
  };
};
```

Version 1 accepts only:

- nodes: `doc`, `paragraph`, `heading` (`level` 2 or 3), `bulletList`, `orderedList` (optional positive-integer `start`), `listItem`, `blockquote`, and `text`;
- marks: `bold`, `italic`, and `link`;
- link attribute: `href` only.

The executable tree grammar is:

- `doc` has zero or more block children and no attributes or marks.
- `paragraph` and `heading` have zero or more `text` children. `heading` has exactly `{ level: 2 | 3 }`; `paragraph` has no attributes.
- `bulletList` and `orderedList` have one or more `listItem` children. `bulletList` has no attributes. `orderedList` accepts only positive-integer `start`, normalizes an omitted value to 1, and canonically emits `{ start }`.
- `listItem` has a `paragraph` first, followed by zero or more paragraph, list, or blockquote children; it has no attributes or marks.
- `blockquote` has one or more paragraph, list, or blockquote children and no attributes or marks.
- `text` has a nonempty `text` string, no children or attributes, and zero or more distinct marks. Marks are permitted only on text and canonically ordered `bold`, `italic`, then `link`; `bold` and `italic` have no attributes and `link` has exactly `{ href }`.

Unknown envelope keys, nodes, marks, attributes, duplicate marks, invalid child relationships, empty text nodes, H1, code, images, tables, mentions, embeds, and raw/executable HTML are invalid. Undo and redo are editor history, not persisted nodes. The document may contain at most 10,000 nodes and nesting depth 32, counting `doc` as depth 1 and including every node in the node count.

Links accept only absolute `http` or `https` URLs with a host, or `mailto` URLs with a valid address form. Protocol-relative and fragment-only URLs plus `javascript:`, `data:`, `vbscript:`, and every other protocol are invalid. The UI adds/changes/removes links through an accessible dialog, does not auto-link text, and opens links only by deliberate action in a new tab with `noopener` and `noreferrer`.

Validation produces one canonical value before hashing, storage, derivation, or sizing. Envelope keys serialize as `schemaVersion`, `document`; node keys as `type`, permitted `attrs`, permitted `marks`, permitted `content`, then `text`; mark keys as `type`, then permitted `attrs`. Empty optional arrays/objects are omitted, except root `document.content` is always emitted. Content order is preserved, marks use the fixed order above, ordered-list start is explicit, strings retain their decoded Unicode values, and no other normalization is performed.

The compact server serialization of that canonical value must be at most 262,144 UTF-8 bytes. The server derives `contentText` by concatenating text nodes in document order with newlines between leaf text blocks. Meaningful-content checks trim this value. Previews collapse whitespace, trim, and take at most 160 Unicode code points without splitting a code point. Clients never submit `contentText`.

`content_json` uses PostgreSQL `json` (not `jsonb`) because M5 does not query inside documents and must preserve Spring's compact canonical bytes. PostgreSQL checks object presence, `octet_length(content_json::text) <= 262144`, schema version, title limits, positive versions, and owner/link integrity. Spring owns full tree/link validation and derived-text generation; there is no second full JSON validator in PostgreSQL.

## Notes — M5.1

### Routes

- `GET /notes?cursor=&attachment=&timeEntryId=` → `200 { data: { notes: NoteSummary[], nextCursor: string | null } }`.
- `POST /notes` with `Idempotency-Key: <UUID>` → `201 { data: Note }`; an identical replay may return `200 { data: Note }`.
- `GET /notes/{id}` → `200 { data: Note }`.
- `PUT /notes/{id}` → `200 { data: Note }`.
- `DELETE /notes/{id}?expectedVersion=` → `204` with no body.

`attachment` is optional and is `ATTACHED` or `STANDALONE`; omission means all Notes. `timeEntryId` is an optional exact owned attachment filter and implies attached. Filters combine with `AND`. A non-owned `timeEntryId` returns an empty page without revealing the Time Entry.

Lists contain at most 20 summaries ordered by `(updatedAt DESC, id DESC)`. Cursors are opaque unpadded base64url per-user ordering anchors. Clients pass them unchanged, deduplicate live-page overlap, and refresh page one after mutations. Empty pages return `nextCursor: null`; blank, padded, malformed, or noncanonical cursors return `INVALID_CURSOR`. Full-text search is deferred.

```ts
type NoteSummary = {
  id: string;
  title: string | null;
  preview: string;
  timeEntryId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
};

type Note = NoteSummary & {
  contentJson: RichTextDocument;
  contentText: string;
  contentSchemaVersion: 1;
};

type CreateNoteRequest = {
  title: string | null;
  contentJson: RichTextDocument;
  timeEntryId: string | null;
};

type UpdateNoteRequest = CreateNoteRequest & {
  expectedVersion: number;
};
```

Titles are trimmed before validation/storage, blank becomes `null`, and the maximum is 120 Unicode code points. Creation requires either a nonblank normalized title or nonblank derived text; otherwise `VALIDATION_ERROR` places a safe cross-field message on both `title` and `contentJson`. Existing Notes may be cleared to a null title and empty document without implicit deletion.

A Note may attach to any owned active or completed Time Entry, move to another owned entry, or detach with `timeEntryId: null`. Missing, deleted, or non-owned attachments return `NOT_FOUND`; a Draft captured against an entry that merely stopped remains valid. Deleting a Time Entry sets attached Notes' `timeEntryId` to null.

M5 additively extends each entry returned by `GET /time-entries/history` with `attachedNoteCount: number`; other Time Entry responses are unchanged. The count is the number of currently attached owned Notes and is zero or positive. History deletion confirmation uses it to state that those Notes survive and detach.

Creation assigns version 1. Title, content, and attachment are one optimistic resource: every successful update increments one version. Update and delete require the current positive `expectedVersion`; stale operations make no change and return `RICH_TEXT_VERSION_CONFLICT`. A conflicting delete is never forced. Repeating an already successful delete with the same owner, Note ID, and expected version returns `204`; reads still return `404`.

### Creation idempotency

Each Note Draft generates one stable UUID supplied in `Idempotency-Key`. Per-owner metadata stores an HMAC-SHA-256 fingerprint of normalized title, validated compact document, and attachment, plus the created Note identity. The HMAC uses a dedicated runtime-injected secret, never appears in logs/responses, and startup fails when M5 writes are enabled without it. An identical replay returns the current Note without mutation even if it has since changed. Reusing the key with a different canonical payload returns `409 IDEMPOTENCY_CONFLICT` without exposing the first payload.

After Note deletion, a content-free tombstone retains owner, creation key, keyed HMAC fingerprint, Note ID, and deleted version for the owner's lifetime. The fingerprint is non-reversible without the separately injected runtime secret and stores no document, title, attachment, or response snapshot. An identical delayed create replay returns `410 NOTE_DELETED` and cannot recreate the Note; a changed replay remains `IDEMPOTENCY_CONFLICT`, and a repeated matching delete remains idempotent. User deletion cascades this metadata.

## Editors and Notes workspace — M5.2

The protected routes are `/notes` and `/notes/{id}`. Desktop uses a Note Summary list beside the selected editor; mobile navigates from the list to the stable Note URL. A null title displays the preview, then `untitled note` only when both are empty. The tracker uses a two-column timer/editor layout on desktop and stacks timer first on mobile. Long documents grow with the page rather than creating a nested editor scroll region.

The shared client-only Tiptap editor primitive uses only `@tiptap/core`, `@tiptap/react`, `@tiptap/starter-kit`, and `@tiptap/extension-link`. It exposes paragraph, H2, H3, bold, italic, bullet list, ordered list, blockquote, link, undo, and redo with keyboard operation, accessible labels, visible focus, and announced save states. Note and Reflection API/save orchestration remain separate.

A new editor remains a current-tab Note Draft until trimmed title or derived text is nonblank. On the first meaningful edit it captures the active Time Entry, or standalone status when none is active, and never silently retargets. The first successful save creates one Note; later typing continues that Note until the user explicitly chooses New Note or opens another Note.

Autosave begins after approximately 750 ms of inactivity. One request may be in flight per document; newer edits coalesce into the next save. Note/Reflection saves use a separate bounded process-local budget initially set to 60 requests per authenticated user per minute so they cannot consume timer/history mutation capacity. The canonical states are `Draft`, `Saving`, `Saved`, `Waiting`, `Offline`, and `Conflict`.

On `RATE_LIMITED`, preserve edits, show `Waiting`, and retry once after `Retry-After` unless newer state or conflict supersedes it. M5.1 extends CORS to allow the `Idempotency-Key` request header and expose `Retry-After`; exact allowed-origin behavior remains unchanged and focused preflight/response tests cover both headers. After a network failure, preserve current-tab edits and retry only after another edit/debounce or explicit Retry; browser online events may inform copy but are not authoritative. M5 has no durable offline queue, reload restoration, background sync, or cross-tab coordination.

Before switching Notes or in-app navigation, flush pending meaningful edits. On failure, default to staying and offer explicit leave-with-loss confirmation. A native `beforeunload` warning exists only while meaningful unsaved edits exist; no unload/keepalive save request is sent.

A stale save stops autosave and preserves local edits. Reloading the server version requires destructive confirmation. Copy is always an explicit user action, writes supported rich content plus plain-text fallback, and announces success/failure. A conflicted Note may be saved as a new Note with a new creation key and the same attachment; success switches the editor to that new Note. There is no automatic merge, last-writer-wins, force overwrite, or automatic clipboard write.

## Daily Logs and Reflections — M5.3

### Routes

- `GET /daily-logs?start=&end=&timeZone=` → `200 { data: { range, days: DailyLogSummary[] } }`.
- `GET /daily-logs/{localDate}?timeZone=` → `200 { data: DailyLog }`.
- `PUT /daily-logs/{localDate}/reflection?timeZone=` → `200 { data: Reflection }`; first creation (`expectedVersion: 0`) requires `Idempotency-Key: <UUID>`, while updates omit it.

`start`, `end`, and path `localDate` values use canonical `YYYY-MM-DD` with years `0001` through `9999`; signed years, year zero, and noncanonical forms are invalid. The supported date-value domain includes both `0001-01-01` and `9999-12-31`, includes future dates, and applies to Reflection identity. `start` and `end` are paired and define a complete half-open `[start, end)` range of 1–42 days. Every date appears even when empty.

An explicit valid `timeZone` query wins. Otherwise the API uses the saved IANA timezone; when neither exists, the browser must supply one. It determines half-open local-day UTC boundaries and DST-length days but never rewrites stored instants.

```ts
type DailyLogRange = {
  start: string;    // inclusive canonical local date
  end: string;      // exclusive canonical local date
  timeZone: string; // effective IANA timezone
};

type DailyLogSummaryPage = {
  range: DailyLogRange;
  days: DailyLogSummary[]; // one item for every date in [start, end)
};

type DailyLogSummary = {
  localDate: string;
  workSeconds: number;
  rotSeconds: number;
  sessionCount: number;
  noteCount: number;
  hasReflectionContent: boolean;
};

type DailyLog = {
  localDate: string;
  timeZone: string;
  generated: {
    workSeconds: number;
    rotSeconds: number;
    sessions: DailySession[];
    noteReferences: NoteReference[];
  };
  reflection: Reflection | null;
};

type DailySession = {
  id: string;
  activityType: "WORK" | "ROT";
  segmentStartTime: string;
  segmentEndTime: string;
  durationSeconds: number;
  sessionLabel: string | null;
};

type NoteReference = {
  id: string;
  title: string | null;
  preview: string;
  timeEntryId: string;
};

type Reflection = {
  contentJson: RichTextDocument;
  contentText: string;
  contentSchemaVersion: 1;
  version: number;
  timeZoneAtCreation: string;
  updatedAt: string;
};

type UpdateReflectionRequest = {
  contentJson: RichTextDocument;
  expectedVersion: number; // 0 only for first creation
};
```

Generated data uses owned completed Time Entries only. Sessions are clipped to the requested local day; segment duration is `segmentEndTime - segmentStartTime`. A spanning Time Entry contributes once to `sessionCount` and each distinct attached Note contributes once to `noteCount` on every intersected date. Note references are deduplicated per day and ordered by segment start, then Note `updatedAt` and ID. Daily Log Summaries never contain Session Labels, Note previews, exact timestamps, or Reflection content.

A Reflection is uniquely identified by `(userId, localDate)`, not projection timezone. First creation requires nonblank derived text and records immutable `timeZoneAtCreation`; valid future dates are allowed. Later saves may replace content with the valid empty document, preserve the row, and increment the version. `hasReflectionContent` reflects trimmed derived text, not row existence. The same Reflection appears for its date label under any requested projection timezone, while generated facts re-bucket.

`expectedVersion: 0` creates the first Reflection and requires one stable client-generated UUID in `Idempotency-Key`; missing or invalid keys return `VALIDATION_ERROR`. Per-owner replay metadata stores that key and the runtime-keyed HMAC fingerprint over local date, creation timezone, and canonical document. An identical ambiguous retry with the same key returns the current Reflection without mutation, even after later edits; reusing the key for a different canonical request returns `IDEMPOTENCY_CONFLICT`. Existing writes require the current version and omit `Idempotency-Key`. Reflection conflicts offer explicit rich/plain copy or destructive-confirmed reload, never a second Reflection, merge, or force overwrite.

Frontend routes are `/daily-logs` for the calendar and `/daily-logs/{localDate}` for stable detail. Desktop shows calendar and selected detail together; mobile navigates into detail. Generated fields are read-only.

## Stable errors and privacy

Errors use `{ error: { code, message, fieldErrors }, timestamp, path }`. Authentication failures are `401`; ownership misses use `404`; deleted creation replays use `410`; conflicts use `409`; rate limits use `429` with `Retry-After`.

- `VALIDATION_ERROR` — invalid request, title, document/schema/depth/node-count/link/size, date, timezone, range, or expected version. Safe reasons are attached to fields; private content is never echoed.
- `MALFORMED_JSON` — invalid JSON.
- `INVALID_CURSOR` — blank, padded, malformed, or noncanonical cursor.
- `NOT_FOUND` — Note or requested attachment is absent or not owned.
- `RICH_TEXT_VERSION_CONFLICT` — stale Note or Reflection `expectedVersion`; response contains no current private document.
- `IDEMPOTENCY_CONFLICT` — a creation key names a different canonical request; response contains neither payload.
- `NOTE_DELETED` — an identical create replay names a Note that was explicitly deleted; response contains no private content.
- `RATE_LIMITED` — the applicable authenticated mutation/save budget is exhausted.

The client blocks autosave locally when the serialized document exceeds 256 KiB, preserves edits, and resumes after it fits; the server remains authoritative for every limit.

Never place bearer tokens, credentials, Note/Reflection JSON, titles, derived text, Session Labels, link URLs, request bodies, resource IDs, or private exception data in logs, errors, telemetry, or non-owner/social/group projections. Owner-scoped DTOs return the private fields explicitly defined above, including resource/attachment IDs, rich documents, timestamps, and validated links. Structured logs use only normalized route templates, correlation ID, status, latency, and stable error category.

## Migration, compatibility, and gate

M5.1 adds additive Notes and content-free idempotency/tombstone storage. M5.3 later adds additive Reflections and their creation replay metadata. Existing `time_entries.notes` Session Labels are never converted, truncated, or deleted. Migrations precede dependent application versions; application rollback leaves additive tables in place and does not imply database rollback. Future document schema versions require a new architecture decision and explicit reader/writer migration or dual-read plan.

Source/local verification requires focused migration/repository/service/controller/client/component tests plus authenticated real-browser acceptance with two users covering ownership, creation replay, autosave/reload, attachment/move/detach, Time Entry deletion warning, cross-tab conflicts, Note conflict copy, Reflection identity, DST/cross-midnight segmentation, privacy payloads/logs, responsive layouts, keyboard operation, and accessible status/error announcements. M5 hosted rollout requires separate product-owner authorization and applicable immutable-build, migration, smoke, privacy, and rollback evidence.
