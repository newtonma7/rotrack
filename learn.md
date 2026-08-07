# Learning review

Use these questions to review the completed baseline phases and understand the implementation decisions.

## M0.2 — Repository hygiene

1. Why should generated build output be ignored instead of committed?
2. What is the difference between `git rm --cached` and deleting a file from disk?
3. Why are compiled artifacts poor evidence that a feature works?

## M0.3 — Development runbook

1. Why should `NEXT_PUBLIC_*` values never contain backend secrets?
2. Why is `ddl-auto: validate` safer than allowing Hibernate to modify a production schema?
3. Why should a README document known limitations instead of only describing the target architecture?

## M0.4 — Toolchains and tests

1. What can change when using `npm install` instead of `npm ci`?
2. Why are `.nvmrc` and `.java-version` repository configuration rather than ignored local files?
3. Why are toolchain pins more reliable than documenting versions only in the README?
4. What does “Maven passed, but no tests ran” tell us—and what does it not tell us?
5. Why is a minimal unit-test runner useful before adding broad UI and integration coverage?
6. Why does a lockfile make `npm ci` reproducible even though package manifests use version ranges?
7. What kinds of tests would require changing Vitest's `.test.ts` include pattern?
8. What does removing an external font stylesheet improve, and what tradeoff does relying on a local regular-weight font introduce?
9. Why is “Maven passed with no tests” different from having meaningful backend test coverage?
10. Which repository files should be treated as unrelated dirty state during phase review?

## M0.5 — Current-state contract reconciliation

1. Why should architecture documentation distinguish checked-in current state from the target design?
2. Why is `201 Created` a better success status for starting a new session than a generic `200 OK`?
3. Which layers must change together when an HTTP response contract is corrected?
4. Why do passing source tests not prove remote migrations, RLS, or two-user ownership behavior?

## M1.1 — Database schema hardening

1. Why does a partial unique index enforce one active session without preventing multiple completed sessions?
2. Why is a text-based migration test weaker than executing SQL against PostgreSQL?
3. What migration strategy is needed when the original baseline has already been applied remotely?
4. Which completed-session tests prove that stale `duration_minutes` cannot affect API or dashboard output?

## M1.2 — Authentication and API errors

1. Why should the server validate JWT issuer, audience, time claims, and UUID `sub` instead of trusting a decoded token?
2. Why was the HS256 fallback removed, and how would keeping it expand the trust boundary?
3. What is the difference between authentication (`401`) and authorization (`403`), and why should resource ownership failures often appear as `404`?
4. Why is a stable error envelope more useful to the frontend than arbitrary exception strings?
5. What does `GlobalExceptionHandler` centralize, and what security risk does its generic fallback reduce?
6. Why do unit and MockMvc tests not replace testing with a real Supabase-signed token and two users?

## M1.3 — Explicit session lifecycle

1. Why must the server persist the active session instead of relying on a browser timer?
2. Why does a partial unique index remain necessary when the service already checks for an active session?
3. How does returning an already-stopped entry make retries safe without changing its authoritative end time?
4. Why is a page-unload request unreliable and incompatible with explicit stop semantics?

## M1.4 — Dashboard time semantics

1. Why are seven local calendar days not always the same duration as 168 hours?
2. What does a half-open range `[start, end)` prevent at adjacent reporting boundaries?
3. Why must the repository select sessions that overlap a range instead of only sessions that start inside it?
4. Why are sessions clipped before totals are calculated, while recent-session resources can still show their full duration?
5. Why does splitting at the next local start-of-day handle both 23-hour and 25-hour DST days?
6. What would break if dashboard bucketing used the server's system timezone?
7. Why does the dashboard contract use timestamp-derived seconds rather than stored or rounded minutes?
8. Why are active sessions excluded from completed totals even though the tracker can display their live elapsed time?
9. Why does the browser send an IANA timezone instead of only a numeric UTC offset?
10. How is the productivity score calculated, and why is its no-time value defined as zero?
11. Which stale frontend assumptions were exposed when the API changed from timeline/minute fields to daily/second fields?
12. Why should loading, empty, error, and retry states each have a distinct user-facing presentation?
