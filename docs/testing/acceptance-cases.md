# Unified Parameters Acceptance Cases

This is the canonical index for acceptance cases, cross-platform coverage,
and user-reported regressions. Every case records reproducible behavior and
the fix that was verified. Production acceptance may use the jobs listed in
the case, but production configuration is never committed here.

## Coverage Matrix

| Case group | Desktop browser | Mobile viewport | Keyboard | Screen reader | No JavaScript | CLI/API | Jenkins agent |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Shared functional cases | Required | Required | Required | Required | Required | Required | Required |
| Layout and localization | Required | Required | Required | Required | N/A | N/A | N/A |
| Provider/reactive | Required | Required | Required | Required | N/A | Required | Required |
| Matrix/table | Required | Required | Required | Required | Required | Required | Required |
| Migration operations | Required | N/A | N/A | N/A | N/A | Required | N/A |

Platform-specific runs must record browser, browser version, OS, viewport,
input method, and screen-reader/device details. A shared case is not complete
until every applicable column passes.

## Shared Cases

| ID | Area | Reproduction and expected result | Verification |
| --- | --- | --- | --- |
| UP-AC-001 | UnifiedChoice | Open a build form with single choice; select each renderer. The value remains a scalar and the control is usable. | Local E2E and Test Harness |
| UP-AC-002 | UnifiedChoice multi | Open a multi-choice form with checkbox and `selectedDisplay=chips`; select and remove values. Values and chips remain synchronized. | Passed 2026-09-11: production browser select/remove/clear kept chips and status synchronized; Linux-agent build |
| UP-AC-003 | Matrix | Open the matrix case with Android/iOS/Web rows and A/B/C columns. Verify semantic headers, stable cell values, selection mode, disabled cells, responsive overflow, and the selected-value summary. | Passed 2026-09-12: semantic `th` headers, stable cell values with row/column accessible names, disabled cell reports its reason and stays unselectable, missing cell is not synthesized, a build submitted `[ios-a, web-b]`, the chips summary follows selection and chip removal (UP-ISSUE-009), and the table scrolls inside its container at a 390x844 viewport without page overflow |
| UP-AC-011 | Unified fixtures | Every acceptance Job configures choice parameters through `unifiedChoice` (+ `selectionMode: multiple` where needed), and the deprecated definition and symbol no longer exist. | Passed 2026-09-11 on `0.2.15-SNAPSHOT`: 32 of 32 Jobs migrated and built, 55 of 55 resolve calls returned `200`, a controller-wide scan of 688 Jobs found no remaining reference, every Job loaded without a class-loading error, and the configuration UI offers only the unified entry |
| UP-AC-012 | Replacement readiness | Inventory every Job on the controller, classify each source parameter with the deployed migration adapters, and state whether lossless replacement is automatic, needs decisions, or is blocked. | Passed 2026-09-13 on `0.3.155-SNAPSHOT`: 688 Jobs scanned; 112 use a source family; 3 Jobs fully automatic, 43 need recorded decisions, 66 are blocked by Class C/D. Report: [`replacement-readiness-2026-09-13.md`](./replacement-readiness-2026-09-13.md) |
| UP-AC-004 | Reactive | Change the dependency and wait for the refresh. Verify loading, stale-selection handling, errors, and no accidental build submission. | Browser plus CLI/API |
| UP-AC-005 | Form | Enter valid and invalid nested values. Verify localized validation, canonical JSON, and environment export. | Browser and build log |
| UP-AC-006 | Localization | Test English, Chinese, Japanese, and Korean. No user-visible hardcoded English appears in the selected locale. | English/Chinese production desktop verified; Japanese/Korean pending |
| UP-AC-007 | Layout | Verify title is bold on the left, description is a bounded wrapping block on the right, and controls align under the description. | Desktop Chromium verified; mobile pending |
| UP-AC-008 | Transport | Submit from UI, CLI, REST, Pipeline, Freestyle, and rebuild. Verify equivalent normalized values. | End to end |
| UP-AC-009 | Migration | Preview and apply Active Choices, Extended Choice, File Parameter, and Git migrations. Preserve source jobs and verify target behavior. | Active Choices/Extended Choice/Git source-target production builds verified; File Parameter is an explicit native-capability boundary |
| UP-AC-010 | Unified Choice entry | Configure one `unifiedChoice` parameter in `selectionMode: single` and `multiple`. Single returns a scalar, multiple returns a deterministic list; the same renderer/validation/transport parity applies to both modes. | Production `0.2.12-SNAPSHOT` deployment verified on 2026-09-10: scalar `staging` and list `android,web` from one definition family, form renders by mode, typed schema reports `selectionMode`; 191 Test Harness cases pass |
| UP-AC-013 | Batch migration | Open the migration page of a Job with one migratable parameter, one parameter that needs a decision, and one Class C parameter. The page lists the applicable count, the blocked parameters, and the line-level diff; applying the batch without accepting the decisions fails, applying with them replaces only the applicable parameter and leaves the blocked definition untouched. | Passed 2026-09-14: `MigrationBatchCoordinatorTest` covers the preview counts, the diff, the refusal without accepted decisions, the atomic apply, the single-record rollback refusal, and the batch rollback; `MigrationCoordinator.previewBatch` returns `CONFIGURATION_CHANGED` for a stale digest. Production: `0.3.166-SNAPSHOT` served the batch section and a 126-line diff for a 78-option production parameter |
| UP-AC-014 | Batch rollback | Roll back an applied batch after the Job was edited manually. The rollback must be refused so the manual edit is not silently overwritten; an unchanged Job must restore the pre-batch XML and mark every record `ROLLED_BACK`. | Passed 2026-09-14: `MigrationBatchCoordinatorTest` asserts the restored source definitions and the record status, and the drift case returns `CONFIGURATION_CHANGED` |
| UP-AC-015 | Migration evidence | Run a build before the migration and a build with the same input after it, then verify the record. The evidence must report `PASS` with equal canonical values; a different input must report `FAIL`, and the absence of a post-migration build must report `PENDING`. | Passed 2026-09-14: `MigrationEvidenceServiceTest` records `PENDING`, then `PASS` for `staging` before and after, then `FAIL` for `staging` versus `production`, including the forced run numbers |
| UP-AC-016 | Repository blind spot | Create a Job whose parameters are materialized, a Job that reads its Pipeline from SCM, and an organization folder. The retirement dashboard and the JSON report must report the unscanned state and list the SCM Job and the folder. | Passed 2026-09-14: `RepositoryScanTest` asserts `unscanned`, the SCM Job list without the inline-Pipeline Job, the organization folder, and the `repositoryScan` block in `unified-parameters-retirement/report`. Production: the deployed report returned `unscanned=true` with 623 SCM-defined Jobs, 5 multibranch or organization folders, and 259 materialized Jobs |
| UP-AC-017 | Migration localization | Render the migration page and the retirement dashboard with a Chinese locale. The view chrome, adapter decisions, fact labels, and verification cases must be Chinese and no unresolved `!Key!` may appear. | Passed 2026-09-14: `MigrationLocalizationUiTest` renders both pages in `zh_CN`; `MigrationMessageCoverageTest` proves that every referenced key exists and that the Chinese, Japanese, and Korean bundles translate the full key set. Production: both pages rendered in Chinese on `0.3.166-SNAPSHOT` with no unresolved keys |
| UP-AC-018 | Simulated replacement | Run the controller-wide simulation for a Job that recorded values under the source control. The report must rebuild the target, replay every recorded value, and mark the parameter `EQUIVALENT` when the value is representable and the environment export matches, `MISMATCH` when a recorded value is no longer offered, and `MANUAL_REDESIGN` for a script source while keeping the recorded value contract. | Passed 2026-09-15: `ReplacementSimulationServiceTest` covers the equivalent, stale-value, script-source, no-recorded-build, verdict-matrix, and controller-report cases; the report endpoint requires only administrative read access |
| UP-AC-019 | Multiple-choice export order | Migrate an Extended Choice multi-select whose downstream reads the environment variable. The migrated parameter must accept `environmentExportOrder: submitted` and export exactly the string the source control exported, while the canonical value stays the sorted set; a definition without the field must keep exporting the sorted set. | Passed 2026-09-15: `EnvironmentExportOrderTest` covers the canonical default, the submitted order for delimited and JSON export, validation of an unknown order, and the unchanged canonical value; `ExtendedChoiceMigrationAdapterTest` asserts the migration selects `submitted`; `ReplacementSimulationServiceTest` proves a submitted-order build value is `EQUIVALENT` |

