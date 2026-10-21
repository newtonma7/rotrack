import { describe, expect, it } from "vitest";
import { richTextToHtml } from "@/lib/rich-text-html";
import type { RichTextDocument } from "@/types/notes";

describe("rich-text clipboard HTML", () => {
  it("preserves ordered-list numbering and escapes private text", () => {
    const document: RichTextDocument = {
      schemaVersion: 1,
      document: {
        type: "doc",
        content: [{
          type: "orderedList",
          attrs: { start: 4 },
          content: [{ type: "listItem", content: [{ type: "paragraph", content: [{ type: "text", text: "<private & safe>" }] }] }],
        }],
      },
    };
    expect(richTextToHtml(document)).toBe('<ol start="4"><li><p>&lt;private &amp; safe&gt;</p></li></ol>');
  });

  it("preserves checklist state and rich inline marks", () => {
    const document: RichTextDocument = {
      schemaVersion: 1,
      document: {
        type: "doc",
        content: [{
          type: "taskList",
          content: [{ type: "taskItem", attrs: { checked: true }, content: [{ type: "paragraph", content: [{ type: "text", text: "finished", marks: [{ type: "bold" }, { type: "italic" }] }] }] }],
        }],
      },
    };
    const html = richTextToHtml(document);
    expect(html).toContain('data-checked="true"');
    expect(html).toContain(" checked disabled");
    expect(html).toContain("<em><strong>finished</strong></em>");
  });
});
