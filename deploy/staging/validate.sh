#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATES="$ROOT/templates"
SENTINEL_RE='__ROTRACK_STAGING_[A-Z0-9_]+__'

fail() {
  printf 'staging validation: FAIL: %s\n' "$*" >&2
  exit 1
}

pass() {
  printf 'staging validation: PASS: %s\n' "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command is unavailable: $1"
}

list_env_keys() {
  sed -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$1" \
    | sed -nE 's/^([A-Z][A-Z0-9_]*)=.*/\1/p' \
    | sort
}

assert_exact_lines() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  diff -u <(printf '%s\n' "$expected" | sort) <(printf '%s\n' "$actual" | sort) >/dev/null \
    || fail "$label names differ from the exact contract"
}

env_value() {
  local file="$1"
  local key="$2"
  local line
  line="$(grep -E "^${key}=" "$file" || true)"
  [[ -n "$line" ]] || fail "missing $key in $file"
  printf '%s' "${line#*=}"
}

assert_positive_integer() {
  local name="$1"
  local value="$2"
  [[ "$value" =~ ^[0-9]+$ ]] && (( value > 0 )) \
    || fail "$name must be a positive integer"
}

validate_https_origin() {
  local name="$1"
  local value="$2"
  python3 - "$value" <<'PY' || fail "$name must be one exact HTTPS origin with a concrete hostname and optional valid port"
import re
import sys
from urllib.parse import urlsplit

value = sys.argv[1]
try:
    parsed = urlsplit(value)
    port = parsed.port
except ValueError:
    raise SystemExit(1)
labels = (parsed.hostname or "").split(".")
valid_label = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
valid = (
    parsed.scheme == "https"
    and parsed.netloc != ""
    and parsed.username is None
    and parsed.password is None
    and parsed.path == ""
    and parsed.query == ""
    and parsed.fragment == ""
    and parsed.hostname is not None
    and "*" not in parsed.hostname
    and not parsed.hostname.endswith(".")
    and len(labels) >= 2
    and all(valid_label.fullmatch(label) for label in labels)
    and (port is None or 1 <= port <= 65535)
)
raise SystemExit(0 if valid else 1)
PY
}

