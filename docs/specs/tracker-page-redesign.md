# Tracker page redesign implementation spec

**Status:** Verified
**Design provenance:** the approved throwaway prototype was removed before publication; this specification preserves the accepted production design.
**Route:** `/tracker`
**Primary design:** `journal + notes`

## 1. Goal

Replace the current card-based tracker layout with a quiet, notes-first journal surface:

- The timer is a compact centered control band between the application navbar and a horizontal divider.
- The rest of the page is an open writing canvas rather than a timer card.
- A slim Mindspace sidebar lets the user switch between private Notes.
- Notes autosave through the existing Spring API and rich-text contracts.
- Existing explicit timer behavior remains unchanged: only `WORK` and `ROT`, one active session, server-authoritative timestamps, and explicit stop.

The HTML prototype is visual/state exploration only. Production code must use the existing authenticated API client, React components, Tiptap editor, and API contracts.

## 2. Contract decisions before implementation

### 2.1 Editable date is not currently a Note field

The prototype displays an editable `08 / 26` value. The current `Note` contract has no date field. A Note is an independent document optionally attached to a Time Entry; its dates come from `createdAt`, `updatedAt`, or its attached entry.

**Recommended implementation:** render the effective local date as a non-persisted journal label for this tracker slice. Do not send it in `POST /notes` or `PUT /notes/{id}`. A true editable date selector belongs to the Daily Logs/Reflections slice and should use `/daily-logs/{localDate}` after that contract is implemented.

If product requires arbitrary persisted Note dates, stop and create an architecture decision for a new Note date model before implementation. Do not add an uncontracted `date` field to the request DTO.

### 2.2 Checklist blocks amend RichTextDocument v1 before rollout

Rich-text schema version 1 has not been migrated or deployed to a hosted environment. The product-owner decision for this redesign therefore amends version 1 before rollout instead of introducing version 2 compatibility machinery.

Version 1 additionally supports `taskList` and `taskItem`. A `taskList` contains one or more `taskItem` children. A `taskItem` has exactly `{ checked: boolean }`, begins with a paragraph, and may continue with supported paragraphs, lists, nested task lists, or blockquotes. Task lists may appear at the document root and in the supported nested block positions. Empty task lists, missing or non-boolean checked state, unknown fields, and invalid child relationships are rejected.

Checklist state persists only through the validated JSON envelope. Do not store arbitrary HTML or silently convert checked items to bullet lists. Existing version-1 documents remain valid without rewriting.

## 3. Visual and interaction specification

### 3.1 Page canvas

- Background uses the existing warm rotrack canvas tokens with transparent panels; no white card surrounding the timer.
- Keep the small, low-contrast gradient orb as ambient decoration.
- Remove the large outlined decorative background circle.
- Use the existing Figtree family. The timer is Figtree with tabular numerals, not Digital-7.
- Use orange sparingly for brand identity and state emphasis; no orange accent words in the journal labels.
- Preserve the floating application navbar and existing navigation destinations.

### 3.2 Timer band

Order, top to bottom:

1. Figtree timer readout, centered.
2. A compact pill containing `work` and `rot` buttons.
3. `stop` appears only when a session is active.
4. Small mode/status copy: `choose a mode when you’re ready` while idle, or the active mode plus start state while running.
5. Horizontal divider below the band.

Rules:

- Only `WORK` and `ROT` are valid controls.
- Starting the already active type is idempotent at the UI level; starting the other type is disabled while a session exists.
- Timer display is derived from the server `startTime` and updates locally every second.
- The timer never stops on reload, navigation, tab changes, minimize, hide, or browser close.
- Stop remains explicit and uses the active entry ID.
- Loading and error states must not shift the layout or remove the existing session from view.

### 3.3 Mindspace sidebar

Desktop layout is a two-column journal surface:

- Left: narrow Mindspace sidebar.
- Right: Note canvas.
- One vertical hairline separates the columns.

Sidebar contents:

- `mindspace` label.
- New Note button.
- Owned Note summaries, ordered by API `updatedAt DESC, id DESC`.
- Selected Note has a quiet neutral background; no orange text treatment.
- Each item shows title, or preview/`untitled note` fallback, plus lightweight metadata where available.
- A `+ new note` action duplicates the top new-note affordance for discoverability.

