# Parameter Foundry

![Parameter Foundry — Shape the input. Define the build.](docs/media/hero.png)

<p align="center"><strong>Typed by design · Reactive by configuration · Migrated with evidence</strong></p>

**Typed choices, reactive forms, file uploads, and migration tools for Jenkins.**

Parameter Foundry brings parameter configuration, validation, and value handling
into one plugin. Build searchable lists and selection matrices, resolve options
from governed backends, collect structured input, and deliver uploaded files to
builds. Use the same definitions from Jenkins forms, Pipeline, REST, and CLI,
with explicit compatibility modes when an existing Job must keep its legacy
string behavior.

[Getting started](docs/guide/getting-started.md) ·
[Feature overview](docs/guide/features.md) ·
[Parameter reference](docs/guide/parameters.md) ·
[Examples](docs/guide/examples.md) ·
[Changelog](CHANGELOG.md)

## Project status

The source repository is public. The latest tag is **1.27.386**, while the
**1.28** development line prepares official Jenkins hosting. Community hosting
and update-center distribution remain pending
([hosting request #5412](https://github.com/jenkins-infra/repository-permissions-updater/issues/5412)).
See [installation and prerequisites](docs/guide/installation.md) for the supported
runtime, source-build workflow, and the distinction between tagged and development
versions. Historical release entries do not imply availability through the plugin manager.

The main parameter, Provider, file-upload, and migration capabilities are
implemented. Source-plugin compatibility is deliberately scoped: compare the
actual Job, callers, consumers, and retained build history before retiring a
plugin. The opt-in **File Parameters history reader remains a development
capability**, separate from the built-in Foundry file parameters.

## What you can build

| Capability | What it provides |
|---|---|
| Single and multiple choices | Dropdowns, searchable selectors, lists, radio buttons, checkboxes, selected-value chips, defaults, bounds, disabled options, and validated custom single values |
| Selection matrices | Inline or Provider-backed rows and columns, selectable cells, per-row rules, row/column shortcuts, select-all, invert, and clear |
| Dynamic and reactive options | Global or Folder-scoped Providers, dependency-driven refresh, snapshots, caching, explicit fallbacks, and health diagnostics |
| Backend integrations | Static lists, Jenkins nodes/labels, dependency mappings, matrix composition, managed configuration, REST, Git refs, and reviewed catalog scripts |
| Typed forms | Twelve field types, nested objects/arrays, validation, sections, grids, tabs, conditional layouts, map/JSON output, and controlled environment export |
| Literal reference content | Sanitized HTML notices with an independent string value; no source script execution |
| File uploads | Workspace, Base64, or stash delivery, configurable limits, Pipeline wrappers, build-owned retention, downloads, and Rebuild reuse |
| Automation | Declarative/Scripted Pipeline, Job DSL, JCasC, standard Jenkins REST/CLI, a typed JSON API, and downstream builds |
| Legacy compatibility | Explicit Active Choices, Extended Choice, and List Git Branches transports; independent UI/API defaults; preserved legacy strings and editable Rebuild values |
| Migration and retirement | Inventory, classified previews, generated snippets, reviewed apply/clone, atomic per-Job batches, rollback, A/B evidence, read-only simulation, and retirement reports |

See the [complete feature overview](docs/guide/features.md) for configuration
symbols, supported sources, security controls, and capability boundaries.

### See a matrix become a build input

![Real Jenkins matrix interaction: clear, select the staging column, combine a row, select all, then invert.](docs/media/matrix-demo.gif)

Five captured states from the real plugin: **0 → 5 → 7 → 15 → 0 selected cells**.
Column shortcuts and bulk actions update the submitted value, not just the display.
[Static alternative](docs/media/matrix.png) · [Capture provenance](docs/media/README.md)

<details>
<summary>Explore the real interface: choices and typed forms</summary>

![Search, custom choice and disabled radio options in Jenkins](docs/media/choices.png)

![Typed deployment form with conditional settings](docs/media/typed-form.png)

</details>

### Follow the value, not just the widget

![Inputs flow through type-specific decoding and shared validation to a build; governed Providers supply options.](docs/media/evaluation-flow.svg)

[Transport contracts](docs/guide/parameters.md#input-decoding) explain where ordinary
typed values and explicit legacy strings differ. File bytes use their own bounded
upload lifecycle.

Screenshots come from isolated Jenkins instances running the plugin with
synthetic fixtures. Browse the [real-plugin UI gallery](docs/ui-gallery/README.md)
for forms, file uploads, migration tools, and desktop/mobile examples.

## Build and install

Start with the [installation guide](docs/guide/installation.md), which covers
prerequisites, building a chosen version, dependencies, and local deployment.
Then follow [Getting started](docs/guide/getting-started.md) to configure a Job.

Contributors can use `make verify` for local validation or `make verify-container`
for the pinned build environment. The [development guide](docs/development-environment.md)
is the maintained reference for toolchain versions and build commands.

## A Pipeline example

```groovy
pipeline {
  agent any
  parameters {
    foundryChoice(
      name: 'CHANNEL',
      choices: [[value: 'staging', label: 'Staging'],
                [value: 'production', label: 'Production']],
      renderer: 'radio',
      defaultMode: 'explicit',
      defaultValue: 'staging'
    )
    foundryChoice(
      name: 'TARGETS',
      selectionMode: 'multiple',
      choices: [[value: 'web'], [value: 'android'], [value: 'ios']],
      renderer: 'checkbox',
      selectedDisplay: 'chips',
      defaultMode: 'explicit',
      defaultValues: ['web'],
      minSelections: 1,
      environmentExport: 'json'
    )
  }
  stages {
    stage('Inspect request') {
      steps {
        echo "Channel: ${params.CHANNEL}"
        echo "Targets: ${params.TARGETS.join(', ')}"
      }
    }
  }
}
```

For dynamic options, define a Provider in **Manage Jenkins → System → Parameter
Foundry** or a Folder, then reference its `providerId`. Add `dependsOn` when its
options depend on other parameters. See [Providers](docs/guide/providers.md).

For file input, use `foundryFile` and
[`withFoundryFileParameter`](docs/guide/file-parameters.md). For structured
input, use [`foundryForm`](docs/guide/parameters.md#foundryform).

## Values and compatibility

Ordinary single choices produce a string or `null`; multiple choices produce a
sorted canonical list; forms produce a map or JSON string. Environment export
is configured separately. Binary uploads use multipart transport and are not
accepted by the typed JSON API.

Compatibility modes preserve selected source behaviors rather than forcing all
legacy values into this ordinary model. Active Choices and Git compatibility can
retain Core strings; static Extended Choice multiple parameters can opt into
`valueTransport: 'core-string'`. Use
[`foundryParameterText`](docs/guide/pipeline-parameter-text.md) when Pipeline needs
the saved legacy literal without environment overrides or list reconstruction.

Migration previews never execute source scripts. Jenkinsfile-declared parameters
must be changed in their generating source. Successful migration of current
configuration does not establish that a source plugin can safely be uninstalled;
review [migration](docs/guide/migration.md) and
[historical values](docs/guide/history-compatibility.md) first.

## Security and governance

- Shared server-side evaluation validates parameter input before scheduling.
- Connection Profiles govern network destinations, credentials, paths, queries,
  timeouts, redirects, and response limits. Provider resolution is budgeted.
- Job-supplied scripts do not execute in the parameter core. The bundled script
  companion uses reviewed catalog entries and Jenkins Script Security.
- Reference HTML is sanitized. File destinations and upload sizes are validated;
  arbitrary controller paths are not accepted as input.
- Whole-response REST modes retain TLS verification and require explicit,
  finite declarations for special failure choices and redirect destinations.

See [the threat model](docs/security/threat-model.md) and
[best practices](docs/guide/best-practices.md). Do not put credentials, private
service addresses, or real Job XML into public issue reports.

## Documentation

| Task | Guide |
|---|---|
| Understand the complete capability set | [Feature overview](docs/guide/features.md) |
| Configure a first parameter | [Getting started](docs/guide/getting-started.md) |
| Look up fields, defaults, or transports | [Parameter reference](docs/guide/parameters.md) |
| Copy configurations | [Cookbook](docs/guide/examples.md) |
| Connect a backend or build a cascade | [Providers](docs/guide/providers.md) |
| Use entire HTTP responses as choices | [Whole-response REST](docs/guide/raw-rest-responses.md) |
| Upload and consume files | [File parameters](docs/guide/file-parameters.md) |
| Read exact saved strings in Pipeline | [Saved parameter text](docs/guide/pipeline-parameter-text.md) |
| Replace older plugins safely | [Migration](docs/guide/migration.md) · [History compatibility](docs/guide/history-compatibility.md) |
| Diagnose a problem | [Troubleshooting](docs/guide/troubleshooting.md) |

Every Jenkins configuration field includes inline help. The
[user guide](docs/guide/README.md) is the user entry point,
[docs/README.md](docs/README.md) indexes contributor and design material, and
[llms.txt](llms.txt) provides a concise index for AI assistants.

## Development and contributions

```bash
make run                 # local Jenkins development instance
make verify              # Maven tests and configured quality checks
make migration-fixtures  # validate migration fixtures
make e2e                 # real-browser Foundry checks
make source-canary       # source-plugin compatibility checks
make pct                 # Plugin Compatibility Tester
make gallery             # regenerate the real-plugin UI gallery
```

Browser/container workflows have additional prerequisites; see
[Development environment](docs/development-environment.md),
[UI Lab](tools/ui-lab/README.md), and
[acceptance cases](docs/testing/acceptance-cases.md). Platform-dependent tests may
skip when their required filesystem or network capabilities are unavailable;
report those skips separately from passing tests.

Use [GitHub Issues](https://github.com/xykong/parameter-foundry-plugin/issues)
for bugs and feature requests while Jenkins community hosting is pending. Include
the plugin/Jenkins versions, input transport, a synthetic reproduction, and
expected versus actual behavior. Contributions should include relevant checks
and update the guides, inline help, and acceptance coverage when behavior changes.

## License

[MIT](LICENSE.md).
