#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
IMAGE_REF=${IMAGE_REF:?Set IMAGE_REF to the locally built immutable candidate tag}

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  ENGINE=docker
elif command -v podman >/dev/null 2>&1 && podman info >/dev/null 2>&1; then
  ENGINE=podman
else
  printf '%s\n' 'No usable Docker or Podman engine is available.' >&2
  exit 2
fi

TMP=$(mktemp -d)
CONTAINER_ID=
cleanup() {
  if [ -n "$CONTAINER_ID" ]; then
    "$ENGINE" rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true
  fi
  rm -rf "$TMP"
}
trap cleanup EXIT HUP INT TERM

"$ENGINE" image inspect "$IMAGE_REF" > "$TMP/inspect.json"
python3 - "$TMP/inspect.json" <<'PY'
import json
import re
import sys

image = json.load(open(sys.argv[1], encoding="utf-8"))[0]
config = image["Config"]
assert image["Os"] == "linux", image["Os"]
assert image["Architecture"] == "amd64", image["Architecture"]
assert config["User"] == "10001:10001", config["User"]
assert "8080/tcp" in config["ExposedPorts"], config["ExposedPorts"]
assert config["Entrypoint"] == ["/opt/rotrack/bin/container-entrypoint.sh"], config["Entrypoint"]
health_config = config.get("Healthcheck") or image.get("Healthcheck")
assert health_config, "image has no HEALTHCHECK metadata"
health = " ".join(health_config["Test"])
assert "/api/v1/health" in health, health
assert "/api/v1/readiness" not in health, health
labels = config["Labels"]
assert re.fullmatch(r"[0-9a-f]{40}", labels["org.opencontainers.image.revision"])
assert labels["org.opencontainers.image.created"]
assert labels["org.opencontainers.image.version"]
PY

CONTAINER_ID=$("$ENGINE" create "$IMAGE_REF")
"$ENGINE" export "$CONTAINER_ID" > "$TMP/rootfs.tar"
tar -tf "$TMP/rootfs.tar" > "$TMP/paths"
grep -qx 'opt/rotrack/application.jar' "$TMP/paths"
grep -qx 'opt/rotrack/bin/container-entrypoint.sh' "$TMP/paths"

if grep -Eiq '(^|/)(\.git|frontend|target|playwright-report|test-results)(/|$)|(^|/)\.env($|\.)|storage-state.*\.json$|(^|/)(credentials|secrets)(/|$)' "$TMP/paths"; then
  printf '%s\n' 'Forbidden source, credential, or test artifact found in runtime filesystem.' >&2
  grep -Ei '(^|/)(\.git|frontend|target|playwright-report|test-results)(/|$)|(^|/)\.env($|\.)|storage-state.*\.json$|(^|/)(credentials|secrets)(/|$)' "$TMP/paths" >&2
  exit 1
fi

printf 'container image contract: passed for %s\n' "$IMAGE_REF"