assert_aws_identifiers() {
  local env_file="$1"
  local region="$2"
  local execution_role task_role account production_account secret_key secret_arn secret_account secret_region secret_name
  execution_role="$(env_value "$env_file" ROTRACK_STAGING_AWS_EXECUTION_ROLE_ARN)"
  task_role="$(env_value "$env_file" ROTRACK_STAGING_AWS_TASK_ROLE_ARN)"
  [[ "$execution_role" =~ ^arn:aws(-us-gov)?:iam::([0-9]{12}):role/rotrack-staging[-/A-Za-z0-9+=,.@_]*$ ]] \
    || fail "staging execution role ARN has an invalid or non-staging shape"
  account="${BASH_REMATCH[2]}"
  [[ "$task_role" =~ ^arn:aws(-us-gov)?:iam::([0-9]{12}):role/rotrack-staging[-/A-Za-z0-9+=,.@_]*$ ]] \
    || fail "staging task role ARN has an invalid or non-staging shape"
  [[ "${BASH_REMATCH[2]}" == "$account" ]] || fail "staging IAM roles use different AWS accounts"
  [[ "$task_role" != "$execution_role" ]] || fail "staging task and execution roles must be distinct"
  production_account="$(env_value "$env_file" ROTRACK_PRODUCTION_AWS_ACCOUNT_ID)"
  [[ "$production_account" =~ ^[0-9]{12}$ && "$production_account" != "$account" ]] \
    || fail "staging and production AWS account identities must be valid and distinct"

  while IFS='|' read -r secret_key secret_name; do
    secret_arn="$(env_value "$env_file" "$secret_key")"
    [[ "$secret_arn" =~ ^arn:aws(-us-gov)?:secretsmanager:([a-z0-9-]+):([0-9]{12}):secret:rotrack/staging/backend/${secret_name}-[A-Za-z0-9]{6,}$ ]] \
      || fail "$secret_key has an invalid or non-staging secret ARN"
    secret_region="${BASH_REMATCH[2]}"
    secret_account="${BASH_REMATCH[3]}"
    [[ "$secret_region" == "$region" && "$secret_account" == "$account" ]] \
      || fail "$secret_key differs from the staging AWS region/account"
  done <<'EOF'
ROTRACK_STAGING_DATABASE_URL_SECRET_ARN|DATABASE_URL
ROTRACK_STAGING_DATABASE_USERNAME_SECRET_ARN|DATABASE_USERNAME
ROTRACK_STAGING_DATABASE_PASSWORD_SECRET_ARN|DATABASE_PASSWORD
ROTRACK_STAGING_DATABASE_CA_CERTIFICATE_PEM_SECRET_ARN|DATABASE_CA_CERTIFICATE_PEM
ROTRACK_STAGING_SUPABASE_JWKS_URI_SECRET_ARN|SUPABASE_JWKS_URI
ROTRACK_STAGING_SUPABASE_ISSUER_URI_SECRET_ARN|SUPABASE_ISSUER_URI
EOF

  [[ "$(env_value "$env_file" ROTRACK_STAGING_ALB_TARGET_GROUP_ARN)" =~ ^arn:aws(-us-gov)?:elasticloadbalancing:${region}:${account}:targetgroup/rotrack-staging[-A-Za-z0-9]*/[a-f0-9]+$ ]] \
    || fail "staging target-group ARN differs from the staging region/account or naming contract"
  [[ "$(env_value "$env_file" ROTRACK_STAGING_ECS_SUBNET_IDS)" =~ ^subnet-[a-f0-9]+(,subnet-[a-f0-9]+)*$ ]] \
    || fail "staging subnet IDs have an invalid shape"
  [[ "$(env_value "$env_file" ROTRACK_STAGING_ECS_SECURITY_GROUP_IDS)" =~ ^sg-[a-f0-9]+(,sg-[a-f0-9]+)*$ ]] \
    || fail "staging security-group IDs have an invalid shape"
}

scan_for_committed_values() {
  local paths=("$ROOT" "$ROOT/../../docs/operations/staging")
  if grep -RIEq 'AKIA[0-9A-Z]{16}|eyJ[A-Za-z0-9_-]{10,}\.|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----' "${paths[@]}"; then
    fail "credential-shaped content found in staging files"
  fi
  if grep -RIEq 'arn:aws(-[a-z-]+)?:[^:]+:[^:]*:[0-9]{12}:' "${paths[@]}"; then
    fail "committed AWS account identifier found in staging files"
  fi
  if grep -RIEq 'https://[a-z0-9]{20}\.supabase\.co' "${paths[@]}"; then
    fail "committed Supabase project URL found in staging files"
  fi
}

