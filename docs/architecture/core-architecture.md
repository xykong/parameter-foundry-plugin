# Unified Parameters Core Architecture

## 1. Purpose

This document defines the implementation architecture for the safe core
plugin. It turns the product model and semantic parity ADRs into concrete
Jenkins extension points, packages, data flow, and compatibility boundaries.

The architecture follows these decisions:

- [ADR 0002: UI and API Semantic Parity](../decisions/0002-ui-api-semantic-parity.md)
- [ADR 0004: Release Scope and Migration Promise](../decisions/0004-release-scope-and-migration-promise.md)
- [ADR 0005: Canonical Value and Serialization](../decisions/0005-canonical-value-and-serialization.md)
- [ADR 0006: API Input, Errors, and Permissions](../decisions/0006-api-input-errors-and-permissions.md)
- [ADR 0007: Provider Snapshots, Cache, and Failure Policy](../decisions/0007-provider-snapshots-cache-and-failure.md)
- [ADR 0009: Public Model and Pipeline Data Binding](../decisions/0009-public-model-and-pipeline-data-binding.md)
- [ADR 0010: Governed Connection Profiles](../decisions/0010-governed-connection-profiles.md)
- [ADR 0011: Migration Adapter Packaging](../decisions/0011-migration-adapter-packaging.md)
- [ADR 0012: Typed API Parameter Adapters](../decisions/0012-typed-api-parameter-adapters.md)
- [ADR 0013: Definition Validation Lifecycle](../decisions/0013-definition-validation-lifecycle.md)
- [ADR 0014: Technical Preview and GA Gates](../decisions/0014-technical-preview-and-ga-gates.md)

## 2. Architectural Rules

1. All submission entry points call one `ParameterEvaluationService`.
2. Entry adapters decode transport formats but contain no default or
   validation rules.
3. Renderers display an evaluated model but do not decide legal values.
4. Providers return normalized data; they do not create `ParameterValue`.
5. Core configuration cannot execute arbitrary Groovy, JavaScript, HTML, or
   controller file reads.
6. Persisted Job configuration uses stable public model classes and explicit
   model versions.
7. High-risk or domain-specific capabilities use companion plugins.

## 3. Package Layout

```text
io.jenkins.plugins.unifiedparameters
  definition/    public ParameterDefinition implementations
                  and matching ParameterValue classes required by Jenkins
                  Pipeline subtype discovery
  model/         Option, defaults, validation, export, canonical values
  evaluation/    decoder-independent evaluation pipeline
  provider/      Provider SPI, Inline Provider, snapshots and cache
  renderer/      Renderer descriptors and evaluated view models
  validation/    built-in validators and error catalog
  api/           Job action and typed API DTOs
  migration/     migration SPI and records, no source-specific code
  security/      permission checks, redaction and outbound-policy helpers
  telemetry/     bounded metrics and health summaries
```

Package dependencies point inward toward `model` and `evaluation`.
`evaluation` does not depend on Jelly, Stapler request objects, CLI command
objects, or a specific Renderer.

## 4. Public Parameter Types

### 4.1 `1.0`

```text
UnifiedChoiceParameterDefinition
UnifiedMultiChoiceParameterDefinition
UnifiedChoiceParameterValue
UnifiedMultiChoiceParameterValue
```

Both definitions extend Jenkins `ParameterDefinition`. Their
`@DataBoundConstructor` contains only `name`; all other public fields use
`@DataBoundSetter`.

Each definition has a unique `@Symbol`:

- `unifiedChoice`;
- `unifiedMultiChoice`.

The public types remain separate because Jenkins configuration, Pipeline
snippets, environment export, and user documentation are clearer when single
and multiple cardinality are not hidden behind one large definition.

They share immutable internal components:

```text
Option
DefaultSpec
ValidationSpec
RendererSpec
ExportSpec
ParameterModelVersion
```

### 4.2 Persisted `1.0` Fields

`UnifiedChoiceParameterDefinition` persists:

