#!/usr/bin/env bash
set -euo pipefail

if ! command -v python3 >/dev/null 2>&1; then
  printf 'Workflow policy guard requires python3 for fail-closed full-file scanning.\n' >&2
  exit 1
fi

workflow_dir=${ROTRACK_WORKFLOW_DIR:-.github/workflows}
mapfile -t workflows < <(find "$workflow_dir" -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) -print | sort)
if (( ${#workflows[@]} == 0 )); then
  printf 'No workflow files found.\n' >&2
  exit 1
fi

failures=0
for workflow in "${workflows[@]}"; do
  if grep -Eq "^[[:space:]]*[\"']?pull_request_target[\"']?:" "$workflow"; then
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
    mapping_key=${mapping_key#-}
    mapping_key=${mapping_key#"${mapping_key%%[![:space:]]*}"}
    mapping_key=${mapping_key%"${mapping_key##*[![:space:]]}"}
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
    mapping_value=${trimmed#*:}
    if [[ "$mapping_key" == "permissions" && "$mapping_value" =~ ^[[:space:]]*(read-all|write-all)([[:space:]#]|$) ]]; then
      printf '%s: broad read-all/write-all permissions are forbidden at every scope.\n' "$workflow" >&2
      failures=1
    elif [[ "$mapping_key" == "permissions" && "$mapping_value" =~ ^[[:space:]]+[^#[:space:]] ]]; then
      printf '%s: inline permissions: forms are unsupported; use a block permission map.\n' "$workflow" >&2
      failures=1
    fi
    if [[ "$mapping_key" == "uses" && "$mapping_value" =~ ^[[:space:]]*actions/upload-artifact@ ]]; then
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
  done < <(grep -E "^[[:space:]]+[\"']?(actions|attestations|checks|contents|deployments|discussions|id-token|issues|packages|pages|pull-requests|repository-projects|security-events|statuses)[\"']?:[[:space:]]*write([[:space:]#]|$)" "$workflow" || true)

  if (( has_secret_reference != 0 && has_pull_request_trigger != 0 )); then
    printf '%s: secrets may not be exposed to an automatic pull-request workflow.\n' "$workflow" >&2
    failures=1
  fi

  workflow_name=$(basename "$workflow")
  if [[ "$workflow_name" != "authenticated-e2e.yml" ]]; then
    if grep -Eq 'rotrack-authenticated-e2e|ROTRACK_AUTHENTICATED_E2E_ENABLED|ROTRACK_E2E_|ROTRACK_(NONPRODUCTION|PRODUCTION)_SUPABASE_PROJECT_REF' "$workflow"; then
      printf '%s: alternate workflow contains an authenticated E2E marker; keep the privileged flow in authenticated-e2e.yml only.\n' "$workflow" >&2
      failures=1
    fi
    # Scan the whole file so indexed, whole-context, and multiline GitHub
    # expressions cannot evade the reserved privileged-surface policy. The
    # standalone nonproduction token is likewise reserved to the canonical
    # workflow, including quoted environment values.
    # Scanner contract: status 0 means a reserved surface was found, status
    # 1 means clear, and every other status is a scanner failure.
    scanner_status=0
    if python3 - "$workflow" <<'PY'
import re
import sys

try:
    text = open(sys.argv[1], encoding="utf-8").read()
    secret_expression = re.compile(r"\$\{\{(?:(?!\}\}).)*\bsecrets\b(?:(?!\}\}).)*\}\}", re.DOTALL)
    reserved_environment = re.compile(r"\bnonproduction\b")
    has_reserved_surface = secret_expression.search(text) or reserved_environment.search(text)
except Exception as error:
    print(f"workflow-policy scanner error: {error}", file=sys.stderr)
    raise SystemExit(2)

raise SystemExit(0 if has_reserved_surface else 1)
PY
    then
      scanner_status=0
    else
      scanner_status=$?
    fi
    case "$scanner_status" in
      0)
        printf '%s: alternate workflow may not contain privileged secrets or nonproduction environment; only authenticated-e2e.yml may use them.\n' "$workflow" >&2
        failures=1
        ;;
      1)
        ;;
      *)
        printf '%s: workflow-policy scanner failed; refusing to treat scanner errors as a clean result.\n' "$workflow" >&2
        failures=1
        ;;
    esac
  fi

  if [[ "$workflow_name" == "authenticated-e2e.yml" ]]; then
    if ! grep -Eq '^  repository_dispatch:[[:space:]]*$' "$workflow" || \
       ! grep -Eq '^    types:[[:space:]]*$' "$workflow" || \
       ! grep -Eq '^      - rotrack-authenticated-e2e[[:space:]]*$' "$workflow" || \
       grep -Eq "^  [\"']?(workflow_dispatch|pull_request|pull_request_target|push|schedule|workflow_run|workflow_call)[\"']?:" "$workflow" || \
       grep -Eq '^  repository_dispatch:[[:space:]]+[^#[:space:]]' "$workflow"; then
      printf '%s: protected authenticated E2E must use only the trusted-default-branch repository_dispatch event.\n' "$workflow" >&2
      failures=1
    fi
    if ! grep -Eq '^[[:space:]]+environment:[[:space:]]+nonproduction([[:space:]#]|$)' "$workflow"; then
      printf '%s: protected authenticated E2E must target the logical nonproduction environment.\n' "$workflow" >&2
      failures=1
    fi
    job_count=$(awk '
      $0 == "jobs:" { in_jobs=1; next }
      in_jobs && /^[^[:space:]]/ { exit }
      in_jobs && /^  [^[:space:]][^:]*:[[:space:]]*(#.*)?$/ { count++ }
      END { print count + 0 }
    ' "$workflow")
    if (( job_count != 1 )); then
      printf '%s: authenticated-e2e.yml must contain exactly one top-level job.\n' "$workflow" >&2
      failures=1
    fi
    if ! grep -Eq '^  authenticated-e2e-protected:[[:space:]]*$' "$workflow"; then
      printf '%s: authenticated-e2e.yml must contain the expected authenticated E2E job.\n' "$workflow" >&2
      failures=1
    fi
    # GitHub evaluates a job-level if before allocating its runner or resolving
    # the job environment. Keep this exact administrator-controlled repository
    # variable comparison so the default/missing value skips before secrets can
    # become available; a truthiness or non-false check is not sufficient.
    if ! awk -v expected='${{ vars.ROTRACK_AUTHENTICATED_E2E_ENABLED == '\''true'\'' }}' '
      $0 == "  authenticated-e2e-protected:" { in_job=1; next }
      in_job && $0 ~ /^  [^[:space:]][^:]*:/ { exit }
      in_job && $0 == "    if: " expected { found=1; exit }
      END { exit(found ? 0 : 1) }
    ' "$workflow"; then
      printf '%s: protected authenticated E2E requires an explicit administrator-controlled enablement gate at job level (ROTRACK_AUTHENTICATED_E2E_ENABLED == '\''true'\'').\n' "$workflow" >&2
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
  done < <(grep -E "^[[:space:]]*(-[[:space:]]+)?[\"']?uses[\"']?:[[:space:]]+[^[:space:]@]+@" "$workflow" || true)
done

if (( failures != 0 )); then
  exit 1
fi

printf 'Workflow policy guard passed (%d workflow files).\n' "${#workflows[@]}"
