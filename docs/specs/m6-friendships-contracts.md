# M6.1 friendship lifecycle and blocking

**Status:** Source/local contract. This work unit implements the database foundation only. The API, search limiter, lifecycle service, and UI below are not yet implemented.
**Roadmap:** [`todo.md`, M6.1](../../todo.md)
**Privacy decision:** [ADR-0007](../adr/0007-limit-friend-discovery-to-authenticated-usernames.md)

## Scope and privacy

- A Friend is a peer with an accepted mutual Friendship. A pending request is not a Friend.
- Only authenticated users can discover canonical usernames through Spring. No public profile route, unauthenticated availability endpoint, email search, or browser table grant is introduced.
- Search returns only username and the viewer's relationship state. Relationship lists return only the relationship ID and peer username. Block lists return only usernames the viewer blocked. No email, profile timestamps, raw user IDs, Work/Rot totals, session data, Notes, or Reflections appear in M6.1 payloads or logs.
- Search excludes the viewer and any peer blocked in either direction. Missing and blocked request targets have the same safe `404 NOT_FOUND` response. No `blockedByPeer` flag or counterpart block list is exposed.
- M6.2 study summaries and M6.3 presence remain separate slices. Existing sharing preferences do not authorize M6.1 activity projections.

## Planned API contract

Every route is under `/api/v1`. The acting user comes exclusively from the validated JWT `sub`. Clients address peers by canonical username, never by an acting-user field. JSON responses use the existing `{ data: ... }` envelope and errors use the existing sanitized error contract.

```ts
type RelationshipState = "NONE" | "OUTGOING" | "INCOMING" | "FRIEND";
type SearchResult = { username: string; relationship: RelationshipState };
type Relationship = { id: string; username: string };
type RelationshipPage = { items: Relationship[]; nextCursor: string | null };
type BlockPage = { items: Array<{ username: string }>; nextCursor: string | null };
```

| Route | Response and behavior |
|---|---|
| `GET /users/search?q=` | `200 { data: { items: SearchResult[] } }`. Literal canonical-prefix search, 3–24 characters after trim/lowercase, `a-z`, `0-9`, `_` only. `_` is literal, not an SQL wildcard. At most 10 results, ordered by username; no total count or arbitrary-offset pagination. |
| `GET /friends?cursor=` | `200 { data: RelationshipPage }`, accepted pairs only. |
| `GET /friend-requests?direction=incoming\|outgoing&cursor=` | `200 { data: RelationshipPage }`, pending requests only. Direction is required. |
| `POST /friend-requests` with `{ username }` | `201 { data: Relationship }`. A repeated or reversed pending request, or an existing friendship, returns `409 FRIENDSHIP_CONFLICT`; it never silently accepts. |
| `POST /friend-requests/{id}/accept` | `200 { data: Relationship }`, recipient only. A repeated accept by that recipient returns the existing accepted relationship. |
| `DELETE /friend-requests/{id}` | `204`, requester cancels or recipient declines a pending request. No body or separate decline route is needed. |
| `DELETE /friends/{id}` | `204`, either participant removes an accepted friendship. |
| `GET /blocks?cursor=` | `200 { data: BlockPage }`, own outgoing blocks only. |
| `PUT /blocks/{username}` | `204`, idempotently blocks an existing peer and deletes the pair's pending or accepted relationship in the same transaction. A counterpart's prior block must not prevent the viewer from recording their own block. |
| `DELETE /blocks/{username}` | `204`, idempotently removes only the viewer's outgoing block. No friendship/request is recreated. A missing username is also a no-op. |

Lists use opaque validated keyset cursors and fixed pages of 20. Relationship lists order by immutable server `created_at` then ID, descending. Blocks order by creation time then blocked-user ID, descending. A cursor must not contain private snapshots; invalid cursors return `INVALID_CURSOR`. Any endpoint accepting a body rejects unknown fields.

