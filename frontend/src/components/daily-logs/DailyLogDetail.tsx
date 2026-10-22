"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { RichTextEditor } from "@/components/notes/RichTextEditor";
import { useReflectionAutosave } from "@/hooks/useReflectionAutosave";
import { BEFORE_APP_NAVIGATION, guardedNavigationProceed } from "@/lib/navigation-guard";
import { formatLocalDate } from "@/lib/daily-log-date";
import type { DailyLog, Reflection } from "@/types/daily-logs";

export function DailyLogDetail({
  dailyLog,
  onReflectionSaved,
  onReload,
}: {
  dailyLog: DailyLog;
  onReflectionSaved: (reflection: Reflection) => void;
  onReload: () => Promise<Reflection | null>;
}) {
  const { generated, localDate, timeZone, reflection } = dailyLog;

  return (
    <>
      <div className="mb-5 flex items-center gap-3">
        <Link
          href={`/daily-logs?timeZone=${encodeURIComponent(timeZone)}`}
          className="rounded-full border border-[var(--rt-line)] px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-[var(--rt-orange)]"
        >
          ← calendar
        </Link>
        <h2 className="font-display text-3xl tracking-[-0.04em]">
          {formatLocalDate(localDate)}
        </h2>
      </div>

      <div className="rounded-[28px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-5 md:p-7">
        <div className="grid grid-cols-2 gap-3">
          <Metric label="work" seconds={generated.workSeconds} />
          <Metric label="rot" seconds={generated.rotSeconds} />
        </div>

        <h3 className="mt-7 font-display text-xl">sessions</h3>
        {generated.sessions.length > 0 ? (
          <ol className="mt-3 space-y-3">
            {generated.sessions.map((session) => (
              <li key={session.id} className="rounded-2xl bg-[var(--rt-cream)] p-4">
                <div className="flex justify-between gap-2">
                  <strong>{session.activityType.toLowerCase()}</strong>
                  <span className="text-sm text-[var(--rt-ink-muted)]">
                    {formatTime(session.segmentStartTime, timeZone)}–
                    {formatTime(session.segmentEndTime, timeZone)}
                  </span>
                </div>
                {session.sessionLabel && <p className="mt-1 text-sm">{session.sessionLabel}</p>}
                <p className="mt-1 text-xs text-[var(--rt-ink-muted)]">
                  {Math.floor(session.durationSeconds / 60)} min
                </p>
              </li>
            ))}
          </ol>
        ) : (
          <p className="mt-3 text-sm text-[var(--rt-ink-muted)]">
            No completed sessions on this day.
          </p>
        )}

        <h3 className="mt-7 font-display text-xl">notes</h3>
        {generated.noteReferences.length > 0 ? (
          <ul className="mt-3 space-y-2">
            {generated.noteReferences.map((note) => (
              <li key={note.id} className="rounded-2xl border border-[var(--rt-line)] p-4">
                <Link
                  href={`/notes/${encodeURIComponent(note.id)}`}
                  className="font-semibold underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-[var(--rt-orange)]"
                >
                  {note.title || "untitled note"}
                </Link>
                <p className="mt-1 text-sm text-[var(--rt-ink-muted)]">{note.preview}</p>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-3 text-sm text-[var(--rt-ink-muted)]">No attached Notes.</p>
        )}
      </div>

      <section
        aria-label="Reflection"
        className="mt-5 rounded-[28px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-5 md:p-7"
      >
        <div className="flex items-center justify-between gap-3">
          <h3 className="font-display text-2xl">reflection</h3>
          <span className="text-xs text-[var(--rt-ink-muted)]">Private to you</span>
        </div>
        {reflection && (
          <p className="mt-1 text-xs text-[var(--rt-ink-muted)]">
            original date label · created in {reflection.timeZoneAtCreation}
          </p>
        )}
        <ReflectionEditor
          key={localDate}
          localDate={localDate}
          timeZone={timeZone}
          reflection={reflection}
          onSaved={onReflectionSaved}
          onReload={onReload}
        />
      </section>
    </>
  );
}

function Metric({ label, seconds }: { label: string; seconds: number }) {
  return (
    <div className="rounded-2xl bg-[var(--rt-cream)] p-4">
      <p className="text-xs uppercase tracking-widest text-[var(--rt-ink-muted)]">{label}</p>
      <p className="mt-1 font-display text-2xl tabular-nums">
        {Math.floor(seconds / 3600)}h {Math.floor((seconds % 3600) / 60)}m
      </p>
    </div>
  );
}

function ReflectionEditor({
  localDate,
  timeZone,
  reflection,
  onSaved,
  onReload,
}: {
  localDate: string;
  timeZone: string;
  reflection: Reflection | null;
  onSaved: (reflection: Reflection) => void;
  onReload: () => Promise<Reflection | null>;
}) {
  const autosave = useReflectionAutosave({ localDate, timeZone, initialReflection: reflection, onSaved });
  const [copyMessage, setCopyMessage] = useState("");
  const { dirty, flush } = autosave;

  useEffect(() => {
    if (!dirty) return;
    const marker = { ...window.history.state, rotrackUnsavedReflection: true };
    window.history.pushState(marker, "", window.location.href);
    let proceeding = false;
    const onPopState = () => {
      if (proceeding) return;
      void flush().then((saved) => {
        if (saved) {
          proceeding = true;
          window.history.back();
        } else {
          window.history.pushState(marker, "", window.location.href);
        }
      });
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [dirty, flush]);

  useEffect(() => {
    const onGuardedNavigation = (event: Event) => {
      const proceed = guardedNavigationProceed(event);
      if (!dirty || !proceed) return;
      event.preventDefault();
      void flush().then((saved) => { if (saved) proceed(); });
    };
    const onAnchorClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const anchor = (event.target as Element | null)?.closest("a");
      const href = anchor?.getAttribute("href");
      if (!anchor || anchor.target === "_blank" || !href?.startsWith("/") || !dirty) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      void flush().then((saved) => { if (saved) window.location.assign(href); });
    };
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (dirty) { event.preventDefault(); event.returnValue = ""; }
    };
    window.addEventListener(BEFORE_APP_NAVIGATION, onGuardedNavigation);
    document.addEventListener("click", onAnchorClick, true);
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => {
      window.removeEventListener(BEFORE_APP_NAVIGATION, onGuardedNavigation);
      document.removeEventListener("click", onAnchorClick, true);
      window.removeEventListener("beforeunload", onBeforeUnload);
    };
  }, [dirty, flush]);

  return (
    <div className="mt-3">
      <RichTextEditor
        ariaLabel="Reflection content"
        initialContent={autosave.document}
        onChange={(next) => autosave.setContent(next)}
        placeholder="What stood out about today?"
        variant="journal"
      />
      <div className="mb-2 flex justify-end">
        <span role="status" aria-live="polite" className="text-xs text-[var(--rt-ink-muted)]">
          {autosave.status}
        </span>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant="outline"
          onClick={() => void autosave.copy().then(() => setCopyMessage("Reflection copied as rich and plain text.")).catch(() => setCopyMessage("Copy is unavailable in this browser."))}
          className="rounded-full"
        >
          Copy
        </Button>
        {autosave.status === "Conflict" && (
          <>
            <Button
              type="button"
              variant="outline"
              onClick={() => void onReload().then((latest) => autosave.reloadServerVersion(latest))}
              className="rounded-full"
            >
              Reload server version
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => void autosave.copy().then(() => setCopyMessage("Your local edits were copied as rich and plain text.")).catch(() => setCopyMessage("Copy is unavailable in this browser."))}
              className="rounded-full"
            >
              Copy my edits
            </Button>
          </>
        )}
        {(autosave.status === "Offline" || autosave.status === "Waiting") && (
          <Button type="button" variant="outline" onClick={autosave.retry} className="rounded-full">
            Retry
          </Button>
        )}
      </div>
      {copyMessage && <p role="status" className="mt-2 text-sm">{copyMessage}</p>}
      {autosave.error && (
        <p role="alert" className="mt-3 rounded-2xl border border-[var(--rt-orange)] bg-[var(--rt-orange-soft)] px-4 py-3 text-sm">
          {autosave.error}
        </p>
      )}
    </div>
  );
}

function formatTime(value: string, timeZone: string): string {
  return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit", timeZone }).format(new Date(value));
}
