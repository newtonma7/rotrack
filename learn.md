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
5. Why does the opt-in PostgreSQL test require both an enable flag and an explicit isolated-target acknowledgement?
6. What is the difference between applying the checked-in migrations to PostgreSQL and recreating equivalent constraints in a temporary table?
7. Why are savepoints needed after deliberately triggering PostgreSQL constraint errors inside a larger transaction?
8. Why does an unconditional transaction rollback make the probe safer but still not prove that the caller selected the correct database?
9. Why is raw JDBC migration proof not the same as a Spring Data repository integration test?
10. Why must a skipped opt-in test be recorded as a limitation rather than a successful database verification?
11. Why does preassigning an ID to a `@GeneratedValue` entity make Spring Data choose `merge`, and how can that hide the intended database constraint assertion?

## M1.2 — Authentication and API errors

1. Why should the server validate JWT issuer, audience, time claims, and UUID `sub` instead of trusting a decoded token?
2. Why was the HS256 fallback removed, and how would keeping it expand the trust boundary?
3. What is the difference between authentication (`401`) and authorization (`403`), and why should resource ownership failures often appear as `404`?
4. Why is a stable error envelope more useful to the frontend than arbitrary exception strings?
5. What does `GlobalExceptionHandler` centralize, and what security risk does its generic fallback reduce?
6. Why do unit and MockMvc tests not replace testing with a real Supabase-signed token and two users?
7. Why must an unsupported-algorithm test publish a trusted key for that algorithm instead of relying on an unknown key rejection?
8. What additional boundary does a generated-token test exercise compared with calling the claim validator directly?
9. Why should a correctly signed token with a missing `sub` have its own test instead of being treated as equivalent to a malformed UUID?
10. What does a real service plus an owner-sensitive mocked repository prove, and what does it still leave unproven about Spring Data and PostgreSQL?
11. Why do active/dashboard reads return empty owned views for User B while an ID-based stop returns `404`?

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

## M1.5 — Browser test infrastructure

1. Why should authenticated Playwright storage state live outside the repository, and why are traces disabled for this suite?
2. Why does required-auth mode fail fast while the ordinary local command reports explicit skips?
3. How does capturing the API origin from the successful start response prevent a false-positive cross-user `404`?
4. Why are Work, Rot, browser-context reopen, and two-user isolation separate serialized scenarios?
5. Why can exact before/after dashboard totals become flaky at a rolling calendar boundary?
6. Why is asserting the new session ID appears for User A and never for User B more stable than comparing every pre-existing dashboard value?
7. Why must storage-state path validation resolve symlinks and require a regular file?
8. What remains unproven when Playwright discovers four tests but skips them because external auth is absent?
9. Why should dashboard integration tests compare the exact new-session delta instead of merely checking that an existing total is large enough?
10. Why must a two-user test compare User B's aggregate buckets before and after—not only check that User A's entry ID is absent from recent sessions?
11. Why should a transitive high-severity advisory be remediated in the lockfile and then revalidated with a fresh `npm ci`?
12. Why can an accessible `CardTitle` still fail a `getByRole("heading")` assertion when the shared primitive renders a `div`?
13. Why must an authenticated tracker E2E helper wait for the initial active-session request to settle before clicking activity buttons?
14. Why should an E2E timestamp comparison account for PostgreSQL microsecond persistence versus Java nanosecond response serialization?

## M2.2 — Deterministic startup and health boundaries

