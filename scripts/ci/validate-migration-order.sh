#!/usr/bin/env bash
set -euo pipefail

migration_dir=${1:-database/migrations}
mapfile -t migrations < <(find "$migration_dir" -maxdepth 1 -type f -name '*.sql' -printf '%f\n' | sort)

if (( ${#migrations[@]} == 0 )); then
  printf 'No SQL migrations found in %s.\n' "$migration_dir" >&2
  exit 1
fi

expected=1
for migration in "${migrations[@]}"; do
  if [[ ! "$migration" =~ ^([0-9]{3})_[a-z0-9][a-z0-9_]*\.sql$ ]]; then
    printf 'Migration name is not ordered NNN_snake_case.sql: %s\n' "$migration" >&2
    exit 1
  fi

  number=$((10#${BASH_REMATCH[1]}))
  if (( number != expected )); then
    printf 'Migration sequence is not contiguous: expected %03d, found %s.\n' "$expected" "$migration" >&2
    exit 1
  fi
  ((expected += 1))
done

printf 'Migration order guard passed: %s\n' "${migrations[*]}"
