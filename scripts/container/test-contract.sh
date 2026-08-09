#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
DOCKERFILE="$ROOT/backend/Dockerfile"
TASK_DEFINITION="$ROOT/deploy/ecs/base/task-definition.json"
SERVICE="$ROOT/deploy/ecs/base/service.json"
TARGET_GROUP="$ROOT/deploy/ecs/base/target-group.json"

fail() {
  printf 'container contract: %s\n' "$1" >&2
  exit 1
}

require_literal() {
  grep -Fq -- "$2" "$1" || fail "$1 is missing: $2"
}

[ -f "$DOCKERFILE" ] || fail "backend/Dockerfile is missing"
[ -f "$ROOT/.dockerignore" ] || fail ".dockerignore is missing"

require_literal "$DOCKERFILE" 'AS build'
require_literal "$DOCKERFILE" 'USER 10001:10001'
require_literal "$DOCKERFILE" 'EXPOSE 8080'
require_literal "$DOCKERFILE" 'STOPSIGNAL SIGTERM'
require_literal "$DOCKERFILE" 'SERVER_SHUTDOWN=graceful'
require_literal "$DOCKERFILE" 'SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=25s'
require_literal "$DOCKERFILE" '/api/v1/health'
require_literal "$DOCKERFILE" 'org.opencontainers.image.revision'
require_literal "$DOCKERFILE" 'org.opencontainers.image.created'
require_literal "$DOCKERFILE" 'ENTRYPOINT ["/opt/rotrack/bin/container-entrypoint.sh"]'
require_literal "$ROOT/scripts/container/build-image.sh" "PLATFORM_ARGS='--platform linux/amd64'"
require_literal "$ROOT/scripts/container/build-image.sh" 'REQUIRE_CLEAN=${REQUIRE_CLEAN:-1}'

require_literal "$ROOT/.dockerignore" '**'
for included in '!backend/Dockerfile' '!backend/pom.xml' '!backend/src/**' '!scripts/container/container-entrypoint.sh'; do
  require_literal "$ROOT/.dockerignore" "$included"
done

python3 - "$TASK_DEFINITION" "$SERVICE" "$TARGET_GROUP" <<'PY'
import json
import pathlib
import sys

task_path, service_path, target_path = map(pathlib.Path, sys.argv[1:])
for path in (task_path, service_path, target_path):
    with path.open(encoding="utf-8") as handle:
        json.load(handle)

task = json.loads(task_path.read_text(encoding="utf-8"))
assert task["requiresCompatibilities"] == ["FARGATE"]
assert task["networkMode"] == "awsvpc"
container = task["containerDefinitions"][0]
assert container["user"] == "10001:10001"
assert container["portMappings"] == [{"name": "http", "containerPort": 8080, "hostPort": 8080, "protocol": "tcp", "appProtocol": "http"}]
assert container["readonlyRootFilesystem"] is True
assert container["stopTimeout"] == 30
assert "/api/v1/health" in " ".join(container["healthCheck"]["command"])
assert container["mountPoints"] == [{"sourceVolume": "tmp", "containerPath": "/tmp", "readOnly": False}]
assert task["volumes"] == [{"name": "tmp"}]

environment = {entry["name"]: entry["value"] for entry in container["environment"]}
expected_environment = {
    "PORT": "8080",
    "DATABASE_CONNECTION_TIMEOUT_MS": "5000",
    "DATABASE_POOL_VALIDATION_TIMEOUT_MS": "2000",
    "DATABASE_MAXIMUM_POOL_SIZE": "5",
    "DATABASE_MINIMUM_IDLE": "0",
    "READINESS_CACHE_TTL": "5s",
    "SUPABASE_JWT_AUDIENCE": "authenticated",
    "SERVER_SHUTDOWN": "graceful",
    "SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE": "25s",
    "LOGGING_STRUCTURED_FORMAT_CONSOLE": "ecs",
    "ROTRACK_STRUCTURED_LOGGING_ENABLED": "${STRUCTURED_LOGGING_ENABLED}",
    "ROTRACK_LOGGING_ENVIRONMENT": "${LOGGING_ENVIRONMENT}",
    "ROTRACK_SERVICE_VERSION": "${SERVICE_VERSION}",
    "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY": "WARN",
    "LOGGING_LEVEL_ORG_HIBERNATE_SQL": "OFF",
    "LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND": "OFF",
    "SPRING_JPA_SHOW_SQL": "false",
}
for name, value in expected_environment.items():
    assert environment.get(name) == value, (name, environment.get(name))
assert environment["CORS_ALLOWED_ORIGINS"] == "${CORS_ALLOWED_ORIGINS}"

secret_names = {entry["name"] for entry in container["secrets"]}
assert secret_names == {
    "DATABASE_URL",
    "DATABASE_USERNAME",
    "DATABASE_PASSWORD",
    "DATABASE_CA_CERTIFICATE_PEM",
    "SUPABASE_JWKS_URI",
    "SUPABASE_ISSUER_URI",
}
assert container["image"] == "${IMAGE_REPOSITORY}@${IMAGE_DIGEST}"