validate_templates() {
  require_command jq
  require_command python3
  local frontend_expected backend_expected deployment_expected task_env_expected task_secret_expected
  frontend_expected=$'NEXT_PUBLIC_API_URL\nNEXT_PUBLIC_SUPABASE_KEY\nNEXT_PUBLIC_SUPABASE_URL'
  backend_expected=$'CORS_ALLOWED_ORIGINS\nDATABASE_CA_CERTIFICATE_PATH\nDATABASE_CA_CERTIFICATE_PEM\nDATABASE_CONNECTION_TIMEOUT_MS\nDATABASE_MAXIMUM_POOL_SIZE\nDATABASE_MINIMUM_IDLE\nDATABASE_PASSWORD\nDATABASE_POOL_VALIDATION_TIMEOUT_MS\nDATABASE_URL\nDATABASE_USERNAME\nLOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND\nLOGGING_LEVEL_ORG_HIBERNATE_SQL\nLOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY\nLOGGING_STRUCTURED_FORMAT_CONSOLE\nPORT\nREADINESS_CACHE_TTL\nROTRACK_LOGGING_ENVIRONMENT\nROTRACK_SERVICE_VERSION\nROTRACK_STRUCTURED_LOGGING_ENABLED\nSERVER_SHUTDOWN\nSPRING_JPA_SHOW_SQL\nSPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE\nSUPABASE_ISSUER_URI\nSUPABASE_JWKS_URI\nSUPABASE_JWT_AUDIENCE'
  deployment_expected=$'ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF\nROTRACK_PRODUCTION_AWS_ACCOUNT_ID\nROTRACK_PRODUCTION_SUPABASE_PROJECT_REF\nROTRACK_PRODUCTION_VERCEL_ORG_ID\nROTRACK_PRODUCTION_VERCEL_PROJECT_ID\nROTRACK_PRODUCTION_VERCEL_PROJECT_NAME\nROTRACK_PRODUCTION_VERCEL_TEAM_SLUG\nROTRACK_STAGING_ALB_TARGET_GROUP_ARN\nROTRACK_STAGING_API_ORIGIN\nROTRACK_STAGING_AWS_EXECUTION_ROLE_ARN\nROTRACK_STAGING_AWS_REGION\nROTRACK_STAGING_AWS_TASK_ROLE_ARN\nROTRACK_STAGING_BACKEND_IMAGE_URI\nROTRACK_STAGING_DATABASE_CA_CERTIFICATE_PEM_SECRET_ARN\nROTRACK_STAGING_SERVICE_VERSION\nROTRACK_STAGING_DATABASE_CONNECTION_LIMIT\nROTRACK_STAGING_DATABASE_MAXIMUM_POOL_SIZE\nROTRACK_STAGING_DATABASE_PASSWORD_SECRET_ARN\nROTRACK_STAGING_DATABASE_RESERVED_CONNECTIONS\nROTRACK_STAGING_DATABASE_URL_SECRET_ARN\nROTRACK_STAGING_DATABASE_USERNAME_SECRET_ARN\nROTRACK_STAGING_ECS_CLUSTER_NAME\nROTRACK_STAGING_ECS_DESIRED_TASK_COUNT\nROTRACK_STAGING_ECS_MAX_TASK_COUNT\nROTRACK_STAGING_ECS_SECURITY_GROUP_IDS\nROTRACK_STAGING_ECS_SERVICE_NAME\nROTRACK_STAGING_ECS_SUBNET_IDS\nROTRACK_STAGING_ECS_TASK_FAMILY\nROTRACK_STAGING_FRONTEND_ORIGIN\nROTRACK_STAGING_SUPABASE_ISSUER_URI_SECRET_ARN\nROTRACK_STAGING_SUPABASE_JWKS_URI_SECRET_ARN\nROTRACK_STAGING_SUPABASE_PROJECT_REF\nROTRACK_STAGING_VERCEL_ORG_ID\nROTRACK_STAGING_VERCEL_PROJECT_ID\nROTRACK_STAGING_VERCEL_PROJECT_NAME\nROTRACK_STAGING_VERCEL_TEAM_SLUG'
  task_env_expected=$'CORS_ALLOWED_ORIGINS\nDATABASE_CA_CERTIFICATE_PATH\nDATABASE_CONNECTION_TIMEOUT_MS\nDATABASE_MAXIMUM_POOL_SIZE\nDATABASE_MINIMUM_IDLE\nDATABASE_POOL_VALIDATION_TIMEOUT_MS\nLOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND\nLOGGING_LEVEL_ORG_HIBERNATE_SQL\nLOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY\nLOGGING_STRUCTURED_FORMAT_CONSOLE\nPORT\nREADINESS_CACHE_TTL\nROTRACK_LOGGING_ENVIRONMENT\nROTRACK_SERVICE_VERSION\nROTRACK_STRUCTURED_LOGGING_ENABLED\nSERVER_SHUTDOWN\nSPRING_JPA_SHOW_SQL\nSPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE\nSUPABASE_JWT_AUDIENCE'
  task_secret_expected=$'DATABASE_CA_CERTIFICATE_PEM\nDATABASE_PASSWORD\nDATABASE_URL\nDATABASE_USERNAME\nSUPABASE_ISSUER_URI\nSUPABASE_JWKS_URI'

  assert_exact_lines "$(list_env_keys "$TEMPLATES/frontend.env.template")" "$frontend_expected" "frontend environment"
  assert_exact_lines "$(list_env_keys "$TEMPLATES/backend.env.template")" "$backend_expected" "backend environment"
  assert_exact_lines "$(list_env_keys "$TEMPLATES/deployment.env.template")" "$deployment_expected" "deployment input"

  jq -e . "$TEMPLATES/ecs-task-definition.json.template" >/dev/null \
    || fail "ECS task definition template is not JSON"
  python3 - "$ROOT/../ecs/base/task-definition.json" "$TEMPLATES/ecs-task-definition.json.template" <<'PY' \
    || fail "staging ECS task drifted from the security-critical base contract"
import json
import sys

base = json.load(open(sys.argv[1], encoding="utf-8"))
staging = json.load(open(sys.argv[2], encoding="utf-8"))
for key in ("networkMode", "requiresCompatibilities", "runtimePlatform", "cpu", "memory", "volumes"):
    assert staging[key] == base[key], key
base_container = base["containerDefinitions"][0]
staging_container = staging["containerDefinitions"][0]
for key in (
    "name", "essential", "user", "readonlyRootFilesystem", "linuxParameters",
    "portMappings", "mountPoints", "healthCheck", "stopTimeout",
):
    assert staging_container[key] == base_container[key], key
assert {item["name"] for item in staging_container["environment"]} == {
    item["name"] for item in base_container["environment"]
}
assert {item["name"] for item in staging_container["secrets"]} == {
    item["name"] for item in base_container["secrets"]
}
base_logging = base_container["logConfiguration"]
staging_logging = staging_container["logConfiguration"]
assert staging_logging["logDriver"] == base_logging["logDriver"]
assert set(staging_logging["options"]) == set(base_logging["options"])
for key in ("awslogs-stream-prefix", "mode", "max-buffer-size"):
    assert staging_logging["options"][key] == base_logging["options"][key], key
PY
  assert_exact_lines \
    "$(jq -r '.containerDefinitions[0].environment[].name' "$TEMPLATES/ecs-task-definition.json.template")" \
    "$task_env_expected" "ECS plain environment"
  assert_exact_lines \
    "$(jq -r '.containerDefinitions[0].secrets[].name' "$TEMPLATES/ecs-task-definition.json.template")" \
    "$task_secret_expected" "ECS secret environment"

  grep -Eq "$SENTINEL_RE" "$TEMPLATES/frontend.env.template" \
    || fail "frontend template has no staging sentinels"
  grep -Eq "$SENTINEL_RE" "$TEMPLATES/backend.env.template" \
    || fail "backend template has no staging sentinels"
  grep -Eq "$SENTINEL_RE" "$TEMPLATES/deployment.env.template" \
    || fail "deployment template has no staging sentinels"
  grep -Eq "$SENTINEL_RE" "$TEMPLATES/ecs-task-definition.json.template" \
    || fail "ECS template has no staging sentinels"
  grep -q 'BYPASSRLS' "$TEMPLATES/runtime-role.sql.template" \
    || fail "runtime-role template does not state the required RLS bypass"
  grep -q 'NOINHERIT' "$TEMPLATES/runtime-role.sql.template" \
    || fail "runtime-role template must disable inherited privileges"
  grep -q 'membership_free' "$TEMPLATES/runtime-role-audit.sql.template" \
    || fail "runtime-role audit must prove the role has no memberships"
  grep -q 'no_other_public_relation_privileges' "$TEMPLATES/runtime-role-audit.sql.template" \
    || fail "runtime-role audit must reject unrelated public relation access"
  grep -q 'no_unapproved_public_routine_execute' "$TEMPLATES/runtime-role-audit.sql.template" \
    || fail "runtime-role audit must enforce the public routine allowlist"
  grep -q 'no_direct_create_or_temporary_grant' "$TEMPLATES/runtime-role-audit.sql.template" \
    || fail "runtime-role audit must reject direct database create/temp grants"
  grep -q 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.time_entries' "$TEMPLATES/runtime-role.sql.template" \
    || fail "runtime-role template lacks the exact application DML grant"
  grep -q 'REVOKE ALL PRIVILEGES ON TABLE public.user_preferences' "$TEMPLATES/runtime-role.sql.template" \
    || fail "runtime-role template must reset preference table grants idempotently"
  grep -q 'GRANT SELECT, INSERT, UPDATE ON TABLE public.user_preferences' "$TEMPLATES/runtime-role.sql.template" \
    || fail "runtime-role template lacks the exact preferences DML grant"
  grep -q 'preferences_can_select' "$TEMPLATES/runtime-role-audit.sql.template" \
    || fail "runtime-role audit must check preference SELECT access"
  grep -q 'preferences_cannot_delete' "$TEMPLATES/runtime-role-audit.sql.template" \
    || fail "runtime-role audit must reject preference DELETE access"
  ! grep -Eqi "PASSWORD[[:space:]]+'" "$TEMPLATES/runtime-role.sql.template" \
    || fail "runtime-role template must not contain a password literal"
  scan_for_committed_values
  pass "checked-in templates preserve sentinels, exact names, JSON shape, and no credential-shaped values"
}

