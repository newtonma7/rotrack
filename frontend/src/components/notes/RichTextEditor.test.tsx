/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RichTextEditor } from "@/components/notes/RichTextEditor";

for (const prototype of [Text.prototype, HTMLElement.prototype, Range.prototype]) {
  if (!("getClientRects" in prototype)) Object.defineProperty(prototype, "getClientRects", { configurable: true, value: () => [] });
  if (!("getBoundingClientRect" in prototype)) Object.defineProperty(prototype, "getBoundingClientRect", { configurable: true, value: () => ({ top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0 }) });
}

function focusEditorParagraph(paragraphIndex: number) {
  const editor = screen.getByLabelText("Note content");
  const paragraph = editor.querySelectorAll("p")[paragraphIndex] ?? editor;
  fireEvent.focus(editor);
  const previousElementFromPoint = document.elementFromPoint;
  const previousCaretRangeFromPoint = document.caretRangeFromPoint;
  Object.defineProperty(document, "elementFromPoint", { configurable: true, value: () => paragraph });
  Object.defineProperty(document, "caretRangeFromPoint", { configurable: true, value: () => {
    const caret = document.createRange();
    const node = paragraph.firstChild ?? paragraph;
    caret.selectNodeContents(node);
    caret.collapse(false);
    return caret;
  } });
  fireEvent.mouseDown(paragraph, { clientX: 0, clientY: 0, button: 0 });
  const textNode = paragraph.firstChild;
  const range = document.createRange();
  if (textNode) range.selectNodeContents(paragraph);
  else range.selectNode(paragraph);
  range.collapse(false);
  window.getSelection()?.removeAllRanges();
  window.getSelection()?.addRange(range);
  document.dispatchEvent(new Event("selectionchange"));
  fireEvent.mouseUp(editor, { clientX: 0, clientY: 0, button: 0 });
  Object.defineProperty(document, "elementFromPoint", { configurable: true, value: previousElementFromPoint });
  Object.defineProperty(document, "caretRangeFromPoint", { configurable: true, value: previousCaretRangeFromPoint });
}

function simulateEditorInput(text: string, paragraphIndex = 0, focus = true) {
  const editor = screen.getByLabelText("Note content");
  const paragraph = editor.querySelectorAll("p")[paragraphIndex] ?? editor;
  if (focus) focusEditorParagraph(paragraphIndex);
  for (const character of text) {
    fireEvent.keyDown(editor, { key: character });
    paragraph.textContent = `${paragraph.textContent ?? ""}${character}`;
    const textNode = paragraph.firstChild;
    if (textNode) {
      const range = document.createRange();
      range.setStart(textNode, textNode.textContent?.length ?? 0);
      range.collapse(true);
      window.getSelection()?.removeAllRanges();
      window.getSelection()?.addRange(range);
      document.dispatchEvent(new Event("selectionchange"));
    }
    fireEvent.input(editor, { inputType: "insertText", data: character });
  }
}

