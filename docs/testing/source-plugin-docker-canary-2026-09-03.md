# Source Plugin Docker Canary

- Verification date: September 3, 2026 (UTC)
- Jenkins: `jenkins/jenkins:2.568.2-lts-jdk21`
- Target commit: `fd6662f`
- Scope: synthetic Docker Controller only; no production Job, credential, or
  source-plugin configuration was used

## Command

```bash
make source-canary
```

Detailed redacted output:
[`source-plugin-canary-2026-09-03.json`](./results/source-plugin-canary-2026-09-03.json).

## Passed Checks

- The required source plugins and the single Unified Parameters HPI loaded and
  were active.
- The reference Renderer and Script Provider companion checks passed.
- Active Choices and List Git Branches migration previews passed without
  executing source scripts or copying sensitive source values.
- The List Git Branches preview emitted the `unifiedGitRefProvider` skeleton
  with `refType: 'branch'`.
- Extended Choice clone/apply, audit, Standard REST, webhook-style REST,
  Typed REST, CLI, Pipeline, invalid-input no-queue, Rebuild, rollback, and
  native Stashed File multipart checks passed.
- No unexpected Controller `SEVERE` errors were recorded.

## Boundary

This closes the repeatable local Docker canary for the tested synthetic
fixtures. It does not approve the reference companions for Jenkins community
release, prove organization-specific `ORG_BRANCH` behavior, or authorize
production Job changes.

The source fixture bundle still reports known security advisories for legacy
source plugins. Those advisories are source-environment risks and do not
constitute approval of those plugin versions for production use.
