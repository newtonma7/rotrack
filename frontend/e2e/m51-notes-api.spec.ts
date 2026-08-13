import {
  expect,
  request as playwrightRequest,
  test,
  type APIRequestContext,
  type BrowserContext,
  type Page,
} from "@playwright/test";
import { installApiTargetGuard, isExpectedApiUrl } from "./support/api-target";
import { currentSupabaseAuth } from "./support/critical-path";
import { e2eEnvironment } from "./support/environment";

const twoUsersConfigured = Boolean(
  e2eEnvironment.userAStorageState &&
    e2eEnvironment.userBStorageState &&
    e2eEnvironment.expectedApiUrl,
);
const localHosts = new Set(["localhost", "127.0.0.1", "[::1]"]);

type ApiResult = {
  status: number;
  body: unknown;
  headers: Record<string, string>;
};

type ApiEnvelope<T> = { data: T };
type ApiError = { error?: { code?: string } };
type TimeEntry = {
  id: string;
  endTime: string | null;
  attachedNoteCount?: number;
};
type Note = {
  id: string;
  timeEntryId: string | null;
  version: number;
};
type NoteSummary = Note;
type NotePage = { notes: NoteSummary[]; nextCursor: string | null };
type HistoryPage = { entries: Array<TimeEntry & { attachedNoteCount: number }>; nextCursor: string | null };
type RichTextDocument = {
  schemaVersion: 1;
  document: {
    type: "doc";
    content: Array<{
      type: "paragraph";
      content: Array<{
        type: "text";
        text: string;
        marks?: Array<{ type: "link"; attrs: { href: string } }>;
      }>;
    }>;
  };
};
type NotePayload = {
  title: string;
  contentJson: RichTextDocument;
  timeEntryId: string | null;
};

function assertStatus(result: ApiResult, expected: number): void {
  expect(result.status === expected).toBe(true);
}

function assertCode(result: ApiResult, expected: string): void {
  const body = result.body as ApiError;
  expect(body.error?.code === expected).toBe(true);
}

function data<T>(result: ApiResult): T {
  return (result.body as ApiEnvelope<T>).data;
}

function documentFor(value: string): RichTextDocument {
  return {
    schemaVersion: 1,
    document: {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            {
              type: "text",
              text: value,
              marks: [{ type: "link", attrs: { href: `https://example.invalid/${value}` } }],
            },
          ],
        },
      ],
    },
  };
}

function payload(seed: string, timeEntryId: string | null): NotePayload {
  return {
    title: `m51-${seed}`,
    contentJson: documentFor(`m51-content-${seed}`),
    timeEntryId,
  };
}

async function initializeBrowserAuth(page: Page): Promise<void> {
  await page.goto(e2eEnvironment.baseUrl);
  await expect.poll(
    () => page.evaluate(() => {
      const findAccessToken = (value: unknown): string | null => {
        if (!value || typeof value !== "object") return null;
        if ("access_token" in value && typeof value.access_token === "string") {
          return value.access_token;
        }
        for (const child of Object.values(value)) {
          const token = findAccessToken(child);
          if (token) return token;
        }
        return null;
      };

      const decodeExpiry = (token: string): number | null => {
        try {
          const payload = token.split(".")[1];
          if (!payload) return null;
          const normalized = payload.replace(/-/g, "+").replace(/_/g, "/").padEnd(
            Math.ceil(payload.length / 4) * 4,
            "=",
          );
          const claims = JSON.parse(atob(normalized)) as { exp?: unknown };
          return typeof claims.exp === "number" ? claims.exp : null;
        } catch {
          return null;
        }
      };

      for (let index = 0; index < localStorage.length; index += 1) {
        const storageKey = localStorage.key(index);
        if (!storageKey?.endsWith("-auth-token")) continue;
        const raw = localStorage.getItem(storageKey);
        if (!raw) continue;
        try {
          const expiry = decodeExpiry(findAccessToken(JSON.parse(raw) as unknown) ?? "");
          if (expiry !== null && expiry > Math.floor(Date.now() / 1000) + 60) return true;
        } catch {
          // Keep polling while the auth client replaces an expired session.
        }
      }
      return false;
    }),
    { timeout: 15_000, intervals: [100, 250, 500, 1_000] },
  ).toBe(true);
}