## Platform and Device Cases

| ID | Platform/device | Required checks | Status |
| --- | --- | --- | --- |
| UP-PLAT-001 | Chrome desktop, macOS | Full shared matrix, keyboard focus, localization, table layout | Passed 2026-09-10: Chromium 151, macOS, 1440x1000; agent build via CLI |
| UP-PLAT-002 | Chrome desktop, Linux agent | Build submission, Pipeline/Freestyle values, provider refresh | Pending current run |
| UP-PLAT-003 | Safari/iOS viewport | Responsive layout, table horizontal scroll, touch selection | Partial 2026-09-12: WebKit (Safari engine) with iPhone 14 emulation loads the choice field, keeps both the field and the table inside the page width, scrolls the table inside its own container, and reports 8 named cell checkboxes with 3 row and 3 column headers; a physical iOS device run is still required |
| UP-PLAT-004 | Android Chrome viewport | Responsive layout, touch selection, validation visibility | Partial 2026-09-12: Chromium with Pixel 7 emulation, including touch events, selects an option and a table cell by tap, keeps the page inside the 412px viewport, and scrolls the table in place; a physical Android device run is still required |
| UP-PLAT-005 | Keyboard-only | Tab order, visible focus, radio/checkbox/table operation, error announcement | Passed 2026-09-12 (desktop Chromium): Tab order runs search, toolbar, group toggle, then options; Space toggles an option and a table cell; chips and the `role="status"` summary update; the matrix skips the missing coordinate |
| UP-PLAT-006 | Screen reader | Labels, row/column headers, status and validation announcements | Partial 2026-09-12: the Chromium accessibility tree exposes `searchbox`, `status`, named cell checkboxes, `rowheader` and `columnheader` roles, the disabled cell reason inside its accessible name, and a live region that changes from `Selected 1 of 8` to `Selection updated. Selected 2 of 8`; manual screen-reader sign-off remains |
| UP-PLAT-007 | No JavaScript | Native fallback controls, submission, and readable errors | Local E2E |