1. Why must the JDBC URL be the single source of truth for PostgreSQL `sslmode`?
2. How can URL-property precedence make an apparently strict TLS setting ineffective?
3. Why does managed PostgreSQL require `verify-full`, while `disable` is limited to an explicit local profile?
4. What is the difference between liveness and readiness, and why must liveness avoid database calls?
5. Why is an unauthenticated readiness endpoint useful to an orchestrator but dangerous if every request checks out a connection?
6. How do a short result cache and single-flight synchronization bound readiness pressure on the pool?
7. Why must pool size be budgeted across the maximum Container App replica count and revision overlap rather than per process only?
8. Which CORS origin properties must be rejected even when Java can parse the URI?
9. Why is HTTP allowed only for loopback development origins while deployed browser origins require HTTPS?
10. Why can a database-readiness failure cause Container App replica/revision churn even when container liveness stays healthy?
11. What does a `HikariDataSource` binding test prove that reading raw environment properties does not?
12. Why is `sslrootcert=system` not portable in PostgreSQL JDBC 42.7.x, and what failure does an explicit CA path avoid?
13. Why is capturing a server certificate from an unverified connection not a safe substitute for obtaining the provider's official CA certificate?
14. Why should repository/schema integration evidence be isolated from—and not falsely reported as—production TLS startup evidence?
15. Why can a component with multiple constructors pass direct unit tests but fail in a real Spring context unless the injection constructor is explicit?
16. Why is explicit readiness-TTL parsing more deterministic than depending on whichever conversion service a focused context happens to install?

## M2.1 — Supabase catalog, RLS, and application-role verification

1. What does checking `relrowsecurity` and policy catalog rows prove, and why does it not replace an HTTP Data API two-user matrix?
2. Why does inserting rollback-only `auth.users` fixtures prove the database trigger but not the real Supabase Auth signup flow?
3. Why is `BYPASSRLS` expected for Spring's pooled application role in this architecture, and where must ownership authorization then be enforced?
4. Why is a database identity still overprivileged if it is not a superuser but can create roles/databases, replicate, create schema objects, or hold table-administration privileges?
5. Why must grant remediation be reviewed and applied as an explicit operational migration rather than silently changed by a verification test?

## M3.1 — Pull-request CI

1. Why should third-party GitHub Actions be pinned to immutable commit SHAs instead of mutable version tags?
2. Why is a credential-free pull-request pipeline safer than making authenticated staging E2E part of every pull request?
3. What does the protected authenticated-E2E quarantine prevent, and why must required-auth mode fail on skips or an unexpected API target?
4. Why must migration CI apply the ordered migrations to an isolated PostgreSQL service instead of only parsing the SQL files?
5. Why should CI run both `mvn clean test` and `mvn package` when packaging can expose failures outside focused tests?
6. Why does scanning Git history alone miss secrets in the proposed change, and how can a prospective index/tree be scanned without modifying the developer's real index?
7. Why should artifact-upload and workflow-policy guards reject `.env` files, certificates, storage states, and mutable reusable-workflow references?
8. What does a deliberately failing required check prove that a locally failing script does not?
9. Why do green local CI-equivalent commands not prove that hosted required checks and branch protection are configured correctly?
10. Which M3.1 evidence still requires a hosted pull request and repository settings rather than source-controlled workflow files?

## M3.1 — GitHub public readiness and protected authenticated E2E

1. Why do `CODEOWNERS`, SHA-pinned workflows, local policy guards, and Dependabot configuration not prove that hosted branch protection or required environment reviewers are active?
2. Why must the public-or-paid GitHub protection decision precede adding authenticated non-production secrets?
3. Why does authenticated E2E use `repository_dispatch` from the trusted default branch instead of executing credentialed code from an untrusted pull request?
4. Why must the workflow bind the exact approved Preview and API hosts as well as the logical `nonproduction` environment?
5. Why must the two Playwright storage states contain distinct user IDs and access tokens and belong to the approved non-production Supabase project?
6. Why did the pre-public configured CodeQL workflow that skipped while the repository was private not count as a successful CodeQL scan?
7. Why are historical author-email privacy and future GitHub noreply configuration separate decisions, and why would rewriting the former require explicit approval?
8. What does scheduled Dependabot coverage prove, and what remains unproven about review and merge policy?
9. Why do one-time Gitleaks and OSV scans not replace recurring secret and backend dependency scanning?
10. Which risks do required automated checks, administrator-enforced branch protection, and blocked force pushes reduce for a solo maintainer—and which compromised-account risk do they not eliminate?
11. Why does keeping GitHub auth secrets empty and running disposable authenticated E2E from a trusted local context provide a safer solo-maintainer equivalent than pretending self-approval is independent review?
12. What did temporary public PR #17 prove about the reconciled scanner configuration? The five CI contexts and three default-setup `Analyze` contexts all appeared and succeeded, while the checked-in advanced `CodeQL (java)` and `CodeQL (javascript-typescript)` jobs failed because default setup rejects advanced SARIF; removing the advanced workflow leaves default setup as the sole scanner.
13. Why did PR #17 alone leave M3.1 **Implemented—unverified**, and what closed the gap? PR #17 proved the eight required contexts could pass but did not test enforcement. PR #19 deliberately failed the required `Frontend` context with a temporary TypeScript type error; its open metadata reported `mergeStateStatus: BLOCKED`, completing the hosted-green plus deliberate-red evidence needed to mark M3.1 **Verified**.

