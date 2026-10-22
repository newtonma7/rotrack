"use client";

import { useEffect, useRef, useState } from "react";
import { searchUsers } from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { UserSearchResult } from "@/types/friendships";

type Result =
  | { kind: "loading" }
  | { kind: "ready"; items: UserSearchResult[] }
  | { kind: "error"; message: string }
  | { kind: "waiting"; seconds: number };
type SearchState = { query: string; revision: number; result: Result };

const relationshipLabels = { NONE: "", OUTGOING: "Request sent", INCOMING: "Requested you", FRIEND: "Friend" };

export function FriendSearch({ revision, busy, request, block }: {
  revision: number; busy: boolean;
  request: (username: string) => void;
  block: (username: string) => void;
}) {
  const [input, setInput] = useState("");
  const query = input.trim().toLowerCase();
  const valid = /^[a-z0-9_]{3,24}$/.test(query);
  const [state, setState] = useState<SearchState | null>(null);
  const [attempt, setAttempt] = useState(0);
  const waitUntil = useRef(0);

  useEffect(() => {
    if (!valid) return;
    let active = true;
    let waitingTimer: ReturnType<typeof setTimeout> | undefined;
    function waiting(ms: number) {
      setState({ query, revision, result: { kind: "waiting", seconds: Math.ceil(ms / 1000) } });
      // Expiry enables an explicit retry; it must not launch another search by itself.
      waitingTimer = setTimeout(() => {
        if (active) setState({ query, revision, result: { kind: "error", message: "You can search again now." } });
      }, ms);
    }
    const debounce = setTimeout(() => {
      const remaining = waitUntil.current - Date.now();
      if (remaining > 0) { waiting(remaining); return; }
      setState({ query, revision, result: { kind: "loading" } });
      void searchUsers(query).then(
        ({ items }) => { if (active) setState({ query, revision, result: { kind: "ready", items } }); },
        (error: unknown) => {
          if (error instanceof ApiRequestError && error.status === 429) {
            const ms = Math.max(1000, error.retryAfterMs ?? 60000);
            // The budget belongs to the viewer, even when the query that exhausted it is stale.
            waitUntil.current = Math.max(waitUntil.current, Date.now() + ms);
            if (active) waiting(waitUntil.current - Date.now());
          } else if (active) {
            setState({ query, revision, result: { kind: "error", message: "Could not search usernames." } });
          }
        },
      );
    }, 300);
    return () => { active = false; clearTimeout(debounce); clearTimeout(waitingTimer); };
  }, [query, valid, revision, attempt]);

  const result = state?.query === query && state.revision === revision ? state.result : null;

  return (
    <section aria-labelledby="find-friends" className="rounded-[28px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-5 sm:p-7">
      <h2 id="find-friends" className="font-display text-2xl tracking-tight">find your people.</h2>
      <p className="mb-5 mt-2 text-sm leading-relaxed text-[var(--rt-ink-muted)]">Search by username, never email. A connection does not share your activity or private writing.</p>
      <Label htmlFor="friend-search" className="mb-2 block">Username</Label>
      <Input id="friend-search" value={input} onChange={(event) => setInput(event.target.value)} autoComplete="off" spellCheck={false} maxLength={24} placeholder="at least 3 characters" aria-describedby="search-hint" className="h-11 rounded-xl border-[var(--rt-line)] bg-[var(--rt-cream)]" />
      <div id="search-hint" role="status" aria-live="polite" className="mt-3 text-sm text-[var(--rt-ink-muted)]">
        {!valid ? (query.length < 3 ? "Enter at least 3 characters to find someone." : "Use letters, numbers, or underscores only.")
          : !result || result.kind === "loading" ? "Searching…"
          : result.kind === "waiting" ? `Search limit reached. Wait ${result.seconds} seconds before trying again.`
          : result.kind === "error" ? result.message
          : `${result.items.length} ${result.items.length === 1 ? "username" : "usernames"} found.`}
      </div>
      {valid && result?.kind === "error" && <Button variant="outline" className="mt-3 rounded-full" onClick={() => setAttempt((value) => value + 1)}>Retry search</Button>}
      {valid && result?.kind === "ready" && result.items.length > 0 && (
        <ul aria-label="Username results" className="mt-3 divide-y divide-[var(--rt-line)]">
          {result.items.map((person) => (
            <li key={person.username} className="flex min-w-0 flex-wrap items-center justify-between gap-3 py-3">
              <div className="min-w-0"><span className="break-all font-semibold">@{person.username}</span>
                {person.relationship !== "NONE" && <p className="mt-1 text-xs text-[var(--rt-ink-muted)]">{relationshipLabels[person.relationship]}</p>}
              </div>
              <div className="flex gap-1">
                {person.relationship === "NONE" && <Button disabled={busy} onClick={() => request(person.username)} aria-label={`Add ${person.username}`} className="rounded-full bg-[var(--rt-ink)] text-[var(--rt-paper)]">Add friend</Button>}
                <Button variant="ghost" disabled={busy} className="rounded-full text-[var(--rt-ink-muted)]" aria-label={`Block ${person.username}`} onClick={() => block(person.username)}>Block</Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
