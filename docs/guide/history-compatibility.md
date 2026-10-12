# Historical Git parameter values

The unreleased history reader is an optional retirement companion for the
characterized List Git Branches string value. It is disabled by default and does
not migrate parameter definitions or Jenkinsfiles. Extended Choice and file
history require separate compatibility work.

## Controlled activation

Complete the source-specific definition, generating-source, queued/running-build,
history/Replay and reverse-dependency checks first. Preserve a tested rollback.
Enabling this setting does not approve uninstall.

In **Manage Jenkins → System → Parameter Foundry feature switches**, select
**Enable retired Git parameter history reader** (`legacyGitHistoryEnabled`). Save
and perform a complete Jenkins restart after retiring the source plugin. Changing
the setting never switches classloaders in the current JVM. Configuration as Code
changes also require the setting to be persisted before the activating restart.

Only Jenkins 2.528.3 and 2.568.3 are initial validation targets. Other Core versions
or incompatible loaders fail unavailable. The companion uses a narrowly reviewed
Beta/internal Core API exception, documented in [ADR 0021](../decisions/0021-source-specific-history-readers.md).
It is not a stable general class-loading SPI.

## State and rollback

| State | Meaning |
| --- | --- |
| DISABLED | No reader classes are exposed |
| NATIVE | The installed source plugin owns the historical class |
| SOURCE_UNAVAILABLE | Disabled or failed source state cannot be treated as absence |
| STAGED_SOURCE | A source archive/disable marker is present without a usable source wrapper |
| OTHER_EXPORTER | Another loader already exposes the class |
| ACTIVE | Explicit startup activation and ownership checks passed |
| RESTART_REQUIRED | Configuration or staged source requires a complete restart |
| CONFLICT | A source/exporter appeared after activation; compatibility acceptance is invalid |
| ERROR | Resource, storage, Core or ownership verification failed |

Unavailable states appear through a Jenkins administrative monitor. Conflict
checking is after-the-fact detection; it does not intercept every installation
entry point or make hot installation safe. While active, do not dynamically
reinstall the old source plugin or use `install-plugin -deploy`. Stage rollback
for the next startup and restart completely. A live cache clear cannot unload an
existing class or restore class identity.

The companion verifies a fixed packaged resource and retains it in a fixed
plugin-managed location. There is no user-supplied jar, class name or controller
path. It reads historical values without bulk-rewriting build files; new Jobs use
Foundry definitions and values. Old Pipeline symbols/source and arbitrary Replay
are independent gates, even when value deserialization works.

This page describes the unreleased implementation. Actual-HPI source-state,
cold-restore/rollback, JEP-200, historical UI and security acceptance remain release
requirements; do not infer readiness from the configuration checkbox alone.
