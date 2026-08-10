#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
FOUNDATION="$ROOT/deploy/azure/production/foundation.bicep"
APP="$ROOT/deploy/azure/production/app.bicep"
[ -f "$FOUNDATION" ] || { printf '%s\n' 'Azure foundation Bicep is missing.' >&2; exit 1; }
[ -f "$APP" ] || { printf '%s\n' 'Azure app Bicep is missing.' >&2; exit 1; }
[ ! -e "$ROOT/deploy/azure/main.bicep" ] || { printf '%s\n' 'all-in-one Azure Bicep must not exist.' >&2; exit 1; }
grep -Fq 'USER 10001:10001' "$ROOT/backend/Dockerfile" || { printf '%s\n' 'backend image must run as UID/GID 10001:10001.' >&2; exit 1; }
grep -Fq "PLATFORM_ARGS='--platform linux/amd64'" "$ROOT/scripts/container/build-image.sh" || { printf '%s\n' 'backend image build must target linux/amd64.' >&2; exit 1; }
command -v az >/dev/null 2>&1 || { printf '%s\n' 'az is required for Bicep validation.' >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { printf '%s\n' 'python3 is required for Bicep validation.' >&2; exit 2; }

FOUNDATION_RENDERED=$(mktemp)
APP_RENDERED=$(mktemp)
trap 'rm -f "$FOUNDATION_RENDERED" "$APP_RENDERED"' EXIT HUP INT TERM
az bicep build --file "$FOUNDATION" --outfile "$FOUNDATION_RENDERED" >/dev/null
az bicep build --file "$APP" --outfile "$APP_RENDERED" >/dev/null
python3 - "$FOUNDATION_RENDERED" "$APP_RENDERED" <<'PY'
import json
import pathlib
import sys

foundation = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding='utf-8'))
app = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding='utf-8'))
for template in (foundation, app):
    serialized = json.dumps(template)
    for forbidden in ('rotrack-nonproduction', 'rotracknonproduction', 'arn:aws', 'AWS::'):
        if forbidden in serialized:
            raise SystemExit(f'forbidden deployment target marker: {forbidden}')
foundation_types = {resource['type'] for resource in foundation.get('resources', [])}
if 'Microsoft.App/containerApps' in foundation_types:
    raise SystemExit('foundation must not deploy the Container App')
required_foundation = {
    'Microsoft.OperationalInsights/workspaces',
    'Microsoft.ContainerRegistry/registries',
    'Microsoft.ManagedIdentity/userAssignedIdentities',
    'Microsoft.Authorization/roleAssignments',
    'Microsoft.App/managedEnvironments',
    'Microsoft.Consumption/budgets',
}
if not required_foundation <= foundation_types:
    raise SystemExit(f'foundation resources missing: {sorted(required_foundation - foundation_types)}')
app_types = {resource['type'] for resource in app.get('resources', [])}
if app_types != {'Microsoft.App/containerApps'}:
    raise SystemExit(f'app deployment contains unexpected resource types: {sorted(app_types)}')
PY
printf '%s\n' 'azure bicep validation: passed (foundation/app render only; no Azure mutation)'
