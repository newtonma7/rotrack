/* @vitest-environment jsdom */

import { forwardRef, useEffect, useImperativeHandle } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Note, NoteSummary } from "@/types/notes";

const api = vi.hoisted(() => ({
  getNotes: vi.fn(),
  getNote: vi.fn(),
  deleteNote: vi.fn(),
  getHistory: vi.fn(),
  getPreferences: vi.fn(),
}));
const timer = vi.hoisted(() => ({
  useTimeTracking: vi.fn(),
}));
const editor = vi.hoisted(() => ({
  flush: vi.fn(),
  mounts: 0,
}));

vi.mock("@/lib/api", () => api);
vi.mock("@/hooks/useTimeTracking", () => timer);
vi.mock("@/components/notes/NoteEditor", () => {
  const MockNoteEditor = forwardRef(({ initialNote, activeEntryId, variant, attachments = [], timeZone, onDelete, onReload, onSaved }: { initialNote?: Note | null; activeEntryId?: string | null; variant?: string; attachments?: Array<{ id: string; label: string }>; timeZone?: string; onDelete?: (note: Note) => void; onReload?: () => Promise<Note | null>; onSaved?: (note: Note) => void }, ref) => {
    useEffect(() => { editor.mounts += 1; }, []);
    useImperativeHandle(ref, () => ({ flush: editor.flush, saveAsNew: vi.fn() }), []);
    const saved = initialNote ? { ...initialNote, id: "note-2", title: "copied note" } : { id: "draft-1", title: "saved draft", preview: "", timeEntryId: null, version: 1, createdAt: "2026-08-12T10:00:00Z", updatedAt: "2026-08-12T10:00:00Z", contentJson: { schemaVersion: 1, document: { type: "doc", content: [] } }, contentText: "", contentSchemaVersion: 1 } as Note;
    return <section aria-label="Note editor" data-variant={variant}><span>{initialNote?.title ?? "draft"} · {activeEntryId ?? "no entry"}</span><span>timezone:{timeZone}</span><span>{attachments.map((attachment) => `${attachment.id}:${attachment.label}`).join("|")}</span><button type="button" aria-label={initialNote ? "Save as new Note" : "Simulate draft save"} onClick={() => onSaved?.(saved)}>simulate save</button>{initialNote && <><button type="button" onClick={() => onDelete?.(initialNote)}>Delete note</button><button type="button" onClick={() => void onReload?.()}>Reload server version</button></>}</section>;
  });
  MockNoteEditor.displayName = "MockNoteEditor";
  return { NoteEditor: MockNoteEditor };
});

import { ActiveTracker } from "@/components/tracker/ActiveTracker";

const summary: NoteSummary = {
  id: "note-1",
  title: "Work plan",
  preview: "outline",
  timeEntryId: null,
  version: 1,
  createdAt: "2026-08-12T10:00:00Z",
  updatedAt: "2026-08-12T10:00:00Z",
};
const noteFixture: Note = {
  ...summary,
  contentJson: { schemaVersion: 1, document: { type: "doc", content: [] } },
  contentText: "outline",
  contentSchemaVersion: 1,
};
const note = noteFixture;

function renderTracker() {
  return render(<ActiveTracker />);
}

