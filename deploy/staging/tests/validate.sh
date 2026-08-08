#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

"$ROOT/validate.sh" templates
bash -n "$ROOT/aws-preflight.sh"
grep -q 'simulate-principal-policy' "$ROOT/aws-preflight.sh"
grep -q 'Environment.*staging' "$ROOT/aws-preflight.sh"

mkdir -p "$TMP/unresolved"
cp "$ROOT/templates/deployment.env.template" "$TMP/unresolved/deployment.env"
cp "$ROOT/templates/ecs-task-definition.json.template" "$TMP/unresolved/ecs-task-definition.json"
if "$ROOT/validate.sh" deployment "$TMP/unresolved" >"$TMP/expected-failure.log" 2>&1; then
  printf 'expected unresolved-sentinel validation to fail\n' >&2
  exit 1
fi
grep -q 'unresolved staging sentinel' "$TMP/expected-failure.log"
printf 'staging validation test: PASS: unresolved deployment sentinel was rejected\n'

FIXTURE_ACCOUNT="$(printf '1%.0s' {1..12})"
PRODUCTION_ACCOUNT="$(printf '2%.0s' {1..12})"
export ROTRACK_STAGING_AWS_REGION='us-east-1'
export ROTRACK_PRODUCTION_AWS_ACCOUNT_ID="$PRODUCTION_ACCOUNT"
export ROTRACK_STAGING_AWS_EXECUTION_ROLE_ARN="arn:aws:iam::${FIXTURE_ACCOUNT}:role/rotrack-staging-execution-fixture"
export ROTRACK_STAGING_AWS_TASK_ROLE_ARN="arn:aws:iam::${FIXTURE_ACCOUNT}:role/rotrack-staging-task-fixture"
export ROTRACK_STAGING_BACKEND_IMAGE_URI='registry.example.invalid/rotrack/backend@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
export ROTRACK_STAGING_DATABASE_URL_SECRET_ARN="arn:aws:secretsmanager:us-east-1:${FIXTURE_ACCOUNT}:secret:rotrack/staging/backend/DATABASE_URL-abcdef"
export ROTRACK_STAGING_DATABASE_USERNAME_SECRET_ARN="arn:aws:secretsmanager:us-east-1:${FIXTURE_ACCOUNT}:secret:rotrack/staging/backend/DATABASE_USERNAME-abcdef"
export ROTRACK_STAGING_DATABASE_PASSWORD_SECRET_ARN="arn:aws:secretsmanager:us-east-1:${FIXTURE_ACCOUNT}:secret:rotrack/staging/backend/DATABASE_PASSWORD-abcdef"
export ROTRACK_STAGING_DATABASE_CA_CERTIFICATE_PEM_SECRET_ARN="arn:aws:secretsmanager:us-east-1:${FIXTURE_ACCOUNT}:secret:rotrack/staging/backend/DATABASE_CA_CERTIFICATE_PEM-abcdef"
export ROTRACK_STAGING_SUPABASE_JWKS_URI_SECRET_ARN="arn:aws:secretsmanager:us-east-1:${FIXTURE_ACCOUNT}:secret:rotrack/staging/backend/SUPABASE_JWKS_URI-abcdef"
export ROTRACK_STAGING_SUPABASE_ISSUER_URI_SECRET_ARN="arn:aws:secretsmanager:us-east-1:${FIXTURE_ACCOUNT}:secret:rotrack/staging/backend/SUPABASE_ISSUER_URI-abcdef"
export ROTRACK_STAGING_FRONTEND_ORIGIN='https://frontend.staging.example.invalid'
export ROTRACK_STAGING_API_ORIGIN='https://api.staging.example.invalid'
export ROTRACK_STAGING_SUPABASE_PROJECT_REF='stagingaaaaaaaaaaaaa'
export ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF='developmentaaaaaaaaa'
export ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF='productionaaaaaaaaaa'
export ROTRACK_STAGING_ECS_CLUSTER_NAME='rotrack-staging-fixture'
export ROTRACK_STAGING_ECS_SERVICE_NAME='rotrack-staging-api-fixture'
export ROTRACK_STAGING_ECS_TASK_FAMILY='rotrack-staging-api-fixture'
export ROTRACK_STAGING_ECS_DESIRED_TASK_COUNT='1'
export ROTRACK_STAGING_ECS_MAX_TASK_COUNT='2'
export ROTRACK_STAGING_DATABASE_MAXIMUM_POOL_SIZE='5'
export ROTRACK_STAGING_DATABASE_CONNECTION_LIMIT='30'
export ROTRACK_STAGING_DATABASE_RESERVED_CONNECTIONS='10'
export ROTRACK_STAGING_ECS_SUBNET_IDS='subnet-a1b2c3d4,subnet-b2c3d4e5'
export ROTRACK_STAGING_ECS_SECURITY_GROUP_IDS='sg-a1b2c3d4'
export ROTRACK_STAGING_ALB_TARGET_GROUP_ARN="arn:aws:elasticloadbalancing:us-east-1:${FIXTURE_ACCOUNT}:targetgroup/rotrack-staging-fixture/abcdef1234567890"
export ROTRACK_STAGING_VERCEL_TEAM_SLUG='rotrack-staging-fixture-team'
export ROTRACK_STAGING_VERCEL_PROJECT_NAME='rotrack-staging-fixture'
export ROTRACK_STAGING_VERCEL_ORG_ID='team_stagingfixture'
export ROTRACK_STAGING_VERCEL_PROJECT_ID='prj_stagingfixture'
export ROTRACK_PRODUCTION_VERCEL_TEAM_SLUG='rotrack-production-fixture-team'
export ROTRACK_PRODUCTION_VERCEL_PROJECT_NAME='rotrack-production-fixture'
export ROTRACK_PRODUCTION_VERCEL_ORG_ID='team_productionfixture'
export ROTRACK_PRODUCTION_VERCEL_PROJECT_ID='prj_productionfixture'

