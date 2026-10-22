"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import type { PeoplePage } from "@/types/friendships";

type PageState<T> =
  | { kind: "loading" }
  | { kind: "error" }
  | { kind: "ready"; page: PeoplePage<T>; moreError: boolean };

export function PagedPeople<T extends { username: string }>({
  load, revision, empty, label, actions, details, disabled,
}: {
  load: (cursor?: string) => Promise<PeoplePage<T>>;
  revision: number;
  empty: string;
  label: string;
  actions: (person: T) => ReactNode;
  details?: (person: T) => ReactNode;
  disabled: boolean;
}) {
  const [state, setState] = useState<PageState<T>>({ kind: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const generation = useRef(0);

  useEffect(() => {
    const current = ++generation.current;
    let active = true;
    void load().then(
      (page) => { if (active) { setState({ kind: "ready", page, moreError: false }); setLoadingMore(false); } },
      () => { if (active) setState({ kind: "error" }); },
    );
    return () => { active = false; generation.current = current + 1; };
  }, [load, revision, attempt]);

  async function more() {
    if (state.kind !== "ready" || !state.page.nextCursor || loadingMore) return;
    const current = generation.current;
    setLoadingMore(true);
    try {
      const next = await load(state.page.nextCursor);
      if (generation.current === current) setState({ kind: "ready", page: {
        items: [...state.page.items, ...next.items.filter((item) => !state.page.items.some((existing) => existing.username === item.username))],
        nextCursor: next.nextCursor,
      }, moreError: false });
    } catch {
      if (generation.current === current) setState({ ...state, moreError: true });
    } finally {
      if (generation.current === current) setLoadingMore(false);
    }
  }

  if (state.kind === "loading") return <p role="status" className="py-8 text-sm text-[var(--rt-ink-muted)]">Loading {label}…</p>;
  if (state.kind === "error") return <div className="py-6"><p role="alert">Could not load {label}.</p><Button variant="outline" className="mt-3 rounded-full" onClick={() => setAttempt((value) => value + 1)}>Retry {label}</Button></div>;
  return (
    <div>
      {state.page.items.length === 0 ? <p className="py-8 text-sm text-[var(--rt-ink-muted)]">{empty}</p> : (
        <ul aria-label={label} className="divide-y divide-[var(--rt-line)]">
          {state.page.items.map((person) => (
            <li key={person.username} className="flex min-w-0 flex-wrap items-center justify-between gap-3 py-4">
              <span className="min-w-0 break-all font-semibold">@{person.username}</span>
              <div className="flex flex-wrap items-center gap-1">{actions(person)}</div>
              {details?.(person)}
            </li>
          ))}
        </ul>
      )}
      {state.moreError && <p role="alert" className="py-2 text-sm">Could not load more. Try again.</p>}
      {state.page.nextCursor && <Button variant="outline" className="mt-4 rounded-full" disabled={disabled || loadingMore} onClick={() => void more()}>{loadingMore ? "Loading…" : `Load more ${label}`}</Button>}
    </div>
  );
}
