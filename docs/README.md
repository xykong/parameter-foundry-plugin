# Parameter Foundry Documentation

## User guide

Start here if you are configuring or using the plugin:

- [User guide index](./guide/README.md)
- [Getting started](./guide/getting-started.md)
- [Parameter reference](./guide/parameters.md)
- [Saved parameter text in Pipeline](./guide/pipeline-parameter-text.md)
- [Cookbook](./guide/examples.md)
- [Providers](./guide/providers.md)
- [Migration](./guide/migration.md)
- [Historical parameter values](./guide/history-compatibility.md)
- [Best practices](./guide/best-practices.md)
- [Design goals](./guide/design-goals.md)
- [Troubleshooting](./guide/troubleshooting.md)

The plugin also documents every configuration field inside Jenkins: each field in
a `config.jelly` ships a `help-<field>.html` popup, and
`ConfigurationHelpCoverageTest` fails the build when a field is added without
help.

## Product

- [Product specification](./product/product-spec.md)
- [Functional design](./product/functional-design.md)
- [`1.0` UI interaction specification](./product/ui-interaction-spec.md)
- [`1.0` interactive UI prototype](./ui-prototype/index.html)

## Research

- [Competitor and ecosystem analysis](./research/competitive-analysis.md)
- [Active Choices compatibility validation summary](./research/active-choices-compatibility-summary.md)
- [Active Choices single transport contract and migration gaps](./research/active-choices-single-transport-contract.md)
- [Extended Choice static transport contract and remaining gaps](./research/extended-choice-static-transport-contract.md)
- [Naming and brand evaluation](./research/naming-and-brand-evaluation.md)
- [Real Jenkins UI Gallery](./ui-gallery/README.md)

## Decisions

- [0001: Product name and identity](./decisions/0001-product-name-and-identity.md)
- [0002: UI and API semantic parity](./decisions/0002-ui-api-semantic-parity.md)
- [0003: Repository separation](./decisions/0003-repository-separation.md)
- [0004: Release scope and migration promise](./decisions/0004-release-scope-and-migration-promise.md)
- [0005: Canonical value and serialization](./decisions/0005-canonical-value-and-serialization.md)
- [0006: API input, errors, and permissions](./decisions/0006-api-input-errors-and-permissions.md)
- [0007: Provider snapshots, cache, and failure policy](./decisions/0007-provider-snapshots-cache-and-failure.md)
- [0008: Rebuild and historical compatibility](./decisions/0008-rebuild-and-historical-compatibility.md)
- [0009: Public model and Pipeline data binding](./decisions/0009-public-model-and-pipeline-data-binding.md)
- [0010: Governed Connection Profiles](./decisions/0010-governed-connection-profiles.md)
- [0011: Migration adapter packaging](./decisions/0011-migration-adapter-packaging.md)
- [0012: Typed API parameter adapters](./decisions/0012-typed-api-parameter-adapters.md)
- [0013: Definition validation lifecycle](./decisions/0013-definition-validation-lifecycle.md)
- [0014: Technical preview and GA gates](./decisions/0014-technical-preview-and-ga-gates.md)
- [0015: Native file transport boundary](./decisions/0015-native-file-transport-boundary.md)
- [0016: Single HPI product packaging](./decisions/0016-single-hpi-product-packaging.md)
- [0017: Product renaming to Parameter Foundry](./decisions/0017-product-renaming-to-parameter-foundry.md)
- [0018: Public repository hygiene and the private lab overlay](./decisions/0018-public-hygiene-and-private-lab-overlay.md)
- [0019: Release publication mechanism](./decisions/0019-release-publication-mechanism.md)

- [0020: Built-in file parameters](./decisions/0020-built-in-file-parameters.md)
- [0021: Source-specific history readers](./decisions/0021-source-specific-history-readers.md)

- [File upload user guide](./guide/file-parameters.md)

## Architecture

- [Core architecture](./architecture/core-architecture.md)
- [Configuration and compatibility lifecycle](./architecture/configuration-lifecycle.md)
- [Migration Assistant architecture](./architecture/migration-assistant.md)
- [Companion extension boundary](./architecture/companion-extension-boundary.md)

## Security

- [Threat model](./security/threat-model.md)
- [Companion plugin review checklist](./security/companion-review-checklist.md)
- [October 9 file upload security review](./security/file-upload-review-2026-10-09.md)

## Migration

- [Migration design fixtures](./migration/README.md)

## Testing

- [October 10 literal Active Choices preview verification](./testing/literal-active-preview-verification-2026-10-10.md)
- [October 10 Active Choices single compatibility verification](./testing/active-choices-single-verification-2026-10-10.md)
- [October 10 release 0.10.304 verification](./testing/release-0.10.304-verification-2026-10-10.md)
- [October 10 Active Choices empty inline option verification](./testing/active-choices-empty-options-verification-2026-10-10.md)
- [October 10 Extended Choice single transport verification](./testing/extended-choice-single-verification-2026-10-10.md)
- [October 10 Extended Choice literal form verification](./testing/extended-choice-form-verification-2026-10-10.md)
- [October 10 Extended Choice default compatibility verification](./testing/extended-choice-default-verification-2026-10-10.md)
- [October 9 unified file delivery modes verification](./testing/file-delivery-modes-verification-2026-10-09.md)
- [Reactive Active Choices REST source contract](./research/active-choices-reactive-rest-contract.md)
- [October 10 Extended Choice browser form order verification](./testing/extended-choice-form-order-verification-2026-10-10.md)
- [October 9 Extended Choice transport verification](./testing/extended-choice-transport-verification-2026-10-09.md)

- [October 9 built-in file parameters verification](./testing/file-parameters-verification-2026-10-09.md)

- [Parity test matrix](./testing/parity-test-matrix.md)
- [Requirement release and test manifest](./testing/requirements.yaml)
- [Performance and accessibility test plan](./testing/performance-accessibility-plan.md)
- [Real-service parameter compatibility matrix](./testing/real-service-compatibility-matrix.md)
- [`0.1` Technical Preview verification report](./testing/technical-preview-report.md)
- [September 4 source-plugin Docker canary](./testing/source-plugin-docker-canary-2026-09-04.md)
- [September 4 Plugin Compatibility Tester verification](./testing/pct-verification-2026-09-04.md)
- [September 12 source-plugin Docker canary](./testing/source-plugin-docker-canary-2026-09-12.md)
- [September 12 Plugin Compatibility Tester verification](./testing/pct-verification-2026-09-12.md)
- [September 22 source-plugin Docker canary](./testing/source-plugin-docker-canary-2026-09-22.md)
- [September 22 Plugin Compatibility Tester verification](./testing/pct-verification-2026-09-22.md)
- [`1.0` Static Choice Foundation implementation status](./testing/1.0-implementation-status.md)
- [`1.1` Governed Provider Platform implementation status](./testing/1.1-implementation-status.md)
- [`1.2` Reactive and Cascade implementation status](./testing/1.2-implementation-status.md)
- [`2.0` Complete Requirement Coverage implementation status](./testing/2.0-implementation-status.md)

## History

- [Project origin and migrated design history](./history/project-origin.md)

## Development

- [Development environment](./development-environment.md)