| Field | Required | Notes |
|-------|----------|-------|
| `name` | yes | constructor field |
| `description` | no | plain text |
| `choices` | yes in `1.0` | persisted static `List<Option>` |
| `provider` | alternative in `1.1` | typed dynamic `ProviderSpec`; mutually exclusive with `choices` |
| `renderer` | no | defaults to select |
| `defaultMode` | no | defaults to `none` |
| `defaultValue` | no | valid only with explicit mode |
| `required` | no | defaults to false |
| `environmentExport` | no | typed export configuration |
| `modelVersion` | yes | current persisted-model version |

`UnifiedMultiChoiceParameterDefinition` persists the same fields with:

- a multiple-capable Renderer;
- `defaultValues`;
- min/max selection settings;
- duplicate policy;
- delimited/json/none export mode.
- `standardApiInputMode`, defaulting to repeated form fields.

Public fields are converted into immutable internal `DefaultSpec`,
`ValidationSpec`, `RendererSpec`, and `ExportSpec` objects before evaluation.
Those internal objects are not separately persisted in `1.x`.

`Option` persists:

```text
value
label
description
group
disabled
disabledReason
tags
metadata
selected import hint
```

`metadata` is a bounded string-to-string map in `1.0`; it cannot contain
arbitrary Java objects. `selected` is normalized into Provider default
candidates and is not an independent runtime default.

`UnifiedChoiceParameterValue` persists:

```text
name
canonical String or null
configuration digest
snapshot metadata when applicable
export mode
```

`UnifiedMultiChoiceParameterValue` persists:

```text
name
immutable canonical list
configuration digest
snapshot metadata when applicable
export mode and delimiter when applicable
```

`equals` and `hashCode` follow ADR 0005 business-value equality and do not
include configuration digest, snapshot metadata, or export settings.

Descriptors, constructors, and setters provide immediate authoring validation,
but XStream loading may bypass them. The authoritative
`DefinitionValidationService` from ADR 0013 runs before every render and
evaluation.

### 4.3 Later Types

`unifiedCascade`, `unifiedForm`, and any standalone text/boolean definitions
are later public types. They must reuse the same evaluation service but do not
expand the `1.0` persisted model prematurely.

## 5. Evaluation Pipeline

```text
Transport Adapter
  -> InputDecoder
  -> DecodedInput
  -> DependencyResolver
  -> ProviderResolver
  -> DefaultResolver
  -> Canonicalizer
  -> ParameterValidator
  -> ParameterValueFactory
  -> ExportPolicy
```

The service API is conceptually:

```java
EvaluationResult evaluate(
    UnifiedParameterDefinition definition,
    DecodedInput input,
    EvaluationContext context);
```

`EvaluationResult` contains either:

- canonical value, snapshot metadata, warnings, and export preview; or
- stable structured errors and no `ParameterValue`.

`InputDecoder` uses only the definition's explicit transport mode and raw
request fields. It never queries Provider options or guesses an encoding from
submitted text. This preserves the pipeline order and keeps transport parsing
independent from business validation.

The following adapters call this service:

- browser form submission;
- standard Jenkins REST;
- Typed REST;
- CLI;
- Pipeline `build`;
- remote/webhook/upstream integrations using Jenkins parameter contracts;
- Rebuild.

Any Jenkins-required `createValue(...)` overload delegates to a transport
decoder and then to the service. It does not implement a parallel parser.

## 6. Provider Architecture

### 6.1 SPI

```text
UnifiedParameterProvider
  resolve(ProviderRequest) -> ProviderResult
  descriptor()
```

`ProviderResult` contains normalized options, default candidates, warnings,
and redacted provenance. It cannot return a `ParameterValue`, Jelly fragment,
HTML string, JavaScript, or arbitrary executable object.

Provider descriptors declare:

- stable type ID and Symbol;
- supported parameter families;
- required permission for configuration and health tests;
- whether credentials or outbound network access are used;
- cache and timeout capabilities;
- configuration validation.

Persisted static `choices` are adapted to this SPI through an internal Inline
Provider. There is no public or persisted `InlineProviderSpec` in `1.x`.

### 6.2 Core and Companion Boundary

| Core | Companion |
|------|-----------|
| Inline | Script/Groovy |
| Global/Folder registry | Git refs |
| Managed Config | artifact repository |
| governed REST JSON/XML | container registry |
| derived/cascade | cloud resource |

Companion Providers use the public SPI and remain subject to canonical
validation. Installing a companion increases the attack surface only for
administrators who choose it.

