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
The next snapshot also accepts empty static option lists in this multiple-choice
compatibility mode, preserving literal defaults and the source configuration's
empty-list CLI behavior through `legacyEmptyOptionsOmitCli`.
Identical duplicate options in legacy lists and checkboxes keep separate selection
states and repeated submitted values; canonical values remain a set.
Literal defaults, single selection and custom-delimiter behavior still require
the per-Job checks documented in the [migration boundaries](./docs/guide/migration.md#boundaries).
Git branch migrations have a separate literal-input compatibility mode with
independent dropdown selection and API/CLI defaults.
Git Providers also support `sortOrder: none` for unsorted legacy ref categories;
verify full versus short ref values and actual dropdown order for each migration.
Git `NONE` selection keeps multi-row lists unselected while preserving the separate
API default; explicitly configured searchable Git lists also retain their row count.
Version `0.14.320` adds [`foundryParameterText`](./docs/guide/pipeline-parameter-text.md)
for Pipeline consumers that need saved legacy parameter strings without environment
overrides or reconstruction from canonical lists.
The step also accepts an explicit `fallbackIfMissing` literal for builds without
an accessible saved value; it does not evaluate parameter defaults or Providers.
Version `0.8.296` retains exact static multiple-choice defaults
through `legacyDefaultLiteral`, literal form input and editable historical Rebuild
values and static single transport compatibility. Source/browser verification
is recorded in the testing guide. Empty-token transport preserves the characterized
source contract; per-Job and exact-release gates still apply.
Unreleased multiple Active Choices string transport preserves raw CLI/form values,
independent API defaults and Core string history. Explicit source regex/no-filter and retain-valid refresh policies are under
verification. HTML reference migration retains a separate gate; see the
[parameter reference](./docs/guide/parameters.md).
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
| Use a whole HTTP response as one choice | [docs/guide/raw-rest-responses.md](./docs/guide/raw-rest-responses.md) |
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

Version `0.12.314` adds reactive Active Choices single transports using a governed Provider with an explicit literal
API default and dependency-bound snapshots. Whole-response REST choices support finite
administrator-declared TLS rejection keys while retaining default certificate validation;
an explicit `rawStatusPolicy` can also preserve reviewed nonredirect response bodies
without changing the default HTTP failure mappings. Separately reviewed finite
redirect destinations can opt in to same-origin following with a shared deadline
and final-request failure selectors;
see [whole-response REST choices](docs/guide/raw-rest-responses.md).
REST Providers offer data-only ordinary/cascade
JSON response modes with source-derived UI/API metadata and per-parameter minimum filter
length (`activeChoicesFilterLength`, default 1); see the [Provider guide](./docs/guide/providers.md#rest-source-response-modes). Real-browser and input parity remain separate
[per-Job gates](./docs/research/active-choices-reactive-rest-contract.md).

Version `0.13.316` fixes reviewed data-only Active Choices mappings: they use a plain dropdown and reset to the new list default on dependency changes, while keeping an independent literal API default. See [reactive Providers](./docs/guide/providers.md#reactive-providers).

Choice list renderers accept `visibleItemCount` (0 for automatic sizing, 1–1000 for an explicit row count). It affects display height only, applies with and without JavaScript, and does not limit options or selections. Extended Choice multiple-list migration preserves positive configured row counts. Legacy zero-row sizing needs separate UI verification.

Literal presentation references use `foundryReference`; see [parameter types](docs/guide/parameters.md).

History retirement controls introduced in 0.21.349 are described in the [historical parameter guide](docs/guide/history-compatibility.md); production activation requires source-specific retirement checks. Unreleased File history controls and their remaining workflow gates are documented there as well.

Static Extended Choice multiple migrations may set `valueTransport: 'core-string'` to save an actual Jenkins `StringParameterValue`. Pipeline `params`, build API values and environment exports retain the source-compatible string. Form strings preserve order, spaces and empty tokens; CLI and repeated fields retain the original trimming and unavailable-value filtering rules. This avoids rewriting scripts that consume the old string directly. The default `typed` transport keeps multiple values as a list. Core string transport requires static `extended-choice` multiple compatibility with `delimited` environment export; Provider-backed choices keep typed transport.
