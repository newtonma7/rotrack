#!/usr/bin/env bash
set -euo pipefail

: "${ROTRACK_TEST_DATABASE_ISOLATED:?Set ROTRACK_TEST_DATABASE_ISOLATED=true for a disposable database}"
if [[ "${ROTRACK_TEST_DATABASE_ISOLATED,,}" != "true" ]]; then
  printf 'Refusing migration apply without ROTRACK_TEST_DATABASE_ISOLATED=true.\n' >&2
  exit 1
fi

for libpq_override in PGHOSTADDR PGSERVICE PGSERVICEFILE; do
  if [[ -n "${!libpq_override:-}" ]]; then
    printf 'Refusing migration apply while %s can override the isolated connection target.\n' "$libpq_override" >&2
    exit 1
  fi
done
unset PGHOSTADDR PGSERVICE PGSERVICEFILE

pg_host=${PGHOST:-localhost}
case "$pg_host" in
  localhost|127.0.0.1|::1) ;;
  *)
    printf 'Refusing migration apply to non-loopback PostgreSQL host: %s\n' "$pg_host" >&2
    exit 1
    ;;
esac

pg_port=${PGPORT:-5432}
pg_database=${PGDATABASE:-rotrack_ci}
pg_user=${PGUSER:-postgres}
psql_args=(--host "$pg_host" --port "$pg_port" --username "$pg_user" --dbname "$pg_database" --set ON_ERROR_STOP=1 --no-psqlrc)

"$(dirname "$0")/validate-migration-order.sh"

# Supabase supplies this auth boundary. The isolated CI service recreates only
# the minimum contract needed to apply and verify the repository migrations.
psql "${psql_args[@]}" <<'SQL'
BEGIN;
CREATE SCHEMA auth;
CREATE TABLE auth.users (
  id UUID PRIMARY KEY,
  email TEXT,
  raw_user_meta_data JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE FUNCTION auth.uid()
RETURNS UUID
LANGUAGE sql
STABLE
AS 'SELECT NULL::UUID';
COMMIT;
SQL

while IFS= read -r migration; do
  printf 'Applying %s\n' "$migration"
  psql "${psql_args[@]}" --single-transaction --file "$migration"
done < <(find database/migrations -maxdepth 1 -type f -name '*.sql' | sort)

printf 'Applied all ordered migrations to the isolated local PostgreSQL target.\n'
