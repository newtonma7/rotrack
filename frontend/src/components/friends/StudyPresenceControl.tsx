"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/context/AuthProvider";
import { Button } from "@/components/ui/button";
import { getStudyPresence } from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import { subscribeStudyPresenceChanged } from "@/lib/study-presence-events";
import type { StudyPresence } from "@/types/study-presence";

type PresenceState =
  | { kind: "loading" }
  | { kind: "paused" }
  | { kind: "available"; presence: StudyPresence }
  | { kind: "unavailable" }
  | { kind: "error" };

export function StudyPresenceControl({ username }: { username: string }) {
  const { user } = useAuth();
  return <PresenceRequest key={`${user?.id ?? "signed-out"}:${username}`} username={username} />;
}

function PresenceRequest({ username }: { username: string }) {
  const [state, setState] = useState<PresenceState>({ kind: "loading" });
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let disposed = false;
    let generation = 0;
    let controller: AbortController | undefined;
    let timer: ReturnType<typeof setInterval> | undefined;

    function cancel() {
      generation++;
      controller?.abort();
      clearInterval(timer);
      timer = undefined;
    }

    async function refresh() {
      if (disposed || document.visibilityState !== "visible") return;
      const current = ++generation;
      controller?.abort();
      controller = new AbortController();
      setState({ kind: "loading" });
      try {
        const presence = await getStudyPresence(username, controller.signal);
        if (!disposed && current === generation) setState({ kind: "available", presence });
      } catch (error: unknown) {
        if (!disposed && current === generation) {
          setState(error instanceof ApiRequestError && error.status === 404 ? { kind: "unavailable" } : { kind: "error" });
        }
      }
    }

    function resume() {
      cancel();
      if (document.visibilityState !== "visible") {
        setState({ kind: "paused" });
        return;
      }
      void refresh();
      timer = setInterval(() => void refresh(), 30_000);
    }

    // Hiding clears private values as well as the timer; aborted/stale replies cannot restore them.
    document.addEventListener("visibilitychange", resume);
    const unsubscribe = subscribeStudyPresenceChanged(resume);
    resume();
    return () => {
      disposed = true;
      cancel();
      document.removeEventListener("visibilitychange", resume);
      unsubscribe();
    };
  }, [username, retry]);

  const text = state.kind === "available" ? (state.presence.studying ? "Studying now" : "Not studying")
    : state.kind === "loading" ? "Checking presence…"
    : state.kind === "paused" ? "Presence paused"
    : state.kind === "unavailable" ? "Presence unavailable" : "Could not load presence";
  return (
    <div aria-label={`Study presence for ${username}`} className="flex w-full basis-full flex-wrap items-center gap-2 text-sm">
      <span aria-hidden="true" className={`size-2 shrink-0 rounded-full ${state.kind === "available" && state.presence.studying ? "bg-[var(--rt-orange)]" : "bg-[var(--rt-line)]"}`} />
      <p role="status" className="text-[var(--rt-ink-muted)]">{text}</p>
      {(state.kind === "error" || state.kind === "unavailable") && <Button variant="ghost" className="h-8 rounded-full px-3 text-xs" aria-label={`Retry presence for ${username}`} onClick={() => setRetry((value) => value + 1)}>Retry</Button>}
    </div>
  );
}
