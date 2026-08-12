# Azure non-production deployment runbook

**Scope:** the approved ACA implementation boundary and its non-production guardrails. This runbook must not target the reserved separate production Azure/Supabase lane. The product-owner-approved single-environment decision allows the canonical hosted candidate to use the shared ACA boundary; it does not waive the M3/production-readiness STOP.

## Current candidate checkpoint — 2026-08-11

- Source commit `744635c` committed Azure readback/rollback-selection hardening. Focused Azure contract/readback, publish, preflight, RBAC, container, and release checks passed.
- The reviewed backend candidate was deployed to the canonical ACA implementation boundary. Candidate traffic was 100%; readiness/readback, selected digest/service-version equality, production runtime label, and scale `1..1` passed. Full revision and digest identifiers remain in private evidence.
- The canonical Vercel Production candidate was deployed from the same reviewed commit and canonical alias readback passed.
- Public smoke passed: frontend `200`, exact API liveness/readiness `200` contracts, HTTP redirect to HTTPS, allowed CORS, and denied unrelated CORS.
- Hosted authenticated smoke passed `4/4` with zero skipped, unexpected, or flaky results; API-target binding passed. Operator-owned synthetic accounts and stopped rows remain by product-owner decision, so cleanup is not claimed.
- The corrected exact no-schema-change backend/frontend rollback rehearsal passed prior backend health, prior frontend promotion, rollback public smoke, rollback authenticated `4/4`, candidate restoration, and final candidate health/CORS/auth; final state is the candidate.
- Rate limiting remains explicitly deferred/accepted. Cloudflare Free is future exploration only. Ten genuine zero-replica trials completed on 2026-08-11 with readiness 10/10, p50 34.586 seconds, and p95/max 39.425 seconds; the 30-second p95 criterion was not met. The product owner subsequently chose `minReplicas=1` for the canonical shared-hosted app and accepted the resulting idle cost pending actual billing. Collector redaction, alert delivery/receipt, and alert routing evidence remain open. The Azure action-group provider test-notification command returned failure; synthetic alert delivery is **NOT VERIFIED**, and no successful delivery or receipt is claimed. Backup limitation is accepted as already documented.

The M3/production-readiness STOP remains. Keep full identifiers only in private evidence.

## Historical non-production checkpoint — 2026-08-09

The platform owner authorized and the coordinator observed:

- Azure providers required by Container Apps, ACR, managed identity, Log Analytics, Insights, and quota are registered;
- resource group `rotrack-nonproduction` in `eastus2`;
- Container Apps Consumption environment `rotrack-nonproduction-env`;
- Basic ACR with admin login disabled and a user-assigned identity scoped to `AcrPull` on that registry;
- Log Analytics workspace with `PerGB2018`, 30-day retention, and a 0.1 GB daily cap;
- resource-group budget of 15 subscription-currency units per month with Actual alerts at 50%, 80%, and 100%;
- Container App `rotrack-api-nonproduction`, HTTPS-only ingress, port `8080`, scale `0..1`, 30-second termination grace, liveness/readiness probes, exact Vercel Preview CORS, and secrets injected through ACA secret references;
- an immutable Linux/amd64 OCI-compatible backend image in ACR; deployment readback proved the image digest equals `ROTRACK_SERVICE_VERSION`;
- `GET /api/v1/health` and `GET /api/v1/readiness` returned `200` after the corrected revision started;
- one Vercel Preview deployment built successfully with the non-production Supabase values and ACA API URL. Vercel SSO protection remains enabled; no automation-bypass secret remains configured;
- after a local-only unreachable-object finding, the non-production runtime database password was rotated, the prior password was rejected, local health/readiness passed, and the existing ACA configuration was redeployed and restarted; post-restart HTTPS health/readiness and deployment readback passed.

Production resources and `rotrack-prod` were not created, changed, migrated, or queried by this procedure. Public GitHub visibility, `main` protection, exact required contexts, `nonproduction` protected-main policy, empty auth-secret inventories, absent/default-disabled `ROTRACK_AUTHENTICATED_E2E_ENABLED`, and public-repo security features are separately read back. PR #18 supplied hosted-green protected-path evidence; PR #19 supplied deliberate-red required-check blocking evidence, with the required `Frontend` context failing and open PR metadata reporting `mergeStateStatus: BLOCKED`. One preliminary scale-from-zero health wake-up completed in 28.185 seconds; nine more trials and readiness p95/maximum are required. This checkpoint does **not** verify authenticated E2E, alert delivery, rollback, Supabase logical backup/restore, or production readiness.

## Cost posture

- ACR Basic is the predictable baseline cost (about USD 5/month at the observed retail rate).
- The canonical shared-hosted Container App now keeps one idle replica by product-owner decision; the subscription free grant applies before consumption charges, and actual billing will be observed.
- Log Analytics has a 0.1 GB daily cap and 30-day retention.
- The Azure budget is a notification, not a hard spending cap. Cost and student-credit data may be delayed.
- Initial application scale is one maximum replica. Reassess the fleet-wide rate-limit boundary before increasing it.

## Files and secret handling

