# ADR 0009: Public Model and Pipeline Data Binding

- Status: Accepted
- Date: August 28, 2026

## Context

The product examples used a concise flat Pipeline DSL, while the initial
architecture described persisted `ProviderSpec`, `DefaultSpec`, and
`RendererSpec` objects. Leaving both shapes public would cause UI, Pipeline,
Job DSL, JCasC, XML, and Snippet Generator output to drift.

## Decision

### Public and Internal Models

The persisted public `1.x` ParameterDefinition model is intentionally concise.
Nested runtime models are internal evaluation objects.

`UnifiedChoiceParameterDefinition`:

```text
name                         constructor field
description
choices                      static List<Option>
provider                     dynamic ProviderSpec, added in 1.1
renderer                     stable renderer ID
defaultMode
defaultValue
required
environmentExport
modelVersion
```

`UnifiedMultiChoiceParameterDefinition`:

```text
name                         constructor field
description
choices                      static List<Option>
provider                     dynamic ProviderSpec, added in 1.1
renderer                     stable renderer ID
defaultMode
defaultValues
required
minSelections
maxSelections
duplicatePolicy
environmentExport
standardApiInputMode
modelVersion
```

Exactly one of `choices` or `provider` is configured. `1.0` supports only
`choices`. The evaluation layer converts `choices` to an internal Inline
Provider and converts flat default/validation/export fields to immutable
internal specs.

### Data Binding

- `@DataBoundConstructor` contains only `name`.
- Every other public field uses `@DataBoundSetter`.
- Setters accept only typed scalar, enum, `Option`, or approved `Describable`
  values.
- `choices` and `provider` are mutually exclusive regardless of setter order.
- Descriptor and constructor/setter validation provide immediate feedback for
  UI, Pipeline, Job DSL, and JCasC.
- XStream/XML can bypass setters; ADR 0013 authoritative stored-definition
  validation enforces the same rule before every use.
- Public enums use stable lowercase IDs in Pipeline/JCasC and stable Java enum
  constants internally.

### Pipeline DSL

Static `1.0` syntax remains:

```groovy
unifiedChoice(
    name: 'TARGETS',
    selectionMode: 'multiple',
    choices: [
        [value: 'android', label: 'Android'],
        [value: 'ios', label: 'iOS']
    ],
    defaultMode: 'explicit',
    defaultValues: ['android', 'ios'],
    renderer: 'checkbox',
    selectedDisplay: 'chips',
    minSelections: 1,
    standardApiInputMode: 'repeated'
)
```

Dynamic `1.1` syntax uses the same definition with a typed nested Provider:

```groovy
unifiedChoice(
    name: 'RELEASE',
    provider: restOptions(
        connection: 'release-catalog',
        path: '/releases',
        valuePath: '$[*].id',
        labelPath: '$[*].name'
    ),
    renderer: 'searchable'
)
```

The static DSL does not persist an `InlineProviderSpec`; `choices` is the
persisted source of truth. The dynamic DSL does not expose credentials, base
URLs, arbitrary class names, or raw Java implementation types.

`choices` is always a typed `List<Option>`. Pipeline/JCasC use maps:

```groovy
choices: [
    [value: 'production'],
    [value: 'staging', label: 'Staging']
]
```

`label` defaults to `value`. A bare `List<String>` shorthand is not part of the
stable `1.x` contract because Jenkins data binding cannot represent both
strings and nested Options with one unambiguous persisted property. A future
shorthand depends on a successful compatibility-preserving binding spike.

### Snippet Generator, Job DSL, and JCasC

- Snippet Generator emits `choices` for static definitions and `provider` for
  dynamic definitions, never both.
- It omits fields equal to semantic defaults.
- It emits `defaultMode: 'explicit'` whenever default values are emitted.
- Job DSL and JCasC use the same public property names and stable IDs.
- XML persists the public fields, not the internal evaluation specs.
- Copy/save/reload must not rewrite a flat static definition into a nested
  Provider form.

### Compatibility

Adding `provider` in `1.1` is additive. Existing `choices` configurations remain
valid and do not migrate structurally.

A future public field alias requires a separate compatibility decision. The
plugin does not support multiple writable aliases for the same persisted
property because setter order would be ambiguous.

## Consequences

- The documented elegant static DSL is the actual persisted model.
- Dynamic Providers extend the same public parameter types without rewriting
  static Jobs.
- Internal architecture remains composable without leaking complexity into
  Jenkins configuration surfaces.
