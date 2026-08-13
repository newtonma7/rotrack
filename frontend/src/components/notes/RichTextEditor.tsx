"use client";

import { useEffect, useState } from "react";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Link from "@tiptap/extension-link";
import { validateRichTextDocument } from "@/lib/rich-text";
import type { RichTextDocument } from "@/types/notes";

const EMPTY_DOCUMENT: RichTextDocument = { schemaVersion: 1, document: { type: "doc", content: [] } };

export function isSafeRichTextLink(value: string): boolean {
  try {
    const url = new URL(value.trim());
    if (url.protocol === "http:" || url.protocol === "https:") return Boolean(url.hostname);
    if (url.protocol === "mailto:") return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(url.pathname);
    return false;
  } catch {
    return false;
  }
}

export function RichTextEditor({
  initialContent = EMPTY_DOCUMENT,
  onChange,
  onSelectedLinkChange,
  ariaLabel = "Note content",
}: {
  initialContent?: RichTextDocument;
  onChange?: (document: RichTextDocument, text: string) => void;
  onSelectedLinkChange?: (href: string | null) => void;
  ariaLabel?: string;
}) {
  const [linkOpen, setLinkOpen] = useState(false);
  const [linkValue, setLinkValue] = useState("");
  const [linkError, setLinkError] = useState<string | null>(null);
  const [selectedLink, setSelectedLink] = useState<string | null>(null);
  const editor = useEditor({
    immediatelyRender: false,
    content: initialContent.document,
    extensions: [
      StarterKit.configure({
        code: false,
        codeBlock: false,
        hardBreak: false,
        horizontalRule: false,
        strike: false,
        link: false,
        heading: { levels: [2, 3] },
      }),
      Link.configure({ autolink: false, openOnClick: false, protocols: ["http", "https", "mailto"], validate: isSafeRichTextLink, HTMLAttributes: { target: "_blank", rel: "noopener noreferrer" } }),
    ],
    editorProps: {
      attributes: {
        class: "min-h-64 px-5 py-4 outline-none [&_blockquote]:border-l-4 [&_blockquote]:border-[var(--rt-orange)] [&_blockquote]:pl-4 [&_h2]:font-display [&_h2]:mt-5 [&_h2]:text-2xl [&_h3]:font-display [&_h3]:mt-4 [&_h3]:text-xl [&_ol]:list-decimal [&_ol]:pl-6 [&_p]:mb-3 [&_ul]:list-disc [&_ul]:pl-6",
        "aria-label": ariaLabel,
      },
    },
    onUpdate: ({ editor: current }) => {
      const draft = { schemaVersion: 1 as const, document: { type: "doc" as const, content: (current.getJSON() as unknown as { content?: RichTextDocument["document"]["content"] }).content ?? [] } };
      const validation = validateRichTextDocument(draft);
      onChange?.(validation.ok ? validation.document : draft, current.getText());
    },
    onSelectionUpdate: ({ editor: current }) => {
      const href = current.getAttributes("link").href ?? null;
      setSelectedLink(href);
      onSelectedLinkChange?.(href);
    },
  });

  useEffect(() => () => editor?.destroy(), [editor]);

  useEffect(() => {
    if (!editor) return;
    const handleLinkClick = (event: MouseEvent) => {
      const anchor = (event.target as Element | null)?.closest("a");
      const href = anchor?.getAttribute("href");
      if (!href) return;
      event.preventDefault();
      setSelectedLink(href);
      onSelectedLinkChange?.(href);
    };
    const editorDom = editor.view.dom;
    editorDom.addEventListener("click", handleLinkClick);
    return () => editorDom.removeEventListener("click", handleLinkClick);
  }, [editor, onSelectedLinkChange]);

  useEffect(() => {
    if (!editor) return;
    const nextContent = initialContent.document;
    if (JSON.stringify(editor.getJSON()) !== JSON.stringify(nextContent)) {
      editor.commands.setContent(nextContent, { emitUpdate: false });
    }
  }, [editor, initialContent]);

  if (!editor) return <div className="min-h-64" aria-label={ariaLabel} />;

  const openLinkDialog = () => {
    setLinkValue(editor.getAttributes("link").href ?? "");
    setLinkError(null);
    setLinkOpen(true);
  };

  const openSelectedLink = () => {
    if (!selectedLink) return;
    const opened = window.open(selectedLink, "_blank", "noopener,noreferrer");
    if (opened) opened.opener = null;
  };

  const applyLink = () => {
    const value = linkValue.trim();
    if (!value) {
      editor.chain().focus().unsetLink().run();
      setLinkOpen(false);
      return;
    }
    if (!isSafeRichTextLink(value)) {
      setLinkError("Use an absolute http(s) URL or a valid mailto address.");
      return;
    }
    editor.chain().focus().setLink({ href: value }).run();
    setLinkOpen(false);
  };

  return (
    <div className="overflow-hidden rounded-[24px] border border-[var(--rt-line)] bg-[var(--rt-paper)]">
      <div className="flex flex-wrap items-center gap-1 border-b border-[var(--rt-line)] bg-[var(--rt-cream-soft)] p-2" aria-label="Editor toolbar">
        <ToolbarButton label="Paragraph" active={editor.isActive("paragraph")} onClick={() => editor.chain().focus().setParagraph().run()}>¶</ToolbarButton>
        <ToolbarButton label="Heading 2" active={editor.isActive("heading", { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>H2</ToolbarButton>
        <ToolbarButton label="Heading 3" active={editor.isActive("heading", { level: 3 })} onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}>H3</ToolbarButton>
        <ToolbarButton label="Bold" active={editor.isActive("bold")} onClick={() => editor.chain().focus().toggleBold().run()}>B</ToolbarButton>
        <ToolbarButton label="Italic" active={editor.isActive("italic")} onClick={() => editor.chain().focus().toggleItalic().run()}><em>I</em></ToolbarButton>
        <ToolbarButton label="Bullet list" active={editor.isActive("bulletList")} onClick={() => editor.chain().focus().toggleBulletList().run()}>• list</ToolbarButton>
        <ToolbarButton label="Ordered list" active={editor.isActive("orderedList")} onClick={() => editor.chain().focus().toggleOrderedList().run()}>1. list</ToolbarButton>
        <ToolbarButton label="Blockquote" active={editor.isActive("blockquote")} onClick={() => editor.chain().focus().toggleBlockquote().run()}>“</ToolbarButton>
        <ToolbarButton label="Link" active={editor.isActive("link")} onClick={openLinkDialog}>↗</ToolbarButton>
        <ToolbarButton label="Open selected link" onClick={openSelectedLink} disabled={!selectedLink}>open</ToolbarButton>
        <span className="mx-1 h-6 w-px bg-[var(--rt-line)]" aria-hidden="true" />
        <ToolbarButton label="Undo" onClick={() => editor.chain().focus().undo().run()} disabled={!editor.can().undo()}>↶</ToolbarButton>
        <ToolbarButton label="Redo" onClick={() => editor.chain().focus().redo().run()} disabled={!editor.can().redo()}>↷</ToolbarButton>
      </div>
      <EditorContent editor={editor} />
      {linkOpen && (
        <div className="border-t border-[var(--rt-line)] bg-[var(--rt-cream)] p-4" role="dialog" aria-label="Add link">
          <label htmlFor="rich-link" className="block text-sm font-semibold">Link URL</label>
          <div className="mt-2 flex flex-wrap gap-2">
            <input id="rich-link" value={linkValue} onChange={(event) => setLinkValue(event.target.value)} className="min-w-0 flex-1 rounded-full border border-[var(--rt-line)] bg-[var(--rt-paper)] px-4 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-[var(--rt-orange)]" autoFocus />
            <button type="button" onClick={applyLink} className="rounded-full bg-[var(--rt-ink)] px-4 py-2 text-sm font-semibold text-[var(--rt-cream)]">Apply</button>
            <button type="button" onClick={() => setLinkOpen(false)} className="rounded-full border border-[var(--rt-ink)] px-4 py-2 text-sm">Cancel</button>
          </div>
          {linkError && <p className="mt-2 text-sm text-[var(--rt-orange-deep)]" role="alert">{linkError}</p>}
        </div>
      )}
    </div>
  );
}

function ToolbarButton({ label, active, disabled, onClick, children }: { label: string; active?: boolean; disabled?: boolean; onClick: () => void; children: React.ReactNode }) {
  return <button type="button" aria-label={label} aria-pressed={active} title={label} disabled={disabled} onClick={onClick} className={`min-h-8 rounded-full px-2.5 text-xs font-semibold outline-none focus-visible:ring-2 focus-visible:ring-[var(--rt-orange)] disabled:opacity-40 ${active ? "bg-[var(--rt-ink)] text-[var(--rt-cream)]" : "hover:bg-[var(--rt-paper)]"}`}>{children}</button>;
}
