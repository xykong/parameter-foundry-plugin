# ADR 0021: Source-specific history readers

- Status: Accepted design; implementation pending
- Date: October 13, 2026
- Extends: ADR 0008
- Preserves: ADR 0016

## Context

Replacing current parameter definitions does not remove serialized source values
from build records. Git string values are also capable of appearing in saved CPS
locals. XML aliases do not resolve classes in River binary storage, and an
unconditional XStream compatibility alias can override an installed source class.

A history-only class with the original binary name and serialization contract can
read a characterized source value. However, after exposing that class from the
Foundry loader, dynamically loading the source plugin creates two classes with the
same name. On Jenkins 2.568.3, the native and bridge values then differ in class
identity and equality while Uber retains the bridge class. Dynamic initializers
are filtered to the new plugin loaders; a Foundry startup check does not rerun.
This is an unsupported transition, not a cache problem that can be repaired live.

## Decision

Source-specific readers are reviewed companions inside the single product HPI.
They require explicit, persisted retirement-mode opt-in and a complete controller
restart. Source absence alone does not enable a reader on a fresh installation.
New Jobs continue using Foundry definitions and values; readers neither introduce
source definitions into new configuration nor rewrite historical files in bulk.

The first implementation candidate is the characterized List Git Branches string
value contract. This design does not promise readers for arbitrary source classes.
Extended Choice JSON file delivery and legacy file ownership/storage require
separate contracts and review. The complete migration and retirement objective
still requires every relevant family, producer, consumer and history gate.

## Packaging and initialization

The legacy-named classes must remain outside the initial plugin classpath. A
fixed nested resource holds the reader jar. Initialization verifies its expected
digest, bounded size and exact entry list before extracting it into a fixed,
plugin-managed directory with symlink and replacement checks. User input cannot
select a jar, class name, controller path or code to compile.

The reader retains the characterized UID, inheritance, fields and passive value
behavior. Its rendering resources are reviewed separately. Normal JEP-200
filtering remains enabled; package-wide whitelists, Core patches and arbitrary
reflection are not an implementation path.

Initialize after plugin availability and before persisted configuration is
consumed. Confirm explicit opt-in, genuine source absence, no failed or staged
source installation, and no other exporter of the legacy class. Expose the jar
through the actual Foundry PluginWrapper using the supported API, clear miss
caches through the supported API, and verify resulting class ownership. Failure
must produce an explicit unavailable/error state rather than a compatibility claim.

## Supported lifecycle

| Situation | Reader behavior |
| --- | --- |
| Opt-in off | Do not expose reader classes |
| Opt-in changed during the current JVM | Record restart required; do not switch loaders live |
| Source active at startup | Skip the reader; verify native class ownership |
| Source disabled, failed, staged, or another exporter present | Do not treat this as source absence; report the corresponding unavailable state |
| Opt-in on, source genuinely absent at startup | Verify and activate the reader |
| Native source introduced after activation | Report persistent visible conflict/restart-required state; invalidate compatibility acceptance |
| Native cold reinstall or reader rollback | Complete restart; skip reader and restore native ownership |

After activation, the source plugin may only be staged for the next startup.
`dynamicLoad` and `install-plugin -deploy` are unsupported for reinstalling it.
An AdministrativeMonitor and lifecycle checks can detect conflict; they are
after-the-fact detection, not a universal plugin-install veto. Do not replace the
PluginManager, repair `equals`, or pretend a second initializer/cache clear
unloads a class. The controlled migration and rollback procedures enforce the
restart boundary. This product does not promise safe arbitrary administrator hot
installation of a source plugin after reader activation.

## Release gates

- Actual HPI resource isolation, integrity, extraction and class ownership.
- Active, disabled, failed, absent and staged source states, opt-in changes, and
  Foundry disable/upgrade/downgrade handling on the supported controller versions.
- Native-to-reader complete-restart restoration and reader-to-native rollback,
  including XML, Java, River and a paused synthetic Pipeline.
- Positive and negative cache cases, plus the hot-reinstall counterexample with
  explicit conflict state rather than an assertion of restored class identity.
- Historical parameter rendering and actual Rebuild using the packaged reader.
- Independent security review, complete Maven verification and applicable real
  browser checks before publication or production rollout.

Definition references, generated Jenkinsfiles, queued/running builds, stored
Pipeline source/Replay and reverse dependencies remain independent retirement
gates. A reader does not approve uninstall, execute historical business logic for
verification, or silently expand ADR 0008 into universal binary compatibility.

## Consequences

Historical support remains optional and source-specific while administrators
install one product HPI. Enabling it adds a restart-sensitive class-loading
contract, so controlled retirement and rollback are part of its supported use.
Until an implementation passes these gates, retain the source plugin or use an
explicitly approved history-preservation plan; this accepted design is not a
released capability.
