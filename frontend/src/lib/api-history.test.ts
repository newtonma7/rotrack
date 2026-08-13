import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  createHistoryEntry,
  deleteHistoryEntry,
  getHistory,
  updateHistoryEntry,
} from "@/lib/api";
import type { HistoryEntry, HistoryEntryInput } from "@/types/history";

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

const entry: HistoryEntry = {
  id: "entry-1",
  activityType: "WORK",
  startTime: "2026-08-12T10:00:00Z",
  endTime: "2026-08-12T11:00:00Z",
  durationSeconds: 3601,
  notes: "deep work",
  attachedNoteCount: 2,
};
const input: HistoryEntryInput = {
  activityType: entry.activityType,
  startTime: entry.startTime,
  endTime: entry.endTime,
  notes: entry.notes,
};

function response(data: unknown) {
  return new Response(JSON.stringify({ data }), { status: 200 });
}

describe("history API", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("lists completed entries with the fixed server page and preserves an opaque cursor", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(response({ entries: [entry], nextCursor: "opaque/next?value" }))
      .mockResolvedValueOnce(response({ entries: [], nextCursor: null }));

    await expect(getHistory()).resolves.toEqual({ entries: [entry], nextCursor: "opaque/next?value" });
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/time-entries/history",
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: "Bearer redacted-token" }) }),
    );

    await getHistory("opaque/next?value");
    expect(fetchMock).toHaveBeenLastCalledWith(
      "http://localhost:8080/api/v1/time-entries/history?cursor=opaque%2Fnext%3Fvalue",
      expect.anything(),
    );
  });

  it("preserves typed API errors from a history mutation", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({
      error: { code: "VALIDATION_ERROR", message: "End time is invalid.", fieldErrors: { endTime: "must be after startTime" } },
    }), { status: 400 }));

    await expect(createHistoryEntry(input)).rejects.toMatchObject({
      code: "VALIDATION_ERROR",
      status: 400,
      fieldErrors: { endTime: "must be after startTime" },
    });
  });

  it("uses typed create, update, and delete contracts", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(response(entry))
      .mockResolvedValueOnce(response(entry))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(createHistoryEntry(input)).resolves.toEqual(entry);
    await expect(updateHistoryEntry(entry.id, input)).resolves.toEqual(entry);
    await expect(deleteHistoryEntry(entry.id)).resolves.toBeUndefined();

    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8080/api/v1/time-entries");
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({ method: "POST", body: JSON.stringify(input) }));
    expect(fetchMock.mock.calls[1][0]).toBe("http://localhost:8080/api/v1/time-entries/entry-1");
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({ method: "PUT", body: JSON.stringify(input) }));
    expect(fetchMock.mock.calls[2][0]).toBe("http://localhost:8080/api/v1/time-entries/entry-1");
    expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({ method: "DELETE" }));
  });

  it("accepts a no-content delete response without trying to parse JSON", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 204 }));

    await expect(deleteHistoryEntry(entry.id)).resolves.toBeUndefined();
  });
});
