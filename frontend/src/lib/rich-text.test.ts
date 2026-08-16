import { describe, expect, it } from "vitest";
import { isSafeRichTextLink, validateRichTextDocument } from "@/lib/rich-text";

describe("rich-text canonicalization", () => {
  it("matches the server mailto contract", () => {
    expect(isSafeRichTextLink("mailto:person@example.test")).toBe(true);
    expect(isSafeRichTextLink("mailto:person@example.test?subject=private")).toBe(false);
    expect(isSafeRichTextLink("mailto:person@example.test#private")).toBe(false);
  });

  it("omits empty optional content arrays like the server canonical value", () => {
    const result = validateRichTextDocument({
      schemaVersion: 1,
      document: { type: "doc", content: [{ type: "paragraph", content: [] }] },
    });

    expect(result).toEqual({
      ok: true,
      document: { schemaVersion: 1, document: { type: "doc", content: [{ type: "paragraph" }] } },
    });
  });

  it("rejects unknown ordered-list attributes", () => {
    const result = validateRichTextDocument({
      schemaVersion: 1,
      document: {
        type: "doc",
        content: [{
          type: "orderedList",
          attrs: { start: 1, type: null },
          content: [{ type: "listItem", content: [{ type: "paragraph" }] }],
        }],
      },
    });

    expect(result).toEqual({ ok: false, error: "Ordered list attributes are invalid." });
  });

  it("canonicalizes checklist items with exact checked attributes and nested task lists", () => {
    const result = validateRichTextDocument({
      schemaVersion: 1,
      document: {
        type: "doc",
        content: [{
          type: "taskList",
          content: [{
            type: "taskItem",
            attrs: { checked: true },
            content: [
              { type: "paragraph", content: [{ type: "text", text: "ship it" }] },
              {
                type: "taskList",
                content: [{ type: "taskItem", attrs: { checked: false }, content: [{ type: "paragraph" }] }],
              },
            ],
          }],
        }],
      },
    });

    expect(result).toEqual({
      ok: true,
      document: {
        schemaVersion: 1,
        document: {
          type: "doc",
          content: [{
            type: "taskList",
            content: [{
              type: "taskItem",
              attrs: { checked: true },
              content: [
                { type: "paragraph", content: [{ type: "text", text: "ship it" }] },
                { type: "taskList", content: [{ type: "taskItem", attrs: { checked: false }, content: [{ type: "paragraph" }] }] },
              ],
            }],
          }],
        },
      },
    });
  });

  it("canonicalizes supported block children after a checklist item's first paragraph", () => {
    const result = validateRichTextDocument({
      schemaVersion: 1,
      document: {
        type: "doc",
        content: [{
          type: "taskList",
          content: [{
            type: "taskItem",
            attrs: { checked: false },
            content: [
              { type: "paragraph", content: [{ type: "text", text: "parent" }] },
              { type: "paragraph", content: [{ type: "text", text: "detail" }] },
              { type: "bulletList", content: [{ type: "listItem", content: [{ type: "paragraph", content: [{ type: "text", text: "bullet" }] }] }] },
              { type: "orderedList", attrs: { start: 2 }, content: [{ type: "listItem", content: [{ type: "paragraph", content: [{ type: "text", text: "ordered" }] }] }] },
              { type: "taskList", content: [{ type: "taskItem", attrs: { checked: true }, content: [{ type: "paragraph", content: [{ type: "text", text: "nested" }] }] }] },
              { type: "blockquote", content: [{ type: "paragraph", content: [{ type: "text", text: "quoted" }] }] },
            ],
          }],
        }],
      },
    });

    expect(result).toEqual({
      ok: true,
      document: {
        schemaVersion: 1,
        document: {
          type: "doc",
          content: [{
            type: "taskList",
            content: [{
              type: "taskItem",
              attrs: { checked: false },
              content: [
                { type: "paragraph", content: [{ type: "text", text: "parent" }] },
                { type: "paragraph", content: [{ type: "text", text: "detail" }] },
                { type: "bulletList", content: [{ type: "listItem", content: [{ type: "paragraph", content: [{ type: "text", text: "bullet" }] }] }] },
                { type: "orderedList", attrs: { start: 2 }, content: [{ type: "listItem", content: [{ type: "paragraph", content: [{ type: "text", text: "ordered" }] }] }] },
                { type: "taskList", content: [{ type: "taskItem", attrs: { checked: true }, content: [{ type: "paragraph", content: [{ type: "text", text: "nested" }] }] }] },
                { type: "blockquote", content: [{ type: "paragraph", content: [{ type: "text", text: "quoted" }] }] },
              ],
            }],
          }],
        },
      },
    });
  });

  it("rejects checklist items without exactly a boolean checked attribute", () => {
    const result = validateRichTextDocument({
      schemaVersion: 1,
      document: {
        type: "doc",
        content: [{
          type: "taskList",
          content: [{ type: "taskItem", attrs: { checked: "yes", extra: false }, content: [{ type: "paragraph" }] }],
        }],
      },
    });

    expect(result).toEqual({ ok: false, error: "Task item attributes are invalid." });
  });

  it("rejects headings after a task item's first paragraph", () => {
    const result = validateRichTextDocument({
      schemaVersion: 1,
      document: {
        type: "doc",
        content: [{
          type: "taskList",
          content: [{ type: "taskItem", attrs: { checked: false }, content: [{ type: "paragraph" }, { type: "heading", attrs: { level: 2 } }] }],
        }],
      },
    });

    expect(result).toEqual({ ok: false, error: "Task items cannot contain headings or unsupported blocks after their first paragraph." });
  });
});
