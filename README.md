# Parameter Foundry

## Introduction

Parameter Foundry is a community-oriented Jenkins plugin project that aims to
provide one secure, typed, extensible parameter model across the Jenkins UI,
Pipeline, REST API, CLI, webhooks, upstream builds, remote triggers, and
Rebuild.

The current development branch implements the roadmap through the `2.0`
foundation: static and governed dynamic choices, reactive dependencies and
Provider snapshots, typed Foundry Forms, migration execution and rollback,
trusted companion extension contracts, reference Renderer and sandboxed Script
Provider companions, built-in workspace/Base64/stash file parameters, source migration
boundaries, and retirement reporting.
Static Extended Choice migrations include an explicit transport compatibility
mode for legacy CLI, repeated REST fields and form JSON submissions.
Multiple-choice browser forms preserve declared option order for legacy environment export;
explicit API inputs retain the caller’s order.
Literal defaults, single selection and custom-delimiter behavior still require
the per-Job checks documented in the [migration boundaries](./docs/guide/migration.md#boundaries).
Git branch migrations have a separate literal-input compatibility mode with
independent dropdown selection and API/CLI defaults.
Version `0.8.296` retains exact static multiple-choice defaults
through `legacyDefaultLiteral`, literal form input and editable historical Rebuild
values and static single transport compatibility. Source/browser verification
is recorded in the testing guide. Empty-token transport preserves the characterized
source contract; per-Job and exact-release gates still apply.
Version `0.9.299` adds an explicit unflagged Active Choices
single transport path and configured-node order. Use
[the observed source contract](./docs/research/active-choices-single-transport-contract.md)
as a migration gate. Version `0.9.301` aligns configured-node
empty-name filtering with source `String.trim()` semantics, preserving Unicode
whitespace and the original name text.
Version `0.10.304` migration previews select this compatibility mode for eligible literal
single choices and preserve their descriptions and distinct UI/API defaults.
Inline empty options remain visible and submit empty strings in this mode; ordinary
choice modes continue to reject empty options.
Versions follow `<major>.<minor>.<commit-count>`, where the last field is the
number of commits on `master`; see [`CHANGELOG.md`](./CHANGELOG.md) for released
changes and `AGENTS.md` for the release rules.

Product and technical design documents are under [`docs/`](./docs/).
The reproducible competitor UI benchmark is under
[`tools/ui-lab/`](./tools/ui-lab/), with generated results in
[`docs/ui-gallery/`](./docs/ui-gallery/).

## Documentation

New to the plugin? Start with the user guide:

| I want to | Read |
|---|---|
| Create my first parameter | [docs/guide/getting-started.md](./docs/guide/getting-started.md) |
| Look up a field, default, or transport | [docs/guide/parameters.md](./docs/guide/parameters.md) |
| Copy a working configuration | [docs/guide/examples.md](./docs/guide/examples.md) |
| Upload and consume a file | [docs/guide/file-parameters.md](./docs/guide/file-parameters.md) |
| Connect a backend | [docs/guide/providers.md](./docs/guide/providers.md) |
| Move off another parameter plugin | [docs/guide/migration.md](./docs/guide/migration.md) |
| Fix an error | [docs/guide/troubleshooting.md](./docs/guide/troubleshooting.md) |

Every configuration field also documents itself inside Jenkins through a help
popup, and [`llms.txt`](./llms.txt) is the machine-readable entry point for AI
assistants. Released changes are listed in [`CHANGELOG.md`](./CHANGELOG.md); the
internal design set lives under [`docs/`](./docs/README.md).

## Development

Requirements:

- JDK 21 for the build; the plugin emits Java 17 bytecode
- Maven 3.9+
- Docker and Google Chrome for the UI Lab

Run the complete verification in the pinned JDK 21 container:

```bash
make verify-container
```

Run a local Jenkins development instance:

```bash
make run
```

Regenerate the competitor UI Gallery:

```bash
make gallery
```

Run the real-browser Parameter Foundry E2E suite:

```bash
make e2e
```

## Issues

Report issues and enhancements in the [Jenkins issue tracker](https://issues.jenkins.io/).

## Contributing

Refer to the Jenkins project
[contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md).

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

Reactive Active Choices single transports can use a governed Provider with an explicit literal
API default and dependency-bound snapshots. REST Providers offer data-only ordinary/cascade
JSON response modes with source-derived UI/API metadata and per-parameter minimum filter
length (`activeChoicesFilterLength`, default 1); see the [Provider guide](./docs/guide/providers.md#rest-source-response-modes). Real-browser and input parity remain separate
[per-Job gates](./docs/research/active-choices-reactive-rest-contract.md).
