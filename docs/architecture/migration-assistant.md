# Migration Assistant Architecture

## 1. Product Promise

Migration is defined as moving a user's business requirement to Unified
Parameters with equivalent or better observable behavior.

It is not binary compatibility, unchanged XML, unchanged Pipeline syntax, or
automatic understanding of arbitrary scripts. The normative wording is in
[ADR 0004](../decisions/0004-release-scope-and-migration-promise.md).

## 2. Components

```text
Migration Scanner
  -> Source Adapter
  -> Normalized Intent
  -> Classification and Decisions
  -> Target Definition Generator
  -> Preview and Verification Fixture
  -> Apply Coordinator
  -> Migration Record and Retirement Scanner
```

The core plugin owns the framework, normalized intent, UI/API, migration
records, and apply safety. Source-specific adapters are isolated so they do
not expand the safe core runtime.

## 3. Adapter Packaging

Source adapters ship inside the single `unified-parameters` HPI. They remain
runtime-optional by source class and are controlled by the global migration
adapter feature switch; administrators do not need to install a separate
adapter HPI.

An adapter:

- declares supported source plugin IDs and version ranges;
- uses public source APIs where available;
- may inspect live source objects while the source plugin is installed;
- must not read arbitrary controller paths;
- returns normalized intent, not a pre-created target Java object;
- is versioned with the Unified Parameters HPI when source configuration
  changes;
- cannot schedule builds or modify Jobs directly.

The former sibling-HPI packaging is retained as historical context in
[`ADR 0011`](../decisions/0011-migration-adapter-packaging.md); the active
packaging decision is
[`ADR 0016`](../decisions/0016-single-hpi-product-packaging.md).

## 4. Normalized Intent

```yaml
source:
  plugin: extended-choice-parameter
  version: ...
  job: folder/job
  parameter: TARGETS
intent:
  family: choice
  cardinality: multiple
  options:
    source: inline
  renderer: checkbox
  defaults:
    mode: explicit
  validation:
    required: true
  export:
    mode: delimited
evidence:
  configurationDigest: sha256:...
  adapterVersion: ...
```

Normalized intent records the business meaning and evidence needed for a
decision. It does not copy secrets or full source scripts into reports.

## 5. Classification

| Class | Meaning | Required output |
|-------|---------|-----------------|
| A | deterministic equivalent mapping | complete target definition and verification fixture |
| B | equivalent after explicit semantic choice | target alternatives, impact explanation, selected decision |
| C | safe target capability exists but source code intent needs refactoring | generated Provider/Form skeleton, required user inputs, test plan |
| D | source mechanism is unsafe or intent cannot be proven | business-intent questions, safe redesign options, no automatic apply |

Class C/D is not a product capability gap when Unified Parameters can express
the business requirement. It means only that arbitrary source implementation
code cannot be converted without understanding its intent.

## 6. Release Capabilities

| Release | Migration capability |
|---------|----------------------|
| `1.0` | discover Extended Choice static definitions, classify, preview semantic differences, generate target Pipeline/Job configuration and verification fixture |
| `1.1` | clone Job, apply static Job changes with backup, rollback and audit; never modify SCM sources |
| `1.2` | dynamic Provider/cascade adapters and generated refactoring skeletons |
| `2.0` | remaining source adapters and retirement dashboard |

## 7. Generated-Job Rules

- SCM Jenkinsfiles receive a patch/snippet; the plugin never pushes to the
  repository.
- Job DSL receives generated target DSL.
- Multibranch child Jobs are report-only; the generating Jenkinsfile or
  organization configuration must change.
- The assistant detects when a future `properties()` call is likely to
  overwrite a migrated runtime Job property.
- In-place apply is limited to administrator-selected, non-generated Jobs.

## 8. Preview and Semantic Diff

Every preview shows:

- source intent and target capability;
- renderer and Provider mapping;
- default/empty/duplicate/order changes;
- standard API and environment export changes;
- security improvements;
- required companion Providers;
- configuration digest and adapter version;
- verification cases and unresolved decisions.

Diff presentation is redacted by default. Privileged recovery data is stored
separately from ordinary UI reports.

## 9. Apply Safety

Apply uses compare-before-write:

1. re-read Job configuration;
2. compare it with the preview digest;
3. abort with `UNIFIED_PARAMETER_CONFIGURATION_CHANGED` on drift;
4. write a plugin-owned protected backup;
5. apply through Jenkins configuration/save APIs;
6. reload and evaluate generated fixtures;
7. write an immutable migration record.

Migration records contain:

- source/target digests;
- adapter and framework versions;
- actor and time;
- decisions;
- result and verification summary;
- backup reference and retention expiration.

Backups may contain sensitive source configuration. They use controller-only
file permissions, are excluded from telemetry, have administrator-configured
retention, and are not returned by normal REST responses.

## 10. Rollback and Concurrency

Rollback:

- verifies the current target digest before restoring;
- refuses to overwrite later manual edits;
- records a new audit event;
- does not reinstall or uninstall plugins automatically;
- does not rewrite historical build records.

Batch migration:

- requires `Jenkins.ADMINISTER`;
- uses bounded batches and resumable records;
- stops after a configured failure threshold;
- never holds a global Jenkins lock while running verification;
- excludes queued/running Jobs unless explicitly approved.

## 11. Verification Fixture

Each adapter generates the applicable cases:

- explicit value;
- default and absent input;
- explicit empty input;
- invalid/disabled/duplicate value;
- standard REST;
- Typed REST;
- CLI;
- Pipeline `build`;
- Rebuild;
- environment export;
- Provider success/error for dynamic migrations.

The fixture compares business behavior, not source Java object equality.

## 12. Retirement

The retirement scanner reports independently:

- active Job configuration references;
- generated source references;
- queued/running builds;
- optional integration-plugin dependencies;
- historical source class references where safely detectable.

“Functionally migrated” means active requirements run on Unified Parameters.
“Safe to uninstall source plugin” is a separate administrator decision
governed by ADR 0008.
