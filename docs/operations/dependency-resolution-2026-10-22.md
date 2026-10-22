# Dependency resolution checkpoint — 2026-10-22

## Publication and merge boundary

[PR #59](https://github.com/newtonma7/rotrack/pull/59) contains the previously local Daily Logs and Friends work and the dependency updates below.
The unrelated signup PR #55 is outside this change.

**Operator decision:** Keep the full audit and merge gate blocked for the unpatched
[`braces` advisory GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm).
No advisory exception, audit-policy narrowing, branch-protection bypass, or merge is authorized while that blocker remains.
No hosted migration or manual deployment is included.

## Dependency PR disposition

The lockfile records these versions. The 16 overlapping update PRs were closed as superseded by #59; #43 was closed with the incompatibility evidence below. None was represented as merged. The unrelated #55 remains open.

| PR | Dependency | Integrated version or disposition |
| --- | --- | --- |
| #38 | Spring Boot | 4.1.1, including required Jackson 3 and modular-starter migration |
| #40 | Supabase browser client | 2.118.0 |
| #41 | eslint-config-next | 16.4.2, aligned with Next |
| #42 | lucide-react | 1.61.0 |
| #43 | ESLint 10 | Deferred. React and accessibility plugins declare ESLint 9 peers; unadapted ESLint 10 fails while loading `react/display-name`. Retained the patched 9.39.5 line. ESLint 9 is now upstream-EOL, so migration remains a tooling follow-up, not a completed major upgrade. |
| #44 | Recharts | 3.10.1 |
| #46 | @humanfs/node | 0.16.8 |
| #47 | Tiptap | Core, React, extensions, and ProseMirror adapter aligned at exact 3.32.0 |
| #48 | Vitest and @vitest/mocker | 4.1.12 |
| #49 | sharp | 0.35.5 |
| #51 | js-yaml | 4.3.2 |
| #52 | undici | 8.12.3 |
| #53 | actions/setup-java | 6.0.1, pinned to upstream tag commit `de7274f081f381c8f8158605e0321c36c376e2e6` |
| #54 | Next | 16.4.2 |
| #56 | brace-expansion | 1.1.21 and 5.0.12 |
| #57 | source-map-js | 1.2.2 |
| #58 | Vite | 8.3.5 |

Tiptap's exact versions avoid mixing extensions with incompatible exact peer requirements.
The editor now exposes Tiptap's accessible textbox role. Its tests wait for post-render slash-menu attributes rather than assuming synchronous effects.

## Security findings

The original lockfile audit reported 45 findings: 31 moderate, 13 high, and one critical.
After the available upgrades, the clean-resolution audit reported five high entries from one unpatched dependency chain:

`eslint-config-next → @next/eslint-plugin-next → fast-glob → micromatch → braces@3.0.3`

The advisory describes stack exhaustion from deeply nested brace patterns.
GitHub reports `first_patched_version: null`; npm's latest `braces` release is 3.0.3.
The proposed npm downgrade to eslint-config-next 14 does not preserve the current Next/ESLint toolchain and was not applied.

A final successful full audit confirms five high entries and no critical findings. Every remaining reported node is marked dev-only in the lockfile. Separate production-only audit attempts timed out at the registry's bulk advisory endpoint; no production-only audit pass is claimed.
An offline audit without cached advisories is not security evidence.
The existing full CI audit remains enabled; an unavailable audit service is not a pass.

## Verification records

Raw local evidence is outside the repository at `/tmp/rotrack-dependency-resolution/`.
The backend upgrade record is `backend-upgrade.md`; integrated Java 21 results are in `backend-integrated.log`.
The source migration preserves strict unknown-field/numeric validation, canonical rich-text serialization, JWT ownership, database grants, and `ddl-auto: validate`.

An old Boot 3.4.1/Jackson 2 process and the new Boot 4.1.1/Jackson 3 process shared only the disposable database. A local probe created Unicode/link/task Notes and a Reflection under the old version, replayed both creation keys under the new version, then read a new-version Note update through the old version. Identity, versions, and canonical documents remained unchanged; no stored-data conversion was required. Evidence: `jackson-cross-version.log`.

Frontend lint and typecheck pass. Two sequential full Vitest runs pass 202/202; the normal Turbopack production build passes. Structural compatibility fixes preserve event-driven loading, query identity, stale-response guards, timer restoration, and autosave coalescing without disabling lint rules. Local browser groups pass 14/14 for tracker/Notes/Daily Logs and 2/2 for Friends. The separate groups respect the default mutation budget. Final commands, limitations, and hosted-check readbacks are recorded in `todo.md`.
Local browser fixtures use generated ES256 JWTs with real Spring security and disposable PostgreSQL.
They do not verify Supabase token issuance or hosted production readiness.
