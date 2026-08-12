# ADR-0001: Canonical username registration lifecycle

**Status:** Accepted for the username-registration slice
**Date:** 2026-08-11

## Context

rotrack uses Supabase Auth for registration and creates an owned `public.users` profile from the Auth signup trigger. The existing profile schema permits nullable, case-sensitive usernames. The product needs a required identifier for new accounts without adding a registration API, availability endpoint, public profiles, or a username-change flow.

The current product is pre-user and existing accounts are disposable test accounts. The `public.users` table is owner-readable through RLS; no public username read path exists yet.

## Decision

- `username` is the canonical domain term. “Public handle” describes a future use, not a second field or current visibility level.
- A username is trimmed, canonical lowercase, and limited to `^[a-z0-9_]{3,24}$`.
- The fixed reserved-name list is enforced by a database `CHECK` constraint. No reservation table, admin UI, or runtime configuration is introduced.
- Canonical lowercase storage uses an ordinary unique constraint/index. No `citext` or functional uniqueness mechanism is needed.
- Usernames are immutable after profile creation. The database must reject direct update attempts as well as UI attempts; no username-change flow exists in this slice.
- A username is reserved at the initial Auth signup trigger, before email confirmation. Abandoned unconfirmed signups are not expired or cleaned up by this slice.
- Usernames remain owner-only under the existing RLS boundary. Public profiles/search and future social exposure require a later decision.
- Because the product is pre-user, rollout uses one enforcement migration after disposable accounts are prepared separately. The migration fails closed on invalid existing data and never invents, renames, or deletes accounts.
- Server-side trigger and constraint failures are mapped to safe generic signup copy. The frontend gives precise local format/reserved-name feedback but does not add a preflight availability API.

## Consequences

The database, rather than the browser or a race-prone availability check, owns validity and uniqueness. An unconfirmed signup can hold a username indefinitely until the account is handled by existing operational processes. Adding username changes, public discovery, expiry, or administrative reservations requires a new contract and migration/authorization decision.

## Alternatives considered

- A compatibility migration was rejected for the current zero-user/pre-user rollout; it becomes appropriate if real users exist before deployment coordination is possible.
- A username availability endpoint was rejected because the database constraint already resolves concurrent claims and the endpoint would add an unnecessary enumeration surface.
- Trigger-only reserved-name validation was rejected because direct profile writes must not bypass the policy.
