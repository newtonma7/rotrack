#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
IMAGE_REF=${IMAGE_REF:?Set IMAGE_REF to the locally built candidate tag}
ENV_FILE=${1:?Pass an absolute runtime env-file outside the repository}

case "$ENV_FILE" in
  /*) ;;
  *)
    printf '%s\n' 'The runtime env-file path must be absolute.' >&2
    exit 1
    ;;
esac
ENV_DIR=$(CDPATH= cd -- "$(dirname "$ENV_FILE")" && pwd)
case "$ENV_DIR/" in
  "$ROOT"/*)
    printf '%s\n' 'The runtime env-file must remain outside the repository.' >&2
    exit 1
    ;;
esac
[ -r "$ENV_FILE" ] || { printf '%s\n' 'The runtime env-file is not readable.' >&2; exit 1; }
: "${DATABASE_CA_CERTIFICATE_PEM:?Export DATABASE_CA_CERTIFICATE_PEM from an authorized CA file without printing it}"

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  ENGINE=docker
elif command -v podman >/dev/null 2>&1 && podman info >/dev/null 2>&1; then
  ENGINE=podman
else
  printf '%s\n' 'No usable Docker or Podman engine is available.' >&2
  exit 2
fi

CONTAINER_ID=
cleanup() {
  if [ -n "$CONTAINER_ID" ]; then
    "$ENGINE" rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT HUP INT TERM

set -- run -d
if [ -n "${ROTRACK_CONTAINER_NETWORK:-}" ]; then
  case "$ROTRACK_CONTAINER_NETWORK" in
    *[!A-Za-z0-9_.-]*)
      printf '%s\n' 'ROTRACK_CONTAINER_NETWORK contains unsupported characters.' >&2
      exit 1
      ;;
  esac
  set -- "$@" --network "$ROTRACK_CONTAINER_NETWORK"
fi
set -- "$@" \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m,mode=1777 \
  --env-file "$ENV_FILE" \
  --env DATABASE_CA_CERTIFICATE_PEM \
  --publish 127.0.0.1::8080 \
  "$IMAGE_REF"
CONTAINER_ID=$("$ENGINE" "$@")
HOST_PORT=$("$ENGINE" port "$CONTAINER_ID" 8080/tcp | awk -F: 'NR == 1 { print $NF }')

attempt=0
until HEALTH=$(curl --silent --show-error --fail-with-body "http://127.0.0.1:$HOST_PORT/api/v1/health" 2>/dev/null); do
  attempt=$((attempt + 1))
  RUNNING=$("$ENGINE" inspect --format '{{.State.Running}}' "$CONTAINER_ID" 2>/dev/null || true)
  if [ "$attempt" -ge 90 ] || [ "$RUNNING" != true ]; then
    printf '%s\n' 'Container did not become live; raw logs were intentionally not printed.' >&2
    exit 1
  fi
  sleep 1
done
[ "$HEALTH" = '{"status":"ok"}' ] || { printf '%s\n' 'Unexpected liveness response.' >&2; exit 1; }

READINESS=$(curl --silent --show-error --fail-with-body "http://127.0.0.1:$HOST_PORT/api/v1/readiness")
[ "$READINESS" = '{"status":"ready"}' ] || { printf '%s\n' 'Unexpected readiness response.' >&2; exit 1; }

"$ENGINE" stop --time 30 "$CONTAINER_ID" > /dev/null
STOP_EXIT_CODE=$("$ENGINE" inspect --format '{{.State.ExitCode}}' "$CONTAINER_ID")
STOP_OOM_KILLED=$("$ENGINE" inspect --format '{{.State.OOMKilled}}' "$CONTAINER_ID")
if [ "$STOP_EXIT_CODE" = 137 ] || [ "$STOP_OOM_KILLED" = true ]; then
  printf '%s\n' 'Container required a forced kill or was OOM-killed during shutdown.' >&2
  exit 1
fi
CONTAINER_ID=
printf 'container smoke: liveness=200 readiness=200 sigterm-stop=passed exit=%s port=%s\n' "$STOP_EXIT_CODE" "$HOST_PORT"
