import type { RichTextBlock, RichTextDocument, RichTextListItem, RichTextTaskItem, RichTextText } from "@/types/notes";

type ClipboardNode = RichTextBlock | RichTextListItem | RichTextTaskItem | RichTextText;

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character] ?? character);
}

/** Serialize the validated v1 tree, never HTML supplied by an editor or API caller. */
export function richTextToHtml(document: RichTextDocument): string {
  const render = (node: ClipboardNode): string => {
    if (node.type === "text") {
      let text = escapeHtml(node.text);
      for (const mark of node.marks ?? []) {
        switch (mark.type) {
          case "bold": text = `<strong>${text}</strong>`; break;
          case "italic": text = `<em>${text}</em>`; break;
          case "link": text = `<a href="${escapeHtml(mark.attrs.href)}">${text}</a>`; break;
          default: {
            const exhaustive: never = mark;
            return exhaustive;
          }
        }
      }
      return text;
    }
    const content = (node.content ?? []).map(render).join("");
    switch (node.type) {
      case "paragraph": return `<p>${content}</p>`;
      case "heading": return `<h${node.attrs.level}>${content}</h${node.attrs.level}>`;
      case "bulletList": return `<ul>${content}</ul>`;
      case "orderedList": return `<ol${node.attrs?.start === undefined ? "" : ` start="${node.attrs.start}"`}>${content}</ol>`;
      case "taskList": return `<ul data-type="taskList">${content}</ul>`;
      case "taskItem": return `<li data-type="taskItem" data-checked="${node.attrs.checked}"><label><input type="checkbox"${node.attrs.checked ? " checked" : ""} disabled aria-label="Checklist item"><span></span></label><div>${content}</div></li>`;
      case "listItem": return `<li>${content}</li>`;
      case "blockquote": return `<blockquote>${content}</blockquote>`;
      default: {
        const exhaustive: never = node;
        return exhaustive;
      }
    }
  };
  return document.document.content.map(render).join("");
}
