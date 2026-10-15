# Changelog

All notable changes to Parameter Foundry are recorded in this file. Sections
follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and releases are
listed newest first.

Versions follow `<major>.<minor>.<commit-count>`: the second field changes when
the release scope changes, and the third field is the number of commits on
`master` at the released commit (`git rev-list --count master`). A `-SNAPSHOT`
suffix marks a build that is deployed but not tagged.

## Unreleased

### Added

- Add an opt-in, restart-bound reader for completed native Base64/Stashed File history, with a separate history byte limit and administrative status.

### Fixed

- Preserve literal Extended Choice option descriptions as labels during migration, and require governed conversion for dynamic description sources.
- Clarify that Rebuild can reuse retained Foundry uploads, while older file types and delivery mode changes require a replacement.

## 0.24.375 - 2026-10-15

### Added

- Support explicit finite TLS rejection fingerprints with fixed whole-response REST choices while preserving default TLS verification.
- Add an optional, explicitly declared choice for unresolved DNS in whole-response REST mode; resolver diagnostics and policy refusals remain private.

### Fixed

- Freeze raw REST request configuration before asynchronous evaluation and include proxy, timeout and response limits in Connection Profile digests; existing caches and submission snapshots may require fresh resolution.
- Include DNS evaluation time in whole-response HTTP outcome budgets without promising hard DNS cancellation.

## 0.23.372 - 2026-10-15

### Added

- Add a whole-response REST mode with exact UTF-8 values, ordered trailing choices, explicit classified failure choices and uncached source recovery.

### Fixed

- Bind restricted whole-response HTTP connections to the checked DNS addresses while retaining the original Host and TLS name.

## 0.22.370 - 2026-10-15

### Added

- Preserve native string build values for static Extended Choice multiple migrations with opt-in `valueTransport: core-string`, including Pipeline params, build APIs, environment exports and Rebuild.

## 0.21.349 - 2026-10-13

The HPI is `dist/parameter-foundry-0.21.349.hpi` (SHA-256
`9f7c8c6036f258f36ee4cc39695adff571a03ea99d55fd95b355664fbc44154d`, 685,532 bytes).

### Added

- Add a separately enabled historical Extended Choice string reader for Jobs with migrated current definitions.
- Add an opt-in historical Git string value reader with full-restart activation, source ownership checks and administrative status reporting.

### Fixed

- Preserve pre-migration Git string values when copying historical defaults for Rebuild, without changing the configured Job default.
- Preserve pre-migration Extended Choice string values in editable single and multiple Rebuild forms without changing configured defaults.
- Exclude sensitive historical values from ordinary choice default copies.

## 0.19.340 - 2026-10-13

The HPI is `dist/parameter-foundry-0.19.340.hpi` (SHA-256
`501dbaa2d1c77a08ef7136279879bdf821eb0c72bcef9e48ffa97531065a69ee`, 661,022 bytes).

### Added

- Add sanitized literal HTML references with independent native string transports.
- Add explicit Active Choices source-regex, no-filter and retain-valid dependency refresh policies while preserving existing defaults.
- Add multiple Active Choices string transports with independent API defaults and exact saved string history. Explicit typed arrays remain validated; ordinary multiple parameters keep their existing export rules.

## 0.18.335 - 2026-10-12

The HPI is `dist/parameter-foundry-0.18.335.hpi` (SHA-256
`729b575e632838370ed36e79e0bb54a2c29a63697a964e7a798cac95e726ce06`, 652,340 bytes).

### Added

- Git refs Providers support `sortOrder: none` for the unsorted Java HashSet order used by a List Git Branches ref category.

### Fixed

- List Git Branches migration previews no longer translate `SortMode.NONE` into alphabetical order.
- Git compatibility lists with `gitSelectionMode: none` and multiple visible rows retain an empty UI selection while keeping the API default unchanged.

## 0.17.331 - 2026-10-12

The HPI is `dist/parameter-foundry-0.17.331.hpi` (SHA-256
`d0dadd7aaedb7a76df1794eab0fbcfb07561062de63a54f91f1f7a683bab8d73`, 651,229 bytes).

### Added

- `foundryParameterText` accepts an explicit literal fallback for builds without an accessible saved value, preserving consumer defaults without evaluating parameter definitions.

## 0.16.327 - 2026-10-12

