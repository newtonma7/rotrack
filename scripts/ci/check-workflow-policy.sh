#!/usr/bin/env bash
set -euo pipefail

workflow_dir=${ROTRACK_WORKFLOW_DIR:-.github/workflows}
mapfile -t workflows < <(find "$workflow_dir" -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) -print | sort)
if (( ${#workflows[@]} == 0 )); then
  printf 'No workflow files found.\n' >&2
  exit 1
fi

failures=0
for workflow in "${workflows[@]}"; do
  if grep -Eq '^[[:space:]]*pull_request_target:' "$workflow"; then
    printf '%s: pull_request_target is forbidden because untrusted PR code must not receive privileged context.\n' "$workflow" >&2
    failures=1
  fi

  # Keep these checks line-based and dependency-free. Only non-comment lines
  # are inspected, so policy examples in comments cannot create false alarms.
  has_secret_reference=0
  has_pull_request_trigger=0
  while IFS= read -r line; do
    trimmed=${line#"${line%%[![:space:]]*}"}
    [[ -z "$trimmed" || "$trimmed" == \#* ]] && continue

    if [[ "$trimmed" == *'secrets.'* ]]; then
      has_secret_reference=1
    fi
    mapping_key=${trimmed%%:*}
    mapping_key=${mapping_key#\"}
    mapping_key=${mapping_key%\"}
    mapping_key=${mapping_key#\'}
    mapping_key=${mapping_key%\'}
    indentation=${line%%[![:space:]]*}
    # Normalize quoted YAML keys before checking the top-level trigger. This
    # prevents \"pull_request\": and 'pull_request': from bypassing the guard.
    if [[ "$indentation" == "  " && "$mapping_key" == "pull_request" ]]; then
      has_pull_request_trigger=1
    fi

    if [[ "$trimmed" =~ ^on:[[:space:]]+[^#[:space:]] ]]; then
      printf '%s: inline on: forms are unsupported; use a block trigger map.\n' "$workflow" >&2
      failures=1
    fi
    if [[ "$trimmed" =~ ^permissions:[[:space:]]+[^#[:space:]] ]]; then
      printf '%s: inline permissions: forms are unsupported; use a block permission map.\n' "$workflow" >&2
      failures=1
    fi
    if [[ "$trimmed" =~ ^permissions:[[:space:]]*(read-all|write-all)([[:space:]#]|$) ]]; then
      printf '%s: broad read-all/write-all permissions are forbidden at every scope.\n' "$workflow" >&2
      failures=1
    fi
    if [[ "$trimmed" =~ ^(-[[:space:]]+)?uses:[[:space:]]+[^[:space:]#]*upload-artifact@ ]]; then
      printf '%s: artifact upload is forbidden; authenticated and generated output may contain sensitive data.\n' "$workflow" >&2
      failures=1
    fi
  done < "$workflow"

  if ! grep -Eq '^permissions:[[:space:]]*(#.*)?$' "$workflow"; then
    printf '%s: an explicit top-level permissions block is required.\n' "$workflow" >&2
    failures=1
  fi
  while IFS= read -r permission; do
    permission_name=${permission%%:*}
    permission_name=${permission_name//[[:space:]]/}
    if [[ "$permission_name" != "security-events" ]]; then
      printf '%s: write permission is not allowed for %s; keep workflow permissions least-privilege.\n' "$workflow" "$permission_name" >&2
      failures=1
    fi
  done < <(grep -E '^[[:space:]]+(actions|attestations|checks|contents|deployments|discussions|id-token|issues|packages|pages|pull-requests|repository-projects|security-events|statuses):[[:space:]]*write([[:space:]#]|$)' "$workflow" || true)

  if (( has_secret_reference != 0 && has_pull_request_trigger != 0 )); then
    printf '%s: secrets may not be exposed to an automatic pull-request workflow.\n' "$workflow" >&2
    failures=1
  fi

  if [[ "$(basename "$workflow")" == "authenticated-e2e.yml" ]]; then
    if ! grep -Eq '^  repository_dispatch:[[:space:]]*$' "$workflow" || \
       ! grep -Eq '^    types:[[:space:]]*$' "$workflow" || \
       ! grep -Eq '^      - rotrack-authenticated-e2e[[:space:]]*$' "$workflow" || \
       grep -Eq '^  (workflow_dispatch|pull_request|pull_request_target|push|schedule|workflow_run|workflow_call):' "$workflow" || \
       grep -Eq '^  repository_dispatch:[[:space:]]+[^#[:space:]]' "$workflow"; then
      printf '%s: protected authenticated E2E must use only the trusted-default-branch repository_dispatch event.\n' "$workflow" >&2
      failures=1
    fi
    if ! grep -Eq '^[[:space:]]+environment:[[:space:]]+nonproduction([[:space:]#]|$)' "$workflow"; then
      printf '%s: protected authenticated E2E must target the logical nonproduction environment.\n' "$workflow" >&2
      failures=1
    fi
  fi

  while IFS= read -r line; do
    ref=${line#*@}
    ref=${ref%%[[:space:]#]*}
    ref=${ref%\"}
    ref=${ref%\'}
    if [[ ! "$ref" =~ ^[0-9a-f]{40}$ ]]; then
      printf '%s: action dependency is not pinned to a full commit SHA: %s\n' "$workflow" "$line" >&2
      failures=1
    fi
  done < <(grep -E '^[[:space:]]*(-[[:space:]]+)?uses:[[:space:]]+[^[:space:]@]+@' "$workflow" || true)
done

if (( failures != 0 )); then
  exit 1
fi

printf 'Workflow policy guard passed (%d workflow files).\n' "${#workflows[@]}"
