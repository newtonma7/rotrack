"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { updateReflection } from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import { richTextToPlainText, validateRichTextDocument } from "@/lib/rich-text";
import { richTextToHtml } from "@/components/notes/NoteEditor";
import type { Reflection } from "@/types/daily-logs";
import type { RichTextDocument } from "@/types/notes";

export type ReflectionSaveStatus = "Draft" | "Saving" | "Saved" | "Waiting" | "Offline" | "Conflict";
export const EMPTY_RICH_TEXT: RichTextDocument = { schemaVersion: 1, document: { type: "doc", content: [] } };
const MAX_DOCUMENT_BYTES = 262_144;
const SIZE_ERROR = "This reflection is too large to save. Remove content until it is under 256 KiB.";
const key = () => globalThis.crypto.randomUUID();
const bytes = (value: string) => typeof TextEncoder === "undefined" ? value.length : new TextEncoder().encode(value).byteLength;
type CreateSnapshot = { key: string; request: { contentJson: RichTextDocument; expectedVersion: 0 }; timeZone: string; localDate: string; revision: number };

export function useReflectionAutosave({ localDate, timeZone, initialReflection, onSaved }: {
  localDate: string;
  timeZone: string;
  initialReflection: Reflection | null;
  onSaved?: (reflection: Reflection) => void;
}) {
  const [reflection, setReflection] = useState(initialReflection);
  const [document, setDocument] = useState(initialReflection?.contentJson ?? EMPTY_RICH_TEXT);
  const [status, setStatusState] = useState<ReflectionSaveStatus>(initialReflection ? "Saved" : "Draft");
  const [error, setError] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const documentRef = useRef(document);
  const reflectionRef = useRef(reflection);
  const dirtyRef = useRef(false);
  const revision = useRef(0);
  const creationKey = useRef(key());
  const pendingCreate = useRef<CreateSnapshot | null>(null);
  const statusRef = useRef(status);
  const paramsRef = useRef({ localDate, timeZone, onSaved });
  const inFlight = useRef<Promise<boolean> | null>(null);
  const performSaveRef = useRef<() => Promise<boolean>>(() => Promise.resolve(false));
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mounted = useRef(true);

  useEffect(() => {
    paramsRef.current = { localDate, timeZone, onSaved };
  }, [localDate, onSaved, timeZone]);

  const setStatus = useCallback((next: ReflectionSaveStatus) => {
    statusRef.current = next;
    if (mounted.current) setStatusState(next);
  }, []);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; if (timer.current) clearTimeout(timer.current); if (retryTimer.current) clearTimeout(retryTimer.current); };
  }, []);

  const performSave = useCallback((): Promise<boolean> => {
    if (inFlight.current) return inFlight.current;
    if (!dirtyRef.current) return Promise.resolve(true);
    const validation = validateRichTextDocument(documentRef.current);
    if (!validation.ok) { setError(validation.error); setStatus("Waiting"); return Promise.resolve(false); }
    const contentJson = validation.document;
    if (bytes(JSON.stringify(contentJson)) > MAX_DOCUMENT_BYTES) { setError(SIZE_ERROR); setStatus("Waiting"); return Promise.resolve(false); }
    const contentText = richTextToPlainText(contentJson);
    const existing = reflectionRef.current;
    if (!existing && !contentText.trim()) { dirtyRef.current = false; setDirty(false); setStatus("Draft"); return Promise.resolve(true); }

    let createSnapshot = pendingCreate.current;
    if (!existing && !createSnapshot) {
      createSnapshot = {
        key: creationKey.current,
        request: { contentJson, expectedVersion: 0 },
        timeZone: paramsRef.current.timeZone,
        localDate: paramsRef.current.localDate,
        revision: revision.current,
      };
      pendingCreate.current = createSnapshot;
    }
    const savedRevision = existing ? revision.current : createSnapshot!.revision;
    setStatus("Saving"); setError(null);
    const operation = (async () => {
      try {
        const saved = await updateReflection(
          existing ? paramsRef.current.localDate : createSnapshot!.localDate,
          existing ? { contentJson, expectedVersion: existing.version } : createSnapshot!.request,
          existing ? undefined : createSnapshot!.key,
          existing ? paramsRef.current.timeZone : createSnapshot!.timeZone,
        );
        if (!mounted.current) return true;
        if (!existing && pendingCreate.current === createSnapshot) pendingCreate.current = null;
        reflectionRef.current = saved;
        setReflection(saved);
        dirtyRef.current = savedRevision !== revision.current;
        setDirty(dirtyRef.current);
        paramsRef.current.onSaved?.(saved);
        if (dirtyRef.current) {
          setStatus("Waiting");
          if (timer.current) clearTimeout(timer.current);
          timer.current = setTimeout(() => { timer.current = null; void performSaveRef.current(); }, 750);
        } else {
          documentRef.current = saved.contentJson;
          setDocument(saved.contentJson);
          setStatus("Saved");
        }
        return true;
      } catch (cause) {
        if (!mounted.current) return false;
        if (cause instanceof ApiRequestError && (cause.code === "RICH_TEXT_VERSION_CONFLICT" || cause.code === "IDEMPOTENCY_CONFLICT")) {
          setStatus("Conflict");
          setError(cause.code === "IDEMPOTENCY_CONFLICT"
            ? "This Reflection creation could not be replayed safely. Your edits are preserved; copy them before reloading."
            : "This reflection changed elsewhere. Your edits are preserved.");
        } else if (cause instanceof ApiRequestError && (cause.code === "RATE_LIMITED" || cause.status === 429)) {
          setStatus("Waiting"); setError("Saving is waiting briefly before trying again.");
          if (retryTimer.current === null) retryTimer.current = setTimeout(() => { retryTimer.current = null; void performSaveRef.current(); }, cause.retryAfterMs ?? 1000);
        } else {
          setStatus("Offline"); setError("Your edits are safe in this tab. Retry when the connection returns.");
        }
        return false;
      }
    })();
    inFlight.current = operation;
    return operation.finally(() => { if (inFlight.current === operation) inFlight.current = null; });
  }, [setStatus]);
  useEffect(() => { performSaveRef.current = performSave; }, [performSave]);

  const schedule = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    if (retryTimer.current) clearTimeout(retryTimer.current);
    retryTimer.current = null;
    if (!dirtyRef.current || statusRef.current === "Conflict") return;
    setStatus("Waiting");
    timer.current = setTimeout(() => { timer.current = null; void performSaveRef.current(); }, 750);
  }, [setStatus]);

  const setContent = useCallback((next: RichTextDocument) => {
    documentRef.current = next;
    setDocument(next);
    revision.current += 1;
    dirtyRef.current = true;
    setDirty(true);
    setError(null);
    if (statusRef.current !== "Conflict") schedule();
  }, [schedule]);

  const flush = useCallback(async (): Promise<boolean> => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    if (retryTimer.current) clearTimeout(retryTimer.current);
    retryTimer.current = null;
    while (dirtyRef.current) {
      if (statusRef.current === "Conflict") return false;
      const saved = await performSave();
      if (!saved) return false;
    }
    return true;
  }, [performSave]);

  const retry = useCallback(() => {
    if (statusRef.current === "Conflict") return;
    if (retryTimer.current) clearTimeout(retryTimer.current);
    retryTimer.current = null;
    void performSave();
  }, [performSave]);

  const reloadServerVersion = useCallback((latest: Reflection | null) => {
    if (!window.confirm("Discard your local edits and reload the server version?")) return false;
    if (timer.current) clearTimeout(timer.current);
    if (retryTimer.current) clearTimeout(retryTimer.current);
    timer.current = null; retryTimer.current = null;
    reflectionRef.current = latest; setReflection(latest);
    const next = latest?.contentJson ?? EMPTY_RICH_TEXT;
    documentRef.current = next; setDocument(next); dirtyRef.current = false; setDirty(false); setError(null);
    pendingCreate.current = null;
    creationKey.current = key();
    setStatus(latest ? "Saved" : "Draft");
    return true;
  }, [setStatus]);

  const copy = useCallback(async () => {
    const validated = validateRichTextDocument(documentRef.current);
    if (!validated.ok) return "";
    const plain = richTextToPlainText(validated.document);
    if (navigator.clipboard?.write && typeof ClipboardItem !== "undefined") {
      await navigator.clipboard.write([new ClipboardItem({
        "text/html": new Blob([richTextToHtml(validated.document)], { type: "text/html" }),
        "text/plain": new Blob([plain], { type: "text/plain" }),
      })]);
    } else if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(plain);
    return plain;
  }, []);

  useEffect(() => {
    const online = () => { if (dirtyRef.current && statusRef.current !== "Conflict") { setStatus("Waiting"); void performSave(); } };
    window.addEventListener("online", online);
    return () => window.removeEventListener("online", online);
  }, [performSave, setStatus]);
  return { reflection, document, status, error, dirty, setContent, flush, retry, reloadServerVersion, copy, getCurrentReflection: () => reflectionRef.current };
}