## User-Reported Issues

### UP-ISSUE-001: Matrix fell back to checkbox

- Reproduction: configure `renderer=table`, open the build form, and inspect
  the parameter.
- Actual: the UI showed a normal checkbox and reported
  `parseJson is not defined`.
- Cause: the table enhancement called a missing JSON helper; validation also
  initially omitted `table`.
- Fix: add safe JSON parsing and include `table` in the multiple-choice
  renderer allowlist.
- Verification: local build and production plugin `0.2.5-SNAPSHOT`; matrix
  case verified again with plugin `0.2.9-SNAPSHOT` on desktop Chromium.

### UP-ISSUE-002: Title and description layout

- Reproduction: open a Unified Parameters build form with a long description.
- Actual: Jenkins displayed title and description vertically and controls
  were not visually aligned.
- Fix: scope a plugin entry class, use a two-column title/description grid,
  wrap the description, bold the title, and indent controls below it; collapse
  to one column on narrow screens.
- Verification: plugin `0.2.6-SNAPSHOT`; desktop/mobile visual verification
  remains required for each supported browser.

### UP-ISSUE-003: User-visible errors were English

- Reproduction: trigger a Reactive, Form, or import error in a Chinese browser.
- Actual: internal English error text was shown.
- Fix: route client-side errors through localized message mapping and add
  Chinese messages for Form and Config Import states.
