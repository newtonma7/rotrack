import { describe, expect, it } from "vitest";
import { validateRichTextDocument } from "@/lib/rich-text";

describe("rich-text canonicalization", () => {
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
});
