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

  it("passes attachment filters to the typed list API", async () => {
    render(<NotesWorkspace />);
    await waitFor(() => expect(api.getNotes).toHaveBeenCalled());
    fireEvent.click(screen.getByRole("button", { name: "Attached" }));
    await waitFor(() => expect(api.getNotes).toHaveBeenLastCalledWith({ attachment: "ATTACHED", timeEntryId: undefined }));
  });
});
