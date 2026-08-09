# Security policy

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Report it
privately through GitHub's Security Advisories / private vulnerability
reporting for this repository. Include the affected commit or path, impact,
steps to reproduce, and a minimal proof of concept that does not contain real
credentials or private user data.

If private vulnerability reporting is not available, contact the repository
owner through the GitHub profile instead of posting sensitive details publicly.

This project is under active development. Security reports are acknowledged
as time permits; no response or remediation time is guaranteed.

## Scope and handling

The most important boundaries are authentication and authorization, ownership
isolation, CI workflows, secret handling, and privacy of stored application
data. Do not test against production or use real user accounts. Use disposable
local/non-production data only, and remove any temporary credentials or
storage state after testing.