## M3.2 — Backend container and deployment artifact

1. How does a multi-stage image reduce the runtime attack surface and keep build tools and source out of the final image?
2. Why is a deny-by-default `.dockerignore` safer than trying to enumerate every possible secret or generated file?
3. What risks remain if a container declares a non-root user but the orchestrator can still grant a writable root filesystem or extra Linux capabilities?
4. Why must container liveness avoid the database while readiness verifies that the application can serve database-backed traffic?
5. Why should the provider CA be injected at runtime rather than copied from a developer machine into the image?
6. Why must the JDBC and container-entrypoint guards reject TLS override properties such as `sslfactory` and `sslhostnameverifier` in addition to requiring `sslmode=verify-full`?
7. What does a SIGTERM smoke test prove about graceful shutdown that a successful `docker stop` command alone may hide?
8. Why is an immutable registry digest release evidence while a local tag, image ID, or local content digest is not?
9. How should database connection limits account for desired replicas, maximum replicas, and overlapping revisions during rollout?
10. Why must a Container App's managed identity be narrowly scoped to the selected registry and runtime resources?
11. Why must the non-production deployment bind `ROTRACK_SERVICE_VERSION` to the exact deployed `sha256:<64 lowercase hex>` registry digest?
12. Why can a Docker manifest-v2 image still be OCI-compatible, and why must portability claims distinguish media type from runtime compatibility?
13. Why should Docker and Podman registry authentication use transient configuration rather than modify the operator's persistent credential store?
14. Why does locally proving read-only-root compatibility not justify claiming that Azure Container Apps enforces a read-only root filesystem?

## M3.2 — Production-separated non-production operations

1. Why must the non-production Supabase project, Vercel Preview values/build, and Azure boundaries be demonstrably separated from `rotrack-prod`, Vercel Production values/build, and the production Azure environment/resource group/app?
2. Why do placeholder-only templates and committed variable names provide a useful contract without becoming deployment evidence?
3. What should an Azure read-only preflight verify about the selected subscription, managed environment, resource group, Container App, managed identity, registry pull, and secret boundary before deployment?
4. Why must the application runtime role be audited for memberships, direct grants, sequence access, routine execution, and `BYPASSRLS` rather than checked only for superuser status?
5. Why is `BYPASSRLS` intentional for this backend architecture, and which ownership boundary must compensate for it?
6. Why must the non-production CORS allowlist contain exact HTTPS frontend origins rather than wildcards or loosely matched domains?
7. Why is an official managed-database CA provenance record necessary even after local TLS container tests pass?
8. Why do successful local render and validation steps not prove that Vercel, Container Apps, Supabase, managed identity, DNS, or TLS are configured remotely?
9. Which values belong in a redacted non-production evidence record, and which values must never be committed?
10. Why do observed non-production health, readiness, CORS, and Preview build results still not establish authenticated smoke, alerting, cold-start, backup, or rollback readiness?
11. Why must Free-plan pause ownership, encrypted logical-export retention, and a restore rehearsal (or explicit product-owner risk acceptance) be release safeguards?
12. Why are Azure budget alerts notifications rather than a hard spending cap, and why must delayed cost/credit-expiry data be accounted for?

