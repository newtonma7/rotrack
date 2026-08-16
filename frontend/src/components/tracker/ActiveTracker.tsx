"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { NoteEditor, type NoteEditorHandle } from "@/components/notes/NoteEditor";
import { deleteNote, getHistory, getNote, getNotes, getPreferences } from "@/lib/api";
import { useTimeTracking } from "@/hooks/useTimeTracking";
import { getBrowserTimeZone } from "@/lib/timezone";
import type { HistoryEntry } from "@/types/history";
import type { Note, NoteSummary } from "@/types/notes";
import type { TimeEntry } from "@/types/time-entry";

export function ActiveTracker() {
  // Timer lifecycle stays explicit and server-owned; Notes load/autosave independently so a Notes failure never stops or hides tracking.
  const { activeEntry, elapsed, loading: timerLoading, error: timerError, start, stop } = useTimeTracking();
  const editorRef = useRef<NoteEditorHandle>(null);
  const notesRequestSequence = useRef(0);
  const notesMutationGeneration = useRef(0);
  const selectionSequence = useRef(0);
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [notesLoading, setNotesLoading] = useState(true);
  const [notesLoadingMore, setNotesLoadingMore] = useState(false);
  const [notesError, setNotesError] = useState<string | null>(null);
  const [selectedNote, setSelectedNote] = useState<Note | null>(null);
  const [selectedNoteId, setSelectedNoteId] = useState<string | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [selectionBusy, setSelectionBusy] = useState(false);
  const [editorNonce, setEditorNonce] = useState(0);
  const [attachmentOptions, setAttachmentOptions] = useState<AttachmentOption[]>([]);
  const [attachmentsLoading, setAttachmentsLoading] = useState(true);
  const [attachmentsError, setAttachmentsError] = useState<string | null>(null);
  const [attachmentsReload, setAttachmentsReload] = useState(0);
  const [timeZone, setTimeZone] = useState(getBrowserTimeZone);

  const loadNotes = useCallback(async (cursor?: string, preserveCurrent = false) => {
    const requestId = ++notesRequestSequence.current;
    const mutationGeneration = notesMutationGeneration.current;
    if (cursor) setNotesLoadingMore(true);
    else setNotesLoading(true);
    setNotesError(null);
    try {
      const page = await getNotes(cursor ? { cursor } : {});
      if (requestId !== notesRequestSequence.current) return;
      setNotes((current) => {
        if (cursor || preserveCurrent || mutationGeneration !== notesMutationGeneration.current) {
          return mergeSummaries(preserveCurrent ? [...page.notes, ...current] : [...current, ...page.notes]);
        }
        return page.notes;
      });
      setNextCursor(page.nextCursor);
    } catch (requestError) {
      if (requestId === notesRequestSequence.current) {
        setNotesError(requestError instanceof Error ? requestError.message : "Notes could not be loaded.");
      }
    } finally {
      if (requestId === notesRequestSequence.current) {
        setNotesLoading(false);
        setNotesLoadingMore(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadNotes();
  }, [loadNotes]);

  useEffect(() => {
    let mounted = true;
    const loadAttachmentOptions = async () => {
      const preferences = await getPreferences().catch(() => null);
      const effectiveTimeZone = preferences?.timeZone || getBrowserTimeZone();
      if (mounted) setTimeZone(effectiveTimeZone);
      const entries: HistoryEntry[] = [];
      const seenCursors = new Set<string>();
      let cursor: string | undefined;
      do {
        const page = await getHistory(cursor);
        entries.push(...page.entries);
        if (!page.nextCursor || seenCursors.has(page.nextCursor)) break;
        seenCursors.add(page.nextCursor);
        cursor = page.nextCursor;
      } while (mounted);
      if (mounted) setAttachmentOptions(entries.map((entry) => toAttachmentOption(entry, effectiveTimeZone)));
    };
    void loadAttachmentOptions().catch(() => {
      if (mounted) setAttachmentsError("Past time entries could not be loaded.");
    }).finally(() => {
      if (mounted) setAttachmentsLoading(false);
    });
    return () => { mounted = false; };
  }, [attachmentsReload]);

  const flushBeforeChange = useCallback(async () => {
    const flushed = await editorRef.current?.flush() ?? true;
    if (flushed || window.confirm("Your edits could not be saved. Leave and lose these edits?")) return true;
    return false;
  }, []);

  const selectNote = async (noteId: string) => {
    if (selectionBusy || !(await flushBeforeChange())) return;
    const requestId = ++selectionSequence.current;
    setSelectionBusy(true);
    setDetailLoading(true);
    setDetailError(null);
    try {
      const note = await getNote(noteId);
      if (requestId !== selectionSequence.current) return;
      setSelectedNote(note);
      setSelectedNoteId(note.id);
      if (note.id !== selectedNoteId) setEditorNonce((current) => current + 1);
    } catch (requestError) {
      if (requestId === selectionSequence.current) {
        setSelectedNote(null);
        setSelectedNoteId(null);
        setEditorNonce((current) => current + 1);
        setDetailError(requestError instanceof Error ? requestError.message : "Note could not be loaded.");
      }
    } finally {
      if (requestId === selectionSequence.current) {
        setDetailLoading(false);
        setSelectionBusy(false);
      }
    }
  };

  const startDraft = async () => {
    if (!(await flushBeforeChange())) return;
    ++selectionSequence.current;
    setSelectedNote(null);
    setSelectedNoteId(null);
    setDetailError(null);
    setDetailLoading(false);
    setEditorNonce((current) => current + 1);
  };

  const handleDelete = async (note: Note) => {
    setSelectionBusy(true);
    setDetailLoading(true);
    setDetailError(null);
    try {
      await deleteNote(note.id, note.version);
      notesMutationGeneration.current += 1;
      await loadNotes();
      ++selectionSequence.current;
      setSelectedNote(null);
      setSelectedNoteId(null);
      setEditorNonce((current) => current + 1);
    } catch (requestError) {
      setDetailError(safeDetailError(requestError, "Note could not be deleted."));
    } finally {
      setDetailLoading(false);
      setSelectionBusy(false);
    }
  };

  const reloadSelectedNote = async () => {
    if (!selectedNoteId) return null;
    setDetailError(null);
    try {
      const fresh = await getNote(selectedNoteId);
      setSelectedNote(fresh);
      return fresh;
    } catch (requestError) {
      setDetailError(safeDetailError(requestError, "Note could not be reloaded."));
      return null;
    }
  };

  const handleSaved = (saved: Note) => {
    notesMutationGeneration.current += 1;
    setNotes((current) => mergeSummaries([saved, ...current]));
    setSelectedNoteId(saved.id);
    setSelectedNote(saved);
    if (!notesLoading) void loadNotes(undefined, true);
  };

  const isActive = Boolean(activeEntry);
  const activeType = activeEntry?.activityType;
  const attachments = mergeAttachments([
    ...(activeEntry ? [toAttachmentOption(activeEntry, timeZone)] : []),
    ...attachmentOptions,
    ...(selectedNote?.timeEntryId && !attachmentOptions.some((option) => option.id === selectedNote.timeEntryId)
      ? [{ id: selectedNote.timeEntryId, label: "attached time entry" }]
      : []),
  ]);

  return (
    <div className="relative isolate mx-auto max-w-[1400px] overflow-x-clip">
      <span aria-hidden="true" className="pointer-events-none absolute bottom-[12%] left-[5%] size-3.5 rounded-full bg-[radial-gradient(circle_at_32%_28%,var(--rt-paper),var(--rt-line))] opacity-60 shadow-sm" />
      <section aria-label="Timer" className="relative flex min-h-[250px] flex-col items-center justify-center gap-[13px] text-center">
        <div
          role="timer"
          aria-live="polite"
          aria-label={isActive ? `${activeType?.toLowerCase()} session elapsed time` : "No active session"}
          className={`font-display text-[clamp(3.6rem,7vw,5.4rem)] leading-[0.9] tabular-nums tracking-[-0.06em] ${isActive ? "text-[var(--rt-ink)]" : "text-[var(--rt-ink-muted)]"}`}
        >
          {elapsed}
        </div>
        <div className="inline-flex items-center gap-1 rounded-full border border-[var(--rt-line)] bg-[var(--rt-paper)]/40 p-1 backdrop-blur-md">
          <ActivityButton label="Work" activityType="WORK" active={activeType === "WORK"} disabled={timerLoading || (isActive && activeType !== "WORK")} onClick={() => { if (activeType !== "WORK") void start("WORK"); }} />
          <ActivityButton label="Rot" activityType="ROT" active={activeType === "ROT"} disabled={timerLoading || (isActive && activeType !== "ROT")} onClick={() => { if (activeType !== "ROT") void start("ROT"); }} />
          {isActive && <Button type="button" variant="ghost" onClick={() => void stop()} disabled={timerLoading} className="h-9 rounded-full border border-[var(--rt-line)] px-3 text-xs font-normal text-[var(--rt-ink-muted)]">Stop</Button>}
        </div>
        <div data-testid="timer-status-area" className="flex min-h-10 flex-col justify-start text-xs text-[var(--rt-ink-muted)]">
          <p>{isActive ? `${activeType?.toLowerCase()} · running` : "choose a mode when you’re ready"}</p>
          {timerLoading && <p role="status" className="mt-1">Restoring timer…</p>}
          {timerError && <p role="alert" aria-label="Timer error" className="mt-1 text-[var(--rt-ink-soft)]">{timerError}</p>}
        </div>
      </section>

      <section aria-label="Journal and notes" className="mx-auto mt-10 max-w-[1040px] border-t border-[var(--rt-line)] pt-6 lg:mt-0 lg:pt-9">
        <div className="grid items-start lg:grid-cols-[190px_minmax(0,1fr)]">
          <aside aria-label="Mindspace notes" className="min-w-0 border-b border-[var(--rt-line)] pb-5 lg:min-h-[54vh] lg:border-r lg:border-b-0 lg:pr-6">
            <div className="flex items-center justify-between gap-3 text-[0.68rem] font-bold uppercase tracking-[0.16em] text-[var(--rt-ink-muted)]">
              <h2>mindspace</h2>
              <Button type="button" variant="ghost" aria-label="New note" onClick={() => void startDraft()} disabled={selectionBusy} className="size-7 rounded-full border border-[var(--rt-line)] p-0 text-[var(--rt-ink-muted)]"><span aria-hidden="true" className="text-sm leading-none">+</span></Button>
            </div>
            <div className="mt-5" aria-live="polite">
              {notesLoading ? <p role="status" className="py-6 text-sm text-[var(--rt-ink-muted)]">Loading notes…</p> : notesError ? <div role="alert" aria-label="Notes error" className="py-4 text-sm"><p>{notesError}</p><Button type="button" variant="outline" onClick={() => void loadNotes()} className="mt-3 rounded-full">Try again</Button></div> : notes.length === 0 ? <p className="py-6 text-sm text-[var(--rt-ink-muted)]">No notes yet. Start a draft beside your timer.</p> : <ul className="space-y-1">{notes.map((summary) => <li key={summary.id}><Button type="button" variant="ghost" disabled={selectionBusy} onClick={() => void selectNote(summary.id)} aria-current={selectedNoteId === summary.id ? "true" : undefined} className={`h-auto w-full rounded-xl px-3 py-2.5 text-left outline-none transition focus-visible:ring-2 focus-visible:ring-[var(--rt-orange)] disabled:opacity-60 ${selectedNoteId === summary.id ? "bg-[var(--rt-line)]" : "hover:bg-[var(--rt-line)]/70"}`}><span className="block truncate text-[0.82rem] font-semibold">{summary.title || summary.preview || "untitled note"}</span><span className="mt-1 block truncate text-[0.68rem] font-normal text-[var(--rt-ink-muted)]">{summary.preview || (summary.timeEntryId ? "attached" : "standalone")}</span></Button></li>)}</ul>}
              {nextCursor && <Button type="button" variant="ghost" onClick={() => void loadNotes(nextCursor)} disabled={notesLoadingMore} className="mt-3 rounded-full px-3">{notesLoadingMore ? "Loading…" : "Load more"}</Button>}
              <Button type="button" variant="ghost" onClick={() => void startDraft()} disabled={selectionBusy} className="mt-5 block rounded-full px-3 py-2 text-sm text-[var(--rt-ink-muted)] hover:bg-[var(--rt-cream-soft)] hover:text-[var(--rt-ink)]">+ new note</Button>
            </div>
          </aside>

          <div className="min-w-0 pt-6 lg:pt-0 lg:pl-[42px]">
            {detailLoading && <p role="status" className="mb-4 text-sm text-[var(--rt-ink-muted)]">Opening note…</p>}
            {detailError && <p role="alert" aria-label="Note detail error" className="mb-4 rounded-2xl border border-[var(--rt-orange)] bg-[var(--rt-orange-soft)] px-4 py-3 text-sm">{detailError}</p>}
            {attachmentsLoading && <p role="status" className="mb-3 text-xs text-[var(--rt-ink-muted)]">Loading past time entries…</p>}
            {attachmentsError && <div role="alert" aria-label="Attachment options error" className="mb-3 flex items-center gap-3 text-xs text-[var(--rt-ink-muted)]"><span>{attachmentsError}</span><Button type="button" variant="ghost" className="h-auto rounded-full px-2 py-1 text-xs" onClick={() => { setAttachmentsError(null); setAttachmentsLoading(true); setAttachmentsReload((current) => current + 1); }}>Try again</Button></div>}
            <NoteEditor key={`editor-${editorNonce}`} ref={editorRef} initialNote={selectedNote} activeEntryId={activeEntry?.id ?? null} attachments={attachments} timeZone={timeZone} variant="journal" onSaved={handleSaved} onDelete={handleDelete} onReload={reloadSelectedNote} />
            <p className="mt-8 text-xs text-[var(--rt-ink-muted)]">Notes stay private. Your active entry is captured when meaningful writing begins.</p>
          </div>
        </div>
      </section>

      <p className="mx-auto mt-10 max-w-[1040px] text-center text-xs text-[var(--rt-ink-muted)]">Sessions keep running until you stop them explicitly. <Link href="/dashboard" className="hover:underline">View dashboard</Link></p>
    </div>
  );
}

type AttachmentOption = { id: string; label: string };

function toAttachmentOption(entry: HistoryEntry | TimeEntry, timeZone: string): AttachmentOption {
  const date = new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", timeZone }).format(new Date(entry.startTime));
  return { id: entry.id, label: `${entry.activityType.toLowerCase()} · ${date}` };
}

function safeDetailError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

function mergeAttachments(items: AttachmentOption[]): AttachmentOption[] {
  const seen = new Set<string>();
  return items.filter((item) => !seen.has(item.id) && seen.add(item.id));
}

function ActivityButton({ label, activityType, active, disabled, onClick }: { label: string; activityType: "WORK" | "ROT"; active: boolean; disabled: boolean; onClick: () => void }) {
  const activeClass = activityType === "ROT" ? "bg-[var(--rt-orange)]" : "bg-[var(--rt-ink)]";
  return <Button type="button" variant="ghost" onClick={onClick} disabled={disabled} aria-label={label} aria-pressed={active} className={`h-10 min-w-[92px] rounded-full border-0 px-4 text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--rt-orange)] disabled:pointer-events-none disabled:opacity-50 ${active ? `${activeClass} text-[var(--rt-cream)]` : "bg-transparent hover:bg-[var(--rt-ink)]/5"}`}>{label.toLowerCase()}</Button>;
}

function mergeSummaries(items: NoteSummary[]): NoteSummary[] {
  const seen = new Set<string>();
  return items.filter((item) => !seen.has(item.id) && seen.add(item.id));
}