- Verification: plugin `0.2.9-SNAPSHOT`; English fallback and Chinese browser
  checks required. Server-side error-code migration remains tracked work.

### UP-ISSUE-004: Multi-choice enhancement failed after localization

- Reproduction: open a multi-choice build form with option labels different
  from their values. Run `make e2e`, which waits for TARGETS to become interactive.
- Actual: enhancement failed because `optionRow` used an undefined `field`
  when calling `detailsFor`; the browser test timed out.
- Fix: explicitly pass the parameter field into `optionRow` and then into
  `detailsFor`.
- Regression: existing TARGETS browser interaction, chips, keyboard and rebuild
  assertions in `tools/e2e/run-e2e.py` exercise this path.
- Verification: passed on 2026-09-10. `make e2e` succeeded, `mvn -B -ntp verify`
  passed with 179 tests, and the production matrix/migration pages rendered.

### UP-ISSUE-005: Single-choice search list was oversized

- Reproduction: open a single-choice parameter that uses search/custom values
  with only two matching options.
- Actual: the native list had a forced minimum height and at least three rows,
  producing a very tall empty box.
- Fix: remove the fixed minimum height and size the list from its actual
  rendered option count, capped at eight rows.
- Regression: the E2E suite now asserts a two-option native list stays under
  160 px while search/selection behavior continues to pass.
- Verification: passed on 2026-09-10. `make e2e` succeeded and
  `mvn -B -ntp verify` passed with 179 tests. The production mixed-parameter
  page measured a 74 px list and its Linux-agent build succeeded.

### UP-ISSUE-006: Migration comparison examples showed only the source plugin

- Reproduction: open the Extended Choice and Git migration example build forms.
- Actual: some jobs exposed only the legacy source parameter, so users could
  not compare the migrated Unified Parameters control with the original.
- Fix: keep the legacy source parameters, add equivalent Unified Parameters
  controls for Extended Choice and governed Git refs, print both values in the
  build log, and label the File Parameter case as a native-capability boundary
  instead of creating a false equivalent choice.
- Regression: verify old and new controls are present on the build page, no
  enhancement alert is shown, and both mapped values appear in build logs.
- Verification: passed on 2026-09-10 with real Chromium desktop checks and
  Linux-agent builds for Extended Choice source/clone and Git branch cases.

### UP-ISSUE-007: Unified Choice multiple mode failed to resolve options

- Reproduction: open the build form of a `unifiedChoice` parameter configured
  with `selectionMode: multiple`, or POST its name to
  `unified-parameters/api/v1/resolve`.
- Actual: the request returned HTTP `500` and the build form could not load any
  option, because the resolve endpoint still cast the definition to the legacy
  multi-choice class. `java.lang.ClassCastException` was recorded in the
  controller log.
- Cause: unifying the entry point made `UnifiedChoiceParameterDefinition` able
  to report `isMultiple()`, but the resolve endpoint kept a cast that only the
  legacy definition satisfied.
- Fix: evaluate through the shared base definition instead of casting, and
  cover the resolve path for a multiple-mode unified choice.
- Verification: passed on 2026-09-11 with plugin `0.2.13-SNAPSHOT`. The resolve
  sweep returned `200` for all 55 Unified choice parameters across the 32
  acceptance Jobs, and a real browser confirmed that a `selectionMode: multiple`
  field loads its options and keeps two chips synchronized.

### UP-ISSUE-008: Matrix cell values cannot be submitted

- Reproduction: open the matrix acceptance build form (rows Android/iOS/Web,
  columns A/B/C), select a cell such as `android-a`, then submit or validate it.
