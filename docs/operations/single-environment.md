# Single shared hosted environment

**Decision date:** 2026-08-11
**Decision owner:** Product owner/operator
**Status:** Authorized shared implementation boundary; full M3 and production-readiness gates remain stopped, with dated narrow M4 and M5.2 rollout decisions below

## Decision

The Azure subscription does not currently provide a second Container Apps managed-environment quota boundary. The product owner accepts one shared hosted implementation boundary for the canonical hosted path until that boundary can be separated.

The current path is:

- canonical Vercel Production alias;
- canonical ACA implementation boundary;
- the existing shared hosted Supabase project;
- the existing shared Azure resource boundary.

The reserved separate production Supabase/Azure lane is unused. Full aliases, project references, revision identifiers, digests, account details, and deployment identifiers remain in private evidence only.

This is an operational boundary decision, not a general release approval. The dated product-owner decisions below permit only narrow zero-user/pre-user M4 and M5.2 rollout actions while full M3 remains unmet. They do not waive unresolved M3 operational risks, authorize M5.3, or support broad production-readiness claims.

## Narrow M4 rollout override — 2026-08-12

For the zero-user/pre-user boundary only, the product owner authorized applying migrations `004_user_preferences.sql` and `005_time_entry_history.sql`, deploying the reviewed M4 application, and running hosted M4 acceptance despite the full M3 gate. That rollout passed and M4 is **Verified**.

## M5.2 rollout authorization — 2026-08-16

For the zero-user/pre-user boundary only, the product owner authorizes migration `006_notes.sql`, deployment of protected-PR-reviewed M5.2 backend/frontend artifacts, stable hosted-only Notes HMAC secret injection, Notes writes, and disposable-user acceptance on this canonical shared path. Apply the additive migration before the dependent application and retain it during application rollback. M5.2 remains **Implemented—unverified** until hosted evidence passes. This authorization excludes M5.3 and broad production-readiness claims.

## Accepted risks

- A backend deployment changes the API for every hosted user.
- Hosted smoke and user data share the same database unless disposable accounts/data are used deliberately.
- A bad migration or release can affect the public application before rollback.
- Scale-to-zero cold starts can affect all users.
- There is no independent production rollback target.

## Operating rules

1. Use disposable accounts and data for smoke tests.
2. Apply migrations before application versions that require them, and retain the documented backup risk decision.
3. Deploy only reviewed commits and immutable backend artifacts.
4. Rebuild Vercel Production after changing any `NEXT_PUBLIC_*` value; those values are embedded at build time.
5. Keep CORS as an exact origin allowlist. Never add a wildcard or an ephemeral deployment URL as a permanent origin.
6. Treat Preview deployments as disposable test builds, not as a separate security, data, or rollback boundary.
7. Reopen this decision before adding a second hosted database or Azure boundary.

## Candidate readback — 2026-08-11

- Source commit `744635c` passed focused Azure contract/readback, publish, preflight, RBAC, container, and release checks.
- The backend candidate reached 100% traffic at the canonical ACA boundary. Readiness/readback, selected digest/service-version equality, production runtime label, and scale `1..1` passed.
- The same reviewed commit produced the canonical Vercel Production candidate, and canonical alias readback passed.
- Public smoke passed for frontend `200`, exact API liveness/readiness `200` contracts, HTTP redirect to HTTPS, allowed CORS, and denied unrelated CORS.
- Hosted authenticated smoke passed `4/4` with zero skipped, unexpected, or flaky results; API-target binding passed.
- The corrected exact no-schema-change backend/frontend rollback rehearsal passed prior backend health, prior frontend promotion, rollback public smoke, rollback authenticated `4/4`, candidate restoration, and final candidate health/CORS/auth. Final state is the candidate.
- Operator-owned synthetic accounts and stopped rows remain by product-owner decision; cleanup is not claimed.
- Rate limiting remains explicitly deferred/accepted. Cloudflare Free is future exploration only. Ten genuine zero-replica trials completed on 2026-08-11 with readiness 10/10, p50 34.586 seconds, and p95/max 39.425 seconds; the 30-second p95 criterion was not met. The product owner subsequently chose `minReplicas=1` for the canonical shared-hosted app and accepted the resulting idle cost pending actual billing. Collector redaction, alert delivery/receipt, and alert routing evidence remain open. The Azure action-group provider test-notification command returned failure; synthetic alert delivery is **NOT VERIFIED**, and no successful delivery or receipt is claimed. The backup limitation is accepted as already documented.

## Product-owner pre-user posture — 2026-08-11

The product has zero active users. Broad observability, collector-side redaction proof, threshold tuning, and fleet-wide edge rate limiting are deferred until before real-user onboarding or until usage/abuse makes them material. This does not waive the documented production-readiness STOP.

## Reversal

To return to separated environments, obtain a second Azure quota boundary, establish the reserved production Supabase/Azure lane, perform the required migration, runtime-role, RLS, browser, health, monitoring, budget, and rollback checks, then switch the canonical frontend/API path and exact CORS origins. Do not point the public frontend at the reserved production data boundary before its full contract is verified.
