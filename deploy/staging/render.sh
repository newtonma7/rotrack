#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT="${1:-$ROOT/rendered}"
mkdir -p "$OUTPUT"
chmod 700 "$OUTPUT"

python3 - "$ROOT/templates" "$OUTPUT" <<'PY'
import os
import pathlib
import re
import sys

templates = pathlib.Path(sys.argv[1])
output = pathlib.Path(sys.argv[2])
inputs = {
    templates / "deployment.env.template": output / "deployment.env",
    templates / "ecs-task-definition.json.template": output / "ecs-task-definition.json",
}
pattern = re.compile(r"__([A-Z][A-Z0-9_]+)__")
missing: set[str] = set()
rendered: dict[pathlib.Path, str] = {}

for source, destination in inputs.items():
    text = source.read_text(encoding="utf-8")

    def replace(match: re.Match[str]) -> str:
        name = match.group(1)
        value = os.environ.get(name, "")
        if not value:
            missing.add(name)
            return match.group(0)
        if "\n" in value or "\r" in value:
            raise SystemExit(f"render failed: {name} must be a single-line value")
        return value

    rendered[destination] = pattern.sub(replace, text)

if missing:
    raise SystemExit("render failed: missing environment names: " + ", ".join(sorted(missing)))

for destination, text in rendered.items():
    destination.write_text(text, encoding="utf-8")
    destination.chmod(0o600)
PY

"$ROOT/validate.sh" deployment "$OUTPUT"
printf 'staging render: PASS: wrote validated local files under %s (ignored by Git)\n' "$OUTPUT"
