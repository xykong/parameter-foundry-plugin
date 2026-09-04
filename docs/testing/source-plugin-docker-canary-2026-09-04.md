# Source Plugin Docker Canary

- Verification date: September 4, 2026
- Jenkins: `jenkins/jenkins:2.568.2-lts-jdk21`
- Target commit: `fe4d66f`
- Scope: synthetic Docker Controller only; no production Job, credential, or
  source-plugin configuration was used

## Command

```bash
make source-canary
```

Detailed redacted output:
[`source-plugin-canary-2026-09-04.json`](./results/source-plugin-canary-2026-09-04.json).

## Passed Checks

- One `unified-parameters` HPI loaded with the core, migration adapters,
  accessible Renderer, Script Provider, and Git refs Provider.
- Active Choices and List Git Branches migration previews passed without
  executing source scripts or copying sensitive source values.
- The built-in `accessible-choice-grid`, `sandbox-groovy`, and
  `unifiedGitRefProvider` capabilities passed their resolve/build checks.
- Extended Choice clone/apply, audit, Standard REST, webhook-style REST,
  Typed REST, CLI, Pipeline, invalid-input no-queue, Rebuild, rollback, and
  native Stashed File multipart checks passed.
- No unexpected Controller `SEVERE` errors were recorded.

## Boundary

This closes the repeatable local Docker canary for the single-HPI build and
synthetic fixtures. It does not approve production migration, prove
organization-specific `ORG_BRANCH` behavior, or replace manual accessibility
and source-plugin compatibility validation.
