# ADR 0008: Rebuild and Historical Compatibility

- Status: Accepted
- Date: August 28, 2026

## Context

Unified Parameters promises that migrated user requirements can be expressed
and rebuilt with the new plugin. It does not provide binary compatibility with
source-plugin classes. Historical builds may still contain serialized source
`ParameterValue` objects after the active Job is migrated.

## Decision

### Builds Created by Unified Parameters

Each new build stores:

- the Unified Parameters canonical value;
- parameter-definition configuration digest;
- bounded Provider snapshot metadata;
- the export mode used.

Rebuild pre-populates the historical canonical value using the current
Unified ParameterDefinition and its original Renderer family. Before
scheduling, the value is evaluated against the current definition and current
or authorized snapshot policy.

Multi-choice values are reserialized with the stable machine-value comparator
from ADR 0005. Provider order and historical submission order never change the
Rebuild output.

If a historical value is now unknown, disabled, or stale:

- the UI shows the historical value and a stable validation error;
- the user must choose a valid replacement or cancel;
- Rebuild does not silently bypass validation;
- automated Rebuild receives the same error code.

### Builds Created Before Migration

The migration promise covers active Job configuration and user requirements,
not deserialization of every historical source-plugin Java object.

Migration does not rewrite historical `build.xml` files automatically.
Before source-plugin retirement, the dashboard reports:

- active Job configuration references;
- Pipeline/Job DSL/Jenkinsfile source references;
- queued or running builds;
- historical build records containing source-plugin class names when they can
  be safely detected.

Administrators choose one of:

1. retain the source plugin for historical-record rendering;
2. archive or retain the old controller history separately;
3. accept that old records may have degraded rendering after uninstall;
4. use a separately reviewed, source-specific history converter if one is
   ever provided.

None of those choices blocks functional migration of the active Job.

### Rebuild Integration

`1.0` supports a tested Rebuild plugin version through the normal
`ParameterDefinition` rendering and value-creation contract whenever
possible. The implementation must not:

- patch Rebuild internals at runtime;
- accept an unvalidated historical string;
- depend on browser DOM scraping;
- preserve source-plugin Java classes in new Job configuration.

Before implementation is considered complete, a technical spike must verify:

- how Rebuild selects the current ParameterDefinition;
- how historical custom ParameterValue objects are exposed;
- how the plugin preserves Renderer selection;
- behavior when the Job definition changed after the original build.

If the supported Rebuild version cannot preserve the contract through public
integration points, the project must either contribute an upstream extension
or document a compatibility adapter. It must not weaken validation.

### Compatibility Boundary

Supported:

- migrated active Jobs can be configured, built, automated, and rebuilt using
  only Unified Parameters and any explicitly selected companion Providers;
- old business requirements can be recreated 1:1 or better;
- new Unified Parameters build records remain readable across plugin upgrades.

Not promised:

- unchanged source Job XML;
- unchanged Pipeline `$class` syntax;
- zero-touch migration;
- rendering historical source-plugin build records after uninstall;
- automatic conversion of arbitrary script intent.

## Consequences

- The functional migration promise is strong without implying binary
  compatibility.
- Source-plugin retirement becomes an explicit operational decision backed by
  evidence.
- Historical-record conversion, if ever implemented, is isolated from the
  safe core migration path.
