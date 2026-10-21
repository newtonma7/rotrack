# rotrack Spring Boot API

REST API for rotrack time tracking. Validates Supabase JWTs and connects to Supabase PostgreSQL.

## Prerequisites

- Java 21+
- Maven 3.9+
- Supabase Auth configuration and a PostgreSQL schema matching the checked-out API

## Setup

1. Copy `.env.example` values into your environment or IDE run configuration.
2. For M5.3 source validation, apply ordered migrations through `database/migrations/007_reflections.sql` to a disposable local PostgreSQL database. The hosted environment remains on migration 006. Applying 007 or deploying this API to the hosted environment requires separate product-owner authorization.
3. Run the API:

```bash
mvn spring-boot:run
```

M6.1 currently adds only migration `008_friendships.sql` and isolated storage verification. Apply 001–008 to a disposable local database to run that suite, following [`src/test/README.md`](src/test/README.md). Friendship/search endpoints are not implemented yet. Hosted migration 008 remains unauthorized.

Liveness: `GET http://localhost:8080/api/v1/health`

Database readiness: `GET http://localhost:8080/api/v1/readiness`

## Endpoints

- `POST /api/v1/time-entries/start` — start session `{ "activityType": "WORK" | "ROT" }`
- `PUT /api/v1/time-entries/{id}/stop` — stop session
- `GET /api/v1/time-entries/active` — get active session
- `GET /api/v1/dashboard/stats` — timezone-aware aggregates
- `GET /api/v1/preferences`, `PUT /api/v1/preferences` — owned settings
- `GET /api/v1/time-entries/history`, `POST /api/v1/time-entries`, `PUT /api/v1/time-entries/{id}`, `DELETE /api/v1/time-entries/{id}` — owned completed history
- `GET /api/v1/notes`, `GET /api/v1/notes/{id}`, `POST /api/v1/notes`, `PUT /api/v1/notes/{id}`, `DELETE /api/v1/notes/{id}` — owned private Notes

- `GET /api/v1/daily-logs`, `GET /api/v1/daily-logs/{localDate}` return generated owned daily summaries and detail.
- `PUT /api/v1/daily-logs/{localDate}/reflection` saves a private versioned Reflection. Exact contracts are in [`docs/specs/m5-contracts.md`](../docs/specs/m5-contracts.md).

Notes and Reflection writes require a stable runtime-only `ROTRACK_NOTES_HMAC_SECRET` of at least 32 UTF-8 bytes when `ROTRACK_NOTES_WRITES_ENABLED=true`. `/health` and `/readiness` are unauthenticated, sanitized orchestrator probes. All application-data endpoints require `Authorization: Bearer <supabase_jwt>`.
