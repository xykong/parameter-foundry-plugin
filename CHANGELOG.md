# Changelog

All notable changes to Parameter Foundry are recorded in this file. Sections
follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and releases are
listed newest first.

Versions follow `<major>.<minor>.<commit-count>`: the second field changes when
the release scope changes, and the third field is the number of commits on
`master` at the released commit (`git rev-list --count master`). A `-SNAPSHOT`
suffix marks a build that is deployed but not tagged.

## Unreleased

The product identity moves from the working name "Unified Parameters" to
**Parameter Foundry** ([ADR 0017](docs/decisions/0017-product-renaming-to-parameter-foundry.md)).
This is a breaking change for every Pipeline, Job DSL, REST, CLI, and
configuration reference to the plugin.

### Changed

- The plugin short name and Maven `artifactId` are `parameter-foundry`, the
  repository is `jenkinsci/parameter-foundry-plugin`, and the Java package root
  is `io.jenkins.plugins.parameterfoundry`.
- The Pipeline and Job DSL symbols are `foundryChoice` and `foundryForm`. Every
  supporting symbol follows the same namespace (`foundryStaticProvider`,
  `foundryMatrixProvider`, `foundryFormField`, and so on).
- The typed API and migration endpoints moved to `/parameter-foundry/api/v1/…`
  and `/parameter-foundry-replacement-simulation/report`.
- The CLI commands are `parameter-foundry-migration` and
  `parameter-foundry-migration-verify`.
- Stable error codes now use the `PARAMETER_FOUNDRY_` prefix: a client that
  matches `UNIFIED_PARAMETER_RATE_LIMITED` must match
  `PARAMETER_FOUNDRY_RATE_LIMITED` instead.
- The REST presence marker is `__parameter_foundry_presence`.

### Compatibility

- Stored Job configuration, Pipeline scripts, and the Jenkins update center
  entry must be migrated to the new short name and symbols. No aliases are
  published for the former `unified-*` names.
- Build records written by an earlier `unified-parameters` release keep their
  historical class names; see
  [ADR 0017](docs/decisions/0017-product-renaming-to-parameter-foundry.md) for
  the consequences.
- Requirement, acceptance, and issue identifiers (`UP-…`) and the internal
  `up-` browser styling prefix are unchanged, so historical verification
  records keep resolving.
- Released artifacts and dated verification records keep the names they were
  published and measured under. `dist/unified-parameters-0.3.233.hpi` stays the
  reproducible `0.3.233` artifact.

## 0.3.233 - 2026-09-21

Released as a controlled custom plugin for the Jenkins image: the HPI is built
from this commit (`git rev-list --count master` = 233) with SHA-256 recorded in
[`docs/testing/production-release-2026-09-21.md`](docs/testing/production-release-2026-09-21.md),
so an image rebuild no longer depends on a private snapshot in
`JENKINS_HOME/plugins`.

### Added

- A single choice can use `renderer: list` as well, which renders the options as a list box (the
  equivalent of the Active Choices `PT_SINGLE_SELECT` control with a visible item count). Required
  parameters have no None entry.
- Selected values stay out of the way: the chip list scrolls inside a bounded box and collapses to a
  `Show selected (n)` toggle once a selection is long, so filling a table no longer pushes it off
  screen. The toggle is four languages and remembers whether the reader expanded it.
- A choice table fills in one click: the row and column captions are buttons that fill or clear the
  whole line, and a toolbar selects every cell, inverts the selection, or clears the table. Every
  shortcut keeps the chips, the summary, and the submitted values in sync, and a row that carries a
  rank constraint fills its rank instead of breaking the rule.
- Matrix Provider (`unifiedMatrixProvider`): a table that crosses two governed lists, each axis
  either an inline list or another Provider, with `{row}`/`{column}` cell templates, axis captions,
  and a per-row selection constraint (`any`, `at-most-one`, `exactly-one`) enforced in the browser
  and again before the build is scheduled. This is the declarative replacement for a parameter
  pair that meant "these registries on these agents".
- Dependency mapping Provider (`unifiedMappingProvider`): a declarative dependency value → option
  list mapping for parameters whose options depend on another parameter's value, with a default
  list for every other value. It executes nothing, so an Active Choices cascade that branches on a
  parameter converts without a script engine.
- The Active Choices migration recognises `if (DEP == "value") { return [...] } else { return [...] }`
  and proposes the mapping with the literal option lists it found, instead of a manual redesign.
