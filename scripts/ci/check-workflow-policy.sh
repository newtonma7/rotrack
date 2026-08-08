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
  if grep -Eq 'actions/upload-artifact@' "$workflow"; then
    printf '%s: artifact upload is forbidden; authenticated and generated output may contain sensitive data.\n' "$workflow" >&2
    failures=1
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
