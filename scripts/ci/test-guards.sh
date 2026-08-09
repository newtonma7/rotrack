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
  'name: fixture' 'on:' '  workflow_call:' 'jobs:' '  delegated:' \
  '    uses: owner/repository/.github/workflows/ci.yml@main'
expect_workflow_failure missing-permissions 'explicit top-level permissions' \
  'name: fixture' 'on:' '  push:' 'jobs:' '  test:' '    runs-on: ubuntu-latest' \
  '    steps:' '      - run: true'
expect_workflow_failure privileged-trigger 'pull_request_target is forbidden' \
  'name: fixture' 'on:' '  pull_request_target:' 'permissions:' '  contents: read' 'jobs:' \
  '  test:' '    runs-on: ubuntu-latest' '    steps:' '      - run: true'
expect_workflow_failure artifact-upload 'artifact upload is forbidden' \
  'name: fixture' 'on:' '  push:' 'permissions:' '  contents: read' 'jobs:' '  test:' \
  '    runs-on: ubuntu-latest' '    steps:' \
  '      - uses: actions/upload-artifact@aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
expect_workflow_failure write-permission 'least-privilege' \
  'name: fixture' 'on:' '  push:' 'permissions:' '  contents: write' 'jobs:' '  test:' \
  '    runs-on: ubuntu-latest' '    steps:' '      - run: true'
expect_workflow_failure secret-pull-request 'secrets may not be exposed' \
  'name: fixture' 'on:' '  pull_request:' 'permissions:' '  contents: read' 'jobs:' \
  '  test:' '    runs-on: ubuntu-latest' '    steps:' \
  '      - run: echo "${{ secrets.EXAMPLE }}"'
expect_workflow_failure quoted-secret-pull-request 'secrets may not be exposed' \
  'name: fixture' 'on:' '  "pull_request":' 'permissions:' '  contents: read' 'jobs:' \
  '  test:' '    runs-on: ubuntu-latest' '    steps:' \
  '      - run: echo "${{ secrets.EXAMPLE }}"'
expect_workflow_failure inline-on 'inline on:' \
  'name: fixture' 'on: push' 'permissions:' '  contents: read' 'jobs:' '  test:' \
  '    runs-on: ubuntu-latest' '    steps:' '      - run: true'
expect_workflow_failure inline-permissions 'inline permissions:' \
  'name: fixture' 'on:' '  push:' 'permissions: { contents: read }' 'jobs:' '  test:' \
  '    runs-on: ubuntu-latest' '    steps:' '      - run: true'
expect_workflow_failure job-write-all 'read-all/write-all' \
  'name: fixture' 'on:' '  push:' 'permissions:' '  contents: read' 'jobs:' '  test:' \
  '    permissions: write-all' '    runs-on: ubuntu-latest' '    steps:' '      - run: true'
expect_workflow_failure root-write-all 'read-all/write-all' \
  'name: fixture' 'on:' '  push:' 'permissions: write-all' 'jobs:' '  test:' \
  '    runs-on: ubuntu-latest' '    steps:' '      - run: true'

expect_workflow_pass() {
  local name=$1
  shift
  rm -f "$workflow_fixture"/*
  printf '%s\n' "$@" >"$workflow_fixture/fixture.yml"
  if ! ROTRACK_WORKFLOW_DIR="$workflow_fixture" "$script_dir/check-workflow-policy.sh" \
      >"$tmp_dir/${name}.stdout" 2>"$tmp_dir/${name}.stderr"; then
    printf 'Expected workflow-policy fixture %s to pass.\n' "$name" >&2
    cat "$tmp_dir/${name}.stderr" >&2
    exit 1
  fi
}
expect_workflow_pass comment-only-upload \
  'name: fixture' 'on:' '  push:' 'permissions:' '  contents: read' 'jobs:' '  test:' \
  '    runs-on: ubuntu-latest' '    steps:' \
  '      # actions/upload-artifact@aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
  '      - run: true'

expect_authenticated_trigger_failure() {
  local name=$1
  shift
  rm -f "$workflow_fixture"/*
  {
    printf '%s\n' 'name: fixture' 'on:'
    printf '%s\n' "$@"
    printf '%s\n' 'permissions:' '  contents: read' 'jobs:' '  test:' \
      '    runs-on: ubuntu-latest' '    steps:' '      - run: true'
  } >"$workflow_fixture/authenticated-e2e.yml"
  set +e
  ROTRACK_WORKFLOW_DIR="$workflow_fixture" "$script_dir/check-workflow-policy.sh" \
    >"$tmp_dir/${name}.stdout" 2>"$tmp_dir/${name}.stderr"
  local status=$?
  set -e
  if (( status == 0 )) || ! grep -q 'trusted-default-branch repository_dispatch' "$tmp_dir/${name}.stderr"; then
    printf 'Expected authenticated trigger fixture %s to fail closed.\n' "$name" >&2
    exit 1
  fi
}
expect_authenticated_trigger_failure workflow-dispatch '  workflow_dispatch:'
expect_authenticated_trigger_failure automatic-trigger '  repository_dispatch:' '    types:' \
  '      - rotrack-authenticated-e2e' '  push:'

# The privileged browser flow is a trusted-default-branch repository dispatch.
authenticated_workflow=.github/workflows/authenticated-e2e.yml
for required in \
  'repository_dispatch:' \
  'rotrack-authenticated-e2e' \
  'github.event.client_payload.confirm_nonproduction' \
  'environment: nonproduction' \
  'ROTRACK_NONPRODUCTION_SUPABASE_PROJECT_REF' \
  'ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF' \
  'ROTRACK_E2E_APPROVED_FRONTEND_HOST' \
  'ROTRACK_E2E_APPROVED_API_HOST' \
  '.vercel.app' \
  '.azurecontainerapps.io'; do
  if ! grep -qF "$required" "$authenticated_workflow"; then
    printf 'Authenticated workflow is missing the required target-policy marker: %s.\n' "$required" >&2
    exit 1
  fi
done
if grep -Eq 'disposable-staging|confirm_disposable_staging|STAGING_SUPABASE_REF|DEVELOPMENT_SUPABASE_REF|STAGING_SCOPE|three distinct Supabase|ROTRACK_PRODUCTION_FRONTEND_URL|ROTRACK_PRODUCTION_API_URL' "$authenticated_workflow"; then
  printf 'Authenticated workflow still contains a legacy staging, three-project, or production-URL secret assumption.\n' >&2
  exit 1
fi
if grep -Eq '^  (workflow_dispatch|pull_request|pull_request_target|push|schedule|workflow_run|workflow_call):' "$authenticated_workflow"; then
  printf 'Authenticated workflow has an untrusted or automatic trigger.\n' >&2
  exit 1
fi

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