Behavior:

- Selecting a Note flushes pending edits before switching.
- If flushing fails, stay on the current Note and offer an explicit leave-and-lose-edits confirmation.
- Selecting a Note loads the full resource with `GET /notes/{id}`.
- A new Note starts as a current-tab Draft and is not created until meaningful title or content exists.
- The first meaningful edit captures the active Time Entry if one exists; otherwise the Note is standalone.
- The editor never silently retargets an existing Note when the active timer changes.

### 3.4 Note canvas

- No large `today’s notes` heading above the editor.
- Keep the small local-save status at the upper right: `all changes local to this prototype` becomes the real autosave status in production (`Draft`, `Saving`, `Saved`, `Waiting`, `Offline`, `Conflict`).
- Keep the journal date label only as the effective local-date display until the date contract is resolved; it must not be sent as Note data.
- Large title input with the existing 120-code-point title limit.
- Open writing area with no nested scroll container.
- Empty state copy should invite an agenda, loose thought, plan, or reflection without becoming instructional clutter.
- Editor toolbar is quiet by default and becomes visible on focus/hover.
- Attachment controls remain available but secondary. The user must be able to see or change whether the Note is standalone or attached to a Time Entry.

### 3.5 Blocks

The production editor must use the current validated `RichTextDocument` grammar:

- Paragraph/text
- H2
- H3
- Bullet list
- Ordered list
- Blockquote
- Bold, italic, and safe links
- Checklist/task list with persisted checked state and supported nesting
- Undo/redo

The UI may present these as a compact `add block` affordance or toolbar, but it must serialize through Tiptap to the existing JSON envelope and run `validateRichTextDocument` before save. Checklist controls use the official Tiptap Task List and Task Item extensions; raw checklist HTML is never persisted.

### 3.6 Responsive behavior

Desktop:

- Timer band remains centered.
- Sidebar and editor appear side by side.
- Long documents grow the page; there is no nested editor scroll region.

Mobile:

- Timer remains centered above the divider.
- Sidebar becomes a full-width note picker above the selected Note.
- Vertical separator becomes a horizontal divider.
- Editor remains below the picker and uses the full available width.
- Navbar links may collapse according to the existing header behavior.
- The bottom prototype switcher is not part of production.

## 4. TypeScript component plan

### 4.1 Route composition

Keep `frontend/src/app/tracker/page.tsx` as the route shell and preserve the existing protected layout.

Replace the current `ActiveTracker` presentation with a composed production surface, for example:

```text
frontend/src/components/tracker/TrackerWorkspace.tsx
├── TrackerTimerBand
├── TrackerNotesSidebar
└── TrackerNoteCanvas
    └── NoteEditor / RichTextEditor
```

Use one writer for the tracker subsystem. Do not copy the HTML prototype into production and do not create a second API client.

### 4.2 `TrackerWorkspace`

Responsibilities:

- Coordinate the active session hook and selected Note state.
- Load the Note summary page on mount.
- Track the selected Note ID and current Draft mode.
- Flush the editor before selection, navigation, or creating a new Note.
- Keep the sidebar list synchronized after Note creation/update/delete.
- Pass `activeEntry?.id ?? null` into a new Draft editor.

It should not own timer API details or duplicate Note autosave logic.

### 4.3 `TrackerTimerBand`

Use `useTimeTracking()` as the only timer orchestration boundary.

Required props/state:

```ts
type TrackerTimerBandProps = {
  activeEntry: TimeEntry | null;
  elapsed: string;
  loading: boolean;
  error: string | null;
  onStart: (activityType: ActivityType) => Promise<void>;
  onStop: () => Promise<void>;
};
```

Use existing `ActivityType = "WORK" | "ROT"` and do not add idle/stagnant buckets.

### 4.4 `TrackerNotesSidebar`

Use `NoteSummary[]` from `getNotes()` rather than storing duplicate rich documents.

Required behavior:

