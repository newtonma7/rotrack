#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  ENGINE=docker
  FORMAT_ARGS=
  PLATFORM_ARGS='--platform linux/amd64'
elif command -v podman >/dev/null 2>&1 && podman info >/dev/null 2>&1; then
  ENGINE=podman
  # Podman's OCI default drops Docker HEALTHCHECK metadata used by local runtimes.
  FORMAT_ARGS='--format docker'
  PLATFORM_ARGS='--platform linux/amd64'
else
  printf '%s\n' 'No usable Docker or Podman engine is available; run test-contract.sh instead.' >&2
  exit 2
fi

REVISION=$(git -C "$ROOT" rev-parse HEAD)
SHORT_REVISION=$(git -C "$ROOT" rev-parse --short=12 HEAD)
CREATED=$(git -C "$ROOT" show -s --format=%cI HEAD)
SOURCE_DATE_EPOCH=$(git -C "$ROOT" show -s --format=%ct HEAD)
VERSION=${IMAGE_VERSION:-$SHORT_REVISION}
if [ "$ENGINE" = podman ]; then
  REPRODUCIBLE_ARGS="--timestamp $SOURCE_DATE_EPOCH"
else
  # BuildKit consumes this predefined argument to normalize layer timestamps.
  REPRODUCIBLE_ARGS="--build-arg SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH"
fi

REQUIRE_CLEAN=${REQUIRE_CLEAN:-1}
if [ -n "$(git -C "$ROOT" status --porcelain --untracked-files=normal)" ]; then
  if [ "$REQUIRE_CLEAN" = 1 ]; then
    printf '%s\n' 'Refusing a release image from a dirty worktree. Set REQUIRE_CLEAN=0 only for local development validation.' >&2
    exit 1
  fi
  VERSION="${VERSION}-dirty"
fi

IMAGE_REPOSITORY=${IMAGE_REPOSITORY:-rotrack-api}
IMAGE_TAG=${IMAGE_TAG:-$VERSION}
IMAGE_REF="${IMAGE_REPOSITORY}:${IMAGE_TAG}"
if [ "${NO_CACHE:-0}" = 1 ]; then
  CACHE_ARGS=--no-cache
else
  CACHE_ARGS=
fi

# Optional engine flags are deliberately unquoted so each remains a separate argument.
# shellcheck disable=SC2086
"$ENGINE" build $FORMAT_ARGS $PLATFORM_ARGS $REPRODUCIBLE_ARGS $CACHE_ARGS \
  --file "$ROOT/backend/Dockerfile" \
  --build-arg "BUILD_OUTPUT_EPOCH=$SOURCE_DATE_EPOCH" \
  --build-arg "IMAGE_CREATED=$CREATED" \
  --build-arg "IMAGE_REVISION=$REVISION" \
  --build-arg "IMAGE_VERSION=$VERSION" \
  --tag "$IMAGE_REF" \
  "$ROOT"

IMAGE_ID=$("$ENGINE" image inspect --format '{{.Id}}' "$IMAGE_REF")
IMAGE_DIGEST=$("$ENGINE" image inspect --format '{{.Digest}}' "$IMAGE_REF" 2>/dev/null || true)
[ -n "$IMAGE_DIGEST" ] || IMAGE_DIGEST='unavailable-until-registry-push'

printf 'image_ref=%s\n' "$IMAGE_REF"
printf 'image_id=%s\n' "$IMAGE_ID"
printf 'local_content_digest=%s\n' "$IMAGE_DIGEST"
printf 'revision=%s\n' "$REVISION"
printf '%s\n' 'registry_digest=unavailable (image was not pushed)'
