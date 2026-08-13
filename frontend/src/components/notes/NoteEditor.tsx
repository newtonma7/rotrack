"use client";

import { forwardRef, useImperativeHandle, useState } from "react";
import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { RichTextEditor } from "@/components/notes/RichTextEditor";
import { useNoteAutosave } from "@/hooks/useNoteAutosave";
import type { Note, RichTextDocument } from "@/types/notes";

export interface NoteEditorHandle {
  flush: () => Promise<boolean>;
  hasUnsaved: () => boolean;
  saveAsNew: () => Promise<boolean>;
}

type AttachmentOption = { id: string; label: string };

export const NoteEditor = forwardRef<NoteEditorHandle, {
  initialNote?: Note | null;
  activeEntryId?: string | null;
  attachments?: AttachmentOption[];
  onSaved?: (note: Note) => void;
  onDelete?: (note: Note) => void;
  onReload?: () => Promise<Note | null>;
}>(({ initialNote, activeEntryId = null, attachments = [], onSaved, onDelete, onReload }, ref) => {
  const autosave = useNoteAutosave({ initialNote, activeEntryId, onSaved });
  useImperativeHandle(ref, () => ({ flush: autosave.flush, hasUnsaved: () => autosave.dirty, saveAsNew: autosave.saveAsNew }), [autosave.dirty, autosave.flush, autosave.saveAsNew]);

  const copy = async () => {
    try {
      const plain = autosave.contentText;
      const html = richTextToHtml(autosave.document);
      if (navigator.clipboard?.write && typeof ClipboardItem !== "undefined") {
        await navigator.clipboard.write([new ClipboardItem({ "text/html": new Blob([html], { type: "text/html" }), "text/plain": new Blob([plain], { type: "text/plain" }) })]);
      } else if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(plain);
      } else {
        throw new Error("Clipboard unavailable");
      }
      setCopyStatus("Copied rich text and plain text.");
    } catch {
      setCopyStatus("Copy is unavailable in this browser.");
    }
  };
  const [copyStatus, setCopyStatus] = useState<string | null>(null);

  const reload = async () => {
    if (!initialNote || !window.confirm("Discard your local edits and reload the server version?")) return;
    const freshNote = await onReload?.() ?? initialNote;
    autosave.reloadServerVersion(freshNote);
  };

  return (
    <section aria-label="Note editor" className="min-w-0 rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-5 shadow-[0_20px_50px_-20px_rgba(10,10,10,0.12)] md:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <label htmlFor="note-title" className="sr-only">Note title</label>
          <input id="note-title" value={autosave.title} onChange={(event) => autosave.setTitle(event.target.value)} placeholder="untitled note" maxLength={120} className="w-full bg-transparent font-display text-3xl tracking-[-0.02em] outline-none placeholder:text-[var(--rt-ink-muted)] focus-visible:ring-2 focus-visible:ring-[var(--rt-orange)] md:text-4xl" />
          <p className="mt-2 text-sm text-[var(--rt-ink-muted)]">{autosave.note?.timeEntryId ? "attached to a time entry" : "standalone note"}</p>
        </div>
        <div className="flex items-center gap-2">
          <span role="status" aria-live="polite" className="rounded-full bg-[var(--rt-cream-soft)] px-3 py-1 text-xs font-semibold">{autosave.status}</span>
          {autosave.note && <Button type="button" variant="ghost" aria-label="Delete note" onClick={() => onDelete?.(autosave.note!)} className="rounded-full text-[var(--rt-ink-muted)] hover:text-[var(--rt-orange-deep)]"><Trash2 aria-hidden="true" /> Delete</Button>}
        </div>
      </div>

      <div className="mt-6"><RichTextEditor initialContent={autosave.document} onChange={autosave.setContent} /></div>
      <div className="mt-5 flex flex-wrap items-end gap-4">
        <label className="min-w-56 flex-1 text-sm font-semibold" htmlFor="note-attachment">Attachment
          <select id="note-attachment" value={autosave.attachmentId ?? "standalone"} onChange={(event) => autosave.setAttachment(event.target.value === "standalone" ? null : event.target.value)} className="mt-2 block w-full rounded-full border border-[var(--rt-line)] bg-[var(--rt-paper)] px-4 py-2 font-normal outline-none focus-visible:ring-2 focus-visible:ring-[var(--rt-orange)]">
            <option value="standalone">Standalone</option>
            {attachments.map((attachment) => <option key={attachment.id} value={attachment.id}>{attachment.label}</option>)}
          </select>
        </label>
        <Button type="button" variant="outline" onClick={() => void copy()} className="rounded-full">Copy</Button>
        {autosave.status === "Conflict" && <>
          <Button type="button" variant="outline" onClick={() => void reload()} className="rounded-full">Reload server version</Button>
          <Button type="button" onClick={() => void autosave.saveAsNew()} className="rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] hover:bg-[var(--rt-orange-deep)]">Save as new Note</Button>
        </>}
        {(autosave.status === "Offline" || autosave.status === "Waiting") && <Button type="button" variant="outline" onClick={autosave.retry} className="rounded-full">Retry</Button>}
      </div>
      {copyStatus && <p role="status" aria-live="polite" className="mt-3 text-sm text-[var(--rt-ink-muted)]">{copyStatus}</p>}
      {autosave.error && <p role="alert" className="mt-3 rounded-2xl border border-[var(--rt-orange)] bg-[var(--rt-orange-soft)] px-4 py-3 text-sm">{autosave.error}</p>}
      {autosave.sizeError && <p role="alert" className="mt-3 rounded-2xl border border-[var(--rt-orange)] bg-[var(--rt-orange-soft)] px-4 py-3 text-sm">{autosave.sizeError}</p>}
    </section>
  );
});
NoteEditor.displayName = "NoteEditor";

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character] ?? character);
}

type ClipboardNode = {
  type: string;
  text?: string;
  attrs?: { href?: string; level?: number; start?: number };
  marks?: Array<{ type: string; attrs?: { href?: string } }>;
  content?: ClipboardNode[];
};

function richTextToHtml(document: RichTextDocument): string {
  const render = (node: ClipboardNode): string => {
    if (node.type === "text") {
      let text = escapeHtml(node.text ?? "");
      for (const mark of node.marks ?? []) {
        if (mark.type === "bold") text = `<strong>${text}</strong>`;
        if (mark.type === "italic") text = `<em>${text}</em>`;
        if (mark.type === "link") text = `<a href="${escapeHtml(mark.attrs?.href ?? "")}">${text}</a>`;
      }
      return text;
    }
    const content = (node.content ?? []).map(render).join("");
    switch (node.type) {
      case "heading": return `<h${node.attrs?.level ?? 2}>${content}</h${node.attrs?.level ?? 2}>`;
      case "bulletList": return `<ul>${content}</ul>`;
      case "orderedList": return `<ol>${content}</ol>`;
      case "listItem": return `<li>${content}</li>`;
      case "blockquote": return `<blockquote>${content}</blockquote>`;
      default: return `<p>${content}</p>`;
    }
  };
  return (document.document.content as unknown as ClipboardNode[]).map(render).join("");
}
