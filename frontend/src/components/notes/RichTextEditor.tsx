"use client";

import { useEffect, useRef, useState } from "react";
import { Extension } from "@tiptap/core";
import type { Editor } from "@tiptap/core";
import { Plugin } from "@tiptap/pm/state";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Link from "@tiptap/extension-link";
import TaskItem from "@tiptap/extension-task-item";
import TaskList from "@tiptap/extension-task-list";
import { isSafeRichTextLink, validateRichTextDocument } from "@/lib/rich-text";
import type { RichTextDocument } from "@/types/notes";

const EMPTY_DOCUMENT: RichTextDocument = { schemaVersion: 1, document: { type: "doc", content: [] } };
const SLASH_MENU_ID = "rich-text-slash-menu";
const SLASH_OPTIONS = [
  { id: "paragraph", label: "Paragraph", keywords: ["paragraph", "text"] },
  { id: "heading-2", label: "Heading 2", keywords: ["heading", "h2", "title"] },
  { id: "heading-3", label: "Heading 3", keywords: ["heading", "h3", "subtitle"] },
  { id: "bullet-list", label: "Bullet list", keywords: ["bullet", "bul", "list"] },
  { id: "ordered-list", label: "Ordered list", keywords: ["ordered", "number", "numbered", "ol"] },
  { id: "checklist", label: "Checklist", keywords: ["checklist", "check", "task", "todo"] },
  { id: "blockquote", label: "Blockquote", keywords: ["blockquote", "quote"] },
] as const;

type SlashOptionId = (typeof SLASH_OPTIONS)[number]["id"];

type SlashMenuState = { query: string } | null;

// Block editor transactions that cannot cross the server's canonical rich-text trust boundary.
const canonicalDocumentGuard = Extension.create({
  name: "canonicalRichTextDocumentGuard",
  addProseMirrorPlugins() {
    return [
      new Plugin({
        filterTransaction: (transaction) => {
          if (!transaction.docChanged) return true;
          return validateTiptapDocument(transaction.doc.toJSON()).ok;
        },
      }),
    ];
  },
});