Search has a separate process-local per-viewer budget of 30 requests/minute, with bounded key retention using existing limiter infrastructure. Invalid authenticated search attempts consume that budget. Exceeding it returns `429 RATE_LIMITED` and `Retry-After`. This is defense in depth, not a fleet-wide edge limiter. Mutations use the existing authenticated mutation budget and normalized route labels. The UI debounces search, performs no request below three characters, ignores stale responses, and honors rate-limit waiting without a retry loop.

Self requests and self blocks return `400 VALIDATION_ERROR`. Missing and foreign relationship IDs both return `404 NOT_FOUND`; responses never reveal peer data. Repeated deletion of an absent relationship may return that same `404` and never recreates state. Wrong-state deletion returns `409 FRIENDSHIP_CONFLICT` only for a currently visible participant-owned row. A requester cannot accept their own pending request. Browser reload/network ambiguity may require refresh, never automatic reverse acceptance or recreation.

## Storage and transaction contract

`008_friendships.sql` is additive:

- `friendships`: opaque UUID primary key, `user_low_id`, `user_high_id`, `requester_id`, `state` exactly `PENDING | ACCEPTED`, server creation/update timestamps, one row per canonical unordered pair.
- Canonical order uses PostgreSQL UUID ordering. Canonical lowercase UUID-string lexical comparison agrees with that ordering; Java's signed `UUID.compareTo` does not for all pairs. Every writer must use the same ordering and tests must include UUIDs on both sides of the signed boundary.
- Both participants reference `users` with cascading account deletion. The requester must be one of those participants. Self pairs, reversed storage order, duplicate pairs, null/unknown states, and nonparticipant requesters are invalid.
- `user_blocks`: directional `(blocker_id, blocked_id)` primary key, server creation timestamp, no self-blocks, both users cascading on account deletion. Reciprocal blocks are independent and valid.
- RLS permits defensive relationship reads only for participants and block reads only for the blocker. No browser DML policy or table privilege is introduced. The existing dedicated API runtime receives only table DML; ownership remains enforced by the service because that role bypasses RLS.
- Participant-list and request lookups need indexed participant/state/creation order. Block primary/reverse indexes support either-direction eligibility checks. Avoid a redundant index duplicating a primary or unique key.

The schema foundation does **not** implement block cleanup or cross-table lifecycle eligibility. Before any relationship or block mutation, the future service must obtain one transaction-scoped lock for the canonical pair, then re-read eligibility and mutate. Use a stable pair-scoped PostgreSQL advisory-lock key derived identically for both directions. Hash collisions may serialize unrelated pairs but must never weaken correctness. Acquire this lock before any relationship/block row lock or write to avoid lock-order inversion. Database uniqueness remains authoritative for duplicate pairs. Tests must exercise reversed concurrent requests, request-versus-block, accept-versus-block, reciprocal blocks, and retry outcomes before the lifecycle is accepted.

M7 invitations do not exist yet. Do not create a speculative invitation table or no-op deletion adapter. When M7 introduces invitations, extend this same transactional block boundary to remove pairwise invitations while preserving third-party group ownership/membership.

## Planned UI and acceptance

Protected `/friends` provides accepted Friends, incoming/outgoing requests, username search, and the viewer's block list. Controls offer request, accept, decline/cancel, remove, block, and unblock with explicit destructive confirmation for block/removal. Empty/loading/error/waiting states, keyboard operation, responsive layouts, and accessible result announcements are required. Refresh relationship lists after transitions and suppress stale search results.

The database foundation requires failing-then-passing isolated PostgreSQL checks for canonical/unique pairs, membership and state constraints, directional/self-block rules, FK cascades, RLS isolation, and exact browser/runtime grants. The complete feature additionally requires service/controller/typed-client/UI tests and real-browser acceptance with distinct users, including authorization, both directions of every transition, blocked/missing equivalence, search wildcard escaping/limits/rate limiting, and the concurrency matrix above. No source-only milestone claims hosted migration or production readiness.