The HPI is `dist/parameter-foundry-0.16.327.hpi` (SHA-256
`d5645196c51de921c6915d6fe8dd4e7fa959b1b06e0bf97033919b248f02a393`, 650,819 bytes).

### Added

- Choice lists support explicit visible row counts, including empty lists and pages without JavaScript; Extended Choice multiple-list migration retains positive configured row counts.

### Fixed

- Extended Choice multiple-choice compatibility accepts empty option lists and preserves the CLI difference between missing options and a comma-only option configuration.
- Identical repeated options in Extended Choice lists and checkboxes retain independent selection and submit their repeated values in declared order.

## 0.14.320 - 2026-10-11

The HPI is `dist/parameter-foundry-0.14.320.hpi` (SHA-256
`2d59c23b781522f00d3571f8e4d97c1525e62d4c59ec0d38c18216aa6f24f3e3`, 648,123 bytes).

### Added

- `foundryParameterText` reads supported saved parameter strings from the current Pipeline build, preserving legacy literals independently of canonical choices and environment overrides.

## 0.13.316 - 2026-10-11

The HPI is `dist/parameter-foundry-0.13.316.hpi` (SHA-256
`615da3f63ae1cb31b5c617148972ddd4b7fce9bea10a229e287ebb36758452d1`, 644,094 bytes).

### Fixed

- Active Choices single choices backed by dependency mappings reselect the new list default when a parent changes, even if the previous value remains valid.

## 0.12.314 - 2026-10-11

The HPI is `dist/parameter-foundry-0.12.314.hpi` (SHA-256
`e17c278564cab2baf498477b823bf69ef9b3e9c57e65323241d9726c31af2692`, 644,061 bytes). Full implementation regression passes 402 tests.

- Preserve each reviewed Active Choices REST dropdown's minimum filter length, including migration snippets and rebuild configuration.

### Added

- Reactive Active Choices single string compatibility with governed Providers, explicit literal API defaults and dependency-bound snapshots.
- Data-only REST JSON response modes for ordinary/reactive Active Choices single dropdowns, with source-derived labels, flags, independent API defaults and regular-expression filtering.

## 0.11.306 - 2026-10-10

The HPI is `dist/parameter-foundry-0.11.306.hpi` (SHA-256
`c74801bc12df7e8f5eb3f93d9883cf354a87cfe6a0b47fc880b0b2f21e10e16f`, 622,202 bytes). Full regression passes 381 tests.

### Fixed

- Extended Choice multiple-choice browser forms preserve declared option order in submitted environment export instead of sorting form inputs alphabetically.

## 0.10.304 - 2026-10-10

The HPI is `dist/parameter-foundry-0.10.304.hpi` (SHA-256
`b586c0690922f35d23298eb14abe8d340dc0919fad6672dc20e772203de8a6e5`, 621,971 bytes). Full regression passes 380 tests; exact release browser gates pass.

### Added

- Active Choices single compatibility preserves inline empty options as selectable empty strings, including migration previews, UI defaults and explicit typed inputs.

### Fixed

- Migration previews for unflagged, static Active Choices single selections
  preserve Core string values, first API defaults, dropdown/radio UI defaults
  and parameter descriptions.

## 0.9.301 - 2026-10-10

The HPI is `dist/parameter-foundry-0.9.301.hpi` (SHA-256
`e66ac6bf6415ad5eb29595b43f0e2240b5b3b29ab6471939593da6eb86e8d24b`,
620,907 bytes). Full regression passes 375 tests; exact release source/browser
gates pass, including configured-node whitespace parity.

### Fixed

- Configured-node Providers preserve Unicode whitespace names and original leading/trailing spaces, matching source scripts that filter empty names with `String.trim()`.

## 0.9.299 - 2026-10-10

### Added

- Explicit Active Choices single string transport compatibility with separate API/UI defaults and editable Core string history.
- Configured-node Provider source preserving built-in-first configuration order, including offline and zero-executor nodes.

## 0.8.296 - 2026-10-10

The HPI is `dist/parameter-foundry-0.8.296.hpi` (SHA-256
`81ad27c92945a626eabf34c58372ffe3247a7dd61ef4723758238f2b6be9c2ce`,
614,668 bytes). Full regression passed 365 tests; the exact release HPI passed
9,792 source transport cases and 22 browser checks.

### Fixed

