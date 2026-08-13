/* @vitest-environment jsdom */

import { StrictMode } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api-errors";
import { requestAppNavigation } from "@/lib/navigation-guard";
import type { Note } from "@/types/notes";

const { router } = vi.hoisted(() => ({ router: { push: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));

const { createNote, updateNote } = vi.hoisted(() => ({ createNote: vi.fn(), updateNote: vi.fn() }));
vi.mock("@/lib/api", () => ({ createNote, updateNote }));

import { NoteEditor } from "@/components/notes/NoteEditor";

const empty = { schemaVersion: 1 as const, document: { type: "doc" as const, content: [] } };
const savedNote: Note = {
  id: "note-1", title: "first title", preview: "", timeEntryId: "entry-1", version: 1,
  createdAt: "2026-08-12T10:00:00Z", updatedAt: "2026-08-12T10:00:00Z", contentJson: empty,
  contentText: "", contentSchemaVersion: 1,
};

describe("NoteEditor", () => {
  beforeEach(() => {
    vi.useRealTimers();
    createNote.mockReset().mockResolvedValue(savedNote);
    updateNote.mockReset().mockResolvedValue({ ...savedNote, version: 2 });
    router.push.mockReset();
  });
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("captures a draft and creates once after the 750ms quiet period", async () => {
    render(<NoteEditor activeEntryId="entry-1" />);
    fireEvent.change(screen.getByLabelText("Note title"), { target: { value: "first title" } });
    await waitFor(() => expect(createNote).toHaveBeenCalledTimes(1), { timeout: 1200 });
    expect(createNote.mock.calls[0][0]).toMatchObject({ title: "first title", timeEntryId: "entry-1" });
    expect(createNote.mock.calls[0][1]).toBeTruthy();
    expect(screen.getByRole("status").textContent).toBe("Saved");
  });

  it("finishes autosave under React development Strict Mode", async () => {
    render(<StrictMode><NoteEditor /></StrictMode>);
    fireEvent.change(screen.getByLabelText("Note title"), { target: { value: "strict save" } });
    await waitFor(() => expect(screen.getByRole("status").textContent).toBe("Saved"), { timeout: 1200 });
  });

  it("coalesces rapid title edits into one create and keeps a stable key", async () => {
    render(<NoteEditor />);
    const title = screen.getByLabelText("Note title");
    fireEvent.change(title, { target: { value: "a" } });
    fireEvent.change(title, { target: { value: "ab" } });
    fireEvent.change(title, { target: { value: "abc" } });
    await waitFor(() => expect(createNote).toHaveBeenCalledTimes(1), { timeout: 1200 });
    expect(createNote.mock.calls[0][0].title).toBe("abc");
  });

  it("keeps an explicit attachment selected before the first meaningful edit", async () => {
    render(<NoteEditor activeEntryId="active-entry" attachments={[{ id: "chosen-entry", label: "chosen" }]} />);
    fireEvent.change(screen.getByLabelText("Attachment"), { target: { value: "chosen-entry" } });
    fireEvent.change(screen.getByLabelText("Note title"), { target: { value: "attached deliberately" } });
    await waitFor(() => expect(createNote).toHaveBeenCalledTimes(1), { timeout: 1200 });
    expect(createNote.mock.calls[0][0].timeEntryId).toBe("chosen-entry");
  });

  it("keeps newer edits made while a save is in flight", async () => {
    let resolveCreate: (note: Note) => void = () => undefined;
    createNote.mockImplementationOnce(() => new Promise<Note>((resolve) => { resolveCreate = resolve; }));
    render(<NoteEditor />);
    const title = screen.getByLabelText("Note title");
    fireEvent.change(title, { target: { value: "first" } });
    await waitFor(() => expect(createNote).toHaveBeenCalledTimes(1), { timeout: 1200 });
    fireEvent.change(title, { target: { value: "newer" } });
    resolveCreate({ ...savedNote, title: "first" });
    await waitFor(() => expect(screen.getByLabelText("Note title")).toHaveProperty("value", "newer"));
    await waitFor(() => expect(updateNote).toHaveBeenCalledTimes(1), { timeout: 1200 });
  });

  it("blocks oversized serialized documents before calling the API", async () => {
    const oversized: Note = { ...savedNote, contentJson: { schemaVersion: 1, document: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "x".repeat(263_000) }] }] } }, contentText: "x".repeat(263_000) };
    render(<NoteEditor initialNote={oversized} />);
    fireEvent.change(screen.getByLabelText("Note title"), { target: { value: "still local" } });
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("too large"), { timeout: 1200 });
    expect(updateNote).not.toHaveBeenCalled();
  });

  it("stays on navigation when save fails, then confirms leave-with-loss", async () => {
    updateNote.mockRejectedValue(new Error("offline"));
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<><NoteEditor initialNote={savedNote} /><a href="/tracker">Tracker</a></>);
    fireEvent.change(screen.getByLabelText("Note title"), { target: { value: "unsaved" } });
    await waitFor(() => expect(screen.getByRole("status").textContent).toBe("Offline"), { timeout: 1200 });
    fireEvent.click(screen.getByRole("link", { name: "Tracker" }));
    await waitFor(() => expect(confirm).toHaveBeenCalled());
    expect(router.push).not.toHaveBeenCalled();
    confirm.mockReturnValue(true);
    fireEvent.click(screen.getByRole("link", { name: "Tracker" }));
    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/tracker"));
    confirm.mockRestore();
  });

  it("guards button-driven app navigation such as sign out", async () => {
    updateNote.mockRejectedValue(new Error("offline"));
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    const proceed = vi.fn();
    render(<NoteEditor initialNote={savedNote} />);
    fireEvent.change(screen.getByLabelText("Note title"), { target: { value: "unsaved" } });
    await waitFor(() => expect(screen.getByRole("status").textContent).toBe("Offline"), { timeout: 1200 });

    requestAppNavigation(proceed);
    await waitFor(() => expect(confirm).toHaveBeenCalled());
    expect(proceed).not.toHaveBeenCalled();
    confirm.mockRestore();
  });

  it("flushes before destructive deletion and uses the saved version", async () => {
    const onDelete = vi.fn();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<NoteEditor initialNote={savedNote} onDelete={onDelete} />);
    fireEvent.change(screen.getByLabelText("Note title"), { target: { value: "changed" } });
    fireEvent.click(screen.getByRole("button", { name: "Delete note" }));
    await waitFor(() => expect(onDelete).toHaveBeenCalledWith(expect.objectContaining({ version: 2 })), { timeout: 1200 });
  });

  it("turns a version conflict into an explicit Save as new action", async () => {
    updateNote.mockRejectedValueOnce(new ApiRequestError("changed", 409, "RICH_TEXT_VERSION_CONFLICT"));
    createNote.mockResolvedValueOnce({ ...savedNote, id: "note-2", title: "copy" });
    render(<NoteEditor initialNote={savedNote} />);
    fireEvent.change(screen.getByLabelText("Note title"), { target: { value: "local copy" } });
    await waitFor(() => expect(screen.getByRole("status").textContent).toBe("Conflict"), { timeout: 1200 });
    fireEvent.click(screen.getByRole("button", { name: "Save as new Note" }));
    await waitFor(() => expect(createNote).toHaveBeenCalledWith(expect.objectContaining({ title: "local copy" }), expect.any(String)));
  });

  it("preserves edits and exposes Waiting after a rate limit", async () => {
    createNote.mockRejectedValueOnce(new ApiRequestError("slow down", 429, "RATE_LIMITED", {}, 1000));
    render(<NoteEditor />);
    fireEvent.change(screen.getByLabelText("Note title"), { target: { value: "kept locally" } });
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("waiting"), { timeout: 1500 });
    expect(screen.getByRole("status").textContent).toBe("Waiting");
    expect(screen.getByLabelText("Note title")).toHaveProperty("value", "kept locally");
  });
});
