# Single shared hosted environment

**Decision date:** 2026-08-10  
**Decision owner:** Product owner/operator  
**Status:** Authorized and implemented; release gates remain open

## Decision

The Azure subscription cannot provision a second Container Apps managed environment. The product owner accepted the operational risk of running one shared hosted environment instead of separate Preview and Production infrastructure.

The canonical hosted path is:

- Vercel Production alias: `https://rotrack-ecru.vercel.app`
- Azure API: `https://rotrack-api-nonproduction.victoriouspond-aad326d0.eastus2.azurecontainerapps.io`
- Supabase: the existing shared project (`mohvexzxaowfiweeuhxw`)
- Azure resource group/app: `rotrack-nonproduction` / `rotrack-api-nonproduction`

The separate `rotrack-prod` Supabase project and guarded production Azure lane are reserved but unused. Vercel Preview deployments may exist as disposable builds, but they are not a security, data, or rollback boundary.

## Accepted risks

- A backend deployment changes the API for every hosted user.
- Hosted tests and user data share the same database unless disposable accounts/data are used deliberately.
- A bad migration or release can affect the public application before rollback.
- Scale-to-zero cold starts can affect all users.
- There is no independent production rollback target.

This decision does not waive authentication, authorization, migration, rate-limit, observability, backup, smoke, or rollback gates. Do not use real private notes or other sensitive data for hosted testing.

## Operating rules

1. Use disposable accounts and data for smoke tests.
2. Apply migrations before application versions that require them, and retain an encrypted logical export before destructive changes.
3. Deploy only reviewed commits and immutable backend image digests.
4. Rebuild Vercel Production after changing any `NEXT_PUBLIC_*` value; those values are embedded at build time.
5. Keep CORS as an exact origin allowlist. Never add a wildcard or an ephemeral deployment URL as a permanent origin.
6. Treat Vercel Preview deployments as untrusted test builds. They must not be described as production or used as a separate rollback environment.
7. Reopen the environment-separation decision before adding a second hosted database or Azure boundary.

## Implemented readback — 2026-08-10

- The shared ACA revision was updated to allow the canonical Vercel Production alias and approved stable Vercel aliases. The API returned liveness `200`.
- A preflight for `https://rotrack-ecru.vercel.app` returned `200`, the exact `Access-Control-Allow-Origin`, and `Access-Control-Allow-Credentials: true`.
- An unrelated origin returned `403` without an allow-origin header.
- Vercel Production environment variables were set to the shared Supabase URL, shared publishable/anon key, and shared API `/api/v1` base. Values are intentionally not recorded here.
- The Production deployment was rebuilt and aliased to `https://rotrack-ecru.vercel.app`. Its compiled client assets contain the shared API hostname and Supabase project reference.

These checks prove wiring and CORS only. They do not prove authenticated dashboard success, backup/restore, alert delivery, cold-start acceptance, or rollback readiness. Those remain tracked in [`todo.md`](../../todo.md).

## Reversal

To return to separated environments, first obtain a second Azure Container Apps quota boundary, apply and verify the production Supabase migrations/runtime role, provision the guarded production lane, perform authenticated and operational release checks, then switch Vercel Production and exact CORS origins. Do not point the public frontend at `rotrack-prod` before the API/database/runtime-role contract has been verified.
