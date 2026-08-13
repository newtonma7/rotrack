/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { NoteSummary } from "@/types/notes";

const api = vi.hoisted(() => ({
  getNotes: vi.fn(),
  getNote: vi.fn(),
  getHistory: vi.fn(),
  getActiveSession: vi.fn(),
  deleteNote: vi.fn(),
  createNote: vi.fn(),
}));
vi.mock("@/lib/api", () => api);
vi.mock("@/components/app/ApplicationHeader", () => ({ ApplicationHeader: () => <header>navigation</header> }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

import { NotesWorkspace } from "@/components/notes/NotesWorkspace";

const first: NoteSummary = { id: "note-1", title: "one", preview: "first", timeEntryId: null, version: 1, createdAt: "2026-08-12T10:00:00Z", updatedAt: "2026-08-12T10:00:00Z" };
const second: NoteSummary = { ...first, id: "note-2", title: "two", preview: "second" };

describe("NotesWorkspace", () => {
  beforeEach(() => {
    api.getNotes.mockReset().mockResolvedValue({ notes: [first], nextCursor: "next" });
    api.getNote.mockReset();
    api.getHistory.mockReset().mockResolvedValue({ entries: [], nextCursor: null });
    api.getActiveSession.mockReset().mockResolvedValue(null);
    api.createNote.mockReset();
  });
  afterEach(() => cleanup());

  it("renders the library/editor seams and deduplicates paginated summaries", async () => {
    api.getNotes.mockResolvedValueOnce({ notes: [first], nextCursor: "next" }).mockResolvedValueOnce({ notes: [first, second], nextCursor: null });
    render(<NotesWorkspace />);
    expect(screen.getByRole("complementary", { name: "Notes library" })).toBeTruthy();
    expect(screen.getByRole("region", { name: "Note editor" })).toBeTruthy();
    await waitFor(() => expect(screen.getByText("one")).toBeTruthy());
    fireEvent.click(screen.getByRole("button", { name: "Load more" }));
    await waitFor(() => expect(screen.getByText("two")).toBeTruthy());
    expect(within(screen.getByRole("complementary", { name: "Notes library" })).getAllByRole("button", { name: /^one/ })).toHaveLength(1);
  });

  it("continues the created Note until New note starts a fresh draft", async () => {
    api.createNote.mockResolvedValueOnce({ id: "created", title: "saved", preview: "", timeEntryId: null, version: 1, createdAt: "2026-08-12T10:00:00Z", updatedAt: "2026-08-12T10:00:00Z", contentJson: { schemaVersion: 1, document: { type: "doc", content: [] } }, contentText: "", contentSchemaVersion: 1 });
    render(<NotesWorkspace />);
    const title = await screen.findByLabelText("Note title");
    fireEvent.change(title, { target: { value: "saved" } });
    await waitFor(() => expect(api.createNote).toHaveBeenCalledTimes(1), { timeout: 1200 });
    await waitFor(() => expect(screen.getByLabelText("Note title")).toHaveProperty("value", "saved"));
    expect(screen.getByRole("status").textContent).toBe("Saved");

    fireEvent.click(screen.getByRole("button", { name: "New note" }));
    await waitFor(() => expect(screen.getByLabelText("Note title")).toHaveProperty("value", ""));
    expect(screen.getByRole("status").textContent).toBe("Draft");
  });

  it("uses list-to-detail mobile navigation while retaining the desktop split", async () => {
    api.getNote.mockResolvedValue({ ...first, contentJson: { schemaVersion: 1, document: { type: "doc", content: [] } }, contentText: "", contentSchemaVersion: 1 });
    render(<NotesWorkspace selectedNoteId="note-1" />);
    await screen.findByDisplayValue("one");

    expect(screen.getByRole("complementary", { name: "Notes library" }).className).toContain("hidden lg:block");
    expect(screen.getByRole("button", { name: "Back to notes" }).className).toContain("lg:hidden");
  });

  it("loads every available history cursor page for attachment options", async () => {
    const firstEntry = { id: "entry-1", activityType: "WORK" as const, startTime: "2026-08-10T10:00:00Z", endTime: "2026-08-10T11:00:00Z", durationSeconds: 3600, notes: null, attachedNoteCount: 0 };
    const secondEntry = { ...firstEntry, id: "entry-2", startTime: "2026-08-09T10:00:00Z" };
    api.getHistory.mockReset().mockResolvedValueOnce({ entries: [firstEntry], nextCursor: "cursor-2" }).mockResolvedValueOnce({ entries: [secondEntry], nextCursor: null });
    render(<NotesWorkspace />);
    await waitFor(() => expect(api.getHistory).toHaveBeenCalledWith("cursor-2"));
    expect(document.querySelector('option[value="entry-2"]')).toBeTruthy();
  });

  it("passes attachment filters to the typed list API", async () => {
    render(<NotesWorkspace />);
    await waitFor(() => expect(api.getNotes).toHaveBeenCalled());
    fireEvent.click(screen.getByRole("button", { name: "Attached" }));
    await waitFor(() => expect(api.getNotes).toHaveBeenLastCalledWith({ attachment: "ATTACHED", timeEntryId: undefined }));
  });
});