- Extended Choice migration retains empty option tokens in CLI/standard exports without adding empty visible choices.
- Static single Extended Choice migrations preserve omitted literal defaults, joined CLI/standard inputs, literal forms and editable historical Rebuild; dropdown/radio defaults remain independent of transport defaults.
- Extended Choice migration parses static options and UI defaults independently of the output delimiter.
- Static multiple-choice compatibility preserves exact omitted-input default exports and distinguishes blank defaults from explicitly empty submissions.
- Extended Choice multiple form submissions retain literal strings and joined arrays, including unlisted values, whitespace and empty elements; Rebuild retains editable historical literals.
- Legacy queue comparisons use the exported string, and typed multiple submissions honor configured export order.
- Extended Choice compatibility requires submitted order with delimited or disabled environment export; use ordinary input modes for canonical or JSON export.

## 0.7.288 - 2026-10-10

The HPI is `dist/parameter-foundry-0.7.288.hpi` (SHA-256
`03782664326463388c5d51093ea8f50153e7cdb2b0e0fae371c6fe26d53d2c96`,
601,384 bytes), built from release commit `ea66fd7`. Full regression passed
338 tests; exact release verification passed 27 related tests and 13 browser checks.

### Added

- List Git Branches input compatibility for Provider-backed single choices, preserving literal revisions, lazy API defaults, snapshot binding checks, independent dropdown selection and editable historical Rebuild revisions.

### Fixed

- Git compatibility quick filtering retains case-insensitive regular expressions, all native options and source default selection after filtering; literal form submission remains available while refs load.
- Git compatibility forms support fresh, uncached ref lists without submitting an immediately expired snapshot; explicitly supplied API snapshots retain binding and expiry checks.
- Git SMART sorting preserves the source plugin's total-length tie break for equal numeric runs, including leading zeros and Unicode digits.

## 0.6.282 - 2026-10-09

The HPI is `dist/parameter-foundry-0.6.282.hpi` (SHA-256
`ce9fcd6ee26675519e8a8079d970d000280c78db62066974254fb400dde5e99b`,
596,422 bytes), built from release commit `fc6a13e`. Full regression passed
322 tests; final related regression passed 16 tests after all changes. The exact
release HPI passed four transport/configuration browser checks.

### Added

- Static Extended Choice multiple-selection transport compatibility, preserving legacy repeated REST fields, comma-separated CLI input, delimited form JSON and repeated submitted environment exports.

## 0.5.279 - 2026-10-09

The HPI is `dist/parameter-foundry-0.5.279.hpi` (SHA-256
`5e812a574a42ee08e26a775bc152cdc84864eb0e06bae351b4eb9955f11fcc23`,
594,357 bytes), built from release commit `b70814c`. Full Maven verification
passed 318 tests and static checks; the exact HPI passed all fifteen file-mode
browser canary checks.

### Fixed

- CLI builds combining stored file uploads and reactive Providers preserve dependency resolution after Jenkins consumes the upload argument.

## 0.5.276 - 2026-10-09

The HPI is `dist/parameter-foundry-0.5.276.hpi` (SHA-256
`3abe4751d1bc9ce05f0b4a0fcdbd426dbc639f53c44a0a1c1e41af596b333274`,
594,471 bytes), built from release commit `6537542`. The full regression passed
316 tests; final file regressions and static checks passed after the lifecycle
repair. The exact HPI passed the disposable real-browser canary.

### Added

- Unified file parameter with workspace, Base64 or stash delivery, configurable per-parameter upload limits, and Core File migration preserving workspace paths and original filenames.
- Built-in Base64 File and Stashed File parameters, bounded multipart/CLI upload,
  Pipeline and Freestyle temporary-file wrappers, and retained upload Rebuild.
- Class B migration targets for File Parameters uploads, with explicit upload
  limits, wrapper changes and historical dependency checks.

### Fixed

- Rejected HTTP/CLI file submissions and cancelled queue items clean pending uploads; crash recovery preserves admitted uploads and removes abandoned data.
- File migrations normalize equivalent duplicate parameter properties so legacy controls cannot shadow the target; conflicting duplicates are refused before writes.
- Rebuild after file-type migration requires a fresh upload instead of silently falling back to incompatible historical values.

## 0.4.270 - 2026-09-23

The Pipeline-declared check of the migration preview now applies to the
parameters a migration adapter recognizes, so the report stays focused on what a
wave could actually migrate.

