#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

cat > "$tmp_dir/safe-paths" <<'PATHS'
frontend/.env.example
frontend/package-lock.json
backend/src/test/README.md
PATHS
ROTRACK_CI_PATH_MANIFEST="$tmp_dir/safe-paths" "$script_dir/guard-sensitive-paths.sh"

cat > "$tmp_dir/forbidden-paths" <<'PATHS'
frontend/.env
frontend/playwright/.auth/user.json
frontend/e2e/storageState.json
backend/target/rotrack-api.jar
local/provider-ca.crt
PATHS
set +e
ROTRACK_CI_PATH_MANIFEST="$tmp_dir/forbidden-paths" "$script_dir/guard-sensitive-paths.sh" \
  >"$tmp_dir/negative.stdout" 2>"$tmp_dir/negative.stderr"
negative_status=$?
set -e
if (( negative_status == 0 )); then
  printf 'Expected the sensitive-path negative fixture to fail, but it passed.\n' >&2
  exit 1
fi
if ! grep -q 'forbidden sensitive or generated path' "$tmp_dir/negative.stderr"; then
  printf 'Sensitive-path negative fixture failed without the expected diagnostic.\n' >&2
  exit 1
fi

workflow_fixture="$tmp_dir/workflows"
mkdir -p "$workflow_fixture"

expect_workflow_failure() {
  local name=$1
  local expected=$2
  shift 2
  rm -f "$workflow_fixture"/*
  printf '%s\n' "$@" >"$workflow_fixture/fixture.yml"
  set +e
  ROTRACK_WORKFLOW_DIR="$workflow_fixture" "$script_dir/check-workflow-policy.sh" \
    >"$tmp_dir/${name}.stdout" 2>"$tmp_dir/${name}.stderr"
  local status=$?
  set -e
  if (( status == 0 )) || ! grep -q "$expected" "$tmp_dir/${name}.stderr"; then
    printf 'Expected workflow-policy fixture %s to fail with %s.\n' "$name" "$expected" >&2
    exit 1
  fi
}

expect_workflow_failure mutable-reusable 'not pinned to a full commit SHA' \
  'name: fixture' 'on: workflow_call' 'jobs:' '  delegated:' \
  '    uses: owner/repository/.github/workflows/ci.yml@main'
expect_workflow_failure privileged-trigger 'pull_request_target is forbidden' \
  'name: fixture' 'on:' '  pull_request_target:' 'jobs:' '  test:' \
  '    runs-on: ubuntu-latest' '    steps:' '      - run: true'
expect_workflow_failure artifact-upload 'artifact upload is forbidden' \
  'name: fixture' 'on: push' 'jobs:' '  test:' '    runs-on: ubuntu-latest' \
  '    steps:' '      - uses: actions/upload-artifact@aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'

set +e
ROTRACK_TEST_DATABASE_ISOLATED=true \
PGHOST=localhost \
PGHOSTADDR=203.0.113.10 \
  "$script_dir/apply-migrations-to-local-postgres.sh" \
  >"$tmp_dir/hostaddr.stdout" 2>"$tmp_dir/hostaddr.stderr"
hostaddr_status=$?
set -e
if (( hostaddr_status == 0 )) || ! grep -q 'PGHOSTADDR can override' "$tmp_dir/hostaddr.stderr"; then
  printf 'Expected PGHOSTADDR migration-target override to fail closed.\n' >&2
  exit 1
fi

printf 'Guard tests passed; deliberate sensitive-path and workflow-policy fixtures returned expected nonzero statuses.\n'
