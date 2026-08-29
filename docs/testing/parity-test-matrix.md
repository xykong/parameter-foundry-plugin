# Parity Test Matrix

## 1. Purpose

This document is the executable definition of “every Jenkins entry point.”
It separates configuration surfaces, submission adapters, and semantic test
dimensions so release gates do not depend on an ambiguous numeric count.

Transport-specific wire-format cases are defined in
[`Format Compatibility Test Matrix`](./format-test-matrix.md).

## 2. Configuration Surfaces

| Surface | `1.0` | Later |
|---------|-------|-------|
| Freestyle Job configuration | required | |
| Declarative Pipeline parameters | required | |
| Scripted Pipeline `properties` | required | |
| Job copy/rename/reload | required | |
| Job DSL | | `1.1` |
| JCasC shared Provider configuration | | `1.1` |
| Multibranch generated properties | compatibility profile | expanded with migration adapters |

Configuration tests compare the resulting persisted definition and semantic
evaluation, not textual XML ordering.

## 3. Submission Adapters

| Adapter | Test requirement |
|---------|------------------|
| Build with Parameters browser page | Jenkins Test Harness and real browser |
| standard `buildWithParameters` REST | form presence marker, single, repeated, explicit empty, compatibility encoding, HTTP status and queue invariant |
| Typed REST `/api/v1/build` | mixed supported Core/Unified definitions, typed values, unsupported-definition 409 |
| Jenkins CLI | documented single/multiple encoding and errors |
| Pipeline `build` step | typed/custom ParameterValue and compatibility string |
| upstream trigger compatibility profile | standard ParameterValue contract |
| webhook compatibility profile | standard Jenkins parameter endpoint behavior |
| remote build trigger compatibility profile | token/auth plus normal validation |
| Rebuild compatibility profile | current Renderer, historical value, revalidation |

Every applicable adapter calls the same evaluation fixtures. Optional
third-party integration profiles declare the exact plugin version tested.

## 4. Semantic Dimensions

Each parameter type runs:

- explicit valid value;
- absent input with `none`, `first`, and explicit defaults;
- explicit empty input;
- selected/empty/absent presence-marker forms, optional single-select blank
  sentinel, and malformed marker metadata;
- required error;
- unknown value;
- disabled value;
- duplicate input;
- minimum and maximum selections;
- multiple-choice submission-order variants with equal membership;
- deterministic serialized order within one snapshot;
- Renderer change with unchanged canonical value;
- option label change with unchanged machine value;
- delimiter conflict;
- configuration reload/copy/rename;
- malformed XStream-loaded definition before every entry point;
- Rebuild valid and stale historical value.

Dynamic Provider features add:

- success, empty, malformed, error, timeout, cancellation;
- fresh, expired, and cross-Job snapshot;
- last-known-good within and beyond maximum staleness;
- dependency change and cycle;
- configuration digest change during submission;
- cache hit, miss, invalidation, and scope isolation.
- unique-dependency request storms, bounded queue, 429/Retry-After, and
  validate/build reserved capacity.

## 5. Assertions

For one fixture, applicable adapters compare:

- success or stable error code;
- canonical value and value class;
- Pipeline `params`;
- environment export;
- business-value equality and separately compared build-record provenance;
- queue scheduling/no-scheduling result;
- Rebuild pre-population;
- Provider snapshot metadata;
- redacted diagnostics.

Snapshot assertions prove ownership, digests, age and fallback traceability.
They do not require replaying the complete historical option set.

Standard endpoint validation assertions run on the minimum Jenkins baseline
and release-target LTS and compare both HTTP status family and unchanged queue
length.

Visual Renderer output is tested separately and is not used as a canonical
value oracle.

## 6. Requirements Traceability

Each numbered requirement appears in a machine-readable manifest:

```yaml
requirement: UP-SEM-004
release: "1.0"
tests:
  - CanonicalEmptyValueContractTest
  - StandardRestExplicitEmptyTest
  - BrowserEmptyMultiChoiceTest
```

A release gate fails when:

- a committed requirement has no test;
- a required adapter is marked skipped without an approved issue;
- two adapters produce different canonical results;
- a validation failure schedules a build.

The manifest, rather than prose count, defines completeness.

## 7. Test Layers

### Unit

- decoders;
- default resolver;
- canonicalizer;
- validators;
- export policies;
- cache keys and snapshot binding;
- error catalog.

### Jenkins Test Harness

- data binding and Symbols;
- Job save/reload/copy;
- REST and permissions;
- Pipeline `params` and downstream build;
- CLI;
- queue scheduling;
- configuration migration fixtures.

### Real-Browser UI Lab

- Jenkins configuration and build pages;
- keyboard and accessibility;
- no-JavaScript fallback;
- themes and responsive layout;
- virtualization;
- Rebuild integration;
- target UI Gallery.

### Compatibility

- minimum Jenkins baseline;
- release-time current supported LTS;
- declared Rebuild/Job DSL/JCasC/integration-plugin versions;
- Plugin Compatibility Tester.

## 8. Security Matrix

Every endpoint runs:

- anonymous, READ-only, BUILD, CONFIGURE, and ADMINISTER authorization cases as
  applicable;
- missing/valid crumb cases;
- API-token authentication;
- oversized/malformed request;
- XSS payloads in display fields;
- snapshot replay and cross-scope access;
- diagnostics redaction.

Provider and migration security cases follow
[the threat model](../security/threat-model.md).

## 9. Release Reporting

CI publishes:

- requirement coverage;
- adapter/semantic matrix result;
- skipped compatibility profiles;
- performance benchmark;
- accessibility report;
- target UI Gallery manifest;
- security scan summaries.

No requirement is declared complete solely because its unit tests pass.
