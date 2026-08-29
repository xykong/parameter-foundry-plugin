# ADR 0011: Migration Adapter Packaging

- Status: Accepted
- Date: August 28, 2026

## Context

`1.0` commits to an Extended Choice static migration adapter, while the safe
core must not depend on source plugins or carry their classes after migration.
The adapter packaging therefore cannot remain conditional or conceptual.

## Decision

### Repository Structure

Before migration-adapter implementation, this repository becomes a Maven
reactor:

```text
pom.xml                                  aggregator
plugin/                                  unified-parameters HPI
migration-extended-choice/               adapter HPI
```

Future adapters add sibling HPI modules.

The core artifact coordinates remain:

```text
io.jenkins.plugins:unified-parameters
```

The first adapter uses:

```text
io.jenkins.plugins:unified-parameters-migration-extended-choice
```

### Dependencies

The adapter HPI has:

- a required dependency on `unified-parameters`;
- a required dependency on the supported Extended Choice plugin while the
  adapter is installed;
- no dependency from the core plugin back to either source plugin or adapter.

Administrators install the adapter only during discovery/migration. After
active Jobs and generated sources are migrated, the adapter and source plugin
may be retired according to ADR 0008.

### Migration SPI

The core HPI exposes a versioned migration SPI package containing:

- source adapter descriptor;
- normalized intent types;
- classification and decision types;
- target-definition builder;
- verification fixture contract;
- redacted migration diagnostics.

Adapters do not receive Job write or build-scheduling capability. They return
normalized intent to the core coordinator.

Breaking SPI changes require a new major SPI version and adapter compatibility
range.

### Supported Versions

Each adapter declares exact source-plugin version ranges and fixture schemas.
Unsupported versions are reported as unsupported and never guessed.

The Complete Replacement gate means:

- 100% success for supported A-class fixtures and version ranges;
- no silent partial conversion;
- deterministic unsupported classification outside that range.

It does not mean every historical source-plugin version is automatically A
class.

### Release Process

- Core and adapters may release independently.
- The `1.0` product milestone requires an installable compatible Extended
  Choice adapter release, but the adapter is not bundled into the core HPI.
- Adapter update-center metadata documents source and core compatibility.
- Adapter security scanning includes both source-plugin parsing and migration
  output.

## Consequences

- The safe core remains free of source-plugin dependencies.
- Adapter lifecycle and supported versions are explicit.
- The repository requires a one-time Maven module move before adapter code is
  added.