describe("RichTextEditor", () => {
  afterEach(() => cleanup());
  it("renders the contract toolbar with accessible controls", () => {
    render(<RichTextEditor />);
    for (const label of ["Paragraph", "Heading 2", "Heading 3", "Bold", "Italic", "Bullet list", "Ordered list", "Checklist", "Blockquote", "Link", "Undo", "Redo"]) {
      expect(screen.getByRole("button", { name: label })).toBeTruthy();
    }
    expect(screen.queryByRole("button", { name: /code/i })).toBeNull();
    expect(screen.getByLabelText("Note content")).toBeTruthy();
  });

  it("uses an open journal canvas with a quiet toolbar variant", () => {
    render(<RichTextEditor variant="journal" />);
    const toolbar = screen.getByLabelText("Editor toolbar");
    expect(toolbar.className).toContain("opacity-0");
    expect(screen.getByLabelText("Note content").className).toContain("min-h-[50vh]");
  });

  it("emits the allowed ordered-list start shape", () => {
    const onChange = vi.fn();
    render(<RichTextEditor onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Ordered list" }));
    const document = onChange.mock.calls.at(-1)?.[0];
    expect(document.document.content[0].type).toBe("orderedList");
    expect(document.document.content[0].attrs).toEqual({ start: 1 });
  });

  it("renders an accessible unchecked checklist and emits its exact JSON shape", async () => {
    const onChange = vi.fn();
    render(<RichTextEditor onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Checklist" }));

    const checkbox = await screen.findByRole("checkbox", { name: "Checklist item" });
    expect(checkbox).toHaveProperty("checked", false);
    expect(onChange.mock.calls.at(-1)?.[0].document.content[0]).toEqual({
      type: "taskList",
      content: [{ type: "taskItem", attrs: { checked: false }, content: [{ type: "paragraph" }] }],
    });
  });

  it("persists checking a checklist item", async () => {
    const onChange = vi.fn();
    render(<RichTextEditor onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Checklist" }));
    const checkbox = await screen.findByRole("checkbox", { name: "Checklist item" });
    fireEvent.click(checkbox);

    expect(checkbox).toHaveProperty("checked", true);
    expect(onChange.mock.calls.at(-1)?.[0].document.content[0].content[0].attrs).toEqual({ checked: true });
  });

  it("does not treat checkbox state as meaningful text", async () => {
    const onChange = vi.fn();
    render(<RichTextEditor onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Checklist" }));
    fireEvent.click(await screen.findByRole("checkbox", { name: "Checklist item" }));

    expect(onChange.mock.calls.at(-1)?.[1]).toBe("");
  });

  it("renders the checked state from a reloaded document", async () => {
    render(<RichTextEditor initialContent={{
      schemaVersion: 1,
      document: {
        type: "doc",
        content: [{ type: "taskList", content: [{ type: "taskItem", attrs: { checked: true }, content: [{ type: "paragraph", content: [{ type: "text", text: "done" }] }] }] }],
      },
    }} />);

    await waitFor(() => expect(screen.getByRole("checkbox", { name: "Checklist item" })).toHaveProperty("checked", true));
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

  it.each([
    ["## ", "heading", { level: 2 }],
    ["### ", "heading", { level: 3 }],
    ["- ", "bulletList", undefined],
    ["* ", "bulletList", undefined],
    ["+ ", "bulletList", undefined],
    ["1. ", "orderedList", { start: 1 }],
    ["[ ] ", "taskList", undefined],
    ["> ", "blockquote", undefined],
  ] as const)("covers %s input-rule handling at the rendered jsdom seam", async (shortcut, type, attrs) => {
    const onChange = vi.fn();
    render(<RichTextEditor onChange={onChange} />);
    simulateEditorInput(shortcut);

    await waitFor(() => expect(onChange.mock.calls.at(-1)?.[0].document.content[0]).toMatchObject({ type, ...(attrs ? { attrs } : {}) }));
  });

  it("exposes a filtered, keyboard-operable slash menu only in an empty paragraph", async () => {
    render(<RichTextEditor />);
    const editor = screen.getByLabelText("Note content");
    expect(editor.getAttribute("role")).toBeNull();
    expect(editor.getAttribute("aria-haspopup")).toBe("listbox");
    expect(editor.getAttribute("aria-expanded")).toBe("false");
    expect(editor.getAttribute("aria-controls")).toBeNull();
    expect(editor.getAttribute("aria-activedescendant")).toBeNull();

    simulateEditorInput("/");
    const menu = await screen.findByRole("listbox", { name: "Insert block" });
    expect(menu.querySelectorAll('[role="option"]')).toHaveLength(7);
    expect(editor.getAttribute("aria-expanded")).toBe("true");
    expect(editor.getAttribute("aria-controls")).toBe("rich-text-slash-menu");
    expect(editor.getAttribute("aria-activedescendant")).toBe("rich-text-slash-option-paragraph");
    expect(menu.className).toContain("max-h-");
    expect(menu.className).toContain("overflow-y-auto");

    fireEvent.keyDown(editor, { key: "ArrowDown" });
    expect(editor.getAttribute("aria-activedescendant")).toBe("rich-text-slash-option-heading-2");
    fireEvent.keyDown(editor, { key: "ArrowUp" });
    expect(editor.getAttribute("aria-activedescendant")).toBe("rich-text-slash-option-paragraph");
    fireEvent.keyDown(editor, { key: "ArrowDown" });
    fireEvent.keyDown(editor, { key: "Enter" });
    await waitFor(() => expect(document.activeElement).toBe(editor));
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(editor.getAttribute("aria-expanded")).toBe("false");
    expect(editor.getAttribute("aria-controls")).toBeNull();
    expect(editor.getAttribute("aria-activedescendant")).toBeNull();
    expect(editor.querySelector("h2")).toBeTruthy();
  });

  it("closes the slash menu on Escape without losing editor focus", async () => {
    render(<RichTextEditor />);
    const editor = screen.getByLabelText("Note content");
    simulateEditorInput("/");
    await screen.findByRole("listbox", { name: "Insert block" });
    fireEvent.keyDown(editor, { key: "Escape" });
    expect(screen.queryByRole("listbox")).toBeNull();
    await waitFor(() => expect(document.activeElement).toBe(editor));
    expect(editor.getAttribute("aria-expanded")).toBe("false");
    expect(editor.getAttribute("aria-controls")).toBeNull();
    expect(editor.getAttribute("aria-activedescendant")).toBeNull();
  });

  it("does not open the slash menu after paragraph text", async () => {
    render(<RichTextEditor />);
    const editor = screen.getByLabelText("Note content");
    simulateEditorInput("hello/");
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(editor.getAttribute("aria-expanded")).toBe("false");
  });

  it("does not reopen the slash menu for persisted slash-prefixed text", async () => {
    render(<RichTextEditor initialContent={{ schemaVersion: 1, document: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "/ordinary text" }] }] } }} />);
    const editor = screen.getByLabelText("Note content");
    focusEditorParagraph(0);

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(editor.getAttribute("aria-expanded")).toBe("false");
  });

  it("keeps the active slash option visible while navigating a bounded menu", async () => {
    render(<RichTextEditor />);
    const editor = screen.getByLabelText("Note content");
    simulateEditorInput("/");
    const menu = await screen.findByRole("listbox", { name: "Insert block" });
    const active = screen.getByRole("option", { name: "Heading 2" });
    Object.defineProperty(active, "offsetTop", { value: 1000 });
    Object.defineProperty(active, "offsetHeight", { value: 32 });
    Object.defineProperty(menu, "scrollTop", { value: 0, writable: true });
    Object.defineProperty(menu, "clientHeight", { value: 120 });
    Object.defineProperty(menu, "scrollHeight", { value: 1000 });
    const scrollIntoView = vi.fn();
    Object.defineProperty(active, "scrollIntoView", { configurable: true, value: scrollIntoView });

    fireEvent.keyDown(editor, { key: "ArrowDown" });
    fireEvent.keyDown(editor, { key: "ArrowDown" });
    fireEvent.keyDown(editor, { key: "ArrowDown" });
    expect(scrollIntoView).toHaveBeenCalled();
    expect(screen.getByRole("option", { name: "Bullet list" }).getAttribute("aria-selected")).toBe("true");
  });

  it("opens the slash menu in a later empty paragraph", async () => {
    render(<RichTextEditor />);
    const editor = screen.getByLabelText("Note content");
    simulateEditorInput("/");
    await screen.findByRole("listbox", { name: "Insert block" });
    fireEvent.click(screen.getByRole("option", { name: "Paragraph" }));
    fireEvent.keyDown(editor, { key: "Enter" });
    fireEvent.keyDown(editor, { key: "/" });

    const menu = await screen.findByRole("listbox", { name: "Insert block" });
    expect(editor.querySelectorAll("p")).toHaveLength(2);
    expect(menu.querySelectorAll('[role="option"]')).toHaveLength(7);
    expect(editor.getAttribute("aria-expanded")).toBe("true");
  });

  it("shows safe no-match copy and removes the active descendant", async () => {
    render(<RichTextEditor />);
    const editor = screen.getByLabelText("Note content");
    simulateEditorInput("/");
    await screen.findByRole("listbox", { name: "Insert block" });
    simulateEditorInput("not-a-block", 0, false);

    const menu = await screen.findByRole("listbox", { name: "Insert block" });
    expect(menu.textContent).toContain("No matching blocks");
    expect(menu.querySelectorAll('[role="option"]')).toHaveLength(0);
    expect(editor.getAttribute("aria-expanded")).toBe("true");
    await waitFor(() => expect(editor.getAttribute("aria-activedescendant")).toBeNull());
  });

  it.each([
    ["quote", "Blockquote"],
    ["check", "Checklist"],
    ["task", "Checklist"],
    ["number", "Ordered list"],
  ])("filters slash commands by the %s keyword", async (query, label) => {
    render(<RichTextEditor />);
    simulateEditorInput("/");
    await screen.findByRole("listbox", { name: "Insert block" });
    simulateEditorInput(query, 0, false);
    const menu = await screen.findByRole("listbox", { name: "Insert block" });
    expect(menu.querySelectorAll('[role="option"]')).toHaveLength(1);
    expect(screen.getByRole("option", { name: label })).toBeTruthy();
  });

  it("closes the slash menu when selection leaves its paragraph", async () => {
    render(<RichTextEditor />);
    const editor = screen.getByLabelText("Note content");
    simulateEditorInput("/");
    await screen.findByRole("listbox", { name: "Insert block" });
    fireEvent.click(screen.getByRole("button", { name: "Heading 2" }));

    await waitFor(() => expect(screen.queryByRole("listbox")).toBeNull());
    expect(editor.getAttribute("aria-expanded")).toBe("false");
  });

  it("rejects a toolbar heading that would make a blockquote non-canonical", () => {
    const onChange = vi.fn();
    render(<RichTextEditor onChange={onChange} />);
    const editor = screen.getByLabelText("Note content");
    fireEvent.click(screen.getByRole("button", { name: "Blockquote" }));
    fireEvent.click(screen.getByRole("button", { name: "Heading 2" }));

    expect(editor.querySelector("blockquote h2")).toBeNull();
    expect(onChange.mock.calls.at(-1)?.[0].document.content[0]).toEqual({
      type: "blockquote",
      content: [{ type: "paragraph" }],
    });
  });

  it.each(["# ", "``` "])("keeps the unsupported %s shortcut as a paragraph", async (shortcut) => {
    const onChange = vi.fn();
    render(<RichTextEditor onChange={onChange} />);
    simulateEditorInput(shortcut);

    await waitFor(() => expect(onChange.mock.calls.at(-1)?.[0].document.content[0].type).toBe("paragraph"));
  });

  it("does not expose unsupported controls or nodes", () => {
    render(<RichTextEditor />);
    for (const name of ["Heading 1", "Code", "Code block", "Underline"]) {
      expect(screen.queryByRole("button", { name })).toBeNull();
    }
    expect(screen.getByLabelText("Note content").querySelector("u, h1, code, pre")).toBeNull();
  });
});