async function apiFor(page: Page): Promise<APIRequestContext> {
  const { accessToken } = await currentSupabaseAuth(page);
  return playwrightRequest.newContext({
    baseURL: e2eEnvironment.expectedApiUrl,
    extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` },
  });
}

async function call(
  api: APIRequestContext,
  method: string,
  path: string,
  body?: unknown,
  headers?: Record<string, string>,
): Promise<ApiResult> {
  const apiUrl = new URL(path.replace(/^\/+/, ""), `${e2eEnvironment.expectedApiUrl!}/`).toString();
  const response = await api.fetch(apiUrl, {
    method,
    data: body,
    headers: {
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      ...headers,
    },
  });
  const rawBody = await response.text();
  const responseBody = rawBody && response.headers()["content-type"]?.includes("json")
    ? (JSON.parse(rawBody) as unknown)
    : rawBody || null;
  return { status: response.status(), body: responseBody, headers: response.headers() };
}

async function createNote(api: APIRequestContext, request: NotePayload, key = crypto.randomUUID()): Promise<ApiResult> {
  return call(api, "POST", "/notes", request, { "Idempotency-Key": key });
}

async function startEntry(api: APIRequestContext): Promise<TimeEntry> {
  const result = await call(api, "POST", "/time-entries/start", { activityType: "WORK" });
  assertStatus(result, 201);
  return data<TimeEntry>(result);
}

async function stopEntry(api: APIRequestContext, id: string): Promise<TimeEntry> {
  const result = await call(api, "PUT", `/time-entries/${id}/stop`);
  assertStatus(result, 200);
  return data<TimeEntry>(result);
}

async function clearActive(api: APIRequestContext): Promise<void> {
  const result = await call(api, "GET", "/time-entries/active");
  assertStatus(result, 200);
  const active = data<TimeEntry | null>(result);
  if (active) await stopEntry(api, active.id);
}

async function completedEntry(api: APIRequestContext): Promise<TimeEntry> {
  const entry = await startEntry(api);
  return stopEntry(api, entry.id);
}

async function note(api: APIRequestContext, id: string): Promise<Note> {
  const result = await call(api, "GET", `/notes/${id}`);
  assertStatus(result, 200);
  return data<Note>(result);
}

async function updateNote(api: APIRequestContext, id: string, request: NotePayload, expectedVersion: number): Promise<ApiResult> {
  return call(api, "PUT", `/notes/${id}`, { ...request, expectedVersion });
}

async function listNotes(api: APIRequestContext, query = ""): Promise<NotePage> {
  const result = await call(api, "GET", `/notes${query}`);
  assertStatus(result, 200);
  return data<NotePage>(result);
}

async function history(api: APIRequestContext): Promise<HistoryPage> {
  const result = await call(api, "GET", "/time-entries/history");
  assertStatus(result, 200);
  return data<HistoryPage>(result);
}

function isLocalHttp(url: string): boolean {
  const parsed = new URL(url);
  return parsed.protocol === "http:" && localHosts.has(parsed.hostname);
}

async function cleanupOwnedFixtures(
  api: APIRequestContext,
  noteIds: Set<string>,
  entryIds: Set<string>,
): Promise<string[]> {
  const failures: string[] = [];
  const attempt = async (label: string, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
    } catch {
      failures.push(label);
    }
  };

  await attempt("active session", async () => clearActive(api));
  let noteIndex = 0;
  for (const id of noteIds) {
    noteIndex += 1;
    await attempt(`note cleanup ${noteIndex}`, async () => {
      const current = await call(api, "GET", `/notes/${id}`);
      if (current.status === 404) return;
      assertStatus(current, 200);
      const deleted = await call(api, "DELETE", `/notes/${id}?expectedVersion=${data<Note>(current).version}`);
      expect([204, 404, 410].includes(deleted.status)).toBe(true);
    });
  }
  let entryIndex = 0;
  for (const id of entryIds) {
    entryIndex += 1;
    await attempt(`time entry cleanup ${entryIndex}`, async () => {
      const deleted = await call(api, "DELETE", `/time-entries/${id}`);
      expect([204, 404].includes(deleted.status)).toBe(true);
    });
  }
  return failures;
}

test.describe("M5.1 authenticated Notes API acceptance", () => {
  test.skip(
    !twoUsersConfigured,
    "User A/User B storage states and a local expected API are required; see e2e/README.md.",
  );
  test.describe.configure({ mode: "serial" });

  test("covers the private two-user Notes and attachment matrix", async ({ browser }) => {
    expect(isLocalHttp(e2eEnvironment.baseUrl)).toBe(true);
    expect(isLocalHttp(e2eEnvironment.expectedApiUrl!)).toBe(true);

    let userAContext!: BrowserContext;
    let userAPage!: Page;
    let userAApi!: APIRequestContext;
    let userBContext!: BrowserContext;
    let userBPage!: Page;
    let userBApi!: APIRequestContext;
    const createdNoteIds = new Set<string>();
    const createdNoteIdsB = new Set<string>();
    const createdEntryIds = new Set<string>();
    try {
      userAContext = await browser.newContext({ storageState: e2eEnvironment.userAStorageState! });
      userAPage = await userAContext.newPage();
      await installApiTargetGuard(userAPage, e2eEnvironment.expectedApiUrl);
      // Wait only for the browser auth client to replace an expired external session;
      // Notes acceptance does not depend on unrelated tracker UI state.
      await initializeBrowserAuth(userAPage);
      const authA = await currentSupabaseAuth(userAPage);
      const browserFetch = await userAPage.evaluate(async ({ apiUrl, token }) => {
        const response = await fetch(`${apiUrl}/notes`, {
          mode: "cors",
          headers: {
            Authorization: `Bearer ${token}`,
            "Idempotency-Key": crypto.randomUUID(),
          },
        });
        return {
          status: response.status,
          contentType: response.headers.get("content-type"),
          url: response.url,
        };
      }, { apiUrl: e2eEnvironment.expectedApiUrl!, token: authA.accessToken });
      expect(isExpectedApiUrl(browserFetch.url, e2eEnvironment.expectedApiUrl!)).toBe(true);
      expect(browserFetch.status).toBe(200);
      expect(browserFetch.contentType?.includes("application/json")).toBe(true);
      userAApi = await apiFor(userAPage);
      userBContext = await browser.newContext({ storageState: e2eEnvironment.userBStorageState! });
      userBPage = await userBContext.newPage();
      await installApiTargetGuard(userBPage, e2eEnvironment.expectedApiUrl);
      await initializeBrowserAuth(userBPage);
      userBApi = await apiFor(userBPage);
      const createOwnedNote = async (request: NotePayload, key?: string): Promise<ApiResult> => {
        const result = await createNote(userAApi!, request, key);
        if (result.status === 201) createdNoteIds.add(data<Note>(result).id);
        return result;
      };
      const createOwnedNoteB = async (request: NotePayload, key?: string): Promise<ApiResult> => {
        const result = await createNote(userBApi!, request, key);
        if (result.status === 201) createdNoteIdsB.add(data<Note>(result).id);
        return result;
      };
      const createOwnedCompletedEntry = async (): Promise<TimeEntry> => {
        const result = await completedEntry(userAApi!);
        createdEntryIds.add(result.id);
        return result;
      };

      await clearActive(userAApi!);
      await clearActive(userBApi!);

      const cors = await call(userAApi, "OPTIONS", "/notes", undefined, {
        Origin: new URL(e2eEnvironment.baseUrl).origin,
        "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "authorization,content-type,idempotency-key",
      });
      assertStatus(cors, 200);
      const corsOrigin = cors.headers["access-control-allow-origin"];
      const corsHeaders = cors.headers["access-control-allow-headers"];
      const exposedHeaders = cors.headers["access-control-expose-headers"];
      expect(corsOrigin === new URL(e2eEnvironment.baseUrl).origin).toBe(true);
      expect(corsHeaders?.toLowerCase().includes("idempotency-key")).toBe(true);
      expect(exposedHeaders?.toLowerCase().includes("retry-after")).toBe(true);
      const deniedCors = await call(userAApi, "OPTIONS", "/notes", undefined, {
        Origin: "http://localhost:9",
        "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "authorization,content-type,idempotency-key",
      });
      assertStatus(deniedCors, 403);
      expect(deniedCors.headers["access-control-allow-origin"] === undefined).toBe(true);

      const activeEntry = await startEntry(userAApi);
      createdEntryIds.add(activeEntry.id);
      const activePayload = payload(crypto.randomUUID(), activeEntry.id);
      const activeCreate = await createOwnedNote(activePayload);
      assertStatus(activeCreate, 201);
      const activeNote = data<Note>(activeCreate);
      expect((await note(userAApi, activeNote.id)).timeEntryId === activeEntry.id).toBe(true);

      await stopEntry(userAApi, activeEntry.id);
      const movedEntry = await createOwnedCompletedEntry();
      const firstUpdate = await updateNote(userAApi, activeNote.id, payload(crypto.randomUUID(), activeEntry.id), 1);
      assertStatus(firstUpdate, 200);
      const moved = await updateNote(userAApi, activeNote.id, payload(crypto.randomUUID(), movedEntry.id), 2);
      assertStatus(moved, 200);
      expect(data<Note>(moved).version === 3).toBe(true);
      expect((await note(userAApi, activeNote.id)).timeEntryId === movedEntry.id).toBe(true);

      const attached = await listNotes(userAApi, `?attachment=ATTACHED&timeEntryId=${encodeURIComponent(movedEntry.id)}`);
      expect(attached.notes.some((entry) => entry.id === activeNote.id)).toBe(true);
      const attachedOnly = await listNotes(userAApi, "?attachment=ATTACHED");
      expect(attachedOnly.notes.every((entry) => entry.timeEntryId !== null)).toBe(true);
      const detached = await updateNote(userAApi, activeNote.id, payload(crypto.randomUUID(), null), 3);
      assertStatus(detached, 200);
      expect(data<Note>(detached).timeEntryId === null).toBe(true);
      const standalone = await listNotes(userAApi, "?attachment=STANDALONE");
      expect(standalone.notes.some((entry) => entry.id === activeNote.id)).toBe(true);

      const foreignCreate = await createNote(userBApi, payload(crypto.randomUUID(), movedEntry.id));
      assertStatus(foreignCreate, 404);
      assertCode(foreignCreate, "NOT_FOUND");
      const foreignRead = await call(userBApi, "GET", `/notes/${activeNote.id}`);
      assertStatus(foreignRead, 404);
      assertCode(foreignRead, "NOT_FOUND");
      const foreignUpdate = await updateNote(userBApi, activeNote.id, payload(crypto.randomUUID(), null), 4);
      assertStatus(foreignUpdate, 404);
      assertCode(foreignUpdate, "NOT_FOUND");
      const foreignDelete = await call(userBApi, "DELETE", `/notes/${activeNote.id}?expectedVersion=4`);
      assertStatus(foreignDelete, 404);
      assertCode(foreignDelete, "NOT_FOUND");
      const userBNotes = await listNotes(userBApi!);
      expect(userBNotes.notes.some((entry) => entry.id === activeNote.id)).toBe(false);

      const deleteEntry = await createOwnedCompletedEntry();
      const attachedForDelete = await createOwnedNote(payload(crypto.randomUUID(), deleteEntry.id));
      assertStatus(attachedForDelete, 201);
      const beforeDelete = await history(userAApi);
      const historyEntry = beforeDelete.entries.find((entry) => entry.id === deleteEntry.id);
      expect(Boolean(historyEntry && historyEntry.attachedNoteCount >= 1)).toBe(true);
      const deletedEntry = await call(userAApi, "DELETE", `/time-entries/${deleteEntry.id}`);
      assertStatus(deletedEntry, 204);
      expect((await note(userAApi, data<Note>(attachedForDelete).id)).timeEntryId === null).toBe(true);

      const replayKey = crypto.randomUUID();
      const replayPayload = payload(crypto.randomUUID(), null);
      const firstReplay = await createOwnedNote(replayPayload, replayKey);
      assertStatus(firstReplay, 201);
      const replayNote = data<Note>(firstReplay);
      const identicalReplay = await createOwnedNote(replayPayload, replayKey);
      assertStatus(identicalReplay, 200);
      expect(data<Note>(identicalReplay).id === replayNote.id).toBe(true);
      const changedReplay = await createOwnedNote(payload(crypto.randomUUID(), null), replayKey);
      assertStatus(changedReplay, 409);
      assertCode(changedReplay, "IDEMPOTENCY_CONFLICT");
      const replayUpdate = await updateNote(userAApi, replayNote.id, payload(crypto.randomUUID(), null), 1);
      assertStatus(replayUpdate, 200);
      const currentReplay = await createOwnedNote(replayPayload, replayKey);
      assertStatus(currentReplay, 200);
      expect(data<Note>(currentReplay).version === 2).toBe(true);
      const replayDelete = await call(userAApi, "DELETE", `/notes/${replayNote.id}?expectedVersion=2`);
      assertStatus(replayDelete, 204);
      const deletedRead = await call(userAApi, "GET", `/notes/${replayNote.id}`);
      assertStatus(deletedRead, 404);
      assertCode(deletedRead, "NOT_FOUND");
      const deletedReplay = await createNote(userAApi, replayPayload, replayKey);
      assertStatus(deletedReplay, 410);
      assertCode(deletedReplay, "NOTE_DELETED");
      const changedDeletedReplay = await createNote(userAApi, payload(crypto.randomUUID(), null), replayKey);
      assertStatus(changedDeletedReplay, 409);
      assertCode(changedDeletedReplay, "IDEMPOTENCY_CONFLICT");
      const repeatedDelete = await call(userAApi, "DELETE", `/notes/${replayNote.id}?expectedVersion=2`);
      assertStatus(repeatedDelete, 204);

      const staleCreate = await createOwnedNote(payload(crypto.randomUUID(), null));
      assertStatus(staleCreate, 201);
      const staleNote = data<Note>(staleCreate);
      const currentUpdate = await updateNote(userAApi, staleNote.id, payload(crypto.randomUUID(), null), 1);
      assertStatus(currentUpdate, 200);
      const staleUpdate = await updateNote(userAApi, staleNote.id, payload(crypto.randomUUID(), null), 1);
      assertStatus(staleUpdate, 409);
      assertCode(staleUpdate, "RICH_TEXT_VERSION_CONFLICT");
      const staleDelete = await call(userAApi, "DELETE", `/notes/${staleNote.id}?expectedVersion=1`);
      assertStatus(staleDelete, 409);
      assertCode(staleDelete, "RICH_TEXT_VERSION_CONFLICT");
      expect((await note(userAApi, staleNote.id)).version === 2).toBe(true);
      assertStatus(await call(userAApi, "DELETE", `/notes/${staleNote.id}?expectedVersion=2`), 204);

      const pageSeed = crypto.randomUUID();
      for (let index = 0; index < 21; index += 1) {
        const created = await createOwnedNoteB(payload(`${pageSeed}-${index}`, null));
        assertStatus(created, 201);
      }
      const firstPage = await listNotes(userBApi!);
      expect(firstPage.notes.length === 20).toBe(true);
      expect(firstPage.nextCursor !== null).toBe(true);
      const secondPage = await listNotes(userBApi!, `?cursor=${encodeURIComponent(firstPage.nextCursor!)}`);
      expect(secondPage.notes.length > 0 && secondPage.notes.length <= 20).toBe(true);
      expect(secondPage.nextCursor === null).toBe(true);
      const firstIds = new Set(firstPage.notes.map((entry) => entry.id));
      expect(secondPage.notes.every((entry) => !firstIds.has(entry.id))).toBe(true);
    } finally {
      const failures: string[] = [];
      const attempt = async (label: string, action: () => Promise<void>): Promise<void> => {
        try {
          await action();
        } catch {
          failures.push(label);
        }
      };
      if (userAApi) await attempt("User A fixture cleanup", async () => {
        failures.push(...await cleanupOwnedFixtures(userAApi, createdNoteIds, createdEntryIds));
      });
      if (userBApi) await attempt("User B fixture cleanup", async () => {
        failures.push(...await cleanupOwnedFixtures(userBApi, createdNoteIdsB, new Set()));
      });
      if (userAApi) await attempt("User A API disposal", () => userAApi.dispose());
      if (userBApi) await attempt("User B API disposal", () => userBApi.dispose());
      if (userAPage) await attempt("User A page disposal", () => userAPage.close());
      if (userBPage) await attempt("User B page disposal", () => userBPage.close());
      if (userAContext) await attempt("User A context disposal", () => userAContext.close());
      if (userBContext) await attempt("User B context disposal", () => userBContext.close());
      if (failures.length > 0) {
        throw new Error(`M5.1 cleanup/disposal failures: ${failures.join(", ")}`);
      }
    }
  });
});
