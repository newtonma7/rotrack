"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createNote, updateNote } from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import { validateRichTextDocument } from "@/lib/rich-text";
import type { CreateNoteRequest, Note, RichTextDocument } from "@/types/notes";

export type NoteSaveStatus = "Draft" | "Saving" | "Saved" | "Waiting" | "Offline" | "Conflict";

const EMPTY_DOCUMENT: RichTextDocument = { schemaVersion: 1, document: { type: "doc", content: [] } };

function createKey(): string {
  return globalThis.crypto.randomUUID();
}

function byteLength(value: string): number {
  return typeof TextEncoder === "undefined" ? value.length : new TextEncoder().encode(value).byteLength;
}

export function noteContentSize(document: RichTextDocument): number {
  return byteLength(JSON.stringify(document));
}

export function useNoteAutosave({
  initialNote,
  activeEntryId = null,
  onSaved,
}: {
  initialNote?: Note | null;
  activeEntryId?: string | null;
  onSaved?: (note: Note) => void;
} = {}) {
  const [note, setNote] = useState<Note | null>(initialNote ?? null);
  const [title, setTitle] = useState(initialNote?.title ?? "");
  const [document, setDocument] = useState<RichTextDocument>(initialNote?.contentJson ?? EMPTY_DOCUMENT);
  const [contentText, setContentText] = useState(initialNote?.contentText ?? "");
  const [attachmentId, setAttachmentId] = useState<string | null>(initialNote?.timeEntryId ?? null);
  const [status, setStatus] = useState<NoteSaveStatus>(initialNote ? "Saved" : "Draft");
  const [error, setError] = useState<string | null>(null);
  const [sizeError, setSizeError] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const noteRef = useRef(note);
  const titleRef = useRef(title);
  const documentRef = useRef(document);
  const contentTextRef = useRef(contentText);
  const attachmentRef = useRef(attachmentId);
  const dirtyRef = useRef(false);
  const revisionRef = useRef(0);
  const inFlightRef = useRef(false);
  const inFlightWaitersRef = useRef<Array<() => void>>([]);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryRevisionRef = useRef(-1);
  const creationKeyRef = useRef(createKey());
  const capturedRef = useRef(Boolean(initialNote));
  const mountedRef = useRef(true);

  useEffect(() => () => {
    mountedRef.current = false;
    if (timerRef.current) clearTimeout(timerRef.current);
    if (retryRef.current) clearTimeout(retryRef.current);
  }, []);

  useEffect(() => {
    noteRef.current = note;
    titleRef.current = title;
    documentRef.current = document;
    contentTextRef.current = contentText;
    attachmentRef.current = attachmentId;
  }, [attachmentId, contentText, document, note, title]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirtyRef.current || (!titleRef.current.trim() && !contentTextRef.current.trim())) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, []);

  const clearScheduled = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    if (retryRef.current) clearTimeout(retryRef.current);
    timerRef.current = null;
    retryRef.current = null;
  }, []);

  const meaningful = useCallback(() => Boolean(titleRef.current.trim() || contentTextRef.current.trim()), []);

  const performSave = useCallback(async (revision = revisionRef.current, retrying = false): Promise<boolean> => {
    if (inFlightRef.current) {
      await new Promise<void>((resolve) => inFlightWaitersRef.current.push(resolve));
      return performSave(revisionRef.current);
    }
    if (!dirtyRef.current || (!noteRef.current && !meaningful())) return true;
    const validation = validateRichTextDocument(documentRef.current);
    if (!validation.ok) {
      setError(validation.error);
      setStatus("Waiting");
      return false;
    }
    const serializedSize = noteContentSize(validation.document);
    if (serializedSize > 262_144) {
      setSizeError("This document is too large to save. Remove content until it is under 256 KiB.");
      setStatus("Waiting");
      return false;
    }
    setSizeError(null);
    setError(null);
    setStatus("Saving");
    inFlightRef.current = true;
    const request: CreateNoteRequest = {
      title: titleRef.current.trim() || null,
      contentJson: validation.document,
      timeEntryId: attachmentRef.current,
    };
    try {
      const saved = noteRef.current
        ? await updateNote(noteRef.current.id, { ...request, expectedVersion: noteRef.current.version })
        : await createNote(request, creationKeyRef.current);
      if (!mountedRef.current) return true;
      noteRef.current = saved;
      setNote(saved);
      // A keystroke may land while the request is in flight. Keep that newer tab-local
      // draft; only replace the editor state when the response covers the latest revision.
      if (revision === revisionRef.current) {
        setTitle(saved.title ?? "");
        setDocument(saved.contentJson);
        setContentText(saved.contentText);
        setAttachmentId(saved.timeEntryId);
      }
      onSaved?.(saved);
      dirtyRef.current = revision !== revisionRef.current;
      setDirty(dirtyRef.current);
      if (dirtyRef.current) {
        setStatus("Waiting");
        timerRef.current = setTimeout(() => void performSave(revisionRef.current), 750);
      } else {
        setStatus("Saved");
      }
      retryRevisionRef.current = -1;
      return true;
    } catch (requestError) {
      if (!mountedRef.current) return false;
      if (requestError instanceof ApiRequestError && requestError.code === "RICH_TEXT_VERSION_CONFLICT") {
        setStatus("Conflict");
        setError("This Note changed elsewhere. Your edits are preserved.");
        return false;
      }
      if (requestError instanceof ApiRequestError && (requestError.code === "RATE_LIMITED" || requestError.status === 429)) {
        setStatus("Waiting");
        setError("Saving is waiting briefly before trying again.");
        if (!retrying && retryRevisionRef.current !== revision) {
          retryRevisionRef.current = revision;
          retryRef.current = setTimeout(() => void performSave(revision, true), requestError.retryAfterMs ?? 1000);
        }
      } else {
        setStatus("Offline");
        setError("Your edits are safe in this tab. Edit again or retry when the connection returns.");
      }
      return false;
    } finally {
      inFlightRef.current = false;
      const waiters = inFlightWaitersRef.current.splice(0);
      waiters.forEach((resolve) => resolve());
    }
  }, [meaningful, onSaved]);

  const schedule = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    if (retryRef.current) clearTimeout(retryRef.current);
    retryRef.current = null;
    if (!dirtyRef.current || (!noteRef.current && !meaningful())) return;
    setStatus("Waiting");
    timerRef.current = setTimeout(() => void performSave(revisionRef.current), 750);
  }, [meaningful, performSave]);

  const markChanged = useCallback((nextTitle: string, nextDocument: RichTextDocument, nextText: string, nextAttachment = attachmentRef.current) => {
    if (!capturedRef.current && (nextTitle.trim() || nextText.trim())) {
      capturedRef.current = true;
      nextAttachment = activeEntryId ?? null;
    }
    titleRef.current = nextTitle;
    documentRef.current = nextDocument;
    contentTextRef.current = nextText;
    attachmentRef.current = nextAttachment;
    setTitle(nextTitle);
    setDocument(nextDocument);
    setContentText(nextText);
    setAttachmentId(nextAttachment);
    revisionRef.current += 1;
    dirtyRef.current = true;
    setDirty(true);
    setError(null);
    if (status !== "Conflict") schedule();
  }, [activeEntryId, schedule, status]);

  const flush = useCallback(async () => {
    clearScheduled();
    if (!dirtyRef.current) return true;
    return performSave(revisionRef.current);
  }, [clearScheduled, performSave]);

  const retry = useCallback(() => {
    clearScheduled();
    void performSave(revisionRef.current);
  }, [clearScheduled, performSave]);

  const setAttachment = useCallback((value: string | null) => {
    // An explicit choice is the draft's context, even when the title/editor is still empty.
    capturedRef.current = true;
    markChanged(titleRef.current, documentRef.current, contentTextRef.current, value);
  }, [markChanged]);

  const reloadServerVersion = useCallback((serverNote: Note) => {
    clearScheduled();
    noteRef.current = serverNote;
    setNote(serverNote);
    setTitle(serverNote.title ?? "");
    setDocument(serverNote.contentJson);
    setContentText(serverNote.contentText);
    setAttachmentId(serverNote.timeEntryId);
    titleRef.current = serverNote.title ?? "";
    documentRef.current = serverNote.contentJson;
    contentTextRef.current = serverNote.contentText;
    attachmentRef.current = serverNote.timeEntryId;
    dirtyRef.current = false;
    setDirty(false);
    setStatus("Saved");
    setError(null);
  }, [clearScheduled]);

  const saveAsNew = useCallback(async () => {
    if (!meaningful()) return false;
    clearScheduled();
    creationKeyRef.current = createKey();
    noteRef.current = null;
    setNote(null);
    dirtyRef.current = true;
    setDirty(true);
    return performSave(revisionRef.current);
  }, [clearScheduled, meaningful, performSave]);

  useEffect(() => {
    const goOnline = () => { if (dirtyRef.current && status === "Offline") setStatus("Waiting"); };
    window.addEventListener("online", goOnline);
    return () => window.removeEventListener("online", goOnline);
  }, [status]);

  return {
    note,
    title,
    document,
    contentText,
    attachmentId,
    status,
    error,
    sizeError,
    dirty,
    setTitle: (value: string) => markChanged(value, documentRef.current, contentTextRef.current),
    setContent: (value: RichTextDocument, text: string) => markChanged(titleRef.current, value, text),
    setAttachment,
    flush,
    retry,
    reloadServerVersion,
    saveAsNew,
    getCurrentNote: () => noteRef.current,
  };
}
