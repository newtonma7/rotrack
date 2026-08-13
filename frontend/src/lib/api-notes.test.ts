import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  createNote,
  deleteNote,
  getNote,
  getNotes,
  updateNote,
} from "@/lib/api";
import type { CreateNoteRequest, Note } from "@/types/notes";

vi.mock("@/lib/supabase/client", () => ({
  supabase: {
    auth: {
      getUser: vi.fn().mockResolvedValue({ data: { user: { id: "user-1" } }, error: null }),
      getSession: vi.fn().mockResolvedValue({
        data: { session: { access_token: "redacted-token", expires_at: Math.floor(Date.now() / 1000) + 3600 } },
      }),
      refreshSession: vi.fn(),
    },
  },
}));

const request: CreateNoteRequest = {
  title: "  Session notes  ",
  contentJson: { schemaVersion: 1, document: { type: "doc", content: [] } },
  timeEntryId: "entry-1",
};
const note: Note = {
  ...request,
  title: "Session notes",
  id: "note-1",
  preview: "A preview",
  version: 1,
  createdAt: "2026-08-12T10:00:00Z",
  updatedAt: "2026-08-12T10:00:00Z",
  contentText: "",
  contentSchemaVersion: 1,
};

function response(data: unknown, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify({ data }), { status });
}

describe("notes API", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("lists notes with URLSearchParams filters", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      response({ notes: [note], nextCursor: null }),
    );

    await expect(getNotes({
      cursor: "opaque/next?value",
      attachment: "ATTACHED",
      timeEntryId: "entry/1",
    })).resolves.toEqual({ notes: [note], nextCursor: null });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/notes?cursor=opaque%2Fnext%3Fvalue&attachment=ATTACHED&timeEntryId=entry%2F1",
      expect.anything(),
    );
  });

  it("exposes Retry-After on rate-limit errors through the typed API seam", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ error: { code: "RATE_LIMITED", message: "try later" } }), {
      status: 429,
      headers: { "Retry-After": "2" },
    }));

    await expect(getNotes()).rejects.toMatchObject({ code: "RATE_LIMITED", status: 429, retryAfterMs: 2000 });
  });

  it("uses the Note DTO for create, read, update, and 204 delete", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(response(note, 201))
      .mockResolvedValueOnce(response(note))
      .mockResolvedValueOnce(response({ ...note, version: 2 }))
      .mockResolvedValueOnce(response(undefined, 204));

    await expect(createNote(request, "creation-key")).resolves.toEqual(note);
    await expect(getNote(note.id)).resolves.toEqual(note);
    await expect(updateNote(note.id, { ...request, expectedVersion: 1 })).resolves.toMatchObject({ version: 2 });
    await expect(deleteNote(note.id, 2)).resolves.toBeUndefined();

    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8080/api/v1/notes");
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      method: "POST",
      body: JSON.stringify(request),
      headers: expect.objectContaining({ "Idempotency-Key": "creation-key" }),
    }));
    expect(fetchMock.mock.calls[1][0]).toBe("http://localhost:8080/api/v1/notes/note-1");
    expect(fetchMock.mock.calls[2][0]).toBe("http://localhost:8080/api/v1/notes/note-1");
    expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({
      method: "PUT",
      body: JSON.stringify({ ...request, expectedVersion: 1 }),
    }));
    expect(fetchMock.mock.calls[3][0]).toBe("http://localhost:8080/api/v1/notes/note-1?expectedVersion=2");
    expect(fetchMock.mock.calls[3][1]).toEqual(expect.objectContaining({ method: "DELETE" }));
  });
});
