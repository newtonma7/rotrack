"use client";

import { useRef, useState } from "react";
import { Users } from "lucide-react";
import { acceptFriendRequest, blockUser, deleteFriendRequest, getBlocks, getFriendRequests, getFriends, removeFriend, requestFriend, unblockUser } from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import { Button } from "@/components/ui/button";
import { FriendSearch } from "@/components/friends/FriendSearch";
import { PagedPeople } from "@/components/friends/PagedPeople";
import type { Relationship } from "@/types/friendships";

const views = [
  { id: "friends", label: "Friends", empty: "No friends yet. Find someone by username to send your first request.", load: getFriends },
  { id: "incoming", label: "Incoming", empty: "No incoming requests. You're all caught up.", load: (cursor?: string) => getFriendRequests("incoming", cursor) },
  { id: "outgoing", label: "Sent", empty: "No outgoing requests.", load: (cursor?: string) => getFriendRequests("outgoing", cursor) },
] as const;

export function FriendsExperience() {
  const [view, setView] = useState<(typeof views)[number]>(views[0]);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const [notice, setNotice] = useState<{ kind: "success" | "error"; text: string } | null>(null);

  async function mutate(action: () => Promise<unknown>, message: string) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setNotice(null);
    try {
      await action();
      setNotice({ kind: "success", text: message });
    } catch (error: unknown) {
      const text = error instanceof ApiRequestError && error.status === 429
        ? `Too many changes. Wait ${Math.ceil((error.retryAfterMs ?? 60000) / 1000)} seconds before trying again.`
        : "Could not complete that change. Refresh the lists before trying again.";
      setNotice({ kind: "error", text });
    } finally {
      inFlight.current = false;
      setBusy(false);
      setRevision((value) => value + 1);
    }
  }

  function block(username: string) {
    if (window.confirm(`Block @${username}? This removes your friendship or pending request and prevents new requests. Unblocking will not restore the connection.`)) {
      void mutate(() => blockUser(username), `@${username} blocked.`);
    }
  }

  function actions(person: Relationship) {
    return <>
      {view.id === "incoming" && <Button disabled={busy} className="rounded-full bg-[var(--rt-ink)] text-[var(--rt-paper)]" aria-label={`Accept ${person.username}`} onClick={() => void mutate(() => acceptFriendRequest(person.id), `You're now friends with @${person.username}.`)}>Accept</Button>}
      <Button variant="outline" disabled={busy} className="rounded-full border-[var(--rt-line)]" aria-label={`${view.id === "friends" ? "Remove" : view.id === "incoming" ? "Decline" : "Cancel request to"} ${person.username}`} onClick={() => {
        if (view.id === "friends") {
          if (window.confirm(`Remove @${person.username} from your friends? You can send a new request later.`)) void mutate(() => removeFriend(person.id), `@${person.username} removed from friends.`);
        } else void mutate(() => deleteFriendRequest(person.id), view.id === "incoming" ? "Request declined." : "Request cancelled.");
      }}>{view.id === "friends" ? "Remove" : view.id === "incoming" ? "Decline" : "Cancel"}</Button>
      <Button variant="ghost" disabled={busy} className="rounded-full text-[var(--rt-ink-muted)]" aria-label={`Block ${person.username}`} onClick={() => block(person.username)}>Block</Button>
    </>;
  }

  return (
    <main className="mx-auto max-w-[1200px] px-5 pb-16 pt-10 sm:px-8 md:pt-16">
      <div className="mb-9 flex flex-wrap items-end justify-between gap-5">
        <div><p className="mb-3 text-xs font-semibold uppercase tracking-[0.18em] text-[var(--rt-orange)]">A little company</p>
          <h1 className="font-display text-5xl tracking-[-0.04em] sm:text-6xl">better <span className="text-[var(--rt-orange)]">together.</span></h1>
          <p className="mt-4 max-w-lg text-[var(--rt-ink-muted)]">Your people, at your pace. Your private space stays yours.</p>
        </div>
        <Button variant="outline" disabled={busy} className="rounded-full border-[var(--rt-line)] bg-[var(--rt-paper)]" onClick={() => setRevision((value) => value + 1)}>Refresh lists</Button>
      </div>
      <div aria-live="polite" className="mb-5">
        {busy ? <p role="status" className="text-sm">Updating connection…</p>
          : notice && <p role={notice.kind === "error" ? "alert" : "status"} className="rounded-2xl border border-[var(--rt-line)] bg-[var(--rt-paper)] px-4 py-3 text-sm">{notice.text}</p>}
      </div>
      <div className="grid min-w-0 gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
        <section aria-labelledby="connections-heading" className="min-w-0 self-start rounded-[28px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-5 sm:p-7">
          <div className="mb-6 flex items-center gap-3"><span className="flex size-10 items-center justify-center rounded-full bg-[var(--rt-orange-soft)]"><Users size={20} strokeWidth={1.6} /></span><h2 id="connections-heading" className="font-display text-2xl tracking-tight">your connections.</h2></div>
          <div aria-label="Connection lists" className="flex flex-wrap gap-1 rounded-2xl bg-[var(--rt-cream)] p-1">
            {views.map((item) => <Button key={item.id} variant="ghost" aria-pressed={view.id === item.id} disabled={busy} onClick={() => setView(item)} className={`flex-1 rounded-xl ${view.id === item.id ? "bg-[var(--rt-paper)] text-[var(--rt-ink)] shadow-sm" : "text-[var(--rt-ink-muted)]"}`}>{item.label}</Button>)}
          </div>
          <PagedPeople key={`${view.id}-${revision}`} load={view.load} revision={revision} label={view.label.toLowerCase()} empty={view.empty} disabled={busy} actions={actions} />
        </section>
        <div className="min-w-0 space-y-6">
          <FriendSearch revision={revision} busy={busy} request={(username) => void mutate(() => requestFriend(username), `Request sent to @${username}.`)} block={block} />
          <section aria-labelledby="blocked-heading" className="rounded-[28px] border border-[var(--rt-line)] bg-[var(--rt-cream-soft)] p-5 sm:p-7">
            <h2 id="blocked-heading" className="font-display text-xl tracking-tight">blocked usernames.</h2>
            <p className="mt-2 text-sm leading-relaxed text-[var(--rt-ink-muted)]">Only your own blocks appear here. Unblocking does not send a request.</p>
            <PagedPeople key={revision} load={getBlocks} revision={revision} label="blocked usernames" empty="You haven't blocked anyone." disabled={busy} actions={(person) => <Button variant="outline" disabled={busy} className="rounded-full border-[var(--rt-line)]" aria-label={`Unblock ${person.username}`} onClick={() => void mutate(() => unblockUser(person.username), `@${person.username} unblocked.`)}>Unblock</Button>} />
          </section>
        </div>
      </div>
    </main>
  );
}