- Actual: the server rejects the value with
  `UNIFIED_PARAMETER_UNKNOWN_VALUE: Unsupported value: android-a` and nothing is
  queued. Only the flat option values `android`, `ios`, and `web` are accepted,
  although the form renders selectable cells with `android-a`-style values.
- Cause: option resolution and validation read the flat `choices` list, while
  the table renderer builds its controls from `table.cells`. The two option
  models are never merged, so every rendered cell value is outside the accepted
  option set. This predates the unified entry point.
- Additional: the table renderer does not expose `disabledReason` for disabled
  cells, although the matrix design requires disabled cells to expose a reason
  where configured. Flat options already do this.
- Fix: selectable options now merge the flat options with one entry per matrix
  cell, and evaluation, defaults, validation, and the rendering fallback read
  that set. Duplicate cell values and cell values colliding with a flat option
  are rejected, and the table renderer exposes a disabled cell reason through
  its accessible name and tooltip.
- Verification: passed on 2026-09-11 with plugin `0.2.14-SNAPSHOT`. A
  matrix-only fixture resolves exactly its eight cells, `android-a` and `web-b`
  validate and build, `web-c` fails as disabled, `ios-b` fails as missing, and
  the browser reports the configured disabled reason.

### UP-ISSUE-009: Table renderer ignored the selected-value display

- Reproduction: configure a multiple-selection parameter with `renderer: table`
  and `selectedDisplay: chips`, then open the build form.
- Actual: the table rendered and cells could be selected, but no chip summary
  appeared and the field had no status line, so the configured `chips` value had
  no effect and the selection summary was missing for assistive technology.
- Cause: the multiple-selection enhancement dispatches table fields to a
  dedicated `enhanceTable` path, which only built the grid and never created the
  chips container or the status element that the option-based path builds.
- Fix: the table path now creates the same chips container and status element,
  renders a chip per selected cell using the cell or row/column caption, updates
  the status on every change, and removes the cell selection when a chip is
  removed.
- Regression: the end-to-end fixture now includes a table parameter with a
  default selection, a disabled cell, and a missing coordinate, and asserts the
  chips, the disabled state, and chip-driven deselection.
- Verification: passed on 2026-09-12 with `make e2e` against the containerized
  Jenkins, and confirmed in production with `0.3.149-SNAPSHOT`: the field shows
  `Android A` with `Selected 1 of 8`, selecting `iOS / A` adds a chip and updates
  the status, and removing the chip clears the cell again.

### UP-ISSUE-010: Enter submitted the form when a custom value was invalid

- Reproduction: on a searchable choice parameter with `allowCustomValue`, type a
  value that does not satisfy the configured pattern and press Enter.
- Actual: the custom value was correctly refused, but the form was submitted and
  a build was queued with the value the form still held. The acceptance Job
  recorded an extra build at the time of the check.
- Cause: the search field only called `preventDefault` when the custom-value
  button was enabled, so an invalid candidate fell through to the browser's
  default form submission.
- Fix: Enter is always intercepted in the search field. It applies the custom
  value when the candidate is valid and otherwise leaves the candidate and its
  validation message in place for correction.
- Regression: `make e2e` now types an invalid custom value, presses Enter, and
  asserts that the validation message appears, the field keeps the typed text,
  the form stays on the build page, and the Job's build list is unchanged.
- Verification: passed on 2026-09-12 with `make e2e` and confirmed in production
  with `0.3.155-SNAPSHOT`: after pressing Enter on an invalid candidate the page
  stayed on the build form, the typed text and the `Value must match: custom-[0-9]+`
  message were preserved, the Job's build list was unchanged, and a valid custom
  value still selected and generated its hidden value. Evidence:
  [`results/issue-010-2026-09-12.json`](./results/issue-010-2026-09-12.json).

## Recording Rule

For every new failure, add a case or issue before changing code. Record the
exact URL/job path only in local operational notes, not in committed files
when it exposes private infrastructure. Update the verification column only
after a real browser, CLI/API, or build-log check has passed.
