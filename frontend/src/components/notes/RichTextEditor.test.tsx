/* @vitest-environment jsdom */

import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
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

  it("rejects unsafe links without adding executable URL content", () => {
    render(<RichTextEditor />);
    fireEvent.click(screen.getByRole("button", { name: "Link" }));
    fireEvent.change(screen.getByLabelText("Link URL"), { target: { value: "javascript:alert(1)" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(screen.getByRole("alert").textContent).toContain("absolute http(s)");
  });
});
