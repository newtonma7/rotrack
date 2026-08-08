#!/usr/bin/env bash
set -euo pipefail

# CI must never package or commit auth material and generated test/build output.
# Tests may provide a newline-delimited manifest; normal runs inspect tracked and
# non-ignored working-tree paths so the guard is useful before a commit too.
check_path() {
  local path=${1#./}
  local lower=${path,,}
  local base=${lower##*/}

  if [[ "$base" == .env* && "$base" != ".env.example" ]]; then
    return 1
  fi

  case "$lower" in
    *.pem|*.key|*.crt|*.cer|*.p12|*.pfx|*credential*.json|*credentials*.txt|\
    *service-account*.json|*secret*.json|*secret*.txt|*storage-state*.json|\
    *storage_state*.json|*storagestate*.json|*auth-state*.json|*/playwright/.auth/*|\
    playwright-report/*|*/playwright-report/*|test-results/*|*/test-results/*|\
    coverage/*|*/coverage/*|backend/target/*|frontend/.next/*)
      return 1
      ;;
  esac

  return 0
}

failures=0
inspect() {
  local path=$1
  [[ -z "$path" ]] && return
  if ! check_path "$path"; then
    printf 'forbidden sensitive or generated path: %s\n' "$path" >&2
    failures=1
  fi
}

if [[ -n "${ROTRACK_CI_PATH_MANIFEST:-}" ]]; then
  while IFS= read -r path || [[ -n "$path" ]]; do
    inspect "$path"
  done < "$ROTRACK_CI_PATH_MANIFEST"
else
  while IFS= read -r -d '' path; do
    inspect "$path"
  done < <(git ls-files --cached --others --exclude-standard -z)
fi

if (( failures != 0 )); then
  printf 'Sensitive-path guard failed. Keep credentials and generated artifacts outside the repository.\n' >&2
  exit 1
fi

printf 'Sensitive-path guard passed.\n'