- Atomic batch migration: the Job page previews every recognized parameter with
  the exact configuration diff, applies them in one Job write, and rolls the
  whole batch back when the Job changed afterwards.
- `unified-parameters-migration`, a CLI that scans the controller, previews a
  Job with its decisions and diff, and applies one atomic batch. The inventory
  is permission-scoped and reports the Jobs it skipped (permissions, no
  parameter definitions) or could not scan, and a target that fails validation
  is listed as blocked instead of aborting the preview.
- `unified-parameters-migration-verify` and `MigrationEvidenceService`, which
  compare the newest build before and after a migration (canonical value,
  environment export, sibling inputs) and store the result as migration
  evidence.
- A repository-side blind-spot report on the retirement dashboard: Jobs that
  read their Pipeline from SCM, plus the multibranch and organization folders
  that generate them, with an `unscanned` flag in the JSON report.
- Read-only replacement simulation
  (`…/unified-parameters-replacement-simulation/report` and
  `<job>/unified-parameters/simulation`): it rebuilds every source parameter's
  target, replays the values production builds recorded, and compares the
  canonical value and environment export, so a lossless replacement can be
  proven for every Job before the wave starts.
- `environmentExportOrder` (`canonical`, the default, or `submitted`) for
  multiple-choice parameters. The canonical value stays the sorted set; the
  setting decides how it is written into the environment variable, so a migrated
  Job whose source control exported the submitted order keeps the same string.
- The replacement simulation separates a dropped option from build history:
  `EQUIVALENT_WITH_HISTORY` marks parameters whose older builds used values the
  source control no longer offers either, so only a real loss stays `MISMATCH`.
- The simulation now verifies every parameter, not only the ones with build
  history: it compares the migrated option set with the source's current option
  set (`OPTION_SET_MISMATCH` when an option is lost), checks that the source
  default stays selectable (`EQUIVALENT_DEFAULT_ONLY` for a parameter that never
  built), and reports the target snippet and the value contract per parameter.
- Active Choices script bodies are read through the model object and the
  script-security wrapper, so literal-list scripts convert to inline choices in
  production instead of being treated as redesign work.
- A stale source default that no recorded build ever carried is decided as
  `DECIDED_NO_DEFAULT`; the option list stays identical and the unused default is
  dropped.
- Migration and retirement surfaces in English, Chinese, Japanese, and Korean,
  including adapter reasons, decisions, fact labels, and verification cases.
- `MigrationMessageCoverageTest`, which fails the build when a referenced
  message key is missing or a locale bundle is incomplete.
- In-plugin documentation: every configuration field now ships a help popup, so
  the form explains itself while you are filling it in.
- A user guide covering getting started, the parameter reference, a cookbook,
  Providers, migration, best practices, design goals, and troubleshooting.
- `llms.txt`, a machine-readable entry point for AI assistants, and a
  documentation index in `README.md` and `docs/README.md`.
- `ConfigurationHelpCoverageTest`, which fails the build when a configuration
  field is added without help.

### Changed

- Migration previews and records report the source default, the resolved sort
  order, the translated filter, and the blocked parameters of the Job.
- The plugin About page now describes the parameter types, highlights, and a
  quick example instead of a single sentence.

### Fixed

- A multiple-choice parameter with `minSelections` (or a table with a per-row constraint) and no
  default could not render its build form: resolving the empty default was rejected as if it were a
  submission. Constraints now apply to submitted values, so the form loads and the user can fill it
  in.
- A single-choice parameter with `renderer: radio` now renders a native radio
  group even when a shared Provider supplies the options or a dependency
  refreshes them. Optional parameters keep an explicit None entry, required ones
  do not, and a group above 60 options says why it falls back to the searchable
  list.
- `renderer: select` is a dropdown again instead of a multi-row list; only
  `renderer: searchable` (or an option set that exceeds the render limits) lists
  matching options.
- A batch preview could not show the configuration diff of a parameter with
  many options: the serialized target exceeded the inline diff limit. The diff
  now keeps element values on one line, so a production-sized choice list is
  reviewable, and the comparison is bounded by size instead of by line count.
- A choice table now honours `selectedDisplay`: it shows the selected chips and a
  selection summary, and removing a chip clears the matching cell.
- Enter in a searchable choice field no longer submits the build form when the
  typed custom value is invalid; the candidate and its validation message stay in
  place so it can be corrected.

## 0.3.143 - 2026-09-11