Managed Config reads content through a managed-configuration plugin API and
never accepts a controller file path. Credentialed REST Providers reference
ADR 0010 Connection Profiles rather than persisting URLs and Credentials in a
Job definition.

## 7. Renderer Architecture

`RendererSpec` is persisted configuration. A Renderer implementation receives
an evaluated view model:

```text
parameter name and description
normalized options
canonical/default selection
validation state
snapshot ID
accessible labels and help
```

The browser may search, virtualize, expand groups, and manage focus. It may not
change defaults, accept unknown values, or bypass disabled values.

`1.0` uses Jenkins-compatible progressive enhancement:

- Jelly renders a valid fallback form;
- external JavaScript modules enhance it;
- no inline script or event-handler attributes;
- Jenkins design tokens and compatible components are preferred;
- the plugin is not a standalone SPA.

Third-party Renderer SPI is not enabled until `2.0` because a Renderer can
increase XSS and CSP risk.

## 8. Validator Architecture

Built-in validators are immutable configuration objects evaluated on the
server:

- required;
- known option;
- disabled option;
- min/max selections;
- duplicate policy;
- custom-value regex/length in `1.1`;
- type and cross-field validation for later forms.

Client-side validation is generated from the same configuration for fast
feedback, but the server result is authoritative.

Error codes are registered in one catalog. Extensions may add namespaced
codes but cannot replace core code meanings.

## 9. Typed API Integration

A Job-scoped `Action` exposes `/unified-parameters/api/v1`. It obtains the Job
from the owning action rather than accepting an arbitrary Job path in request
data.

The scheduling coordinator enumerates every current Job
`ParameterDefinition`, selects a registered typed adapter, and refuses the
request atomically if a definition is unsupported or names are duplicated.
Built-in Jenkins Core and companion-adapter boundaries follow ADR 0012.

DTO binding is strict:

- bounded request size;
- unknown fields rejected;
- no class-name polymorphism;
- no direct binding to persisted Jenkins model objects;
- output redaction before serialization.

Exact endpoint behavior is defined by ADR 0006.

## 10. Persistence and Upgrade

Persisted definitions contain a `modelVersion`. New optional fields have
semantic defaults equal to the behavior of the previous version.

Upgrade rules:

- use `readResolve` only for local invariant repair;
- use explicit migrators for semantic changes;
- never reinterpret an old empty/default value silently;
- preserve unknown future extension data only when it is safe and typed;
- configuration round trips must be stable across save, reload, copy, and
  rename.

Detailed lifecycle rules are in
[Configuration and Compatibility Lifecycle](./configuration-lifecycle.md).
Loaded objects are never trusted solely because construction or setters
completed; ADR 0013 stored-definition validation is the final configuration
gate.

## 11. Threading and Lifecycle

- definitions and normalized models are immutable after construction;
- Provider resolution runs outside the Jenkins queue lock;
- timeouts and cancellation do not leave partial cache entries;
- identical Provider requests may be coalesced;
- build scheduling happens only after complete successful evaluation;
- health and migration scans use bounded executors;
- Jenkins shutdown stops new Provider work and cancels non-build preview work.

## 12. Required Technical Spikes

Before GA implementation is frozen, the `0.1 Technical Preview` must execute
the spike matrix in
[ADR 0014](../decisions/0014-technical-preview-and-ga-gates.md), including:

- flat/nested data binding and Job persistence;
- malformed XStream definition validation;
- Pipeline `params` and downstream `build`;
- browser presence and standard REST HTTP 4xx/no-queue behavior;
- CLI and mixed Core/Unified Typed API;
- Rebuild;
- Jelly no-JavaScript/CSP behavior;
- migration adapter reactor packaging.

Job DSL and JCasC receive their own `1.1` pre-implementation spikes.

Failed spikes change the relevant ADR or scope. They do not create
entry-specific semantic exceptions.

## 13. Definition of Architecture Complete

The core architecture is ready for production implementation when:

- public model and persisted fields are reviewed;
- every entry adapter has a named decoder and contract test;
- the SPI inputs and outputs have no transport/UI dependencies;
- permissions and threat controls map to concrete endpoints;
- required spikes pass in Jenkins Test Harness and the real-browser UI Lab;
- configuration upgrade fixtures are committed.
