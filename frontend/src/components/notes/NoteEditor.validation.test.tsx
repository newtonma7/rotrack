/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Note, RichTextDocument } from "@/types/notes";

const { updateNote, createNote } = vi.hoisted(() => ({ updateNote: vi.fn(), createNote: vi.fn() }));
vi.mock("@/lib/api", () => ({ updateNote, createNote }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/components/notes/RichTextEditor", () => ({
  RichTextEditor: ({ onChange }: { onChange: (document: RichTextDocument, text: string) => void }) => (
    <div>
      <button type="button" onClick={() => onChange(forbidden, "forbidden")}>Inject forbidden nested heading</button>
      <button type="button" onClick={() => onChange(invalidOrder, "bad")}>Inject invalid ordered start</button>
    </div>
  ),
}));

import { NoteEditor } from "@/components/notes/NoteEditor";

const empty = { schemaVersion: 1 as const, document: { type: "doc" as const, content: [] } };
const forbidden = { schemaVersion: 1, document: { type: "doc", content: [{ type: "blockquote", content: [{ type: "heading", attrs: { level: 2 }, content: [{ type: "text", text: "forbidden" }] }] }] } } as unknown as RichTextDocument;
const invalidOrder = { schemaVersion: 1, document: { type: "doc", content: [{ type: "orderedList", attrs: { start: 0 }, content: [{ type: "listItem", content: [{ type: "paragraph", content: [{ type: "text", text: "bad" }] }] }] }] } } as unknown as RichTextDocument;
const note: Note = { id: "note-1", title: "existing", preview: "", timeEntryId: null, version: 1, createdAt: "2026-08-12T10:00:00Z", updatedAt: "2026-08-12T10:00:00Z", contentJson: empty, contentText: "", contentSchemaVersion: 1 };

describe("NoteEditor local rich-text validation", () => {
  beforeEach(() => {
    updateNote.mockReset();
    createNote.mockReset();
  });
  afterEach(() => cleanup());

  it("preserves edits and shows a local error for forbidden nested headings", async () => {
    render(<NoteEditor initialNote={note} />);
    fireEvent.click(screen.getByRole("button", { name: "Inject forbidden nested heading" }));
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("cannot contain headings"), { timeout: 1200 });
    expect(updateNote).not.toHaveBeenCalled();
  });

  it("rejects non-positive ordered-list starts before sending JSON", async () => {
    render(<NoteEditor initialNote={note} />);
    fireEvent.click(screen.getByRole("button", { name: "Inject invalid ordered start" }));
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("positive integer"), { timeout: 1200 });
    expect(updateNote).not.toHaveBeenCalled();
  });
});