### Changed

- Adopted the `<major>.<minor>.<commit-count>` version scheme and recorded it in
  `AGENTS.md`.
- Added this changelog and required a version section for every release.

## 0.2.16 - 2026-09-11

### Changed

- One value type covers both cardinalities: `UnifiedChoiceParameterValue`
  persists `selectionMode`, the scalar `value` for single selection, and the
  canonical `values` set for multiple selection. The exported `value` property
  keeps its per-mode shape and environment export follows the mode.
- Pipeline `build` steps can address the value by its short class name for both
  modes again.

### Removed

- `UnifiedMultiChoiceParameterValue`. Earlier runs still load, but they no
  longer expose the choice values they recorded.

## 0.2.15 - 2026-09-11

### Removed

- The deprecated `UnifiedMultiChoiceParameterDefinition`, its Job DSL alias
  `unifiedMultiChoice`, and its resources. Every stored configuration now uses
  `unifiedChoice` with `selectionMode`.

## 0.2.14 - 2026-09-11

### Added

- Matrix cells are selectable values: resolution, defaults, validation, and the
  no-JavaScript fallback read the flat options plus one entry per cell.
- Disabled matrix cells expose their configured reason to assistive technology.

### Fixed

- Matrix cell values are no longer rejected as unknown values.
- Duplicate cell values and cell values colliding with a flat option are
  configuration errors instead of silently overriding each other.

## 0.2.13 - 2026-09-11

### Fixed

- A `unifiedChoice` in `selectionMode: multiple` no longer fails the resolve
  endpoint with `500`, so the build form loads its options.

## 0.2.12 - 2026-09-11

### Added

- `unifiedChoice` is the single choice entry point: `selectionMode` selects the
  cardinality, and renderer, selected display, transports, validation, and
  environment export follow it.

### Changed

- Provider runtime state is evicted periodically instead of growing for the
  controller lifetime.

### Fixed

- Unlimited maximum selections survive a reload; a definition stored before the
  limit existed no longer becomes "at most zero".

## 0.2.10 - 2026-09-10

### Fixed

- A single-choice search list sizes itself from the rendered option count
  instead of a forced minimum height.

## 0.2.9 - 2026-09-10

### Changed

- Client-side parameter errors are fully localized.

## 0.2.8 - 2026-09-09

### Changed

- Unified Form and configuration import messages are localized.

## 0.2.7 - 2026-09-09

### Changed

- Client-side parameter errors are localized.

## 0.2.6 - 2026-09-09

### Changed

- Parameter titles and descriptions align on one row and option controls indent
  below the description.

## 0.2.5 - 2026-09-09

### Fixed

- The table renderer parses its matrix JSON safely instead of falling back to a
  checkbox list.

## 0.2.4 - 2026-09-09

### Added

- The choice table renderer is validated for multiple selection.

## 0.2.3 - 2026-09-09

### Added

- Static choice matrices render as accessible tables.

## 0.2.2 - 2026-09-09

### Changed

- The multiple-choice control and its selected-value display are configured
  separately (`selectedDisplay: none|chips`).

## 0.2.1 - 2026-09-09

### Fixed

- Multi-choice option labels resolve correctly after localization, and custom
  value validation stays next to the control that failed.

## 0.2 - 2026-09-09

### Added

- Choice matrix model foundation.

### Fixed

- Localized parameter messages render correctly.

## 0.1 - 2026-08-28 to 2026-09-04

Technical preview foundation.

### Added

- `unifiedChoice`, `unifiedMultiChoice`, `unifiedForm`, the shared evaluation
  service, and definition validation.
- Renderers: select, searchable, radio, list, checkbox, chips, schema, and the
  accessible choice grid.
- Providers: static options, managed configuration, REST (JSON and XML), the
  sandboxed Script Provider, and governed Git refs, behind connection profiles,
  rate limits, caching, and failure policies.
- Migration adapters and previews for Active Choices, Extended Choice, List Git
  Branches, HTML, JSON Editor, REST List, Multiselect, Editable Choice,
  Extensible Choice, Custom Checkbox, and Validating String, plus a stashed-file
  boundary that keeps native multipart uploads intact.
- Single-HPI packaging that carries the Script Provider, custom Renderer, and
  Git refs capabilities.
- Verification harness: format compatibility matrix, Docker end-to-end suite,
  UI gallery, Plugin Compatibility Tester runs, and source-plugin canaries.
