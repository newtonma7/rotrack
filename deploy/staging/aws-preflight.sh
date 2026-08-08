#!/usr/bin/env bash
set -euo pipefail
set +x

fail() {
  printf 'staging AWS preflight: FAIL: %s\n' "$1" >&2
  exit 1
}

for command in aws jq; do
  command -v "$command" >/dev/null 2>&1 || fail "required command is unavailable: $command"
done

required=(
  ROTRACK_STAGING_AWS_REGION
  ROTRACK_PRODUCTION_AWS_ACCOUNT_ID
  ROTRACK_STAGING_AWS_EXECUTION_ROLE_ARN
  ROTRACK_STAGING_AWS_TASK_ROLE_ARN
  ROTRACK_STAGING_ECS_CLUSTER_NAME
  ROTRACK_STAGING_ECS_SERVICE_NAME
  ROTRACK_STAGING_DATABASE_URL_SECRET_ARN
  ROTRACK_STAGING_DATABASE_USERNAME_SECRET_ARN
  ROTRACK_STAGING_DATABASE_PASSWORD_SECRET_ARN
  ROTRACK_STAGING_DATABASE_CA_CERTIFICATE_PEM_SECRET_ARN
  ROTRACK_STAGING_SUPABASE_JWKS_URI_SECRET_ARN
  ROTRACK_STAGING_SUPABASE_ISSUER_URI_SECRET_ARN
)
for name in "${required[@]}"; do
  [[ -n "${!name:-}" ]] || fail "required environment name is unset: $name"
done

execution_account="$(cut -d: -f5 <<<"$ROTRACK_STAGING_AWS_EXECUTION_ROLE_ARN")"
task_account="$(cut -d: -f5 <<<"$ROTRACK_STAGING_AWS_TASK_ROLE_ARN")"
caller_account="$(aws sts get-caller-identity --query Account --output text 2>/dev/null)" \
  || fail "could not read the AWS caller identity"
[[ "$caller_account" =~ ^[0-9]{12}$ && "$caller_account" == "$execution_account" && "$caller_account" == "$task_account" ]] \
  || fail "caller and staging role accounts differ"
[[ "$caller_account" != "$ROTRACK_PRODUCTION_AWS_ACCOUNT_ID" ]] \
  || fail "staging caller account matches the protected production account"
[[ "$ROTRACK_STAGING_AWS_EXECUTION_ROLE_ARN" != "$ROTRACK_STAGING_AWS_TASK_ROLE_ARN" ]] \
  || fail "task and execution roles must be distinct"

task_role_name="${ROTRACK_STAGING_AWS_TASK_ROLE_ARN##*/}"
attached_count="$(aws iam list-attached-role-policies \
  --role-name "$task_role_name" --query 'length(AttachedPolicies)' --output text 2>/dev/null)" \
  || fail "could not inspect task-role attached policies"
inline_count="$(aws iam list-role-policies \
  --role-name "$task_role_name" --query 'length(PolicyNames)' --output text 2>/dev/null)" \
  || fail "could not inspect task-role inline policies"
[[ "$attached_count" == 0 && "$inline_count" == 0 ]] \
  || fail "application task role must not have AWS API policies"

secret_arns=(
  "$ROTRACK_STAGING_DATABASE_URL_SECRET_ARN"
  "$ROTRACK_STAGING_DATABASE_USERNAME_SECRET_ARN"
  "$ROTRACK_STAGING_DATABASE_PASSWORD_SECRET_ARN"
  "$ROTRACK_STAGING_DATABASE_CA_CERTIFICATE_PEM_SECRET_ARN"
  "$ROTRACK_STAGING_SUPABASE_JWKS_URI_SECRET_ARN"
  "$ROTRACK_STAGING_SUPABASE_ISSUER_URI_SECRET_ARN"
)
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
aws iam simulate-principal-policy \
  --policy-source-arn "$ROTRACK_STAGING_AWS_EXECUTION_ROLE_ARN" \
  --action-names secretsmanager:GetSecretValue \
  --resource-arns "${secret_arns[@]}" \
  --output json >"$tmp/secrets-simulation.json" 2>/dev/null \
  || fail "could not simulate execution-role secret access"
jq -e --argjson expected "${#secret_arns[@]}" \
  '.EvaluationResults | length == $expected and all(.[]; .EvalDecision == "allowed")' \
  "$tmp/secrets-simulation.json" >/dev/null \
  || fail "execution role cannot read exactly the six required staging secret inputs"

aws ecs describe-services \
  --region "$ROTRACK_STAGING_AWS_REGION" \
  --cluster "$ROTRACK_STAGING_ECS_CLUSTER_NAME" \
  --services "$ROTRACK_STAGING_ECS_SERVICE_NAME" \
  --include TAGS \
  --output json >"$tmp/service.json" 2>/dev/null \
  || fail "could not inspect the staging ECS service"
jq -e \
  '.failures | length == 0' "$tmp/service.json" >/dev/null \
  || fail "ECS service lookup returned failures"
jq -e \
  '.services | length == 1 and any(.[0].tags[]?; .key == "Environment" and .value == "staging")' \
  "$tmp/service.json" >/dev/null \
  || fail "ECS service lacks the required Environment=staging tag"

printf 'staging AWS preflight: PASS: caller, task-role isolation, required secret access, and service tag verified\n'