The HPI is `dist/parameter-foundry-0.4.270.hpi` (SHA-256
`ab093ec9e39115403179dcc2968fa8ae193e30b57271686acce7d6226557519b`, 531,314
bytes), built from commit `9a496dd` and installed on the shared controller over
`0.4.267`, which stays in the controller rollback directory.

### Changed

- A parameter the Pipeline declares is only reported as blocked when a
  migration adapter recognizes it. Plain String, Boolean, and other parameters
  without an adapter no longer appear in the report (UP-ISSUE-015).

## 0.4.267 - 2026-09-23

The migration preview refuses the Jobs whose Pipeline declares the parameter it
would migrate, because those Jobs would revert the change at their next build.

The HPI is `dist/parameter-foundry-0.4.267.hpi` (SHA-256
`c908cda18161a4841fdb10ba6d73bcfa3d52c311c258a1402b1ab0bb7a606709`, 531,499
bytes), built from commit `db8bb46` and installed on the shared controller over
`0.4.263`, which stays in the controller rollback directory.

### Added

- The migration preview reads the declarative tracker the last build left on the
  Job and reports a parameter the Pipeline declares as blocked, with the reason
  that the Jenkinsfile has to change (UP-ISSUE-015). Parameters the Pipeline does
  not declare are still offered.

## 0.4.263 - 2026-09-22

The migration evidence report no longer blames a sibling parameter for an
environment difference, so a Job that keeps a twin parameter next to the
migrated control reaches `PASS` again.

The HPI is `dist/parameter-foundry-0.4.263.hpi` (SHA-256
`7cd863271450cdb941ffad67375a266cee5e6376e6c65d6c620aee82d0e88775`, 529,803
bytes), built from commit `e7cb9b7` and installed on the shared controller over
`0.4.255`, which stays in the controller rollback directory.

### Fixed

- `parameter-foundry-migration-verify` compared every environment key that
  contained the migrated parameter's name. A sibling such as `UP_CHANNEL` next
  to `CHANNEL` was therefore reported as an environment difference and the
  record was written as `FAIL` although both runs recorded the same canonical
  value (UP-ISSUE-014). The comparison now reads the parameter's own variable
  only, and the sibling difference stays in the summary.

## 0.4.255 - 2026-09-21

No user-visible behaviour change: the plugin behaves exactly as `0.4.245`. This
release exists so that the published artifact matches the artifact running on
the maintainer's controller and carries the completed repository hygiene work.

### Changed

- Test fixtures resolve deployment-specific values at run time through
  `PF_LAB_FIXTURES` instead of hard-coding them, so one suite serves a public
  build and a maintainer run
  ([ADR 0018](docs/decisions/0018-public-hygiene-and-private-lab-overlay.md)).
- The repository no longer carries records or tooling that name the maintainer
  environment; those live in a private overlay.

## 0.4.245 - 2026-09-21

The Chinese user interface now uses the product name recorded in the naming
evaluation. The English and Japanese resources already read Parameter Foundry,
so Chinese was the last locale still showing the working name.

### Changed

- The migration page is titled 参数工场迁移 and headed 参数工场迁移预览.
- The retirement dashboard is titled 参数工场退役 and headed 参数工场退役看板.
- The acceptance Folder and its 14 demonstration Jobs describe themselves as
  参数工场 instead of 统一参数 on the controller.

### Fixed

- No Chinese page, help popup, or Job description shows 统一参数 any more.

The HPI is `dist/parameter-foundry-0.4.245.hpi` (SHA-256
`4d774c42659ddf39faf2e3a713eb1b0c0bd23bce42b4095573b395a51f76c403`), built from
commit `75eefac` and installed on the shared controller over `0.4.242`; see
the 0.4.245 release record in the maintainer's lab overlay
([ADR 0018](docs/decisions/0018-public-hygiene-and-private-lab-overlay.md)).

## 0.4.242 - 2026-09-21

The product identity moves from the working name "Unified Parameters" to
**Parameter Foundry** ([ADR 0017](docs/decisions/0017-product-renaming-to-parameter-foundry.md)).
This is a breaking change for every Pipeline, Job DSL, REST, CLI, and
configuration reference to the plugin.

Deployed as an in-place replacement on the shared controller, including the
persisted-state migration for 98 Job configurations and 305 historical build
records; the release record lives in the maintainer's lab overlay
([ADR 0018](docs/decisions/0018-public-hygiene-and-private-lab-overlay.md)).

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
the matching lab-overlay release record, so an image rebuild no longer depends
on a private snapshot in
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
