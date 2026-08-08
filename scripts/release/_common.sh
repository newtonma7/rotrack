#!/usr/bin/env bash

# Shared fail-closed validation for release scripts. This file must be sourced.
set +x
set -Eeuo pipefail
umask 077

RELEASE_SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
RELEASE_REPOSITORY_ROOT="$(cd -- "${RELEASE_SCRIPT_DIR}/../.." && pwd -P)"

release_die() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

release_require_command() {
  command -v "$1" >/dev/null 2>&1 || release_die "required command is unavailable: $1"
}

release_require_variable() {
  local name="$1"
  [[ -n "${!name:-}" ]] || release_die "required variable is unset: ${name}"
}

release_url_host() {
  local url="$1"
  local authority="${url#https://}"
  authority="${authority%%/*}"
  local host="${authority%%:*}"
  printf '%s\n' "${host,,}"
}

release_validate_https_url() {
  local name="$1"
  local url="${!name:-}"
  release_require_variable "$name"
  [[ "$url" == https://* ]] || release_die "${name} must use HTTPS"
  [[ "$url" != *'?'* && "$url" != *'#'* && "$url" != *'@'* ]] || \
    release_die "${name} must not contain a query, fragment, or user information"

  local host
  host="$(release_url_host "$url")"
  [[ "$host" =~ ^[A-Za-z0-9][A-Za-z0-9.-]*[A-Za-z0-9]$ ]] || \
    release_die "${name} must contain a valid DNS hostname"
  [[ "$host" != *.invalid && "$host" != localhost && "$host" != 127.* && "$host" != 0.0.0.0 ]] || \
    release_die "${name} contains a placeholder or loopback hostname"
}

release_inventory_value() {
  local key="$1"
  local line_count
  line_count="$(grep -Ec "^${key}=" "$ROTRACK_STAGING_INVENTORY_FILE" || true)"
  [[ "$line_count" == 1 ]] || release_die "staging inventory must contain exactly one ${key} entry"
  grep -E "^${key}=" "$ROTRACK_STAGING_INVENTORY_FILE" | cut -d= -f2-
}

release_validate_staging_inventory() {
  release_require_command stat
  release_require_command id
  release_require_variable ROTRACK_STAGING_INVENTORY_FILE
  local path="$ROTRACK_STAGING_INVENTORY_FILE"
  [[ -f "$path" && ! -L "$path" ]] || \
    release_die "ROTRACK_STAGING_INVENTORY_FILE must name a regular, non-symlink file"
  local canonical permissions owner
  canonical="$(cd -- "$(dirname -- "$path")" && pwd -P)/$(basename -- "$path")"
  [[ "$canonical" != "${RELEASE_REPOSITORY_ROOT}"/* ]] || \
    release_die "ROTRACK_STAGING_INVENTORY_FILE must remain outside the repository"
  permissions="$(stat -c '%a' "$canonical")" || release_die "could not inspect staging inventory permissions"
  owner="$(stat -c '%u' "$canonical")" || release_die "could not inspect staging inventory owner"
  [[ "${permissions: -2}" == 00 && "$owner" == "$(id -u)" ]] || \
    release_die "staging inventory must be owned by the operator and inaccessible to group/other"

  local key expected actual
  for key in \
    ROTRACK_STAGING_TARGET_ID \
    ROTRACK_STAGING_FRONTEND_URL \
    ROTRACK_STAGING_API_URL \
    ROTRACK_STAGING_SUPABASE_PROJECT_REF \
    ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF \
    ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF \
    ROTRACK_PRODUCTION_FRONTEND_URL \
    ROTRACK_PRODUCTION_API_URL; do
    expected="${!key:-}"
    release_require_variable "$key"
    actual="$(release_inventory_value "$key")"
    [[ "$actual" == "$expected" ]] || release_die "release configuration does not match the approved staging inventory for ${key}"
  done
}

release_validate_staging_context() {
  [[ "${ROTRACK_RELEASE_ENVIRONMENT:-}" == "staging" ]] || \
    release_die "ROTRACK_RELEASE_ENVIRONMENT must be exactly staging"
  [[ "${ROTRACK_STAGING_TARGET_ID:-}" =~ ^staging-[a-z0-9][a-z0-9-]{2,62}$ ]] || \
    release_die "ROTRACK_STAGING_TARGET_ID must be an approved staging-* identifier"
  [[ "${ROTRACK_STAGING_TARGET_ID}" != *placeholder* ]] || \
    release_die "ROTRACK_STAGING_TARGET_ID still contains a placeholder"
  release_validate_https_url ROTRACK_STAGING_FRONTEND_URL
  release_validate_https_url ROTRACK_STAGING_API_URL
  release_validate_https_url ROTRACK_PRODUCTION_FRONTEND_URL
  release_validate_https_url ROTRACK_PRODUCTION_API_URL
  [[ "${ROTRACK_STAGING_API_URL%/}" == */api/v1 ]] || \
    release_die "ROTRACK_STAGING_API_URL must end in /api/v1"
  [[ "${ROTRACK_PRODUCTION_API_URL%/}" == */api/v1 ]] || \
    release_die "ROTRACK_PRODUCTION_API_URL must end in /api/v1"

  local frontend_host api_host production_frontend_host production_api_host
  frontend_host="$(release_url_host "$ROTRACK_STAGING_FRONTEND_URL")"
  api_host="$(release_url_host "$ROTRACK_STAGING_API_URL")"
  production_frontend_host="$(release_url_host "$ROTRACK_PRODUCTION_FRONTEND_URL")"
  production_api_host="$(release_url_host "$ROTRACK_PRODUCTION_API_URL")"
  [[ "$frontend_host" != "$production_frontend_host" && "$frontend_host" != "$production_api_host" && \
     "$api_host" != "$production_frontend_host" && "$api_host" != "$production_api_host" ]] || \
    release_die "a staging URL matches an authoritative production hostname"
  [[ "${ROTRACK_STAGING_SUPABASE_PROJECT_REF:-}" =~ ^[a-z0-9]{20}$ && \
     "${ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF:-}" =~ ^[a-z0-9]{20}$ && \
     "${ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF:-}" =~ ^[a-z0-9]{20}$ ]] || \
    release_die "staging, development, and production Supabase refs must use the 20-character provider shape"
  [[ "$ROTRACK_STAGING_SUPABASE_PROJECT_REF" != "$ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF" && \
     "$ROTRACK_STAGING_SUPABASE_PROJECT_REF" != "$ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF" && \
     "$ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF" != "$ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF" ]] || \
    release_die "staging, development, and production Supabase refs must be distinct"
  release_validate_staging_inventory
}

release_validate_storage_state() {
  local name="$1"
  release_require_command stat
  local path="${!name:-}"
  release_require_variable "$name"
  [[ -f "$path" && ! -L "$path" ]] || release_die "${name} must name a regular, non-symlink file"
  local permissions
  permissions="$(stat -c '%a' "$path")" || release_die "could not inspect ${name} permissions"
  [[ "${permissions: -2}" == "00" ]] || release_die "${name} must not be accessible by group or other users"

  local canonical
  canonical="$(cd -- "$(dirname -- "$path")" && pwd -P)/$(basename -- "$path")"
  [[ "$canonical" != "${RELEASE_REPOSITORY_ROOT}"/* ]] || \
    release_die "${name} must remain outside the repository"
}

release_validate_external_executable() {
  local name="$1"
  local path="${!name:-}"
  release_require_variable "$name"
  release_require_command stat
  release_require_command id
  [[ -x "$path" && -f "$path" && ! -L "$path" ]] || \
    release_die "${name} must be an executable, non-symlink file"

  local canonical permissions owner group_digit other_digit
  canonical="$(cd -- "$(dirname -- "$path")" && pwd -P)/$(basename -- "$path")"
  [[ "$canonical" != "${RELEASE_REPOSITORY_ROOT}"/* ]] || \
    release_die "${name} must remain outside the candidate repository"
  permissions="$(stat -c '%a' "$canonical")" || release_die "could not inspect ${name} permissions"
  owner="$(stat -c '%u' "$canonical")" || release_die "could not inspect ${name} owner"
  group_digit="${permissions: -2:1}"
  other_digit="${permissions: -1}"
  [[ "$owner" == "$(id -u)" && ! "$group_digit" =~ [2367] && ! "$other_digit" =~ [2367] ]] || \
    release_die "${name} must be operator-owned and not group/other writable"
}

release_validate_identifier() {
  local name="$1"
  local value="${!name:-}"
  release_require_variable "$name"
  [[ "$value" =~ ^[A-Za-z0-9][A-Za-z0-9._:@/+~-]{0,255}$ ]] || \
    release_die "${name} must be a non-secret immutable identifier without whitespace"
}
