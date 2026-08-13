"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApplicationHeader } from "@/components/app/ApplicationHeader";
import { Button } from "@/components/ui/button";
import { NoteEditor, type NoteEditorHandle } from "@/components/notes/NoteEditor";
import { deleteNote, getActiveSession, getHistory, getNote, getNotes } from "@/lib/api";
import type { HistoryEntry } from "@/types/history";
import type { Note, NoteAttachmentFilter, NoteSummary } from "@/types/notes";
import type { TimeEntry } from "@/types/time-entry";

export function NotesWorkspace({ selectedNoteId = null }: { selectedNoteId?: string | null }) {
  const router = useRouter();
  const editorRef = useRef<NoteEditorHandle>(null);
  const requestSequence = useRef(0);
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [selectedNote, setSelectedNote] = useState<Note | null>(null);
  const [entries, setEntries] = useState<Array<HistoryEntry | TimeEntry>>([]);
  const [attachment, setAttachment] = useState<NoteAttachmentFilter | "ALL">("ALL");
  const [entryFilter, setEntryFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [detailLoading, setDetailLoading] = useState(Boolean(selectedNoteId));

  const loadList = useCallback(async (cursor?: string) => {
    const requestId = ++requestSequence.current;
    if (cursor) setLoadingMore(true); else setLoading(true);
    setError(null);
    try {
      const page = await getNotes({
        cursor,
        attachment: attachment === "ALL" ? undefined : attachment,
        timeEntryId: entryFilter || undefined,
      });
      if (requestId !== requestSequence.current) return;
      setNotes((current) => cursor ? dedupeSummaries([...current, ...page.notes]) : page.notes);
      setNextCursor(page.nextCursor);
    } catch (requestError) {
      if (requestId === requestSequence.current) setError(requestError instanceof Error ? requestError.message : "Notes could not be loaded.");
    } finally {
      if (requestId === requestSequence.current) { setLoading(false); setLoadingMore(false); }
    }
  }, [attachment, entryFilter]);

  useEffect(() => { void loadList(); }, [loadList]);

  useEffect(() => {
    let active = true;
    void Promise.all([getHistory(), getActiveSession()]).then(([history, current]) => {
      if (active) setEntries([...history.entries, ...(current ? [current] : [])]);
    }).catch(() => { /* Attachments are optional; the editor remains usable standalone. */ });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!selectedNoteId) {
      setSelectedNote(null);
      setDetailError(null);
      setDetailLoading(false);
      return;
    }
    let active = true;
    setDetailLoading(true);
    setDetailError(null);
    void getNote(selectedNoteId).then((note) => {
      if (active) setSelectedNote(note);
    }).catch((requestError) => {
      if (active) setDetailError(requestError instanceof Error ? requestError.message : "Note could not be loaded.");
    }).finally(() => {
      if (active) setDetailLoading(false);
    });
    return () => { active = false; };
  }, [selectedNoteId]);

  const leaveEditor = async (destination: string) => {
    const flushed = await editorRef.current?.flush() ?? true;
    if (!flushed && !window.confirm("Your edits could not be saved. Leave and lose these edits?")) return;
    router.push(destination);
  };

  useEffect(() => {
    const interceptNavigation = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const anchor = (event.target as Element | null)?.closest("a");
      if (!anchor || anchor.target === "_blank") return;
      const href = anchor.getAttribute("href");
      if (!href?.startsWith("/") || !editorRef.current?.hasUnsaved()) return;
      event.preventDefault();
      void leaveEditor(href);
    };
    document.addEventListener("click", interceptNavigation, true);
    return () => document.removeEventListener("click", interceptNavigation, true);
  });

  const handleSaved = (saved: Note) => {
    // Keep the draft editor mounted during its first create so keystrokes that landed
    // during the request cannot be lost to a route/key change. Explicit Save-as-new
    // starts from an existing URL and may switch to the new stable URL safely.
    if (selectedNoteId === saved.id) setSelectedNote(saved);
    if (selectedNoteId && selectedNoteId !== saved.id) {
      setSelectedNote(saved);
      router.replace(`/notes/${saved.id}`);
    }
    setNotes((current) => dedupeSummaries([saved, ...current]));
  };

  const handleDelete = async (note: Note) => {
    if (!window.confirm("Delete this Note permanently?")) return;
    try {
      await deleteNote(note.id, note.version);
      await loadList();
      router.push("/notes");
    } catch (requestError) {
      setDetailError(requestError instanceof Error ? requestError.message : "Note could not be deleted.");
    }
  };

  const attachmentOptions = entries.map((entry) => ({
    id: entry.id,
    label: `${entry.activityType} · ${new Date(entry.startTime).toLocaleDateString()}`,
  }));

  return (
    <div className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
      <ApplicationHeader />
      <main className="mx-auto max-w-[1400px] px-6 py-10 md:px-10 md:py-14">
        <div className="mb-8 flex flex-wrap items-end justify-between gap-5">
          <div><p className="mb-2 text-[0.8rem] font-semibold uppercase tracking-[0.2em] text-[var(--rt-orange)]">private study context</p><h1 className="font-display text-[clamp(2.8rem,7vw,5.5rem)] leading-[0.92]">notes<span className="text-[var(--rt-orange)]">.</span></h1></div>
          <Button onClick={() => void leaveEditor("/notes")} className="rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] hover:bg-[var(--rt-orange-deep)]">New note</Button>
        </div>
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(260px,0.75fr)_minmax(0,1.5fr)]">
          <aside aria-label="Notes library" className="rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-5 md:p-6">
            <div className="flex flex-wrap gap-2">
              {(["ALL", "ATTACHED", "STANDALONE"] as const).map((value) => <button key={value} type="button" aria-pressed={attachment === value} onClick={() => setAttachment(value)} className={`rounded-full border px-3 py-1.5 text-xs font-semibold ${attachment === value ? "border-[var(--rt-ink)] bg-[var(--rt-ink)] text-[var(--rt-cream)]" : "border-[var(--rt-line)]"}`}>{value === "ALL" ? "All" : value === "ATTACHED" ? "Attached" : "Standalone"}</button>)}
            </div>
            <label htmlFor="note-entry-filter" className="mt-4 block text-sm font-semibold">Exact time entry</label>
            <select id="note-entry-filter" value={entryFilter} onChange={(event) => setEntryFilter(event.target.value)} className="mt-2 w-full rounded-full border border-[var(--rt-line)] bg-[var(--rt-paper)] px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-[var(--rt-orange)]"><option value="">All entries</option>{attachmentOptions.map((entry) => <option key={entry.id} value={entry.id}>{entry.label}</option>)}</select>
            <div className="mt-5" aria-live="polite">
              {loading ? <p role="status" className="py-8 text-sm text-[var(--rt-ink-muted)]">Loading notes…</p> : error ? <div role="alert" className="py-4 text-sm"><p>{error}</p><Button variant="outline" onClick={() => void loadList()} className="mt-3 rounded-full">Try again</Button></div> : notes.length === 0 ? <p className="py-8 text-sm text-[var(--rt-ink-muted)]">No notes match these filters.</p> : <ul className="space-y-2">{notes.map((note) => <li key={note.id}><button type="button" onClick={() => void leaveEditor(`/notes/${note.id}`)} className={`w-full rounded-2xl border p-4 text-left outline-none transition focus-visible:ring-2 focus-visible:ring-[var(--rt-orange)] ${selectedNoteId === note.id ? "border-[var(--rt-orange)] bg-[var(--rt-orange-soft)]" : "border-transparent hover:border-[var(--rt-line)] hover:bg-[var(--rt-cream)]"}`}><span className="block truncate font-semibold">{note.title || note.preview || "untitled note"}</span><span className="mt-1 block truncate text-sm text-[var(--rt-ink-muted)]">{note.preview || "empty note"}</span><span className="mt-2 block text-xs text-[var(--rt-ink-muted)]">v{note.version} · {note.timeEntryId ? "attached" : "standalone"}</span></button></li>)}</ul>}
              {nextCursor && <Button variant="outline" onClick={() => void loadList(nextCursor)} disabled={loadingMore} className="mt-4 w-full rounded-full">{loadingMore ? "Loading…" : "Load more"}</Button>}
            </div>
          </aside>
          <div className="min-w-0">
            {detailError ? <div role="alert" className="rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-8"><p>{detailError}</p><Button variant="outline" onClick={() => router.push("/notes")} className="mt-4 rounded-full">Back to notes</Button></div> : detailLoading ? <div role="status" className="rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-8">Loading note…</div> : <NoteEditor key={selectedNote?.id ?? "draft"} ref={editorRef} initialNote={selectedNote} attachments={attachmentOptions} onSaved={handleSaved} onDelete={handleDelete} onReload={selectedNote ? async () => {
              const fresh = await getNote(selectedNote.id);
              setSelectedNote(fresh);
              return fresh;
            } : undefined} />}
          </div>
        </div>
      </main>
    </div>
  );
}

function dedupeSummaries(items: NoteSummary[]): NoteSummary[] {
  const seen = new Set<string>();
  return items.filter((item) => !seen.has(item.id) && seen.add(item.id));
}
