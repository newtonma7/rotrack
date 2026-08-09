# Non-production deployment evidence — 2026-08-09

## Authorization and separation

- Operator/reviewer roles: platform owner plus independent read-only agent reviews; private contacts omitted.
- Explicit non-production authorization: yes.
- Existing shared non-production Supabase project selected: yes.
- `rotrack-prod`, Vercel Production deployment/environment values, and production Azure resources mutated: no. A CLI-generated project-wide Vercel automation bypass was removed immediately, restoring zero bypass secrets.
- Logical GitHub environments exist, but required protection is unavailable while the Free repository is private: open blocker.

## Artifact and infrastructure

- Reviewed image source revision: `ea9b70b`.
- Registry digest: `sha256:a4cced479f81daebe73f0b4e8ee9f0494ca381d297992664929c68df76d92a3a`.
- Registry readback: Linux/amd64; OCI-compatible Docker manifest v2; immutable digest.
- Runtime: Java 21; UID/GID `10001:10001`; port `8080`; writes limited to `/tmp`; 30-second platform termination grace; 25-second Spring drain.
- Azure boundary: `eastus2`, separate non-production resource group, Consumption managed environment, Container App, Basic ACR, and user-assigned identity with exact `AcrPull` scope.
- Log Analytics: `PerGB2018`, 30-day retention, 0.1 GB daily cap.
- Budget: 15 subscription-currency units/month; Actual alerts at 50%, 80%, and 100%. Delivery and student-credit-expiry notification are not yet observed.
- Scaling: min `0`, max `1`, single active revision; database pool max `5`, minimum idle `0`.
- Deployment readback: running image digest equals `ROTRACK_SERVICE_VERSION`; all secret inputs are ACA `secretRef` mappings.
- ACA read-only-root enforcement: unsupported/unverified. Compensating non-root, `/tmp`, no-debug, identity, and secret controls are configured.

## Vercel Preview

- One existing Vercel project used; Preview only.
- Preview build completed successfully with the three approved `NEXT_PUBLIC_*` names and the ACA `/api/v1` URL.
- Exact final Preview origin is the only configured ACA CORS origin; an unrelated HTTPS Origin received no allow-origin header.
- Vercel SSO protection remains enabled. A temporary CLI-generated automation-bypass secret was removed immediately; readback reports zero remaining bypass secrets.
- Direct anonymous frontend smoke is therefore intentionally blocked by Vercel SSO. Browser-asset target inspection and authenticated E2E remain open.

## Fresh-user local acceptance

- The product owner/operator attested that the already-confirmed fresh disposable user completed first sign-in and reached `/dashboard`; passwords, tokens, and account details were not recorded.
- The local frontend ran at `http://localhost:3001`. Independent preflight readback returned HTTP 200 with exact allow-origin for port 3001 and HTTP 403 with no allow-origin for port 3000.
- This closes the manual fresh-user first-sign-in step. It is local acceptance evidence, not the still-open automated authenticated non-production Playwright 4/4 run.

## Smoke results

```text
Azure source/render/negative contracts: passed
Foundation provider readback: passed
ACR digest/media/OS/architecture readback: passed
ACA runtime contract and secretRef readback: passed
GET /api/v1/health: 200 {"status":"ok"}
GET /api/v1/readiness: 200 {"status":"ready"}
HTTP API request: 301 redirect to HTTPS
Allowed exact Preview CORS origin: allow-origin present
Unrelated CORS origin: allow-origin absent
Vercel Preview build: READY
Preliminary scale-from-zero trial 1/10: health wake-up 28.185s; readiness immediately afterward 0.062s
```

The first deployed revision exposed a contract defect: the application rejected the required `sha256:<64-hex>` service version. A failing test was added first; `LoggingProperties` now accepts only that exact digest form in addition to existing bounded legacy identifiers. Java 21 package evidence after the fix: 90 tests discovered, 86 passed, four expected opt-in skips, zero failures/errors. The corrected digest was rebuilt, republished, redeployed, and health/readiness passed.

## GitHub/public-readiness evidence

- Full reachable/side/unreachable history Gitleaks scans: no leaks found.
- Tracked tree: no `.env`, browser storage state, private key, certificate, or build output.
- One-off dependency check: checksum-verified OSV Scanner `2.5.0` ran `scan source --recursive` over the repository while excluding `node_modules`, `target`, and `.next`; exit `0`, zero result groups/findings. Frontend `npm audit --audit-level=high` also passed. Recurring backend dependency scanning is still absent and remains a publication follow-up.
- Dependabot alerts and automated security fixes enabled; open Dependabot alerts: zero at readback.
- Actions restricted to GitHub-owned actions; checked-in actions are full-SHA pinned and a source guard enforces that policy.
- CodeQL source is configured to run only when the repository becomes public.
- The product owner chose to retain the historical author metadata, including its personal-domain author email. Future local commits use the GitHub noreply address; historical metadata rewriting is no longer a blocker.

## Open blockers

1. Deliberate public-or-paid GitHub protection transition; historical author metadata will be retained.
2. Required `main` checks/code-owner review and `nonproduction` reviewer/branch restrictions.
3. Exact GitHub host variables and disposable storage-state secrets, then authenticated Playwright 4/4.
4. Collector redaction sentinel, dashboards, alert routing/delivery, and credit-expiry notification.
5. Complete the remaining nine scale-from-zero trials and calculate readiness p95/maximum; one preliminary wake-up took 28.185 seconds.
6. Supabase encrypted logical exports and restore rehearsal, or explicit data-loss risk acceptance.
7. Exact-digest rollback rehearsal.

Production remains stopped.
