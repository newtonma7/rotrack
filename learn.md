# Learning review

Use these questions to review the completed baseline phases and understand the implementation decisions.

## M0.2 — Repository hygiene

1. Why should generated build output be ignored instead of committed?
2. What is the difference between `git rm --cached` and deleting a file from disk?
3. Why are compiled artifacts poor evidence that a feature works?

## M0.3 — Development runbook

1. Why should `NEXT_PUBLIC_*` values never contain backend secrets?
2. Why is `ddl-auto: validate` safer than allowing Hibernate to modify a production schema?
3. Why should a README document known limitations instead of only describing the target architecture?

## M0.4 — Toolchains and tests

1. What can change when using `npm install` instead of `npm ci`?
2. Why are `.nvmrc` and `.java-version` repository configuration rather than ignored local files?
3. Why are toolchain pins more reliable than documenting versions only in the README?
4. What does “Maven passed, but no tests ran” tell us—and what does it not tell us?
5. Why is a minimal unit-test runner useful before adding broad UI and integration coverage?
6. Why does a lockfile make `npm ci` reproducible even though package manifests use version ranges?
7. What kinds of tests would require changing Vitest's `.test.ts` include pattern?
8. What does removing an external font stylesheet improve, and what tradeoff does relying on a local regular-weight font introduce?
9. Why is “Maven passed with no tests” different from having meaningful backend test coverage?
10. Which repository files should be treated as unrelated dirty state during phase review?

## M1.1 — Database schema hardening

1. Why does a partial unique index enforce one active session without preventing multiple completed sessions?
2. Why is a text-based migration test weaker than executing SQL against PostgreSQL?
3. What migration strategy is needed when the original baseline has already been applied remotely?
4. Which completed-session tests prove that stale `duration_minutes` cannot affect API or dashboard output?