- Loading, empty, and error/retry states.
- Selected state with `aria-current` or `aria-pressed` semantics.
- Keyboard navigation through normal buttons.
- New Note action.
- No private content in URL query parameters or logs.

### 4.5 `TrackerNoteCanvas`

Reuse `NoteEditor` and `useNoteAutosave` rather than reimplementing save state. Refactor `NoteEditor` only to support a visual variant/slot layout; do not fork its optimistic locking, idempotency, conflict, size, or navigation-guard behavior.

The editor must continue to expose:

- `flush()` for pending navigation.
- `retry()` for Offline/Waiting states.
- `reloadServerVersion()` for conflicts.
- `saveAsNew()` for explicit conflict recovery.
- Attachment selection.
- Copy, delete, link safety, and accessible save announcements.

If `NoteEditor` becomes too layout-specific, extract a presentational `NoteEditorChrome` while keeping `useNoteAutosave` as the state owner.

## 5. Backend/API wiring

No new timer endpoints are required.

### 5.1 Timer calls

| UI action | Existing client function | API | Required behavior |
|---|---|---|---|
| Initial restore | `getActiveSession()` | `GET /api/v1/time-entries/active` | Restore the server-owned active entry or show idle |
| Start Work/Rot | `startSession(activityType)` | `POST /api/v1/time-entries/start` | Server timestamps start; `409 ACTIVE_SESSION_EXISTS` preserves current state |
| Explicit stop | `stopSession(activeEntry.id)` | `PUT /api/v1/time-entries/{id}/stop` | Idempotent; clear only after successful response |

Do not send duration, `userId`, or client timestamps. Preserve the existing bearer-token flow through `frontend/src/lib/api.ts`.

### 5.2 Notes calls

| UI action | Existing client function | API |
|---|---|---|
| Load sidebar | `getNotes()` | `GET /api/v1/notes` |
| Open Note | `getNote(id)` | `GET /api/v1/notes/{id}` |
| First meaningful save | `createNote(request, idempotencyKey)` | `POST /api/v1/notes` |
| Autosave existing Note | `updateNote(id, request)` | `PUT /api/v1/notes/{id}` |
| Delete Note | `deleteNote(id, expectedVersion)` | `DELETE /api/v1/notes/{id}?expectedVersion=` |
| Load attachment options | `getHistory()` plus `getActiveSession()` | Existing owned history/active endpoints |

Request rules:

- `CreateNoteRequest` contains only `title`, validated `contentJson`, and `timeEntryId`.
- `UpdateNoteRequest` additionally contains the current `expectedVersion`.
- The browser supplies one stable UUID `Idempotency-Key` per Draft creation.
- The browser never submits `contentText`, duration, timestamps, or user identity.
- Ownership and attachment validity remain Spring service responsibilities.

### 5.3 Error behavior

Use `ApiRequestError` and the existing structured error parser.

- `401`: auth/session failure; preserve safe local Draft and let the protected route/auth boundary handle reauthentication.
- `404`: Note or attachment is no longer available; show a safe recovery state.
- `409 RICH_TEXT_VERSION_CONFLICT`: stop autosave, preserve local edits, offer Reload server version or Save as new Note.
- `409 IDEMPOTENCY_CONFLICT`: keep the Draft and show a non-destructive error.
- `410 NOTE_DELETED`: do not recreate automatically; offer Save as new Note.
- `429 RATE_LIMITED`: honor `Retry-After`, preserve edits, and retry through the existing bounded autosave behavior.
- Network failure: show Offline, preserve current-tab edits, and never silently discard.

## 6. State and lifecycle requirements

### Initial load

1. Render the shell immediately with timer/notes loading states.
2. Restore the active session.
3. Load the first Note summary page.
4. Do not auto-create a Note.
5. Keep timer restoration independent from Notes failure; a Notes outage must not stop or hide an active timer.

### New Note

1. Flush the selected editor.
2. If flush fails, stay unless the user confirms loss.
3. Mount a new Draft editor.
4. Use active entry attachment only when the first meaningful edit occurs, per `useNoteAutosave`.
5. Create once after the 750 ms autosave debounce.
6. Replace the Draft with the returned Note identity/version without losing keystrokes entered during the request.

