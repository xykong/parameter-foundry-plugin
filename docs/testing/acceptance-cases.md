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

Platform-specific runs must record browser, browser version, OS, viewport,
input method, and screen-reader/device details. A shared case is not complete
until every applicable column passes.

## Shared Cases

| ID | Area | Reproduction and expected result | Verification |
| --- | --- | --- | --- |
| UP-AC-001 | UnifiedChoice | Open a build form with single choice; select each renderer. The value remains a scalar and the control is usable. | Local E2E and Test Harness |
| UP-AC-002 | UnifiedChoice multi | Open a multi-choice form with checkbox and `selectedDisplay=chips`; select and remove values. Values and chips remain synchronized. | Local E2E; production desktop Chromium 151 and Linux-agent build |
| UP-AC-003 | Matrix | Open the matrix case with Android/iOS/Web rows and A/B/C columns. Verify semantic headers, stable cell values, selection mode, disabled cells, and responsive overflow. | Production desktop Chromium 151 and Linux-agent build; touch/manual rows pending |
| UP-AC-004 | Reactive | Change the dependency and wait for the refresh. Verify loading, stale-selection handling, errors, and no accidental build submission. | Browser plus CLI/API |
| UP-AC-005 | Form | Enter valid and invalid nested values. Verify localized validation, canonical JSON, and environment export. | Browser and build log |
| UP-AC-006 | Localization | Test English, Chinese, Japanese, and Korean. No user-visible hardcoded English appears in the selected locale. | English/Chinese production desktop verified; Japanese/Korean pending |
| UP-AC-007 | Layout | Verify title is bold on the left, description is a bounded wrapping block on the right, and controls align under the description. | Desktop Chromium verified; mobile pending |
| UP-AC-008 | Transport | Submit from UI, CLI, REST, Pipeline, Freestyle, and rebuild. Verify equivalent normalized values. | End to end |
| UP-AC-009 | Migration | Preview and apply Active Choices, Extended Choice, File Parameter, and Git migrations. Preserve source jobs and verify target behavior. | Active Choices/Extended Choice/Git source-target production builds verified; File Parameter is an explicit native-capability boundary |

## Platform and Device Cases

| ID | Platform/device | Required checks | Status |
| --- | --- | --- | --- |
| UP-PLAT-001 | Chrome desktop, macOS | Full shared matrix, keyboard focus, localization, table layout | Passed 2026-09-10: Chromium 151, macOS, 1440x1000; agent build via CLI |
| UP-PLAT-002 | Chrome desktop, Linux agent | Build submission, Pipeline/Freestyle values, provider refresh | Pending current run |
| UP-PLAT-003 | Safari/iOS viewport | Responsive layout, table horizontal scroll, touch selection | Pending |
| UP-PLAT-004 | Android Chrome viewport | Responsive layout, touch selection, validation visibility | Pending |
| UP-PLAT-005 | Keyboard-only | Tab order, visible focus, radio/checkbox/table operation, error announcement | Pending |
| UP-PLAT-006 | Screen reader | Labels, row/column headers, status and validation announcements | Manual acceptance required |
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

## Recording Rule

For every new failure, add a case or issue before changing code. Record the
exact URL/job path only in local operational notes, not in committed files
when it exposes private infrastructure. Update the verification column only
after a real browser, CLI/API, or build-log check has passed.