validate_rendered() {
  require_command jq
  require_command python3
  local rendered="${1:-$ROOT/rendered}"
  local env_file="$rendered/deployment.env"
  local task_file="$rendered/ecs-task-definition.json"
  [[ -f "$env_file" ]] || fail "missing rendered deployment input: $env_file"
  [[ -f "$task_file" ]] || fail "missing rendered ECS task definition: $task_file"

  if grep -REq "$SENTINEL_RE" "$env_file" "$task_file"; then
    fail "unresolved staging sentinel in deployment input"
  fi
  jq -e . "$task_file" >/dev/null || fail "rendered ECS task definition is not JSON"
  assert_exact_lines \
    "$(list_env_keys "$env_file")" \
    "$(list_env_keys "$TEMPLATES/deployment.env.template")" \
    "rendered deployment input"
  assert_exact_lines \
    "$(jq -r '.containerDefinitions[0].environment[].name' "$task_file")" \
    "$(jq -r '.containerDefinitions[0].environment[].name' "$TEMPLATES/ecs-task-definition.json.template")" \
    "rendered ECS plain environment"
  assert_exact_lines \
    "$(jq -r '.containerDefinitions[0].secrets[].name' "$task_file")" \
    "$(jq -r '.containerDefinitions[0].secrets[].name' "$TEMPLATES/ecs-task-definition.json.template")" \
    "rendered ECS secret environment"

  local staging_ref development_ref production_ref frontend_origin api_origin image service_version image_digest aws_region staging_name
  local staging_vercel_team staging_vercel_project staging_vercel_org_id staging_vercel_project_id
  local production_vercel_team production_vercel_project production_vercel_org_id production_vercel_project_id
  local desired_tasks maximum_tasks rollout_peak_tasks pool_size connection_limit reserved_connections available_connections
  staging_ref="$(env_value "$env_file" ROTRACK_STAGING_SUPABASE_PROJECT_REF)"
  development_ref="$(env_value "$env_file" ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF)"
  production_ref="$(env_value "$env_file" ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF)"
  frontend_origin="$(env_value "$env_file" ROTRACK_STAGING_FRONTEND_ORIGIN)"
  api_origin="$(env_value "$env_file" ROTRACK_STAGING_API_ORIGIN)"
  image="$(env_value "$env_file" ROTRACK_STAGING_BACKEND_IMAGE_URI)"
  service_version="$(env_value "$env_file" ROTRACK_STAGING_SERVICE_VERSION)"
  aws_region="$(env_value "$env_file" ROTRACK_STAGING_AWS_REGION)"
  desired_tasks="$(env_value "$env_file" ROTRACK_STAGING_ECS_DESIRED_TASK_COUNT)"
  maximum_tasks="$(env_value "$env_file" ROTRACK_STAGING_ECS_MAX_TASK_COUNT)"
  pool_size="$(env_value "$env_file" ROTRACK_STAGING_DATABASE_MAXIMUM_POOL_SIZE)"
  connection_limit="$(env_value "$env_file" ROTRACK_STAGING_DATABASE_CONNECTION_LIMIT)"
  reserved_connections="$(env_value "$env_file" ROTRACK_STAGING_DATABASE_RESERVED_CONNECTIONS)"
  staging_vercel_team="$(env_value "$env_file" ROTRACK_STAGING_VERCEL_TEAM_SLUG)"
  staging_vercel_project="$(env_value "$env_file" ROTRACK_STAGING_VERCEL_PROJECT_NAME)"
  staging_vercel_org_id="$(env_value "$env_file" ROTRACK_STAGING_VERCEL_ORG_ID)"
  staging_vercel_project_id="$(env_value "$env_file" ROTRACK_STAGING_VERCEL_PROJECT_ID)"
  production_vercel_team="$(env_value "$env_file" ROTRACK_PRODUCTION_VERCEL_TEAM_SLUG)"
  production_vercel_project="$(env_value "$env_file" ROTRACK_PRODUCTION_VERCEL_PROJECT_NAME)"
  production_vercel_org_id="$(env_value "$env_file" ROTRACK_PRODUCTION_VERCEL_ORG_ID)"
  production_vercel_project_id="$(env_value "$env_file" ROTRACK_PRODUCTION_VERCEL_PROJECT_ID)"

  [[ "$staging_ref" =~ ^[a-z0-9]{20}$ ]] || fail "staging Supabase project ref has an invalid shape"
  [[ "$development_ref" =~ ^[a-z0-9]{20}$ ]] || fail "development Supabase project ref has an invalid shape"
  [[ "$production_ref" =~ ^[a-z0-9]{20}$ ]] || fail "production Supabase project ref has an invalid shape"
  [[ "$staging_ref" != "$development_ref" && "$staging_ref" != "$production_ref" && "$development_ref" != "$production_ref" ]] \
    || fail "staging, development, and production Supabase project refs must be distinct"
  validate_https_origin "frontend origin" "$frontend_origin"
  validate_https_origin "API origin" "$api_origin"
  [[ "$frontend_origin" != "$api_origin" ]] || fail "frontend and API origins must be separate"
  [[ "$staging_vercel_team" =~ ^[a-z0-9][a-z0-9-]{1,99}$ && "$production_vercel_team" =~ ^[a-z0-9][a-z0-9-]{1,99}$ ]] \
    || fail "Vercel team slugs have an invalid shape"
  [[ "$staging_vercel_project" =~ ^rotrack-staging-[a-z0-9][a-z0-9-]{1,80}$ ]] \
    || fail "staging Vercel project must use the rotrack-staging-* naming contract"
  [[ "$production_vercel_project" =~ ^rotrack-production-[a-z0-9][a-z0-9-]{1,80}$ ]] \
    || fail "production Vercel project must use the rotrack-production-* naming contract"
  [[ "$staging_vercel_org_id" =~ ^(team|user)_[A-Za-z0-9]+$ && "$production_vercel_org_id" =~ ^(team|user)_[A-Za-z0-9]+$ ]] \
    || fail "Vercel organization IDs have an invalid shape"
  [[ "$staging_vercel_project_id" =~ ^prj_[A-Za-z0-9]+$ && "$production_vercel_project_id" =~ ^prj_[A-Za-z0-9]+$ ]] \
    || fail "Vercel project IDs have an invalid shape"
  [[ "$staging_vercel_team" != "$production_vercel_team" && \
     "$staging_vercel_project" != "$production_vercel_project" && \
     "$staging_vercel_org_id" != "$production_vercel_org_id" && \
     "$staging_vercel_project_id" != "$production_vercel_project_id" ]] \
    || fail "staging and production Vercel identities must be distinct"
  [[ "$image" =~ @sha256:[a-f0-9]{64}$ ]] || fail "backend image must be immutable and selected by sha256 digest"
  image_digest="${image##*@sha256:}"
  [[ "$service_version" =~ ^[a-f0-9]{64}$ ]] || fail "service version must be the lowercase 64-hex image digest without the sha256 prefix"
  [[ "$service_version" == "$image_digest" ]] || fail "service version must match the immutable backend image digest"
  [[ "$aws_region" =~ ^[a-z]{2}(-gov)?-[a-z]+-[0-9]+$ ]] || fail "AWS region has an invalid shape"
  assert_aws_identifiers "$env_file" "$aws_region"

  for staging_name in ROTRACK_STAGING_ECS_CLUSTER_NAME ROTRACK_STAGING_ECS_SERVICE_NAME ROTRACK_STAGING_ECS_TASK_FAMILY; do
    [[ "$(env_value "$env_file" "$staging_name")" =~ ^rotrack-staging-[a-z0-9][a-z0-9-]{1,62}$ ]] \
      || fail "$staging_name must use the rotrack-staging-* naming contract"
  done

  assert_positive_integer ROTRACK_STAGING_ECS_DESIRED_TASK_COUNT "$desired_tasks"
  assert_positive_integer ROTRACK_STAGING_ECS_MAX_TASK_COUNT "$maximum_tasks"
  assert_positive_integer ROTRACK_STAGING_DATABASE_MAXIMUM_POOL_SIZE "$pool_size"
  assert_positive_integer ROTRACK_STAGING_DATABASE_CONNECTION_LIMIT "$connection_limit"
  [[ "$reserved_connections" =~ ^[0-9]+$ ]] || fail "database reserved connections must be a non-negative integer"
  (( desired_tasks <= maximum_tasks )) || fail "desired ECS task count exceeds maximum task count"
  (( reserved_connections < connection_limit )) || fail "reserved database connections consume the full limit"
  available_connections=$((connection_limit - reserved_connections))
  # ECS maximumPercent=200 permits two generations to overlap at the autoscaling maximum.
  rollout_peak_tasks=$((maximum_tasks * 2))
  (( pool_size * rollout_peak_tasks <= available_connections )) \
    || fail "DATABASE_MAXIMUM_POOL_SIZE × rolling-deployment peak tasks exceeds the staging database budget"

  [[ "$(jq -r '.family' "$task_file")" == "$(env_value "$env_file" ROTRACK_STAGING_ECS_TASK_FAMILY)" ]] \
    || fail "task family differs from deployment input"
  [[ "$(jq -r '.containerDefinitions[0].image' "$task_file")" == "$image" ]] \
    || fail "task image differs from the validated digest input"
  [[ "$(jq -r '.executionRoleArn' "$task_file")" == "$(env_value "$env_file" ROTRACK_STAGING_AWS_EXECUTION_ROLE_ARN)" ]] \
    || fail "task execution role differs from deployment input"
  [[ "$(jq -r '.taskRoleArn' "$task_file")" == "$(env_value "$env_file" ROTRACK_STAGING_AWS_TASK_ROLE_ARN)" ]] \
    || fail "task role differs from deployment input"
  [[ "$(jq -r '.containerDefinitions[0].logConfiguration.options["awslogs-region"]' "$task_file")" == "$aws_region" ]] \
    || fail "task log region differs from deployment input"
  [[ "$(jq -r '.networkMode' "$task_file")" == 'awsvpc' ]] || fail "ECS task must use awsvpc"
  jq -e '.requiresCompatibilities == ["FARGATE"]' "$task_file" >/dev/null \
    || fail "ECS task must be Fargate-only"
  jq -e '.containerDefinitions[0].readonlyRootFilesystem == true' "$task_file" >/dev/null \
    || fail "container root filesystem must be read-only"
  jq -e '.containerDefinitions[0].user == "10001:10001"' "$task_file" >/dev/null \
    || fail "container must run as the fixed non-root UID/GID"
  jq -e '.volumes == [{"name":"tmp"}] and .containerDefinitions[0].mountPoints == [{"sourceVolume":"tmp","containerPath":"/tmp","readOnly":false}]' "$task_file" >/dev/null \
    || fail "read-only task must provide only the declared writable /tmp volume"
  jq -e '.containerDefinitions[0].portMappings[0].containerPort == 8080' "$task_file" >/dev/null \
    || fail "container port must be 8080"
  jq -e '.containerDefinitions[0].healthCheck.command | join(" ") | contains("/api/v1/health")' "$task_file" >/dev/null \
    || fail "container liveness must use /api/v1/health"
  [[ "$(jq -r '.containerDefinitions[0].environment[] | select(.name == "CORS_ALLOWED_ORIGINS") | .value' "$task_file")" == "$frontend_origin" ]] \
    || fail "task CORS origin differs from the staging frontend origin"
  [[ "$(jq -r '.containerDefinitions[0].environment[] | select(.name == "DATABASE_MAXIMUM_POOL_SIZE") | .value' "$task_file")" == "$pool_size" ]] \
    || fail "task database pool differs from the validated pool budget"
  [[ "$(jq -r '.containerDefinitions[0].environment[] | select(.name == "ROTRACK_STRUCTURED_LOGGING_ENABLED") | .value' "$task_file")" == "true" ]] \
    || fail "staging must explicitly enable structured request logging"
  [[ "$(jq -r '.containerDefinitions[0].environment[] | select(.name == "ROTRACK_LOGGING_ENVIRONMENT") | .value' "$task_file")" == "staging" ]] \
    || fail "staging structured logging environment must be staging"
  [[ "$(jq -r '.containerDefinitions[0].environment[] | select(.name == "ROTRACK_SERVICE_VERSION") | .value' "$task_file")" == "$service_version" ]] \
    || fail "task service version differs from the validated immutable release input"

  local name key task_value expected_value
  while IFS='|' read -r name key; do
    task_value="$(jq -r --arg name "$name" '.containerDefinitions[0].secrets[] | select(.name == $name) | .valueFrom' "$task_file")"
    expected_value="$(env_value "$env_file" "$key")"
    [[ -n "$task_value" && "$task_value" == "$expected_value" ]] \
      || fail "$name secret reference differs from deployment input"
  done <<'EOF'
DATABASE_URL|ROTRACK_STAGING_DATABASE_URL_SECRET_ARN
DATABASE_USERNAME|ROTRACK_STAGING_DATABASE_USERNAME_SECRET_ARN
DATABASE_PASSWORD|ROTRACK_STAGING_DATABASE_PASSWORD_SECRET_ARN
DATABASE_CA_CERTIFICATE_PEM|ROTRACK_STAGING_DATABASE_CA_CERTIFICATE_PEM_SECRET_ARN
SUPABASE_JWKS_URI|ROTRACK_STAGING_SUPABASE_JWKS_URI_SECRET_ARN
SUPABASE_ISSUER_URI|ROTRACK_STAGING_SUPABASE_ISSUER_URI_SECRET_ARN
EOF

  pass "rendered deployment has distinct project identities, digest pinning, restricted CORS, and a bounded connection budget"
}

case "${1:-templates}" in
  templates)
    validate_templates
    ;;
  deployment)
    validate_rendered "${2:-$ROOT/rendered}"
    ;;
  *)
    fail "usage: $0 [templates | deployment [rendered-directory]]"
    ;;
esac
