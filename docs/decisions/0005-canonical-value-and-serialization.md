# ADR 0005: Canonical Value and Serialization

- Status: Accepted
- Date: August 28, 2026

## Context

UI/API parity requires one deterministic interpretation of absent values,
explicit empties, defaults, duplicate input, selection ordering, custom
values, disabled values, and environment export.

## Decision

### Internal Evaluation Envelope

Input decoding produces an envelope before defaults are resolved:

```text
DecodedInput
  presence: ABSENT | PRESENT
  rawValues: ordered list of strings or a typed JSON value
  source: UI | STANDARD_REST | TYPED_REST | CLI | PIPELINE | REBUILD
```

`ABSENT` and `PRESENT` with an empty value are never interchangeable.

After evaluation, canonical values are:

| Parameter | Canonical value |
|-----------|-----------------|
| single choice | non-empty `String`, or `null` when optional and explicitly empty |
| multiple choice | immutable selection set exposed as a deterministic `List<String>`; no selection is an empty list |
| boolean | `Boolean` |
| form | immutable typed map validated against the stored schema version |

`ParameterValue.getValue()`, Pipeline `params`, the build record, and export
policies are all derived from this canonical value.

### Absence and Empty Rules

| Input condition | Single choice | Multiple choice |
|-----------------|---------------|-----------------|
| parameter absent | run `DefaultResolver` | run `DefaultResolver` |
| parameter explicitly present without a value | canonical `null` if optional; otherwise required error | canonical empty list if optional; otherwise required/min error |
| explicit supported value(s) | validate and canonicalize | validate membership and canonicalize |

Browser forms and form-encoded API clients use a reserved repeated metadata
field:

```text
__unified_parameters_presence=TARGETS
```

The browser emits one marker for every rendered Unified parameter. Presence is
decoded as:

1. one or more value fields means `PRESENT`;
2. a presence marker without a value field means `PRESENT` with no value;
3. neither value nor marker means `ABSENT`.

The marker is never included in `rawValues`, so a selected checkbox cannot
accidentally become `["", "android"]`. Duplicate or unknown marker values are
malformed input. `__unified_parameters_presence` is reserved: a Job containing
a conflicting parameter definition is configuration-invalid until renamed.
Typed JSON, CLI, Pipeline, and Rebuild carry presence structurally and do not
use the form marker.

For an optional single-choice HTML `<select>`, a no-JavaScript blank option may
submit one empty transport sentinel. It is decoded as “present without a
value” only when the matching presence marker exists. Empty machine values
remain prohibited. Multi-choice `repeated` mode never accepts an empty value
field.

Choice option machine values:

- must be non-empty;
- are compared as exact Java strings;
- are not trimmed, case-folded, or Unicode-normalized;
- must not contain NUL or other prohibited control characters;
- are unique by exact value within a resolved option set.

Search may normalize labels for matching, but search normalization never
changes submitted machine values.

### Default Precedence

`DefaultSpec.mode` is one of:

- `none`;
- `first`;
- `explicit`;
- `provider`.

The precedence is deterministic:

1. `explicit` uses the configured value membership.
2. `provider` uses Provider default candidates in Provider option order.
3. `first` uses the first enabled Provider option.
4. `none` produces `null` for single choice or an empty list for multiple
   choice.

An Option-level `selected` field is an input/import hint only. Provider
normalization converts those hints into Provider default candidates. It is not
an independent runtime default and is ignored unless mode is `provider`.

Disabled values cannot be defaults. Inline stale defaults block configuration
save. Dynamic stale defaults fail closed unless a later Provider policy
explicitly authorizes a bounded last-known-good snapshot.

A required Reactive parameter may intentionally use `defaultMode=none`. This is
valid configuration, but omission-based callers such as Jenkins CLI must submit
the parameter explicitly. The configuration UI shows a warning for this
combination. If omission should be supported, the definition must use a
resolvable `first` or `explicit` default strategy. When Jenkins CLI cannot
obtain a required default, Jenkins Core returns its native `No default value for
the parameter ...` error before queue scheduling; no build is created.

### Multiple-Selection Ordering and Duplicates

`unifiedMultiChoice` models an unordered selection set. Order is not part of
its business value in `1.x`.

The `List<String>` exposed to Pipeline and stored in a build is a deterministic
serialization view:

- every selected machine value is sorted with Java `String.compareTo`;
- renderer DOM order and click order never affect the result.

The comparator is locale-independent UTF-16 code-unit order and is stable
across Provider reorder, UI reorder, API submission order, and Rebuild.
Labels, groups, and Provider option order never affect Pipeline `params`,
environment export, or build-record serialization.

Jobs for which order is business-significant must use a future ordered
collection/Form field rather than `unifiedMultiChoice`.

Duplicate submitted values are rejected by default with
`UNIFIED_PARAMETER_DUPLICATE_VALUE`. A definition may opt into `deduplicate`;
deduplication occurs before deterministic serialization.

Migration adapters may classify a source as A only when source order is not
business-significant or the source already uses the same deterministic order.
An order-sensitive source is B or C.

### Standard API Decoding

Input decoding never depends on Provider options. A multiple-choice definition
stores one explicit `standardApiInputMode`:

| Mode | Decoding |
|------|----------|
| `repeated` | default; every repeated value field is one value, and one field is a one-value list |
| `json-array` | exactly one field containing a JSON string array |
| `delimited` | exactly one field split by the configured compatibility delimiter |

An empty multi-selection uses the presence marker and no value field. An empty
multi-choice value field is invalid because empty choice machine values are
prohibited. A missing value field and missing marker remains `ABSENT`.

There is no exact-known-value, JSON-looking-value, or delimiter guessing.
`InputDecoder` can therefore run before `ProviderResolver`. Provider resolution
and `Canonicalizer` subsequently validate decoded values against the option
set.

Clients that require arrays without per-parameter compatibility settings use
the Typed API.

### Environment Export

| Mode | Behavior |
|------|----------|
| `delimited` | join canonical values with the configured delimiter |
| `json` | compact UTF-8 JSON Array/Object |
| `none` | do not add an environment variable |

`delimited` is the compatibility default for multiple choice. Configuration
save rejects any option containing the delimiter, CR, LF, or NUL. Dynamic
Providers perform the same check at resolution time and fail closed.

An optional single choice with canonical `null` is not exported. An empty
multiple choice exports an empty string in `delimited` mode and `[]` in `json`
mode.

### Build Record

The build stores:

- canonical value;
- parameter-definition configuration digest;
- Provider snapshot metadata when applicable;
- export mode used for that build.

It does not store credentials, authorization headers, or complete sensitive
Provider responses.

### Equality and Provenance

`ParameterValue.equals` and `hashCode` represent business-value equality:

- single choice compares parameter name and canonical String/null;
- multiple choice compares parameter name and selected membership, ignoring
  serialized list order;
- configuration digest, snapshot metadata, export mode, labels, and
  provenance are not equality fields.

Tests that need complete record equality use a separate evaluation-record
comparison.

Snapshot metadata provides traceability, not replay of the complete Provider
response. A build records Provider/configuration/option-set digests, selected
machine values, generation time, fallback status, and redacted provenance.
The product does not claim that an administrator can reconstruct every
historical unselected option after Provider data changes.

## Consequences

- Renderers and entry adapters can vary without changing business semantics.
- Equal multi-choice membership always produces byte-identical List/JSON and
  delimited export order.
- Legacy delimiter support remains explicit and testable.
- The value contract is strict enough to generate one parity fixture for every
  entry point.