### Selection and navigation

- Flush before sidebar selection, application navigation, browser back, and route changes.
- Do not use unload/keepalive saves.
- Preserve the existing native `beforeunload` warning only while meaningful unsaved content exists.
- Timer lifecycle remains independent of Note navigation.

## 7. Accessibility requirements

- Timer uses `role="timer"` and `aria-live="polite"`; do not announce every tick assertively.
- Work/Rot/Stop controls are real buttons with visible focus states and disabled states.
- Sidebar Note buttons expose selected state and readable title/preview.
- Editor and title/date controls have accessible labels.
- Autosave status uses `role="status"`; errors use `role="alert"` without echoing private content.
- Keyboard users can reach all controls, select Notes, operate formatting controls, and recover conflicts.
- Color is never the sole indicator of selected activity, selected Note, save state, or errors.
- Respect reduced-motion preferences and avoid animated timer layout shifts.

## 8. Testing and acceptance

### Frontend unit/component tests

Add or update tests for:

- Tracker renders the centered timer band and Notes workspace.
- Existing active session restores on mount.
- Work and Rot start calls use the correct activity type.
- Stop uses the active entry ID and remains explicit.
- A timer failure does not erase an active restored session.
- Notes summary loading, empty, error, retry, and selected states.
- Selecting a Note loads the owned full Note.
- New Draft first save uses one stable idempotency key.
- Active-entry capture happens on first meaningful Draft edit only.
- Autosave coalesces edits and preserves keystrokes during an in-flight request.
- Version conflict preserves local content and exposes recovery actions.
- Rate limit/offline states preserve content and retry correctly.
- Attachment move/detach uses the existing request shape.
- Navigation flush prevents accidental loss.
- Date label is not included in Note API payloads unless a separately approved date contract exists.
- Checklist checked state survives autosave/reload and malformed checklist JSON is rejected.
- No production code contains the prototype switcher or raw checklist HTML persistence.

### Browser acceptance

Using the existing authenticated Playwright harness:

1. Sign in as User A and open `/tracker`.
2. Verify the journal layout at desktop and mobile widths.
3. Start Work; verify the timer updates and the Note Draft attaches only after meaningful input.
4. Navigate/reload/close and reopen; verify the active session remains active.
5. Add and check a checklist item, reload, and verify its text and checked state return.
6. Select another Note, edit it, reload, and verify the saved content returns.
7. Create a standalone Note with no active session.
8. Stop the session explicitly and verify the server-owned end time.
9. Sign in as User B and verify User A's Notes, active session, attachments, and checklist content are not visible or mutable.
10. Force a stale Note version and verify conflict recovery without data loss.
11. Verify no Note content, token, or private payload is visible in logs or error copy.

### Definition of done

- `npm run lint`
- `npm run typecheck`
- `npm test`
- `npm run build`
- Relevant backend `mvn clean test package`
- Focused migration/API tests remain green; no migration is added for the visual redesign alone.
- Authenticated browser acceptance passes at desktop and mobile widths.
- `git diff --check` passes.
- The prototype remains clearly throwaway and is not imported by production code.

## 9. Implementation order

1. Amend the pre-rollout version-1 rich-text contract and validators for persisted checklist nodes; keep the journal date display-only.
2. Add focused failing backend/frontend tests for checklist validation, canonicalization, autosave, reload, copy, and rejection cases.
3. Add focused failing frontend tests for the new tracker composition and Note selection flow.
4. Extract/rework `ActiveTracker` into `TrackerWorkspace` and `TrackerTimerBand` without changing timer API behavior.
5. Add the Notes sidebar and selection state using existing `getNotes`/`getNote` calls.
6. Add the journal canvas layout as a visual variant of the existing `NoteEditor`.
7. Wire autosave, attachment, conflict, offline, delete, checklist, and navigation-guard behavior through existing hooks/API functions.
8. Add/adjust responsive and accessibility tests.
9. Run focused tests, full frontend checks, backend checks, disposable migration verification, and authenticated browser acceptance.
10. Remove prototype-only concepts that are not covered by approved production contracts.
