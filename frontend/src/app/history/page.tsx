"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Trash2 } from "lucide-react";
import { ApplicationHeader } from "@/components/app/ApplicationHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { createHistoryEntry, deleteHistoryEntry, getHistory, getPreferences, updateHistoryEntry } from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import { toDateTimeLocal, toIsoInstant } from "@/lib/datetime";
import { getBrowserTimeZone } from "@/lib/timezone";
import { formatDuration } from "@/lib/format";
import type { ActivityType } from "@/types/time-entry";
import type { HistoryEntry, HistoryEntryInput } from "@/types/history";

type FormValues = {
  activityType: ActivityType;
  startTime: string;
  endTime: string;
  notes: string;
};
type FieldErrors = Partial<Record<keyof FormValues, string>>;

export default function HistoryPage() {
  const [entries, setEntries] = useState<HistoryEntry[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [formEntry, setFormEntry] = useState<HistoryEntry | null | undefined>(undefined);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const browserTimeZone = useMemo(() => getBrowserTimeZone(), []);
  const [timeZone, setTimeZone] = useState(browserTimeZone);
  const requestSequence = useRef(0);

  const loadHistory = useCallback(async () => {
    const requestId = ++requestSequence.current;
    setLoading(true);
    setLoadingMore(false);
    setError(null);
    try {
      const [preferences, page] = await Promise.all([getPreferences(), getHistory()]);
      if (requestId !== requestSequence.current) return;
      setTimeZone(preferences.timeZone || browserTimeZone);
      setEntries(page.entries);
      setNextCursor(page.nextCursor);
      setLoadMoreError(null);
      setDeleteError(null);
    } catch (requestError) {
      if (requestId === requestSequence.current) {
        setError(requestError instanceof Error ? requestError.message : "History could not be loaded.");
      }
    } finally {
      if (requestId === requestSequence.current) setLoading(false);
    }
  }, [browserTimeZone]);

  useEffect(() => {
    void loadHistory();
  }, [loadHistory]);

  const loadMore = async () => {
    if (!nextCursor || loadingMore) return;
    const requestId = requestSequence.current;
    setLoadingMore(true);
    setLoadMoreError(null);
    try {
      const page = await getHistory(nextCursor);
      if (requestId !== requestSequence.current) return;
      setEntries((current) => {
        const seen = new Set(current.map((entry) => entry.id));
        return [...current, ...page.entries.filter((entry) => !seen.has(entry.id))];
      });
      setNextCursor(page.nextCursor);
    } catch (requestError) {
      if (requestId === requestSequence.current) {
        setLoadMoreError(requestError instanceof Error ? requestError.message : "More history could not be loaded.");
      }
    } finally {
      setLoadingMore(false);
    }
  };

  const saveEntry = async (input: HistoryEntryInput, id?: string) => {
    if (id) await updateHistoryEntry(id, input);
    else await createHistoryEntry(input);
    // Re-read page one: mutations can move an entry across the keyset boundary.
    await loadHistory();
    setFormEntry(undefined);
  };

  const removeEntry = async (entry: HistoryEntry) => {
    if (!window.confirm("Delete this completed entry?")) return;
    setDeletingId(entry.id);
    setDeleteError(null);
    try {
      await deleteHistoryEntry(entry.id);
      await loadHistory();
    } catch (requestError) {
      setDeleteError(requestError instanceof Error ? requestError.message : "Entry could not be deleted.");
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
      <ApplicationHeader />

      <main className="mx-auto max-w-[1100px] px-6 py-12 md:px-10 md:py-16">
        <div className="mb-10 flex flex-wrap items-end justify-between gap-6">
          <div className="max-w-3xl">
            <p className="mb-3 text-[0.8rem] font-semibold uppercase tracking-[0.2em] text-[var(--rt-orange)]">completed, not guessed</p>
            <h1 className="font-display text-[clamp(2.8rem,7vw,5.5rem)] leading-[0.92] tracking-[-0.02em]">your time, in reverse<span className="text-[var(--rt-orange)]">.</span></h1>
            <p className="mt-5 max-w-2xl text-lg leading-relaxed text-[var(--rt-ink-muted)]">Review the entries you actually finished. Active tracking stays on the tracker.</p>
          </div>
          <Button onClick={() => setFormEntry(null)} className="rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] shadow-[0_10px_30px_-10px_rgba(236,107,14,0.6)] hover:bg-[var(--rt-orange-deep)]">Add entry</Button>
        </div>

        {formEntry !== undefined && (
          <HistoryForm key={formEntry?.id ?? "new"} entry={formEntry} timeZone={timeZone} onSave={saveEntry} onCancel={() => setFormEntry(undefined)} />
        )}

        {loading ? (
          <div role="status" aria-live="polite" className="rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-10">Loading history…</div>
        ) : error ? (
          <div role="alert" className="rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-8 md:p-10">
            <p className="font-display text-3xl">history stayed quiet.</p>
            <p className="mt-3 text-[var(--rt-ink-muted)]">{error}</p>
            <Button onClick={() => void loadHistory()} className="mt-6 rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] shadow-[0_10px_30px_-10px_rgba(236,107,14,0.6)] hover:bg-[var(--rt-orange-deep)]">Try again</Button>
          </div>
        ) : (
          <section aria-label="Completed history" className="space-y-4">
            {deleteError && <p role="alert" className="rounded-2xl border border-[var(--rt-orange)] bg-[var(--rt-orange-soft)] px-5 py-4">{deleteError}</p>}
            {entries.length === 0 ? (
              <div className="rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-10">
                <p className="font-display text-3xl">no completed entries yet.</p>
                <p className="mt-3 text-[var(--rt-ink-muted)]">Add a finished Work or Rot block when you need to correct the record.</p>
              </div>
            ) : (
              <div className="overflow-hidden rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)]">
                <ul className="divide-y divide-[var(--rt-line)]">
                  {entries.map((entry) => <HistoryRow key={entry.id} entry={entry} timeZone={timeZone} deleting={deletingId === entry.id} onEdit={() => setFormEntry(entry)} onDelete={() => void removeEntry(entry)} />)}
                </ul>
                {nextCursor && (
                  <div className="border-t border-[var(--rt-line)] p-5 text-center">
                    {loadMoreError && <p role="alert" className="mb-3 text-sm text-[var(--rt-orange-deep)]">{loadMoreError}</p>}
                    <Button variant="outline" onClick={() => void loadMore()} disabled={loadingMore} className="rounded-full">
                      {loadingMore ? "Loading more…" : loadMoreError ? "Try again" : "Load more"}
                    </Button>
                  </div>
                )}
              </div>
            )}
          </section>
        )}
      </main>
    </div>
  );
}

function HistoryRow({ entry, timeZone, deleting, onEdit, onDelete }: { entry: HistoryEntry; timeZone: string; deleting: boolean; onEdit: () => void; onDelete: () => void }) {
  const label = entry.notes?.trim() || `${entry.activityType.toLowerCase()} entry`;
  const duration = entry.durationSeconds;
  return (
    <li className="flex flex-wrap items-center gap-4 px-6 py-5 md:px-8">
      <span aria-hidden="true" className={`h-3 w-3 shrink-0 rounded-full ${entry.activityType === "WORK" ? "bg-[var(--rt-orange)]" : "bg-[var(--rt-ink-soft)]"}`} />
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold">{label}</p>
        <p className="mt-1 text-sm text-[var(--rt-ink-muted)]">{entry.activityType} · {formatDuration(duration)} · {formatHistoryDate(entry.startTime, timeZone)}</p>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="outline" onClick={onEdit} disabled={deleting} className="rounded-full" aria-label={`Edit ${label}`}>Edit</Button>
        <Button variant="ghost" onClick={onDelete} disabled={deleting} className="rounded-full text-[var(--rt-ink-muted)] hover:text-[var(--rt-orange-deep)]" aria-label={`Delete ${label}`}>
          <Trash2 aria-hidden="true" />{deleting ? "Deleting…" : "Delete"}
        </Button>
      </div>
    </li>
  );
}

function HistoryForm({ entry, timeZone, onSave, onCancel }: { entry: HistoryEntry | null; timeZone: string; onSave: (input: HistoryEntryInput, id?: string) => Promise<void>; onCancel: () => void }) {
  const [values, setValues] = useState<FormValues>(() => entry ? {
    activityType: entry.activityType,
    startTime: toDateTimeLocal(entry.startTime, timeZone),
    endTime: toDateTimeLocal(entry.endTime, timeZone),
    notes: entry.notes ?? "",
  } : { activityType: "WORK", startTime: "", endTime: "", notes: "" });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const update = <K extends keyof FormValues>(field: K, value: FormValues[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setFormError(null);
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors: FieldErrors = {};
    if (!values.startTime) nextErrors.startTime = "Start time is required.";
    if (!values.endTime) nextErrors.endTime = "End time is required.";
    if (values.notes.length > 280) nextErrors.notes = "Notes must be 280 characters or fewer.";
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }

    try {
      // A repeated wall-clock hour has two instants; unchanged edit fields must keep the server instant.
      const originalValues = entry && {
        startTime: toDateTimeLocal(entry.startTime, timeZone),
        endTime: toDateTimeLocal(entry.endTime, timeZone),
      };
      const startTime = entry && originalValues?.startTime === values.startTime
        ? entry.startTime
        : toIsoInstant(values.startTime, timeZone);
      const endTime = entry && originalValues?.endTime === values.endTime
        ? entry.endTime
        : toIsoInstant(values.endTime, timeZone);
      if (Date.parse(endTime) <= Date.parse(startTime)) {
        setErrors({ endTime: "End time must be after start time." });
        return;
      }
      setSaving(true);
      setFormError(null);
      await onSave({ activityType: values.activityType, startTime, endTime, notes: values.notes.trim() || null }, entry?.id);
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        setErrors(requestError.fieldErrors as FieldErrors);
        setFormError(requestError.message);
      } else {
        setFormError(requestError instanceof Error ? requestError.message : "Entry could not be saved.");
      }
    } finally {
      setSaving(false);
    }
  };

  const field = (name: keyof FormValues, label: string, input: React.ReactNode) => (
    <div className="space-y-2">
      <Label htmlFor={`history-${name}`}>{label}</Label>
      {input}
      {errors[name] && <p id={`history-${name}-error`} className="text-sm text-[var(--rt-orange-deep)]">{errors[name]}</p>}
    </div>
  );

  return (
    <form onSubmit={submit} noValidate aria-label={entry ? "Edit completed entry" : "Add completed entry"} className="mb-6 rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-6 md:p-8">
      <div className="mb-6 flex items-start justify-between gap-4">
        <div><p className="text-[0.8rem] font-semibold uppercase tracking-[0.18em] text-[var(--rt-orange)]">manual correction</p><h2 className="mt-2 font-display text-3xl">{entry ? "edit this entry." : "add a finished block."}</h2></div>
        <Button type="button" variant="ghost" onClick={onCancel} disabled={saving} className="rounded-full">Cancel</Button>
      </div>
      {formError && <p role="alert" className="mb-5 rounded-2xl border border-[var(--rt-orange)] bg-[var(--rt-orange-soft)] px-4 py-3">{formError}</p>}
      <div className="grid gap-5 md:grid-cols-2">
        {field("activityType", "Activity", <Select value={values.activityType} onValueChange={(value) => update("activityType", value as ActivityType)} disabled={saving}>
          <SelectTrigger id="history-activityType" aria-label="Activity" aria-invalid={Boolean(errors.activityType)} aria-describedby={errors.activityType ? "history-activityType-error" : undefined}><SelectValue /></SelectTrigger>
          <SelectContent><SelectItem value="WORK">Work</SelectItem><SelectItem value="ROT">Rot</SelectItem></SelectContent>
        </Select>)}
        {field("startTime", "Start time", <Input id="history-startTime" type="datetime-local" step={1} value={values.startTime} onChange={(event) => update("startTime", event.target.value)} disabled={saving} aria-invalid={Boolean(errors.startTime)} aria-describedby={errors.startTime ? "history-startTime-error" : undefined} />)}
        {field("endTime", "End time", <Input id="history-endTime" type="datetime-local" step={1} value={values.endTime} onChange={(event) => update("endTime", event.target.value)} disabled={saving} aria-invalid={Boolean(errors.endTime)} aria-describedby={errors.endTime ? "history-endTime-error" : undefined} />)}
        {field("notes", "Notes", <div><Input id="history-notes" value={values.notes} maxLength={280} onChange={(event) => update("notes", event.target.value)} disabled={saving} aria-invalid={Boolean(errors.notes)} aria-describedby={errors.notes ? "history-notes-error" : undefined} /><p className="mt-1 text-right text-xs text-[var(--rt-ink-muted)]">{values.notes.length}/280</p></div>)}
      </div>
      <div className="mt-6 flex justify-end"><Button type="submit" disabled={saving} className="rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] shadow-[0_10px_30px_-10px_rgba(236,107,14,0.6)] hover:bg-[var(--rt-orange-deep)]">{saving ? "Saving…" : entry ? "Save changes" : "Save entry"}</Button></div>
    </form>
  );
}

function formatHistoryDate(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short", timeZone }).format(new Date(iso));
}
