# Non-production deployment evidence — 2026-08-09

## Authorization and separation

- Operator/reviewer roles: platform owner plus independent read-only agent reviews; private contacts omitted.
- Explicit non-production authorization: yes.
- Existing shared non-production Supabase project selected: yes.
- `rotrack-prod`, Vercel Production deployment/environment values, and production Azure resources mutated: no. A CLI-generated project-wide Vercel automation bypass was removed immediately, restoring zero bypass secrets.
- Repository is public. Read-only GitHub metadata confirms protected `main` with pull requests, zero human approvals, strict app-bound checks, administrator enforcement, linear history, no force pushes/deletion, and advisory `CODEOWNERS`; `nonproduction` allows exactly protected `main` with no reviewers or secrets; `production` was unchanged and has zero secrets.

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

- Full reachable history, remote mirror, prospective candidate, and post-cleanup unreachable-object scans found no retained leaks. The remote mirror covered all current remote refs and branches.
- Tracked tree: no `.env`, browser storage state, private key, certificate, user email, live provider hostname, cloud account/project identifier, or build output.
- The public-readiness audit removed the stale `CHAT_PROMPT.md` artifact and generalized developer-specific absolute paths.
- One local-only unreachable Git object contained populated non-production database configuration. It was absent from all refs/reflogs and the GitHub object API, then was purged by exact object ID without exposing its values. The non-production runtime password and consumers were subsequently rotated and verified below.
- One-off dependency check: checksum-verified OSV Scanner `2.5.0` ran `scan source --recursive` over the repository while excluding `node_modules`, `target`, and `.next`; exit `0`, zero result groups/findings. Frontend `npm audit --audit-level=high` also passed. Recurring backend dependency scanning is still absent and remains a publication follow-up.
- Dependabot alerts and automated security fixes enabled; open Dependabot alerts: zero at readback.
- Actions restricted to GitHub-owned actions; checked-in actions are full-SHA pinned and a source guard enforces that policy.
- Public-repo security readback: vulnerability reporting, Dependabot security fixes, secret scanning, and push protection enabled; repository, `nonproduction`, and `production` auth-secret inventories empty; `ROTRACK_AUTHENTICATED_E2E_ENABLED` absent/default-disabled.
- Default CodeQL setup is the sole chosen scanner with required contexts `Analyze (actions)`, `Analyze (java-kotlin)`, and `Analyze (javascript-typescript)`. The checked-in advanced workflow was removed after temporary public PR #17 proved its `CodeQL (java)` and `CodeQL (javascript-typescript)` jobs fail because default setup rejects advanced SARIF.
- Historical temporary PR #17 used a dedicated branch/worktree and one empty credential-free commit. All eight required contexts appeared and succeeded; its advanced CodeQL conflict is preserved as historical evidence, and its remote branch, local branch, and worktree were removed.
- PR #18 passed all eight required contexts and merged through the protected rebase path as `12dfdffb62ee51f6912b290eecc651444e797b90` after removing the conflicting advanced CodeQL workflow. PR #19 used one isolated temporary TypeScript type-error file; required `Frontend` under GitHub Actions app ID `15368` failed with TS2322, the other seven required contexts succeeded, and open PR metadata reported `mergeStateStatus: BLOCKED`. PR #19 was closed unmerged; its remote/local branch, worktree, and temporary file were removed. M3.1 is **Verified**.
- The product owner approved a solo-maintainer equivalent: required automated checks and protected `main`, no force push/deletion, `CODEOWNERS` advisory, GitHub environment auth secrets empty, and authenticated E2E run from a trusted local operator context. The hosted credentialed job remains administrator-disabled unless a future second reviewer and protected environment are available.
- After the deeper audit identified three personal-domain author addresses across 59 historical commits, the product owner explicitly chose to retain all three. No user email is present in tracked file content. Future local commits use the GitHub noreply address.

## Credential rotation after public-readiness audit

- A local-only unreachable Git object containing populated non-production database configuration was absent from refs, reflogs, the remote mirror, and the GitHub object API, then purged by exact object ID.
- The product owner authorized non-production rotation and Azure redeployment. The runtime database password was changed without printing it; the new password authenticated and the old password failed.
- The ignored local backend environment and external mode-`0600` Azure application parameter file were updated. The local backend returned health/readiness `200`.
- The existing immutable non-production Container App configuration was redeployed and its active revision restarted so it could not retain the prior secret. Post-restart HTTPS health/readiness returned `200`; digest/service-version, secret references, HTTPS ingress, probes, and scale readback still passed.
- Temporary private rollback copies were securely removed after verification. No production resource, credential, database, or deployment was queried or mutated.

## Open blockers

1. Keep GitHub environment auth secrets empty and the hosted authenticated job administrator-disabled; run authenticated Playwright 4/4 from a trusted local operator context with disposable external state and exact approved hosts.
2. Collector redaction sentinel, dashboards, alert routing/delivery, and credit-expiry notification.
3. Complete the remaining nine scale-from-zero trials and calculate readiness p95/maximum; one preliminary wake-up took 28.185 seconds.
4. Supabase encrypted logical exports and restore rehearsal, or explicit data-loss risk acceptance.
5. Exact-digest rollback rehearsal.

Production remains stopped.
