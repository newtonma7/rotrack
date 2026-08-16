# rotrack Spring Boot API

REST API for rotrack time tracking. Validates Supabase JWTs and connects to Supabase PostgreSQL.

## Prerequisites

- Java 21+
- Maven 3.9+
- Supabase project with all `database/migrations/*.sql` files applied in order

## Setup

1. Copy `.env.example` values into your environment or IDE run configuration.
2. Apply all ordered migrations through `database/migrations/006_notes.sql` to your Supabase project.
3. Run the API:

```bash
mvn spring-boot:run
```

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

Notes writes require a stable runtime-only `ROTRACK_NOTES_HMAC_SECRET` of at least 32 UTF-8 bytes when `ROTRACK_NOTES_WRITES_ENABLED=true`. `/health` and `/readiness` are unauthenticated, sanitized orchestrator probes. All application-data endpoints require `Authorization: Bearer <supabase_jwt>`.