Checked-in, non-secret assets:

- `deploy/azure/foundation.bicep`
- `deploy/azure/app.bicep`
- `scripts/azure/*.sh` (non-production lane)
- `scripts/azure/production/*.sh` (separate source-only production lane)
- `deploy/azure/tests/*.sh`

Populated parameter files stay outside Git under `~/.config/rotrack/azure/`:

- foundation parameters: mode `0400`;
- application/runtime parameters: mode `0600`.

Never print or commit database credentials, CA PEM, Supabase project refs, JWT settings, Vercel tokens, browser storage state, subscription/tenant/resource IDs, operator contact details, alert destinations, or populated parameter files. Keep the exact provider inventory and credential-rotation record in an access-controlled operator system outside Git. Local ignored `.env` files must be owner-readable only.

Treat this runbook and the infrastructure source as public information: authorization, least privilege, exact target validation, and provider controls must remain secure even when every checked-in command and resource label is known. Repository secrecy is not a substitute for those controls.

## Validate source without mutating Azure

```bash
scripts/azure/validate.sh
deploy/azure/tests/test-contract.sh
deploy/azure/tests/test-publish-image.sh
deploy/azure/tests/test-rbac-role-scope.sh
deploy/azure/tests/test-preflight-budget-shape.sh
deploy/azure/tests/test-readback-scale-shape.sh
deploy/azure/tests/test-target-isolation.sh
deploy/azure/tests/test-production-contract.sh
scripts/azure/production/validate.sh
scripts/container/test-contract.sh
scripts/release/check-release-safeguards.sh
```

## Provisioning order

The order is mandatory because an app cannot pull a digest before it exists:

1. `scripts/azure/foundation-provision.sh` — resource group, ACR, identity/RBAC, Log Analytics, ACA environment, and budget;
2. `scripts/azure/publish-image.sh` — clean-tree build, short-lived ACR login, push, and registry digest/media/OS/architecture readback;
3. create a Vercel Preview once to obtain its exact HTTPS origin;
4. `scripts/azure/app-deploy.sh` — deploy the existing digest with that exact Preview origin;
5. update only Vercel Preview's `NEXT_PUBLIC_API_URL`, rebuild Preview from the reviewed commit, then update ACA CORS to the final Preview URL;
6. `scripts/azure/readback.sh` — verify the exact image/service-version binding and runtime contract.

Required shell variables are intentionally explicit:

```bash
export AZURE_SUBSCRIPTION_ID='<selected-non-production-subscription-id>'
export AZURE_NONPRODUCTION_SUBSCRIPTION_ID="$AZURE_SUBSCRIPTION_ID"
export AZURE_FOUNDATION_PARAMETER_FILE="$HOME/.config/rotrack/azure/nonproduction-foundation.parameters.json"
export AZURE_APP_PARAMETER_FILE="$HOME/.config/rotrack/azure/nonproduction-app.parameters.json"
export ROTRACK_AZURE_CONFIRM=rotrack-nonproduction
export IMAGE_REPOSITORY=rotrack-api
export IMAGE_TAG="$(git rev-parse --short=12 HEAD)"
```

Do not place populated values in shell history. Prefer a private shell session or secure environment injection, and unset sensitive variables afterward.

## Readback and smoke

```bash
scripts/azure/preflight.sh
scripts/azure/readback.sh
```

Then verify:

- HTTPS health and readiness return only `{"status":"ok"}` / `{"status":"ready"}`;
- HTTP redirects to HTTPS;
- the exact Vercel Preview Origin receives `Access-Control-Allow-Origin`;
- an unrelated Origin does not receive that header;
- the active revision is healthy and uses the reviewed digest;
- no secret value appears in command output, logs, or evidence.

Vercel Preview currently uses Vercel Authentication. Keep it enabled. Do not generate or retain a project-wide automation-bypass secret until the protected authenticated workflow has an independently reviewed need and storage contract.

## Remaining release work

Before the M3 gate can pass:

1. keep the read-back `main` protection: pull requests, zero human approvals, strict app-bound required contexts, administrator enforcement, linear history, no force pushes/deletion, and advisory `CODEOWNERS`; keep the guarded environment policy intact;
2. keep repository/nonproduction/production auth-secret inventories empty and `ROTRACK_AUTHENTICATED_E2E_ENABLED` absent/default-disabled. The hosted authenticated smoke passed `4/4` with zero skipped/unexpected/flaky and API-target binding; retained synthetic accounts/rows are not claimed as cleanup;
3. observe collector redaction and route health, readiness, error, restart, auth, connection, budget, and credit-expiry alert delivery/receipt/routing. The Azure action-group provider test-notification command returned failure; synthetic alert delivery is **NOT VERIFIED**, and no successful delivery or receipt is claimed;
4. run at least ten scale-from-zero trials;
5. retain the documented Free-plan backup limitation and its accepted risk decision;
6. keep the corrected exact no-schema-change rollback rehearsal evidence with final candidate restoration; do not mark the release gate complete while rate limiting remains deferred/accepted or the observation safeguards remain open.

Production remains stopped until those gates pass and the owner separately authorizes production mutation.
