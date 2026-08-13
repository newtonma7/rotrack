import type {
  RichTextBlock,
  RichTextBlockquoteChild,
  RichTextDocument,
  RichTextListItem,
  RichTextListItemChild,
  RichTextMark,
  RichTextParagraph,
  RichTextText,
} from "@/types/notes";

const NODE_LIMIT = 10_000;
const DEPTH_LIMIT = 32;

type RichObject = Record<string, unknown>;

export type RichTextValidation =
  | { ok: true; document: RichTextDocument }
  | { ok: false; error: string };

/** Tiptap's schema is broader than the exact M5 persistence grammar. */
export function validateRichTextDocument(value: unknown): RichTextValidation {
  let count = 1; // the root doc
  try {
    if (!isObject(value) || !exactKeys(value, ["schemaVersion", "document"]) || value.schemaVersion !== 1 || !isObject(value.document)) {
      return invalid("Only rich-text schema version 1 is supported.");
    }
    if (!exactKeys(value.document, ["type", "content"]) || value.document.type !== "doc" || !Array.isArray(value.document.content)) {
      return invalid("The document root is invalid.");
    }
    const content = validateBlocks(value.document.content, 2);
    return { ok: true, document: { schemaVersion: 1, document: { type: "doc", content } } };
  } catch (error) {
    return invalid(error instanceof Error ? error.message : "The document contains unsupported formatting.");
  }

  function visit(depth: number): void {
    if (depth > DEPTH_LIMIT) throw new Error("The document is nested too deeply.");
    count += 1;
    if (count > NODE_LIMIT) throw new Error("The document contains too many nodes.");
  }

  function validateBlocks(nodes: unknown[], depth: number): RichTextBlock[] {
    return nodes.map((node) => validateBlock(node, depth));
  }

  function validateBlock(node: unknown, depth: number): RichTextBlock {
    visit(depth);
    if (!isObject(node) || typeof node.type !== "string") throw new Error("The document contains an unsupported node.");
    switch (node.type) {
      case "paragraph": {
        requireAllowedKeys(node, ["type", "content"]);
        const content = validateTextContent(node.content, depth + 1);
        return { type: "paragraph", ...(content.length ? { content } : {}) };
      }
      case "heading": {
        requireAllowedKeys(node, ["type", "attrs", "content"]);
        if (!isObject(node.attrs) || !exactKeys(node.attrs, ["level"]) || (node.attrs.level !== 2 && node.attrs.level !== 3)) {
          throw new Error("Only level 2 and 3 headings are supported.");
        }
        const content = validateTextContent(node.content, depth + 1);
        return { type: "heading", attrs: { level: node.attrs.level }, ...(content.length ? { content } : {}) };
      }
      case "bulletList": {
        requireAllowedKeys(node, ["type", "content"]);
        if (!Array.isArray(node.content) || node.content.length === 0) throw new Error("Lists must contain an item.");
        const items = node.content.map((item) => validateListItem(item, depth + 1));
        return { type: "bulletList", content: [items[0], ...items.slice(1)] };
      }
      case "orderedList": {
        requireAllowedKeys(node, ["type", "attrs", "content"]);
        if (!Array.isArray(node.content) || node.content.length === 0) throw new Error("Lists must contain an item.");
        if (node.attrs !== undefined && (!isObject(node.attrs) || !exactKeys(node.attrs, ["start", "type"]) || ("type" in node.attrs && node.attrs.type !== null))) throw new Error("Ordered list attributes are invalid.");
        const startValue = node.attrs === undefined ? 1 : node.attrs.start ?? 1;
        if (!Number.isInteger(startValue) || (startValue as number) < 1) throw new Error("Ordered list start must be a positive integer.");
        const items = node.content.map((item) => validateListItem(item, depth + 1));
        return { type: "orderedList", attrs: { start: startValue as number }, content: [items[0], ...items.slice(1)] };
      }
      case "blockquote": {
        requireAllowedKeys(node, ["type", "content"]);
        if (!Array.isArray(node.content) || node.content.length === 0) throw new Error("Blockquotes must contain content.");
        const children = node.content.map((child) => validateQuoteChild(child, depth + 1));
        return { type: "blockquote", content: [children[0], ...children.slice(1)] };
      }
      default:
        throw new Error(`The ${node.type} node is not supported here.`);
    }
  }

  function validateTextContent(nodes: unknown, depth: number): RichTextText[] {
    if (nodes === undefined) return [];
    if (!Array.isArray(nodes)) throw new Error("Text content is invalid.");
    return nodes.map((node) => {
      visit(depth);
      if (!isObject(node) || node.type !== "text" || typeof node.text !== "string" || node.text.length === 0 || !exactKeys(node, ["type", "text", "marks"])) {
        throw new Error("Only non-empty text nodes are supported in paragraphs and headings.");
      }
      return { type: "text", text: node.text, ...(node.marks === undefined ? {} : { marks: validateMarks(node.marks) }) };
    });
  }

  function validateListItem(node: unknown, depth: number): RichTextListItem {
    visit(depth);
    if (!isObject(node) || node.type !== "listItem" || !exactKeys(node, ["type", "content"]) || !Array.isArray(node.content) || node.content.length === 0 || !isObject(node.content[0]) || node.content[0].type !== "paragraph") {
      throw new Error("List items must start with a paragraph.");
    }
    const first = validateParagraph(node.content[0], depth + 1);
    const rest = node.content.slice(1).map((child) => {
      if (!isObject(child) || !["paragraph", "bulletList", "orderedList", "blockquote"].includes(String(child.type))) throw new Error("List items cannot contain headings or unsupported blocks.");
      return validateBlock(child, depth + 1) as RichTextListItemChild;
    });
    return { type: "listItem", content: [first, ...rest] };
  }

  function validateParagraph(node: unknown, depth: number): RichTextParagraph {
    visit(depth);
    if (!isObject(node) || node.type !== "paragraph" || !exactKeys(node, ["type", "content"])) throw new Error("Paragraph is invalid.");
    const content = validateTextContent(node.content, depth + 1);
    return { type: "paragraph", ...(content.length ? { content } : {}) };
  }

  function validateQuoteChild(node: unknown, depth: number): RichTextBlockquoteChild {
    if (!isObject(node) || !["paragraph", "bulletList", "orderedList", "blockquote"].includes(String(node.type))) throw new Error("Blockquotes cannot contain headings or unsupported blocks.");
    return validateBlock(node, depth) as RichTextBlockquoteChild;
  }

  function validateMarks(marks: unknown): RichTextMark[] {
    if (!Array.isArray(marks)) throw new Error("Text marks are invalid.");
    const seen = new Set<string>();
    const result = marks.map((mark) => {
      if (!isObject(mark) || typeof mark.type !== "string" || seen.has(mark.type)) throw new Error("Text marks must be distinct and supported.");
      seen.add(mark.type);
      if (mark.type === "bold" || mark.type === "italic") {
        if (!exactKeys(mark, ["type"])) throw new Error("Text mark attributes are invalid.");
        return { type: mark.type } as RichTextMark;
      }
      if (mark.type === "link" && isObject(mark.attrs) && exactKeys(mark.attrs, ["href"]) && typeof mark.attrs.href === "string" && isSafeLink(mark.attrs.href)) {
        return { type: "link", attrs: { href: mark.attrs.href } } as RichTextMark;
      }
      throw new Error("Only safe http(s) and mailto links are supported.");
    });
    return result.sort((a, b) => markOrder(a.type) - markOrder(b.type));
  }
}

function invalid(error: string): RichTextValidation {
  return { ok: false, error };
}

function isObject(value: unknown): value is RichObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function exactKeys(value: RichObject, allowed: string[]): boolean {
  return Object.keys(value).every((key) => allowed.includes(key));
}

function requireAllowedKeys(value: RichObject, allowed: string[]): void {
  if (!exactKeys(value, allowed)) throw new Error("The document contains unsupported attributes.");
}

function markOrder(type: string): number {
  return type === "bold" ? 0 : type === "italic" ? 1 : 2;
}

function isSafeLink(value: string): boolean {
  try {
    const url = new URL(value);
    if (url.protocol === "http:" || url.protocol === "https:") return Boolean(url.hostname);
    return url.protocol === "mailto:" && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(url.pathname);
  } catch {
    return false;
  }
}
