#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
# shellcheck source=_common.sh
. "$SCRIPT_DIR/_common.sh"

require_command az
require_subscription
require_target
require_mutation_confirmation
validate_foundation_parameters
"$SCRIPT_DIR/validate.sh"

# This is the only infrastructure creation phase. It has no runtime secrets and
# cannot receive an image digest, so it cannot accidentally deploy an unpushed image.
az group create \
  --name rotrack-production \
  --location "$FOUNDATION_LOCATION" \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --output none

az deployment group create \
  --name rotrack-production-foundation \
  --resource-group rotrack-production \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --template-file "$FOUNDATION_TEMPLATE" \
  --mode Incremental \
  --parameters "@$FOUNDATION_PARAMETER_FILE" \
  --output none

printf '%s\n' 'azure foundation: completed production foundation deployment (secrets redacted)'
AZURE_WAIT_FOR_RBAC=1 AZURE_FOUNDATION_PARAMETER_FILE="$FOUNDATION_PARAMETER_FILE" "$SCRIPT_DIR/preflight.sh"