service = json.loads(service_path.read_text(encoding="utf-8"))
assert service["launchType"] == "FARGATE"
assert service["platformVersion"] == "1.4.0"
assert service["enableExecuteCommand"] is False
assert service["deploymentConfiguration"]["deploymentCircuitBreaker"] == {"enable": True, "rollback": True}
assert service["loadBalancers"][0]["containerPort"] == 8080

target = json.loads(target_path.read_text(encoding="utf-8"))
assert target["TargetType"] == "ip"
assert target["HealthCheckPath"] == "/api/v1/readiness"
assert target["Matcher"] == {"HttpCode": "200"}
assert target["Protocol"] == "HTTP"
assert target["Port"] == 8080
PY

# Exercise CA materialization with a fake Java command. Secret material must not reach output.
ENTRYPOINT_TMP=$(mktemp -d /tmp/rotrack-certs.contract.XXXXXX)
CA_PATH="/tmp/rotrack-certs/contract-$$.crt"
cleanup() {
  rm -rf "$ENTRYPOINT_TMP" "$CA_PATH"
}
trap cleanup EXIT HUP INT TERM
cat > "$ENTRYPOINT_TMP/java" <<'SH'
#!/bin/sh
printf '%s\n' 'fake-java-executed'
SH
chmod 700 "$ENTRYPOINT_TMP/java"
SECRET_MARKER='container-contract-ca-marker'
ENTRYPOINT_OUTPUT=$(PATH="$ENTRYPOINT_TMP:$PATH" \
  DATABASE_URL="jdbc:postgresql://db.invalid/postgres?sslmode=verify-full&sslrootcert=$CA_PATH" \
  DATABASE_CA_CERTIFICATE_PATH="$CA_PATH" \
  DATABASE_CA_CERTIFICATE_PEM="-----BEGIN CERTIFICATE-----
$SECRET_MARKER
-----END CERTIFICATE-----" \
  "$ROOT/scripts/container/container-entrypoint.sh")
[ "$ENTRYPOINT_OUTPUT" = 'fake-java-executed' ] || fail "entrypoint did not exec Java cleanly"
[ "$(stat -c '%a' "$CA_PATH")" = 600 ] || fail "materialized CA is not mode 0600"
printf '%s' "$ENTRYPOINT_OUTPUT" | grep -Fq "$SECRET_MARKER" && fail "entrypoint printed CA material"

expect_entrypoint_failure() {
  BAD_URL=$1
  if PATH="$ENTRYPOINT_TMP:$PATH" \
    DATABASE_URL="$BAD_URL" \
    DATABASE_CA_CERTIFICATE_PATH="$CA_PATH" \
    DATABASE_CA_CERTIFICATE_PEM="-----BEGIN CERTIFICATE-----
$SECRET_MARKER
-----END CERTIFICATE-----" \
    "$ROOT/scripts/container/container-entrypoint.sh" >/dev/null 2>&1; then
    fail "entrypoint accepted an invalid TLS parameter contract"
  fi
}
expect_entrypoint_failure "jdbc:postgresql://db.invalid/postgres?sslmode=require&note=sslrootcert=$CA_PATH"
expect_entrypoint_failure "jdbc:postgresql://db.invalid/postgres?sslmode=verify-full&sslmode=require&sslrootcert=$CA_PATH"
expect_entrypoint_failure "jdbc:postgresql://db.invalid/postgres?sslmode=verify-full&sslrootcert=/tmp/rotrack-certs/other.crt"
expect_entrypoint_failure "jdbc:postgresql://db.invalid/postgres?sslmode=verify-full&sslrootcert=$CA_PATH&sslfactory=org.postgresql.ssl.NonValidatingFactory"
expect_entrypoint_failure "jdbc:postgresql://db.invalid/postgres?sslmode=verify-full&sslrootcert=$CA_PATH&sslhostnameverifier=example.InsecureVerifier"
if PATH="$ENTRYPOINT_TMP:$PATH" \
  DATABASE_URL='jdbc:postgresql://db.invalid/postgres?sslmode=verify-full&sslrootcert=/tmp/rotrack-certs/../outside.crt' \
  DATABASE_CA_CERTIFICATE_PATH='/tmp/rotrack-certs/../outside.crt' \
  DATABASE_CA_CERTIFICATE_PEM="-----BEGIN CERTIFICATE-----
$SECRET_MARKER
-----END CERTIFICATE-----" \
  "$ROOT/scripts/container/container-entrypoint.sh" >/dev/null 2>&1; then
  fail "entrypoint accepted a CA path containing a parent component"
fi

cleanup
trap - EXIT HUP INT TERM

# Deployment templates may contain variable names and placeholder ARNs, but never concrete AWS account IDs.
if grep -ERq 'arn:aws(-[a-z-]+)?:[^:]+:[^:]*:[0-9]{12}:' "$ROOT/deploy/ecs/base"; then
  fail "ECS base contains a concrete AWS account ID"
fi

printf 'container contract: static checks passed\n'
