# Changelog

All notable changes to Unified Parameters are recorded in this file. Sections
follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and releases are
listed newest first.

Versions follow `<major>.<minor>.<commit-count>`: the second field changes when
the release scope changes, and the third field is the number of commits on
`master` at the released commit (`git rev-list --count master`). A `-SNAPSHOT`
suffix marks a build that is deployed but not tagged.

## Unreleased

Current version: `0.3.155-SNAPSHOT`.

### Added

- In-plugin documentation: every configuration field now ships a help popup, so
  the form explains itself while you are filling it in.
- A user guide covering getting started, the parameter reference, a cookbook,
  Providers, migration, best practices, design goals, and troubleshooting.
- `llms.txt`, a machine-readable entry point for AI assistants, and a
  documentation index in `README.md` and `docs/README.md`.
- `ConfigurationHelpCoverageTest`, which fails the build when a configuration
  field is added without help.

### Changed

- The plugin About page now describes the parameter types, highlights, and a
  quick example instead of a single sentence.

### Fixed

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