"$ROOT/render.sh" "$TMP/rendered"
printf 'staging validation test: PASS: safe synthetic rendered configuration was accepted\n'

expect_render_failure() {
  local name="$1"
  local variable="$2"
  local value="$3"
  if (export "$variable=$value"; "$ROOT/render.sh" "$TMP/$name") >"$TMP/$name.log" 2>&1; then
    printf 'expected rendered staging case %s to fail\n' "$name" >&2
    exit 1
  fi
  printf 'staging validation test: PASS: %s was rejected\n' "$name"
}

expect_render_failure wildcard-origin ROTRACK_STAGING_FRONTEND_ORIGIN 'https://*.staging.example.invalid'
expect_render_failure origin-with-query ROTRACK_STAGING_API_ORIGIN 'https://api.staging.example.invalid?target=other'
OTHER_ACCOUNT="$(printf '9%.0s' {1..12})"
expect_render_failure malformed-secret-arn ROTRACK_STAGING_DATABASE_URL_SECRET_ARN "arn:aws:secretsmanager:us-east-1:${OTHER_ACCOUNT}:secret:other/DATABASE_URL-abcdef"
expect_render_failure production-aws-account ROTRACK_PRODUCTION_AWS_ACCOUNT_ID "$FIXTURE_ACCOUNT"
expect_render_failure identical-iam-roles ROTRACK_STAGING_AWS_TASK_ROLE_ARN "$ROTRACK_STAGING_AWS_EXECUTION_ROLE_ARN"
expect_render_failure non-staging-service-name ROTRACK_STAGING_ECS_SERVICE_NAME 'rotrack-production-api'
expect_render_failure production-vercel-project ROTRACK_PRODUCTION_VERCEL_PROJECT_ID "$ROTRACK_STAGING_VERCEL_PROJECT_ID"
expect_render_failure non-staging-vercel-name ROTRACK_STAGING_VERCEL_PROJECT_NAME 'rotrack-production-fixture'
expect_render_failure mutable-image ROTRACK_STAGING_BACKEND_IMAGE_URI 'registry.example.invalid/rotrack/backend:latest'
expect_render_failure duplicate-project ROTRACK_STAGING_SUPABASE_PROJECT_REF "$ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF"
expect_render_failure connection-exhaustion ROTRACK_STAGING_DATABASE_CONNECTION_LIMIT '29'
expect_render_failure desired-above-maximum ROTRACK_STAGING_ECS_DESIRED_TASK_COUNT '3'
