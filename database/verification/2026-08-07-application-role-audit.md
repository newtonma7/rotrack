# Application-role audit — 2026-08-07

A read-only JDBC catalog query audited the backend identity from the ignored local environment. The command printed booleans only; role, host, database, URL, and credentials remained redacted.

| Check | Result |
|---|---:|
| Non-superuser | PASS |
| `BYPASSRLS` enabled as required by the documented Spring boundary | PASS |
| Required `time_entries` SELECT/INSERT/UPDATE DML | PASS |
| Dedicated non-default role identity | **FAIL** |
| Cannot create databases | **FAIL** |
| Cannot create roles | **FAIL** |
| Cannot replicate | **FAIL** |
| Cannot create objects in `public` | **FAIL** |
| No TRUNCATE/REFERENCES/TRIGGER table-administration privileges | **FAIL** |

**Conclusion:** The configured backend identity is not the required dedicated least-privilege Spring application role. M2.1 remains in progress. No grants or roles were changed during verification.

**Required remediation:** Review and apply an explicit, non-destructive role/grant provisioning change in the development project, inject that dedicated role into the backend environment, and rerun this audit plus the authenticated two-user Spring flow. Production credentials and grants must not be changed without explicit approval.

## Corrected runtime-role audit — 2026-08-08

The original overprivileged identity was not modified. A separate `rotrack_runtime`
role was created and connected successfully over the Supabase session pooler.
The follow-up catalog output was:

| Check | Result |
|---|---:|
| Dedicated runtime identity | PASS |
| Non-superuser | PASS |
| `BYPASSRLS` | PASS |
| Login enabled | PASS |
| Cannot create databases | PASS |
| Cannot create roles | PASS |
| Cannot replicate | PASS |
| SELECT/INSERT/UPDATE on `public.time_entries` | PASS |
| DELETE on `public.time_entries` | DENIED as intended |
| CREATE on `public` schema | DENIED as intended |

The backend subsequently started with this role and passed live health/readiness
checks. No production credentials or roles were changed.
