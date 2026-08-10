# Explicit production Azure lane

This directory is the source-controlled production lane. It is deliberately separate from [`../`](../): changing `AZURE_TARGET`, resource-group, or app environment variables in the non-production scripts cannot select these templates.

The lane targets only:

- resource group `rotrack-production`;
- managed environment `rotrack-production-env`;
- Container App `rotrack-api-production`;
- a `rotrackproduction*` Azure Container Registry name supplied in an operator-owned parameter file;
- managed identity `rotrack-api-production-identity`; and
- runtime logging label `production`.

Every mutating command requires `ROTRACK_AZURE_PRODUCTION_CONFIRM=rotrack-production`, an explicitly selected `AZURE_PRODUCTION_SUBSCRIPTION_ID`, and a declared `AZURE_NONPRODUCTION_SUBSCRIPTION_ID` for target comparison. The selected `AZURE_SUBSCRIPTION_ID` must equal the production subscription; it may equal the non-production subscription when the resource-group, managed-environment, Container App, and managed-identity boundaries remain distinct. Resource names, subscription identity, parameter-file location/mode, immutable image digest, `secretRef` mappings, TLS `verify-full` with the official CA path, exact HTTPS CORS, probes, non-root image, scale bounds, and connection pool budget are checked before mutation/readback.

Use only operator-owned parameter files outside Git:

```text
AZURE_PRODUCTION_SUBSCRIPTION_ID=<production-subscription-guid>
AZURE_NONPRODUCTION_SUBSCRIPTION_ID=<non-production-subscription-guid>  # may equal production when boundaries are separate
AZURE_SUBSCRIPTION_ID=<same-production-subscription-guid>
AZURE_FOUNDATION_PARAMETER_FILE=/restricted/rotrack-production-foundation.json  # mode 0400
AZURE_APP_PARAMETER_FILE=/restricted/rotrack-production-app.json              # mode 0600
ROTRACK_AZURE_PRODUCTION_CONFIRM=rotrack-production
```

The example parameter files contain placeholders only. No production Azure resources, ACR, managed identity, Supabase project, Vercel deployment, secrets, or populated parameter file are claimed or created by this source change. Do not run these scripts without separate production authorization and the release gates in [`../../../docs/operations/release/release-runbook.md`](../../../docs/operations/release/release-runbook.md).
