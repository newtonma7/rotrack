// @vitest-environment jsdom

import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createNote, updateNote } from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import { useNoteAutosave } from "@/hooks/useNoteAutosave";
import type { Note } from "@/types/notes";

vi.mock("@/lib/api", () => ({ createNote: vi.fn(), updateNote: vi.fn() }));

const saved: Note = {
  id: "note-1", title: "first", preview: "", timeEntryId: null, version: 1,
  createdAt: "2026-08-12T10:00:00Z", updatedAt: "2026-08-12T10:00:00Z",
  contentJson: { schemaVersion: 1, document: { type: "doc", content: [] } },
  contentText: "", contentSchemaVersion: 1,
};

beforeEach(() => {
  vi.useFakeTimers();
  vi.mocked(createNote).mockReset().mockResolvedValue(saved);
  vi.mocked(updateNote).mockReset().mockResolvedValue({ ...saved, title: "newer", version: 2 });
});
afterEach(() => { cleanup(); vi.useRealTimers(); });

describe("useNoteAutosave scheduling", () => {
  it("serializes concurrent flushes and saves the newest tab draft after creation", async () => {
    let resolveCreate: (value: Note) => void = () => {};
    vi.mocked(createNote).mockReturnValue(new Promise((resolve) => { resolveCreate = resolve; }));
    const { result } = renderHook(() => useNoteAutosave());
    act(() => result.current.setTitle("first"));
    let firstFlush: Promise<boolean> = Promise.resolve(false);
    act(() => { firstFlush = result.current.flush(); });
    act(() => result.current.setTitle("newer"));
    let secondFlush: Promise<boolean> = Promise.resolve(false);
    act(() => { secondFlush = result.current.flush(); });
    expect(createNote).toHaveBeenCalledTimes(1);
    expect(updateNote).not.toHaveBeenCalled();
    await act(async () => {
      resolveCreate(saved);
      expect(await firstFlush).toBe(true);
      expect(await secondFlush).toBe(true);
    });
    expect(updateNote).toHaveBeenCalledExactlyOnceWith("note-1", expect.objectContaining({ title: "newer", expectedVersion: 1 }));
    expect(result.current.title).toBe("newer");
    expect(result.current.status).toBe("Saved");
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(updateNote).toHaveBeenCalledTimes(1);
  });

  it("bounds rate-limit retry and reuses the creation key without losing edits", async () => {
    vi.mocked(createNote).mockRejectedValue(new ApiRequestError("wait", 429, "RATE_LIMITED", {}, 1500));
    const { result } = renderHook(() => useNoteAutosave());
    act(() => result.current.setTitle("first"));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); });
    expect(createNote).toHaveBeenCalledTimes(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(1499); });
    expect(createNote).toHaveBeenCalledTimes(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(createNote).toHaveBeenCalledTimes(2);
    expect(vi.mocked(createNote).mock.calls[0][1]).toBe(vi.mocked(createNote).mock.calls[1][1]);
    await act(async () => { await vi.advanceTimersByTimeAsync(10_000); });
    expect(createNote).toHaveBeenCalledTimes(2);
    expect(result.current.title).toBe("first");
    expect(result.current.dirty).toBe(true);
    expect(result.current.status).toBe("Waiting");
  });

  it("preserves conflict edits and stops automatic saves until explicit recovery", async () => {
    vi.mocked(updateNote).mockRejectedValue(new ApiRequestError("conflict", 409, "RICH_TEXT_VERSION_CONFLICT"));
    const { result } = renderHook(() => useNoteAutosave({ initialNote: saved }));
    act(() => result.current.setTitle("local"));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); });
    expect(result.current.status).toBe("Conflict");
    act(() => result.current.setTitle("still local"));
    await act(async () => { await vi.advanceTimersByTimeAsync(10_000); });
    expect(updateNote).toHaveBeenCalledTimes(1);
    expect(result.current.title).toBe("still local");
    expect(result.current.dirty).toBe(true);
  });
});