describe("ActiveTracker", () => {
  beforeEach(() => {
    api.getNotes.mockReset().mockResolvedValue({ notes: [], nextCursor: null });
    api.getNote.mockReset().mockResolvedValue(note);
    api.deleteNote.mockReset().mockResolvedValue(undefined);
    api.getHistory.mockReset().mockResolvedValue({ entries: [], nextCursor: null });
    api.getPreferences.mockReset().mockResolvedValue({ timeZone: "UTC" });
    timer.useTimeTracking.mockReset().mockReturnValue({
      activeEntry: { id: "entry-1", activityType: "WORK", startTime: "2026-08-12T10:00:00Z" },
      elapsed: "00:12:34",
      loading: false,
      error: null,
      start: vi.fn(),
      stop: vi.fn(),
    });
    editor.flush.mockReset().mockResolvedValue(true);
    editor.mounts = 0;
  });
  afterEach(() => cleanup());

  it("renders the readout before the mode pill and keeps the date in the Note canvas", async () => {
    api.getNotes.mockResolvedValue({ notes: [summary], nextCursor: null });
    renderTracker();

    const timerBand = screen.getByRole("region", { name: "Timer" });
    expect(timerBand.firstElementChild?.getAttribute("role")).toBe("timer");
    expect(screen.getByRole("timer").textContent).toContain("00:12:34");
    expect(timerBand.querySelector("[data-testid=timer-status-area]")?.className).toContain("min-h-10");
    expect(timerBand.textContent).not.toContain("local date");
    expect(screen.getByRole("heading", { name: "mindspace" })).toBeTruthy();
    expect(screen.getByRole("region", { name: "Note editor" }).getAttribute("data-variant")).toBe("journal");
    expect(screen.getByRole("region", { name: "Note editor" }).textContent).toContain("entry-1");
    await waitFor(() => expect(screen.getByRole("button", { name: /Work plan/ })).toBeTruthy());
    expect(api.getNotes).toHaveBeenCalledWith({});
    expect(api.getNotes.mock.calls[0][0]).not.toHaveProperty("localDate");
  });

  it("merges a saved Draft with a deferred initial Notes response", async () => {
    let resolveInitial: (page: { notes: NoteSummary[]; nextCursor: null }) => void = () => undefined;
    api.getNotes.mockImplementationOnce(() => new Promise((resolve) => { resolveInitial = resolve; }));
    renderTracker();

    fireEvent.click(screen.getByRole("button", { name: "Simulate draft save" }));
    expect(screen.getByText("Loading notes…")).toBeTruthy();
    resolveInitial({ notes: [{ ...summary, id: "existing-note", title: "existing note" }], nextCursor: null });

    await waitFor(() => expect(screen.getByRole("button", { name: /saved draft/ })).toBeTruthy());
    expect(screen.getByRole("button", { name: /existing note/ })).toBeTruthy();
    expect(screen.getByRole("button", { name: /saved draft/ }).getAttribute("aria-current")).toBe("true");
  });

  it("selects the first Draft Note without remounting its editor", async () => {
    api.getNotes.mockResolvedValue({ notes: [], nextCursor: null });
    renderTracker();
    const mountsBeforeSave = editor.mounts;

    fireEvent.click(screen.getByRole("button", { name: "Simulate draft save" }));
    await waitFor(() => expect(screen.getByText("saved draft · entry-1")).toBeTruthy());
    expect(screen.getByRole("button", { name: /saved draft/ }).getAttribute("aria-current")).toBe("true");
    expect(editor.mounts).toBe(mountsBeforeSave);
  });

  it("switches selection to a returned Save as new Note", async () => {
    api.getNotes.mockResolvedValue({ notes: [summary], nextCursor: null });
    renderTracker();
    fireEvent.click(await screen.findByRole("button", { name: /Work plan/ }));
    await screen.findByRole("button", { name: "Save as new Note" });
    const mountsBeforeSaveAsNew = editor.mounts;

    fireEvent.click(screen.getByRole("button", { name: "Save as new Note" }));
    await waitFor(() => expect(screen.getByText("copied note · entry-1")).toBeTruthy());
    expect(screen.getByRole("button", { name: /copied note/ }).getAttribute("aria-current")).toBe("true");
    expect(editor.mounts).toBe(mountsBeforeSaveAsNew);
  });

  it("keeps timer and Notes failures independent", async () => {
    timer.useTimeTracking.mockReturnValue({
      activeEntry: null,
      elapsed: "00:00:00",
      loading: false,
      error: "Timer unavailable",
      start: vi.fn(),
      stop: vi.fn(),
    });
    api.getNotes.mockRejectedValue(new Error("Notes unavailable"));
    renderTracker();

    expect(screen.getByRole("alert", { name: "Timer error" }).textContent).toContain("Timer unavailable");
    await waitFor(() => expect(screen.getByRole("alert", { name: "Notes error" }).textContent).toContain("Notes unavailable"));
    expect(screen.getByRole("region", { name: "Note editor" })).toBeTruthy();
  });

  it("does not restart an already-active mode", () => {
    const start = vi.fn();
    timer.useTimeTracking.mockReturnValue({
      activeEntry: { id: "entry-1", activityType: "WORK", startTime: "2026-08-12T10:00:00Z" },
      elapsed: "00:12:34",
      loading: false,
      error: null,
      start,
      stop: vi.fn(),
    });
    renderTracker();

    fireEvent.click(screen.getByRole("button", { name: "Work" }));
    expect(start).not.toHaveBeenCalled();
  });

  it("keeps the existing explicit start and stop controls in the rendered band", () => {
    const start = vi.fn();
    const stop = vi.fn();
    timer.useTimeTracking.mockReturnValue({
      activeEntry: null,
      elapsed: "00:00:00",
      loading: false,
      error: null,
      start,
      stop,
    });
    renderTracker();

    fireEvent.click(screen.getByRole("button", { name: "Work" }));
    expect(start).toHaveBeenCalledWith("WORK");
    expect(screen.queryByRole("button", { name: "Stop" })).toBeNull();
  });

  it("loads every history page as attachment options", async () => {
    api.getHistory.mockReset()
      .mockResolvedValueOnce({ entries: [{ id: "entry-2", activityType: "ROT", startTime: "2026-08-11T10:00:00Z", endTime: "2026-08-11T11:00:00Z", durationSeconds: 3600, notes: null }], nextCursor: "cursor-2" })
      .mockResolvedValueOnce({ entries: [{ id: "entry-3", activityType: "ROT", startTime: "2026-08-10T10:00:00Z", endTime: "2026-08-10T11:00:00Z", durationSeconds: 3600, notes: null }], nextCursor: null });
    renderTracker();

    await waitFor(() => expect(api.getHistory).toHaveBeenLastCalledWith("cursor-2"));
    expect(screen.getByRole("region", { name: "Note editor" }).textContent).toContain("entry-2");
    expect(screen.getByRole("region", { name: "Note editor" }).textContent).toContain("entry-3");
    expect(screen.getByRole("region", { name: "Note editor" }).textContent).toContain("entry-1");
  });

  it("keeps the saved timezone and exposes retry when past attachments fail", async () => {
    api.getPreferences.mockResolvedValue({ timeZone: "Asia/Tokyo" });
    api.getHistory.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce({ entries: [], nextCursor: null });
    renderTracker();

    await waitFor(() => expect(screen.getByText("timezone:Asia/Tokyo")).toBeTruthy());
    const error = await screen.findByRole("alert", { name: "Attachment options error" });
    fireEvent.click(error.querySelector("button")!);
    await waitFor(() => expect(api.getHistory).toHaveBeenCalledTimes(2));
  });

  it("deletes a selected Note, refreshes summaries, and starts a clean Draft", async () => {
    api.getNotes.mockResolvedValueOnce({ notes: [summary], nextCursor: null }).mockResolvedValueOnce({ notes: [], nextCursor: null });
    renderTracker();
    fireEvent.click(await screen.findByRole("button", { name: /Work plan/ }));
    await screen.findByRole("button", { name: "Delete note" });

    fireEvent.click(screen.getByRole("button", { name: "Delete note" }));
    await waitFor(() => expect(api.deleteNote).toHaveBeenCalledWith("note-1", 1));
    await waitFor(() => expect(api.getNotes).toHaveBeenLastCalledWith({}));
    expect(screen.getByRole("region", { name: "Note editor" }).textContent).toContain("draft");
  });

  it("reloads the selected Note from the API and surfaces safe detail errors", async () => {
    const fresh = { ...note, title: "fresh title", version: 2 };
    api.getNotes.mockResolvedValue({ notes: [summary], nextCursor: null });
    api.getNote.mockResolvedValueOnce(note).mockResolvedValueOnce(fresh);
    renderTracker();
    fireEvent.click(await screen.findByRole("button", { name: /Work plan/ }));
    fireEvent.click(await screen.findByRole("button", { name: "Reload server version" }));

    await waitFor(() => expect(api.getNote).toHaveBeenCalledTimes(2));
    expect(screen.getByRole("region", { name: "Note editor" }).textContent).toContain("fresh title");

    api.getNote.mockRejectedValueOnce(new Error("Note unavailable"));
    fireEvent.click(screen.getByRole("button", { name: "Reload server version" }));
    await waitFor(() => expect(screen.getByRole("alert", { name: "Note detail error" }).textContent).toContain("Note unavailable"));
  });

  it("keeps an existing Note attachment selectable when history does not include it", async () => {
    const attachedSummary = { ...summary, timeEntryId: "old-entry" };
    api.getNotes.mockResolvedValue({ notes: [attachedSummary], nextCursor: null });
    api.getNote.mockResolvedValue({ ...note, timeEntryId: "old-entry" });
    renderTracker();

    fireEvent.click(await screen.findByRole("button", { name: /Work plan/ }));
    await waitFor(() => expect(screen.getByRole("region", { name: "Note editor" }).textContent).toContain("attached time entry"));
  });

  it("flushes the current draft before selecting a summary or starting a new note", async () => {
    api.getNotes.mockResolvedValue({ notes: [summary], nextCursor: null });
    renderTracker();
    await screen.findByRole("button", { name: /Work plan/ });

    fireEvent.click(screen.getByRole("button", { name: /Work plan/ }));
    await waitFor(() => expect(api.getNote).toHaveBeenCalledWith("note-1"));
    expect(editor.flush.mock.invocationCallOrder[0]).toBeLessThan(api.getNote.mock.invocationCallOrder[0]);

    fireEvent.click(screen.getByRole("button", { name: "+ new note" }));
    await waitFor(() => expect(editor.flush).toHaveBeenCalledTimes(2));
  });
});
