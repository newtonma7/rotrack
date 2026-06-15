# rotrack Spring Boot API

REST API for rotrack time tracking. Validates Supabase JWTs and connects to Supabase PostgreSQL.

## Prerequisites

- Java 21+
- Maven 3.9+
- Supabase project with migrations applied (`database/migrations/001_initial_schema.sql`)

## Setup

1. Copy `.env.example` values into your environment or IDE run configuration.
2. Apply the database migration to your Supabase project.
3. Run the API:

```bash
mvn spring-boot:run
```

Health check: `GET http://localhost:8080/api/v1/health`

## Endpoints

- `POST /api/v1/time-entries/start` — start session `{ "activityType": "WORK" | "ROT" }`
- `PUT /api/v1/time-entries/{id}/stop` — stop session
- `PUT /api/v1/time-entries/active/stop` — stop active session
- `GET /api/v1/time-entries/active` — get active session
- `GET /api/v1/dashboard/stats` — weekly aggregates

All endpoints except `/health` require `Authorization: Bearer <supabase_jwt>`.