export function RichTextEditor({
  initialContent = EMPTY_DOCUMENT,
  onChange,
  ariaLabel = "Note content",
  placeholder,
  variant = "card",
}: {
  initialContent?: RichTextDocument;
  onChange?: (document: RichTextDocument, text: string) => void;
  ariaLabel?: string;
  placeholder?: string;
  variant?: "card" | "journal";
}) {
  const isJournal = variant === "journal";
  const [linkOpen, setLinkOpen] = useState(false);
  const [linkValue, setLinkValue] = useState("");
  const [linkError, setLinkError] = useState<string | null>(null);
  const [selectedLink, setSelectedLink] = useState<string | null>(null);
  const [empty, setEmpty] = useState(true);
  const [slashMenu, setSlashMenu] = useState<SlashMenuState>(null);
  const [slashActiveIndex, setSlashActiveIndex] = useState(0);
  const slashMenuRef = useRef<SlashMenuState>(null);
  const slashActiveIndexRef = useRef(0);
  const slashTriggeredRef = useRef(false);
  const editorRef = useRef<Editor | null>(null);
  const slashOptionRefs = useRef<Record<string, HTMLButtonElement | null>>({});

  const updateSlashMenu = (next: SlashMenuState) => {
    const previous = slashMenuRef.current;
    slashMenuRef.current = next;
    setSlashMenu(next);
    if (!next || !previous || next.query !== previous.query) {
      slashActiveIndexRef.current = 0;
      setSlashActiveIndex(0);
    }
  };

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
        underline: false,
        link: false,
        heading: { levels: [2, 3] },
      }),
      Link.configure({ autolink: false, openOnClick: false, protocols: ["http", "https", "mailto"], validate: isSafeRichTextLink, HTMLAttributes: { target: "_blank", rel: "noopener noreferrer" } }),
      TaskList,
      TaskItem.configure({ nested: true, a11y: { checkboxLabel: () => "Checklist item" } }),
      canonicalDocumentGuard,
    ],
    editorProps: {
      attributes: {
        class: isJournal ? "min-h-[50vh] px-0 py-6 outline-none lg:min-h-[40vh] [&_blockquote]:border-l-4 [&_blockquote]:border-[var(--rt-orange)] [&_blockquote]:pl-4 [&_h2]:font-display [&_h2]:mt-5 [&_h2]:text-2xl [&_h3]:font-display [&_h3]:mt-4 [&_h3]:text-xl [&_ol]:list-decimal [&_ol]:pl-6 [&_p]:mb-3 [&_ul]:list-disc [&_ul]:pl-6" : "min-h-64 px-5 py-4 outline-none [&_blockquote]:border-l-4 [&_blockquote]:border-[var(--rt-orange)] [&_blockquote]:pl-4 [&_h2]:font-display [&_h2]:mt-5 [&_h2]:text-2xl [&_h3]:font-display [&_h3]:mt-4 [&_h3]:text-xl [&_ol]:list-decimal [&_ol]:pl-6 [&_p]:mb-3 [&_ul]:list-disc [&_ul]:pl-6",
        "aria-label": ariaLabel,
        "aria-haspopup": "listbox",
        "aria-expanded": "false",
      },
      handleKeyDown: (_view, event) => {
        const current = editorRef.current;
        if (!current) return false;
        if (!slashMenuRef.current && event.key === "/" && current.state.selection.$from.parent.type.name === "paragraph" && current.state.selection.$from.parent.textContent === "") {
          slashTriggeredRef.current = true;
          updateSlashMenu({ query: "" });
          return false;
        }
        const menu = slashMenuRef.current;
        if (!menu) return false;
        if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
          updateSlashMenu({ query: menu.query + event.key });
          return false;
        }
        if (event.key === "Backspace") {
          const query = menu.query.slice(0, -1);
          if (query) updateSlashMenu({ query });
          else {
            slashTriggeredRef.current = false;
            updateSlashMenu(null);
          }
          return false;
        }
        const options = getSlashOptions(menu.query);
        if ((event.key === "ArrowDown" || event.key === "ArrowUp") && options.length > 0) {
          event.preventDefault();
          const delta = event.key === "ArrowDown" ? 1 : -1;
          const nextIndex = (slashActiveIndexRef.current + delta + options.length) % options.length;
          slashActiveIndexRef.current = nextIndex;
          setSlashActiveIndex(nextIndex);
          return true;
        }
        if (event.key === "Escape") {
          event.preventDefault();
          slashTriggeredRef.current = false;
          updateSlashMenu(null);
          current.commands.focus();
          return true;
        }
        if (event.key === "Enter" && options.length > 0) {
          event.preventDefault();
          applySlashOption(current, options[slashActiveIndexRef.current]?.id ?? options[0].id);
          slashTriggeredRef.current = false;
          updateSlashMenu(null);
          return true;
        }
        return false;
      },
      handleDOMEvents: {
        input: () => {
          const current = editorRef.current;
          if (!current) return false;
          if (!slashTriggeredRef.current) {
            const next = getSlashMenuState(current);
            if (next?.query === "") {
              slashTriggeredRef.current = true;
              updateSlashMenu(next);
            }
          } else {
            updateSlashMenu(getSlashMenuState(current));
          }
          return false;
        },
        mouseup: () => {
          const current = editorRef.current;
          if (current && slashMenuRef.current) {
            slashTriggeredRef.current = false;
            updateSlashMenu(null);
          }
          return false;
        },
      },
    },
    onCreate: ({ editor: current }) => {
      editorRef.current = current;
      setEmpty(current.isEmpty);
    },
    onUpdate: ({ editor: current }) => {
      setEmpty(current.isEmpty);
      const validation = validateTiptapDocument(current.getJSON());
      const text = current.getText();
      const hasText = current.getText({ blockSeparator: "" }).length > 0;
      onChange?.(validation.ok ? validation.document : toRichTextDocument(current.getJSON()), hasText ? text : "");
      const nextSlashMenu = getSlashMenuState(current);
      if (slashTriggeredRef.current) updateSlashMenu(nextSlashMenu);
      else if (nextSlashMenu?.query === "") {
        slashTriggeredRef.current = true;
        updateSlashMenu(nextSlashMenu);
      }
    },
    onSelectionUpdate: ({ editor: current }) => {
      const href = current.getAttributes("link").href ?? null;
      setSelectedLink(href);
      if (slashMenuRef.current) updateSlashMenu(getSlashMenuState(current));
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
    };
    const editorDom = editor.view.dom;
    editorDom.addEventListener("click", handleLinkClick);
    return () => editorDom.removeEventListener("click", handleLinkClick);
  }, [editor]);

  useEffect(() => {
    if (!editor) return;
    const nextContent = initialContent.document;
    if (JSON.stringify(editor.getJSON()) !== JSON.stringify(nextContent)) {
      editor.commands.setContent(nextContent, { emitUpdate: false });
    }
    const frame = requestAnimationFrame(() => setEmpty(editor.isEmpty));
    return () => cancelAnimationFrame(frame);
  }, [editor, initialContent]);

  useEffect(() => {
    if (!editor) return;
    const dom = editor.view.dom;
    dom.setAttribute("aria-expanded", slashMenu ? "true" : "false");
    if (slashMenu) dom.setAttribute("aria-controls", SLASH_MENU_ID);
    else dom.removeAttribute("aria-controls");
    const options = getSlashOptions(slashMenu?.query ?? "");
    const active = slashMenu && options.length > 0 ? options[Math.min(slashActiveIndex, options.length - 1)] : undefined;
    if (active) dom.setAttribute("aria-activedescendant", slashOptionId(active.id));
    else dom.removeAttribute("aria-activedescendant");
  }, [editor, slashMenu, slashActiveIndex]);

  useEffect(() => {
    if (!slashMenu) return;
    const options = getSlashOptions(slashMenu.query);
    const active = options[Math.min(slashActiveIndex, options.length - 1)];
    slashOptionRefs.current[active?.id ?? ""]?.scrollIntoView?.({ block: "nearest" });
  }, [slashMenu, slashActiveIndex]);

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

  const slashOptions = getSlashOptions(slashMenu?.query ?? "");
  const activeSlashIndex = Math.min(slashActiveIndex, Math.max(slashOptions.length - 1, 0));

  return (
    <div className={isJournal ? "group/rich-editor flex min-w-0 flex-col" : "overflow-hidden rounded-[28px] border border-[var(--rt-line)] bg-[var(--rt-paper)]"}>
      <div className={isJournal ? "order-2 mt-4 flex flex-wrap items-center gap-1 border-t border-[var(--rt-line)] pt-3 opacity-0 transition-opacity group-hover/rich-editor:opacity-100 group-focus-within/rich-editor:opacity-100" : "flex flex-wrap items-center gap-1 border-b border-[var(--rt-line)] bg-[var(--rt-cream-soft)] p-2"} aria-label="Editor toolbar">
        <ToolbarButton label="Paragraph" active={editor.isActive("paragraph")} onClick={() => editor.chain().focus().setParagraph().run()}>¶</ToolbarButton>
        <ToolbarButton label="Heading 2" active={editor.isActive("heading", { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>H2</ToolbarButton>
        <ToolbarButton label="Heading 3" active={editor.isActive("heading", { level: 3 })} onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}>H3</ToolbarButton>
        <ToolbarButton label="Bold" active={editor.isActive("bold")} onClick={() => editor.chain().focus().toggleBold().run()}>B</ToolbarButton>
        <ToolbarButton label="Italic" active={editor.isActive("italic")} onClick={() => editor.chain().focus().toggleItalic().run()}><em>I</em></ToolbarButton>
        <ToolbarButton label="Bullet list" active={editor.isActive("bulletList")} onClick={() => editor.chain().focus().toggleBulletList().run()}>• list</ToolbarButton>
        <ToolbarButton label="Ordered list" active={editor.isActive("orderedList")} onClick={() => editor.chain().focus().toggleOrderedList().run()}>1. list</ToolbarButton>
        <ToolbarButton label="Checklist" active={editor.isActive("taskList")} onClick={() => editor.chain().focus().toggleTaskList().run()}>☑ list</ToolbarButton>
        <ToolbarButton label="Blockquote" active={editor.isActive("blockquote")} onClick={() => editor.chain().focus().toggleBlockquote().run()}>“</ToolbarButton>
        <ToolbarButton label="Link" active={editor.isActive("link")} onClick={openLinkDialog}>↗</ToolbarButton>
        <ToolbarButton label="Open selected link" onClick={openSelectedLink} disabled={!selectedLink}>open</ToolbarButton>
        <span className="mx-1 h-6 w-px bg-[var(--rt-line)]" aria-hidden="true" />
        <ToolbarButton label="Undo" onClick={() => editor.chain().focus().undo().run()} disabled={!editor.can().undo()}>↶</ToolbarButton>
        <ToolbarButton label="Redo" onClick={() => editor.chain().focus().redo().run()} disabled={!editor.can().redo()}>↷</ToolbarButton>
      </div>
      <div className={isJournal ? "relative order-1" : "relative"}>
        {placeholder && empty && <p aria-hidden="true" className="pointer-events-none absolute left-0 top-6 text-[1.12rem] leading-[1.8] text-[var(--rt-ink-muted)]/50">{placeholder}</p>}
        <EditorContent editor={editor} />
        <div id={SLASH_MENU_ID} role="listbox" aria-label="Insert block" hidden={!slashMenu} className="absolute left-2 right-2 z-10 max-h-60 overflow-y-auto rounded-2xl border border-[var(--rt-line)] bg-[var(--rt-paper)] p-1 shadow-[0_20px_40px_-24px_rgba(10,10,10,0.3)]">
          {slashOptions.length === 0 ? <p className="px-3 py-2 text-sm text-[var(--rt-ink-muted)]">No matching blocks</p> : slashOptions.map((option, index) => (
            <button
              key={option.id}
              id={slashOptionId(option.id)}
              ref={(element) => { slashOptionRefs.current[option.id] = element; }}
              type="button"
              role="option"
              aria-selected={index === activeSlashIndex}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => { applySlashOption(editor, option.id); slashTriggeredRef.current = false; updateSlashMenu(null); }}
              className={`block w-full rounded-xl px-3 py-2 text-left text-sm ${index === activeSlashIndex ? "bg-[var(--rt-cream-soft)]" : "hover:bg-[var(--rt-cream-soft)]"}`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>
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

function getSlashOptions(query: string) {
  const normalized = query.toLocaleLowerCase();
  return SLASH_OPTIONS.filter((option) => option.label.toLocaleLowerCase().startsWith(normalized) || option.keywords.some((keyword) => keyword.startsWith(normalized)));
}

function slashOptionId(id: SlashOptionId): string {
  return `rich-text-slash-option-${id}`;
}

function getSlashMenuState(editor: Editor): SlashMenuState {
  const { $from, empty } = editor.state.selection;
  const paragraph = $from.parent;
  const text = paragraph.textContent;
  if (!empty || paragraph.type.name !== "paragraph" || $from.parentOffset !== paragraph.content.size || !text.startsWith("/")) return null;
  return { query: text.slice(1) };
}

function applySlashOption(editor: Editor, id: SlashOptionId): void {
  const { $from, to } = editor.state.selection;
  const range = { from: $from.start(), to };
  const chain = editor.chain().focus().deleteRange(range);
  if (id === "heading-2") chain.toggleHeading({ level: 2 });
  else if (id === "heading-3") chain.toggleHeading({ level: 3 });
  else if (id === "bullet-list") chain.toggleBulletList();
  else if (id === "ordered-list") chain.toggleOrderedList();
  else if (id === "checklist") chain.toggleTaskList();
  else if (id === "blockquote") chain.toggleBlockquote();
  else chain.setParagraph();
  chain.run();
}

function toRichTextDocument(value: unknown): RichTextDocument {
  const json = adaptTiptapJson(value) as { type?: unknown; content?: RichTextDocument["document"]["content"] };
  return {
    schemaVersion: 1,
    document: {
      type: "doc",
      content: json.type === "doc" && Array.isArray(json.content) ? json.content : [],
    },
  };
}

function validateTiptapDocument(value: unknown) {
  const json = adaptTiptapJson(value) as { type?: unknown; content?: unknown };
  if (json.type !== "doc" || !Array.isArray(json.content)) return { ok: false as const, error: "The document root is invalid." };
  return validateRichTextDocument({ schemaVersion: 1, document: { type: "doc", content: json.content as RichTextDocument["document"]["content"] } });
}

function adaptTiptapJson(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(adaptTiptapJson);
  if (!value || typeof value !== "object") return value;
  const node = Object.fromEntries(Object.entries(value).map(([key, child]) => [key, adaptTiptapJson(child)]));
  if (node.type === "orderedList" && node.attrs && typeof node.attrs === "object" && (node.attrs as { type?: unknown }).type === null) {
    const attrs = { ...node.attrs as Record<string, unknown> };
    delete attrs.type;
    node.attrs = attrs;
  }
  return node;
}

function ToolbarButton({ label, active, disabled, onClick, children }: { label: string; active?: boolean; disabled?: boolean; onClick: () => void; children: React.ReactNode }) {
  return <button type="button" aria-label={label} aria-pressed={active} title={label} disabled={disabled} onClick={onClick} className={`min-h-8 rounded-full px-2.5 text-xs font-semibold outline-none focus-visible:ring-2 focus-visible:ring-[var(--rt-orange)] disabled:opacity-40 ${active ? "bg-[var(--rt-ink)] text-[var(--rt-cream)]" : "hover:bg-[var(--rt-paper)]"}`}>{children}</button>;
}
