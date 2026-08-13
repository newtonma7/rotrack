/* @vitest-environment jsdom */

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup } from "@testing-library/react";
import { RichTextEditor } from "@/components/notes/RichTextEditor";

describe("RichTextEditor", () => {
  afterEach(() => cleanup());
  it("renders the contract toolbar with accessible controls", () => {
    render(<RichTextEditor />);
    for (const label of ["Paragraph", "Heading 2", "Heading 3", "Bold", "Italic", "Bullet list", "Ordered list", "Blockquote", "Link", "Undo", "Redo"]) {
      expect(screen.getByRole("button", { name: label })).toBeTruthy();
    }
    expect(screen.queryByRole("button", { name: /code/i })).toBeNull();
    expect(screen.getByLabelText("Note content")).toBeTruthy();
  });

  it("emits the allowed ordered-list start shape", () => {
    const onChange = vi.fn();
    render(<RichTextEditor onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Ordered list" }));
    const document = onChange.mock.calls.at(-1)?.[0];
    expect(document.document.content[0].type).toBe("orderedList");
    expect(document.document.content[0].attrs).toEqual({ start: 1 });
  });

  it("opens only the selected safe link in a new tab with noreferrer", async () => {
    const open = vi.spyOn(window, "open").mockImplementation(() => null);
    render(<RichTextEditor initialContent={{ schemaVersion: 1, document: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "linked", marks: [{ type: "link", attrs: { href: "https://example.test" } }] }] }] } }} />);
    fireEvent.click(screen.getByRole("link", { name: "linked" }));
    const button = screen.getByRole("button", { name: "Open selected link" }) as HTMLButtonElement;
    await waitFor(() => expect(button.disabled).toBe(false));
    fireEvent.click(button);
    expect(open).toHaveBeenCalledWith("https://example.test", "_blank", "noopener,noreferrer");
    open.mockRestore();
  });

  it("rejects unsafe links without adding executable URL content", () => {
    render(<RichTextEditor />);
    fireEvent.click(screen.getByRole("button", { name: "Link" }));
    fireEvent.change(screen.getByLabelText("Link URL"), { target: { value: "javascript:alert(1)" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(screen.getByRole("alert").textContent).toContain("absolute http(s)");
  });
});
