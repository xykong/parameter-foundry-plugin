# Configuration and Compatibility Lifecycle

## 1. Compatibility Promise

Parameter Foundry preserves configurations created by an earlier supported
version unless a migration is explicitly reported and tested.

Compatibility applies to Parameter Foundry configuration and build records.
It does not imply compatibility with source-plugin Java classes or XML.

### Product Rename Boundary

The rename to Parameter Foundry
([ADR 0017](../decisions/0017-product-renaming-to-parameter-foundry.md)) moved
every persisted class into `io.jenkins.plugins.parameterfoundry`, so the
promise above starts at the first release published under the new coordinates.

- Job configuration and build records written by a `unified-parameters`
  release name the former classes and cannot be deserialized by Parameter
  Foundry.
- A controller that must keep those records readable migrates the Job XML to
  the new class names, keeps the former plugin installed for historical
  rendering, or archives that history. [ADR 0008](../decisions/0008-rebuild-and-historical-compatibility.md)
  already defines those choices for records that outlive their defining
  plugin.
- Records written under the new coordinates follow the rules below unchanged.

## 2. Persisted Model Version

Every public ParameterDefinition and shared Provider record stores a positive
`modelVersion`.

Rules:

- adding an optional field is allowed only when its default reproduces the
  previous behavior;
- changing canonical value type, empty/default behavior, or export behavior
  requires an explicit migrator and ADR;
- persisted enum names are stable and are not renamed for presentation;
- display labels are localized separately from stored identifiers;
- Renderer changes never rewrite canonical values.

## 3. Upgrade Process

```text
Load persisted object
  -> validate modelVersion
  -> apply ordered structural migrators
  -> validate invariants before every use
  -> expose migration warning if semantics require confirmation
  -> save only during an explicit Jenkins save operation
```

`readResolve` may:

- initialize newly added collections;
- replace impossible `null` containers with immutable empty values;
- map a renamed internal field when semantics are identical.

`readResolve` must not:

- select a new default;
- enable custom values;
- change delimiter/export mode;
- discard unknown configured values;
- contact a Provider or network endpoint.

Constructor and `@DataBoundSetter` validation are not trusted for XStream
input. The pure `DefinitionValidationService` from ADR 0013 runs before
configuration/build rendering, save, API/CLI access, Rebuild, and every
evaluation. An invalid loaded object remains inspectable but cannot resolve a
Provider or schedule a build.

## 4. Compatibility Fixtures

For every released model version, the repository stores sanitized fixtures
for:

- Freestyle Job XML;
- Pipeline-generated Job property XML;
- shared Provider configuration when applicable;
- successful and invalid `ParameterValue` build records;
- copy/rename/reload round trips.

Tests load each old fixture with the new plugin, evaluate representative
inputs, save, reload, and compare canonical behavior.

## 5. Jenkins Baseline

At release time the project declares:

- one minimum Jenkins LTS baseline in `pom.xml`;
- tests against that baseline;
- tests against the current supported LTS line;
- any optional integration-plugin version ranges used by Rebuild, Job DSL,
  JCasC, webhook, or migration tests.

The documentation does not promise “current and previous LTS” without naming
the release-time matrix.

## 6. Deprecation

Public Symbols, API v1 fields, persisted enum identifiers, and stable error
codes are deprecated for at least one minor release before removal.

Removal requires:

- a replacement;
- configuration migration;
- release-note warning;
- compatibility fixture;
- major API version when an external JSON contract changes.

## 7. Unsupported or Corrupt Configuration

The plugin fails closed:

- Job configuration remains loadable where Jenkins permits;
- the parameter displays an actionable configuration error;
- builds using the invalid definition are rejected;
- raw secrets or complete provider payloads are not included in diagnostics;
- the plugin never substitutes a first option merely to keep a build running.

## 8. JCasC and Job DSL

JCasC and Job DSL support use the same public model and stable Symbols as the
UI/Pipeline configuration.

Exported configuration:

- omits implicit defaults;
- references Connection Profiles or Credentials by ID where the owning
  configuration type permits;
- never exports secret material;
- uses stable Provider IDs rather than display names;
- round-trips without semantic drift.
