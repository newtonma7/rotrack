"use client";

import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/context/AuthProvider";
import { Button } from "@/components/ui/button";
import { getStudySummary } from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import type { StudySummary } from "@/types/study-summary";

type SummaryState =
  | { kind: "hidden" }
  | { kind: "loading" }
  | { kind: "available"; summary: StudySummary }
  | { kind: "unavailable" }
  | { kind: "error" };

function formatWork(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return `${hours}h ${minutes}m work · last 7 days`;
}

export function StudySummaryControl({ username }: { username: string }) {
  const { user } = useAuth();
  // A new account or peer gets a new instance, so returning to an old account cannot reveal its stale data.
  return <SummaryRequest key={`${user?.id ?? "signed-out"}:${username}`} username={username} />;
}

function SummaryRequest({ username }: { username: string }) {
  const [state, setState] = useState<SummaryState>({ kind: "hidden" });
  const requestId = useRef(0);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; requestId.current += 1; };
  }, []);

  async function reveal() {
    const currentRequest = ++requestId.current;
    setState({ kind: "loading" });
    try {
      const summary = await getStudySummary(username);
      if (mounted.current && requestId.current === currentRequest) {
        setState({ kind: "available", summary });
      }
    } catch (error: unknown) {
      if (mounted.current && requestId.current === currentRequest) {
        setState(error instanceof ApiRequestError && error.status === 404 ? { kind: "unavailable" } : { kind: "error" });
      }
    }
  }

  const capped = state.kind === "available" && state.summary.streakCapped;
  return (
    <div className="w-full basis-full rounded-2xl bg-[var(--rt-cream)] p-4">
      <div className="flex flex-wrap items-center gap-2">
        {state.kind === "hidden" && <Button variant="outline" className="rounded-full border-[var(--rt-line)]" aria-label={`Show study summary for ${username}`} onClick={() => void reveal()}>Show study summary</Button>}
        {state.kind === "loading" && <p role="status" className="text-sm text-[var(--rt-ink-muted)]">Loading study summary…</p>}
        {state.kind === "unavailable" && <><p role="status" className="text-sm">Summary unavailable.</p><Button variant="outline" className="rounded-full border-[var(--rt-line)]" aria-label={`Retry study summary for ${username}`} onClick={() => void reveal()}>Retry</Button></>}
        {state.kind === "error" && <><p role="alert" className="text-sm">Could not load study summary.</p><Button variant="outline" className="rounded-full border-[var(--rt-line)]" aria-label={`Retry study summary for ${username}`} onClick={() => void reveal()}>Retry</Button></>}
        {state.kind === "available" && <>
          <p className="w-full font-semibold tabular-nums">{formatWork(state.summary.workSeconds)}</p>
          <p className="text-sm font-semibold tabular-nums">Study-day streak: {state.summary.studyDayStreak}{capped ? "+" : ""} {state.summary.studyDayStreak === 1 ? "day" : "days"}</p>
          <Button variant="ghost" className="rounded-full text-sm" aria-label={`Refresh study summary for ${username}`} onClick={() => void reveal()}>Refresh</Button>
        </>}
      </div>
      {state.kind !== "hidden" && <p className="mt-2 text-xs leading-relaxed text-[var(--rt-ink-muted)]">Days follow your friend&apos;s saved timezone (UTC if unset); only completed Work counts. {capped && "The + marks a streak that reaches the 366-day lookback limit."}</p>}
    </div>
  );
}