## M3.2 — Observed Azure and Vercel non-production checkpoint

1. Why are Azure foundation provisioning, image publication, and application deployment separate ordered steps?
2. Why should Azure CLI adapters have tests for provider-specific JSON shapes, absent optional scale properties, and normalized budget output?
3. Why must budget readback check amount, period, and Actual alert thresholds instead of trusting that resource creation succeeded?
4. What did the initial ACA revision reveal when it rejected digest-form service versions, and why was a failing test needed before the fix?
5. Why can Vercel Preview and Production not promote identical frontend bytes when `NEXT_PUBLIC_*` values are embedded at build time?
6. Why was a CLI-generated Vercel automation bypass treated as project-wide risk and removed immediately?
7. Why is one scale-from-zero wake-up insufficient, and what do ten trials plus p95 and maximum readiness establish?
8. Why must evidence explicitly record that `rotrack-prod`, Vercel Production values/deployments, and production Azure resources were not mutated?

## M3.3 — Release safeguards and observability

1. Why should a release apply backward-compatible database migrations before deploying application revisions?
2. Why can an application image usually be rolled back independently while a destructive database migration often cannot?
3. Why must a rollback rehearsal identify the exact candidate and prior immutable image digests?
4. Why should rollback hooks be external regular files that are operator-owned and not group- or world-writable?
5. What distinct failures are detected by health, latency, error-rate, restart, authentication-failure, and connection-exhaustion alerts?
6. Why are documented thresholds insufficient until telemetry ingestion, dashboards, routing, and an alert notification test are observed?
7. Why is a structured-log allowlist safer than attempting to redact an unlimited set of arbitrary fields after logging?
8. Which data must never appear in logs, traces, alerts, or monitoring payloads, and how can a redaction sentinel test that boundary?
9. Why do rate limiting, named incident staffing, non-production smoke, and rollback rehearsal remain production blockers even when all source-level safeguards pass?
10. Why should authenticated smoke and rollback commands be prepared in advance but executed only after hosts, protection, identities, and candidate/prior digests are approved and read back?
11. What evidence is required to move M3.3 from **In progress** to **Verified**?
12. Why must M4 feature implementation remain gated until the complete M3 MVP release gate passes?
13. Why are successful public health, readiness, HTTPS, and CORS checks insufficient without fresh-user sign-in and authenticated non-production Playwright 4/4?
14. Why is a process-local per-user mutation limiter not a substitute for a fleet-wide, authentication-adjacent edge control?
15. Why must the evidence record distinguish source safeguards, configured cloud state, observed runtime behavior, and still-unverified operations?
16. Why is globally disabling Spring CSRF protection a valid CodeQL finding even when the current stateless bearer-token API is not directly vulnerable to browser cookie CSRF, and why should only the `/api/v1/**` boundary be exempted?

### Evidence reconciliation — 2026-08-11

- Source commit `744635c` made the Azure readback/rollback-selection hardening testable; focused contract/readback, publish, preflight, RBAC, container, and release checks passed.
- A candidate can be fully read back at the canonical ACA/Vercel boundary without making the full production-readiness gate true. Candidate traffic, runtime label, scale, readiness, selected digest/service-version equality, public smoke, API-target-bound authenticated `4/4`, and corrected exact no-schema-change rollback all passed, but M3 remains stopped.
- Rollback rehearsal must verify both tiers, public behavior, authenticated behavior, candidate restoration, and final candidate health/CORS/auth. “Rollback passed” is not permission to skip unresolved release safeguards.
- Cleanup is a separate claim: product-owner-retained synthetic accounts and stopped rows must remain explicitly unclaimed when they are not removed.
- Rate limiting remains an accepted blocker even after application-level defenses and smoke pass. Cloudflare Free is future exploration only. Ten cold-start trials, collector redaction, alert delivery/receipt, and alert routing remain open; the Azure action-group provider test-notification command returned failure, so synthetic alert delivery is **NOT VERIFIED** and no successful delivery or receipt is claimed. The documented backup limitation is accepted.
