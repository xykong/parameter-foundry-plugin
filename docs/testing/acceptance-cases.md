# Parameter Foundry Acceptance Cases

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
| Binary file parameters (UP-AC-025–034) | Required | Required | Required | Manual sign-off | Multipart fallback | Required | Required |
| Historical stash files (UP-AC-064) | N/A | N/A | N/A | N/A | N/A | Native archive/hostile format/cleanup | Secure local Linux filesystem; unsupported providers reject |
| Historical file streams (UP-AC-063) | N/A | N/A | N/A | N/A | N/A | Unit stream/decoder contract | N/A |
| Migration operations | Required | N/A | N/A | N/A | N/A | Required | N/A |
| Mixed file and reactive CLI (UP-AC-035) | N/A | N/A | N/A | N/A | N/A | Required | Controller and build retention |
| Extended Choice compatibility (UP-AC-036) | Required | Existing renderer coverage | Existing renderer coverage | Existing renderer coverage | Repeated fields | Required | Build environment |
| Extended Choice literal/default fidelity (UP-AC-039) | Required | Required | Existing renderer coverage | Existing renderer coverage | Required | Required | Omission, Pipeline values, Rebuild and persistence |
| Extended Choice literal forms (UP-AC-040) | Required | Required | Existing renderer coverage | Existing renderer coverage | Existing repeated field coverage | Required | Raw equality, persistence and editable Rebuild |
| Extended Choice single transport (UP-AC-041) | Required | Required | Existing renderer coverage | Existing renderer coverage | Required | Required | Scalar defaults, UI selection and Rebuild |
| Extended Choice empty transport (UP-AC-042) | Required | Required | Existing renderer coverage | Existing renderer coverage | Required | Required | Separator positions, omitted defaults and persisted flag |
| Extended Choice empty option lists (UP-AC-053) | Passed | Passed | Existing renderer coverage | Existing renderer coverage | Passed | Harness and exact legacy factory | Empty-list defaults, CLI input and configuration roundtrip |
| Choice visible rows (UP-AC-055) | Passed | Passed | Native list semantics | Native list semantics | Passed | Harness and exact legacy factory | Configuration persistence and Extended Choice migration |
| Extended Choice duplicate entries (UP-AC-054) | Passed | Passed | Passed native controls | Existing native control semantics | Passed submit button | Harness and HTTP | Independent entries, declared-order literals and persistence |
| Literal HTML reference (UP-AC-058) | Passed paired Chromium | Passed paired Chromium | Native hidden string input | Existing semantics | Native unchanged copy | Literal codec / sanitizer | Core string transport and sanitized presentation |
| Active Choices UI policies (UP-AC-057) | Passed paired Chromium | Passed paired Chromium | Native select semantics | Existing semantics | Native fallback scope | Policy validation/snapshots | Regex minimum length and selection retention |
| Active Choices multiple transport (UP-AC-056) | Passed literal unfiltered list | Passed literal unfiltered list | Existing native list semantics | Existing native list semantics | Pending | Native factory / Harness | Core strings, independent defaults, typed lists and old history |
| Active Choices single transport (UP-AC-043) | Required | Required | Existing renderer coverage | Existing renderer coverage | Required | Required | Literal strings, distinct UI/API defaults and configured-node order |
| Configured-node whitespace parity (UP-AC-044) | Required | Required | Existing renderer coverage | Existing renderer coverage | N/A | Required | Original values, source trim filtering and shared Provider resolution |
| Literal Active Choices preview fidelity (UP-AC-045) | Required | Existing renderer coverage | Existing renderer coverage | Existing renderer coverage | Existing compatibility coverage | Required | Preview-generated Core string defaults and literal inputs |
| Active Choices empty inline option (UP-AC-046) | Required | Required | Existing renderer coverage | Existing renderer coverage | Required | Required | Empty option order, default, submission, typed empty vs null and persistence |
| Extended Choice browser submission order (UP-AC-047) | Required | Required | Existing renderer coverage | Existing renderer coverage | Required | Existing caller-order coverage | Declared option order in build environment |
| Active Choices mapping refresh (UP-AC-051) | Required | Required | Existing renderer coverage | Existing renderer coverage | Literal defaults | Required | Dependency order, default reset, persistence and missing-dependency boundary |
| REST single source metadata (UP-AC-050) | Required | Required | Required | Required | Required | Required | Ordinary/cascade captions, flags, source API defaults, regex filter, minimum length and snapshots |
| Active Choices JSON response interpretation (UP-AC-049) | Source renderer coverage | Source renderer coverage | N/A | N/A | N/A | Parser/source oracle | Response order, null labels, fallback and resource boundaries |
| Reactive Active Choices REST fidelity (UP-AC-048) | Required | Required | Required | Existing renderer coverage | Required | Required | Independent defaults, literal transports and governed dependency queries |
| Git ref sorting parity (UP-AC-037) | Existing Provider coverage; NONE browser gate pending | Existing Provider coverage; NONE browser gate pending | N/A | N/A | N/A | Required | Controller normalization, unsorted category order |
| Extended Choice history (UP-AC-062) | Passed Chromium 1440×1000 | Passed Chromium 390×844 | Checkbox/help passed | Twelve actual neutral Rebuilds passed | Text input | 43 focused cases, two Core source-state/cold CPS matrices | Full verify/final artifact and remaining lifecycle pending |
| History reader lifecycle (UP-AC-061) | Passed Chromium 1440×1000 | Passed Chromium 390×844 | Checkbox and help passed | Historical rendering and Rebuild passed | N/A | Nine focused cases; two Core startup/cold CPS matrices | Final verify/artifact and remaining lifecycle gates pending |
| History reader packaging (UP-AC-060) | N/A inactive resource | N/A inactive resource | N/A | N/A | N/A | Digest, exact entries, UID and literal behavior | Activation/lifecycle/UI remain separate gates |
| Git source string history (UP-AC-059) | Passed Chromium 1440×1000 | Passed Chromium 390×844 | Existing textbox semantics | Passed actual Rebuild | Text input | Literal/default copy and Rebuild submission | Legacy string subclasses and unchanged current defaults |
| Git transport compatibility (UP-AC-038) | Passed NONE/list20, list1 and searchable20 | Passed same cases at 390×844 | Existing renderer coverage | Existing renderer coverage | Repeated fields | Empty-list HTTP/default coverage | Build environment and snapshot binding |
| Saved Pipeline parameter text (UP-AC-052) | Snippet/help coverage | N/A | N/A | N/A | N/A | Decoder, saved-value, explicit fallback and rejection coverage | Sandbox/CPS, definition-default merge, Declarative when, current-run filtering and persistence |

Platform-specific runs must record browser, browser version, OS, viewport,
input method, and screen-reader/device details. A shared case is not complete
until every applicable column passes.

## Shared Cases

| ID | Area | Reproduction and expected result | Verification |
| --- | --- | --- | --- |
| UP-AC-062 | Extended Choice string history | Separate opt-in/bundle, current-definition boundary, default-off/restart lifecycle and family substitution rejection. | 43 combined focused tests, two Core source-state/cold CPS matrices and 12 real browser submits passed; full verify/final artifact pending |
| UP-AC-061 | Opt-in Git history reader | Default-off, restart-bound activation, integrity/storage checks, source-state and conflict handling. | Nine focused cases, two Core startup/cold CPS matrices and bridge UI passed; final verify/artifact and remaining lifecycle gates pending |
| UP-AC-060 | Isolated history bundle | Keep legacy-named classes outside the initial plugin classpath; inspect the digest-bound nested jar independently. | Passed 438 full tests and two actual-HPI independent-process isolation cases; no reader activation |
| UP-AC-059 | Git source string history | Rebuild with a pre-migration Core String subclass after replacing its definition; preserve literal input without fetching the Provider. | Passed 436 full tests and six desktop/mobile actual Rebuild submissions |
| UP-AC-058 | Literal HTML reference | Preserve native literal string values separately from sanitized HTML presentation. | Passed Harness, paired browser and complete verify |
| UP-AC-057 | Active Choices UI policies | Compare source regex filtering, selection clearing and constant-cascade retention with explicit policies. | Passed paired desktop/mobile browser and complete verify |
| UP-AC-056 | Active Choices multiple transport | Preserve literal Core strings, independent defaults, validated typed arrays and string history. | Passed 426 full tests, 2 paired viewports, 9 HTTP pairs and 5 CLI/env pairs; filtered/cascade/HTML gates remain |
| UP-AC-052 | Pipeline saved text | Real decoder literal, env override, sensitive/file/unknown/filtered rejection, saved-value reload, missing-action default merge and sandbox/Declarative consumers. | Eight Harness cases and field-help coverage passed; complete candidate verify passed 420 tests; exact candidate-HPI minimal CPS and desktop/mobile help checks passed |
| UP-AC-001 | FoundryChoice | Open a build form with single choice; select each renderer. The value remains a scalar and the control is usable. | Local E2E and Test Harness |
| UP-AC-002 | FoundryChoice multi | Open a multi-choice form with checkbox and `selectedDisplay=chips`; select and remove values. Values and chips remain synchronized. | Passed 2026-09-11: production browser select/remove/clear kept chips and status synchronized; Linux-agent build |
| UP-AC-003 | Matrix | Open the matrix case with Android/iOS/Web rows and A/B/C columns. Verify semantic headers, stable cell values, selection mode, disabled cells, responsive overflow, and the selected-value summary. | Passed 2026-09-12: semantic `th` headers, stable cell values with row/column accessible names, disabled cell reports its reason and stays unselectable, missing cell is not synthesized, a build submitted `[ios-a, web-b]`, the chips summary follows selection and chip removal (UP-ISSUE-009), and the table scrolls inside its container at a 390x844 viewport without page overflow |
| UP-AC-011 | Foundry fixtures | Every acceptance Job configures choice parameters through `foundryChoice` (+ `selectionMode: multiple` where needed), and the deprecated definition and symbol no longer exist. | Passed 2026-09-11 on `0.2.15-SNAPSHOT`: 32 of 32 Jobs migrated and built, 55 of 55 resolve calls returned `200`, a controller-wide scan of 688 Jobs found no remaining reference, every Job loaded without a class-loading error, and the configuration UI offers only the foundry entry |
| UP-AC-012 | Replacement readiness | Inventory every Job on the controller, classify each source parameter with the deployed migration adapters, and state whether lossless replacement is automatic, needs decisions, or is blocked. | Passed 2026-09-13 on `0.3.155-SNAPSHOT`: 688 Jobs scanned; 112 use a source family; 3 Jobs fully automatic, 43 need recorded decisions, 66 are blocked by Class C/D. Report kept in the maintainer's lab overlay ([ADR 0018](../decisions/0018-public-hygiene-and-private-lab-overlay.md)) |
| UP-AC-004 | Reactive | Change the dependency and wait for the refresh. Verify loading, stale-selection handling, errors, and no accidental build submission. | Browser plus CLI/API |
| UP-AC-005 | Form | Enter valid and invalid nested values. Verify localized validation, canonical JSON, and environment export. | Browser and build log |
| UP-AC-006 | Localization | Test English, Chinese, Japanese, and Korean. No user-visible hardcoded English appears in the selected locale. | English/Chinese production desktop verified; Japanese/Korean pending |
| UP-AC-007 | Layout | Verify title is bold on the left, description is a bounded wrapping block on the right, and controls align under the description. | Desktop Chromium verified; mobile pending |
| UP-AC-008 | Transport | Submit from UI, CLI, REST, Pipeline, Freestyle, and rebuild. Verify equivalent normalized values. | Transport half passed 2026-09-22 on the synthetic Docker canary under the renamed plugin: standard REST delimited, webhook-style and typed REST array, CLI JSON array, Pipeline upstream and downstream, Rebuild, and the stashed-file multipart boundary all built `SUCCESS` with the same canonical value for the same input ([record](./source-plugin-docker-canary-2026-09-22.md)); the browser half stays with the E2E run |
| UP-AC-009 | Migration | Preview and apply Active Choices, Extended Choice, File Parameter, and Git migrations. Preserve source jobs and verify target behavior. | Active Choices/Extended Choice/Git source-target production builds verified; File Parameter is an explicit native-capability boundary |
| UP-AC-010 | Foundry Choice entry | Configure one `foundryChoice` parameter in `selectionMode: single` and `multiple`. Single returns a scalar, multiple returns a deterministic list; the same renderer/validation/transport parity applies to both modes. | Production `0.2.12-SNAPSHOT` deployment verified on 2026-09-10: scalar `staging` and list `android,web` from one definition family, form renders by mode, typed schema reports `selectionMode`; 191 Test Harness cases pass |
| UP-AC-013 | Batch migration | Open the migration page of a Job with one migratable parameter, one parameter that needs a decision, and one Class C parameter. The page lists the applicable count, the blocked parameters, and the line-level diff; applying the batch without accepting the decisions fails, applying with them replaces only the applicable parameter and leaves the blocked definition untouched. | Passed 2026-09-14: `MigrationBatchCoordinatorTest` covers the preview counts, the diff, the refusal without accepted decisions, the atomic apply, the single-record rollback refusal, and the batch rollback; `MigrationCoordinator.previewBatch` returns `CONFIGURATION_CHANGED` for a stale digest. Production: `0.3.166-SNAPSHOT` served the batch section and a 126-line diff for a 78-option production parameter |
| UP-AC-014 | Batch rollback | Roll back an applied batch after the Job was edited manually. The rollback must be refused so the manual edit is not silently overwritten; an unchanged Job must restore the pre-batch XML and mark every record `ROLLED_BACK`. | Passed 2026-09-14: `MigrationBatchCoordinatorTest` asserts the restored source definitions and the record status, and the drift case returns `CONFIGURATION_CHANGED` |
| UP-AC-015 | Migration evidence | Run a build before the migration and a build with the same input after it, then verify the record. The evidence must report `PASS` with equal canonical values; a different input must report `FAIL`, and the absence of a post-migration build must report `PENDING`. | Passed 2026-09-14: `MigrationEvidenceServiceTest` records `PENDING`, then `PASS` for `staging` before and after, then `FAIL` for `staging` versus `production`, including the forced run numbers |
| UP-AC-016 | Repository blind spot | Create a Job whose parameters are materialized, a Job that reads its Pipeline from SCM, and an organization folder. The retirement dashboard and the JSON report must report the unscanned state and list the SCM Job and the folder. | Passed 2026-09-14: `RepositoryScanTest` asserts `unscanned`, the SCM Job list without the inline-Pipeline Job, the organization folder, and the `repositoryScan` block in `parameter-foundry-retirement/report`. Production: the deployed report returned `unscanned=true` with 623 SCM-defined Jobs, 5 multibranch or organization folders, and 259 materialized Jobs |
| UP-AC-017 | Migration localization | Render the migration page and the retirement dashboard with a Chinese locale. The view chrome, adapter decisions, fact labels, and verification cases must be Chinese and no unresolved `!Key!` may appear. | Passed 2026-09-14: `MigrationLocalizationUiTest` renders both pages in `zh_CN`; `MigrationMessageCoverageTest` proves that every referenced key exists and that the Chinese, Japanese, and Korean bundles translate the full key set. Production: both pages rendered in Chinese on `0.3.166-SNAPSHOT` with no unresolved keys |
| UP-AC-018 | Simulated replacement | Run the controller-wide simulation for a Job that recorded values under the source control. The report must rebuild the target, replay every recorded value, and mark the parameter `EQUIVALENT` when the value is representable and the environment export matches, `MISMATCH` when a recorded value is no longer offered, and `MANUAL_REDESIGN` for a script source while keeping the recorded value contract. | Passed 2026-09-15: `ReplacementSimulationServiceTest` covers the equivalent, stale-value, script-source, no-recorded-build, verdict-matrix, and controller-report cases; the report endpoint requires only administrative read access |
| UP-AC-019 | Multiple-choice export order | Migrate an Extended Choice multi-select whose downstream reads the environment variable. The migrated parameter must accept `environmentExportOrder: submitted` and export exactly the string the source control exported, while the canonical value stays the sorted set; a definition without the field must keep exporting the sorted set. | Passed 2026-09-15: `EnvironmentExportOrderTest` covers the canonical default, the submitted order for delimited and JSON export, validation of an unknown order, and the unchanged canonical value; `ExtendedChoiceMigrationAdapterTest` asserts the migration selects `submitted`; `ReplacementSimulationServiceTest` proves a submitted-order build value is `EQUIVALENT` |
| UP-AC-020 | Per-Job definition and default verification | For a Job with no recorded value, the simulation must still verify the definition and the absent-input path: the migrated option set must equal the source's current option set, the source default must stay selectable, and a dropped option must be reported instead of passing silently. | Passed 2026-09-16: `ReplacementSimulationServiceTest` reports `OPTION_SET_MISMATCH` for a target that loses an option the source still offers, `NEEDS_DEFAULT_DECISION` when the source default is no longer selectable, `EQUIVALENT_DEFAULT_ONLY` when it is, and `UNVERIFIED` when there is neither history nor a default; `MigrationSnippets` provides the per-parameter target snippet. Production: 208 Jobs, 277 parameters, 2,238 replayed values, 173 of 173 Extended Choice option sets `MATCH`, 28 stale defaults listed as decisions, 0 `MISMATCH` |
| UP-AC-021 | Matrix migration of a production Job | Migrate a Job that logged into a list of registries on one agent into a table whose rows are the registries and whose columns are the agents, allowing at most one agent per registry, and make the Pipeline act on exactly the selected registry/agent cells. | Passed 2026-09-20 on `demo-matrix` with plugin `0.3.216-SNAPSHOT`: `DOCKER_LOGINS` is a Provider-supplied 5x7 table - caption `BUILD_NODE`, corner `DOCKER_REGISTRIES`, `rowSelectionMode: at-most-one`, chips, 35 cells - the rendered form starts at `Selected 0 of 35`, and build `#40` selected two cells, ran the two logins in parallel on `agent-a.example.net` and `agent-b.example.net`, printed `Login Succeeded` for both, and recorded `["registry.example.net@agent-a.example.net", "harbor.example.net@agent-b.example.net"]`. Two defects surfaced on the way: UP-ISSUE-011 and UP-ISSUE-013 |
| UP-AC-023 | Provider-supplied matrix | Configure a table whose rows come from one Provider list and whose columns come from another, with `rowSelectionMode: at-most-one`. The form must render the live axes with their captions, allow at most one cell per row, reject a second cell in the same row, record exactly the selected cells, and offer one-click row, column, and whole-table selection. | Passed 2026-09-20 with `make e2e` on plugin `0.3.226-SNAPSHOT`: `DOCKER_LOGINS` renders the `BUILD_NODE` caption, the `DOCKER_REGISTRIES` corner header, two rows, three columns, six cells; selecting a second cell of a row clears the first; clicking the `staging` column caption selects that cell in both rows; clicking a row caption fills every cell of that row and a second click clears the row; `Select all` fills every cell, `Invert` complements the whole table, `Clear all` empties it; a selection longer than twelve chips collapses to a `Show selected (n)` toggle and the expanded list scrolls inside a bounded box instead of pushing the table down; a row that carries a rank constraint fills its rank instead of breaking the rule; the build records `["registry.example.com@production", "harbor.example.net@staging"]`. Unit coverage: `MatrixProviderDefinitionTest` asserts the cross product, the templates, the fingerprint, the missing axis, the self reference, and both row constraints |
| UP-AC-024 | Single-choice list box | Configure a required single choice with `renderer: list` and a shared Provider. The form must render a list box (options visible, no `multiple` attribute, at least four rows), preselect the configured default, and behave like every other renderer for the value. | Passed 2026-09-20 with `make e2e` on plugin `0.3.229-SNAPSHOT`: `NODE_LIST` renders three visible options in a list box, no `multiple` attribute, `size` >= 4, and `staging` preselected. Production: the `demo-node-choice` Job replaced its failing Active Choices script with this renderer and built on the selected agent |
| UP-AC-022 | Renderer fidelity | Configure Provider-backed single choices with `renderer: radio` (one optional, one required) and a radio group above the render limit. The optional field must render a radio group that includes an explicit None, the required field must render radios without None, the clicked radio must be the submitted value, and the oversized group must degrade to the searchable list with a visible explanation. | Passed 2026-09-20 with `make e2e` on plugin `0.3.216-SNAPSHOT`: `TARGET_NODE` renders four radios (None plus three options) with no select, `LOCKED_NODE` renders three radios, clicking `development` writes the hidden value and the build records `TARGET_NODE=development`, and `WIDE_RADIO` reports `70 options; using the searchable list instead of radio buttons.` |

### Built-in file capability acceptance (October 9, 2026)

| ID | Area | Reproduction / expected behavior | Previous behavior / fix | Verification |
| --- | --- | --- | --- | --- |
| UP-AC-025 | Upload and transport | Configure Base64 and Stashed File, submit bytes through UI/multipart/CLI; Base64 exports encoded bytes, Stashed File restores an identically named file. Reject invalid names, Base64, and oversized content. | Foundry had no upload type; added bounded binary companion and shared ingestion. | Passed 2026-10-09: Harness (including Declarative symbols), CLI and Chromium 153 desktop/mobile UI Lab |
| UP-AC-026 | Agent / lifecycle / Rebuild | Read a stash on an agent; bind a temporary path and exit; rebuild without a replacement; cancel a queued upload. Bytes must survive Rebuild, temporary paths and cancelled pending uploads must be removed. | External plugin required; added build-owned retained copies, isolated wrapper directories and queue cleanup. | Passed 2026-10-09: remote-agent, normal/failed wrapper cleanup, queue cancellation and chained Rebuild after original deletion; UI Lab controller restart and Rebuild |
| UP-AC-027 | Missing / wrong-type / API isolation | Omit a required upload, then use `allowNoFile`; pass a String value masquerading as a file; call Typed JSON on a file Job. Fail missing required and wrong type; optional absence runs body; JSON returns 409 without queueing. | Source uploads were retained native; new binary definitions remain separate from typed option input. | Passed 2026-10-09: `FoundryFileParameterTest`, `ApiSecurityIntegrationTest` and UI Lab Typed JSON rejection |
| UP-AC-028 | File migration | Preview source Base64/Stashed File; accept limits/wrapper/history decisions, migrate and rollback. Preserve name/description, show Foundry symbols; never report binary equivalence from option replay. | Class D native retention replaced by class B Foundry targets and binary manual-verification gate. | Passed 2026-10-09: adapter/coordinator apply/rollback Harness, audit parser distinction, and real-source-plugin Canary class B preview |

[Verification evidence](./file-parameters-verification-2026-10-09.md).

File platform matrix: desktop Chromium/macOS and Android-sized Chromium viewport
passed on Chromium 153.0.8010.12 in `tools/ui-lab/scripts/check-foundry-files.py`; physical devices, manual
screen reader and other browser engines remain pending. The Lab uses synthetic
content on a disposable localhost controller and never reads production data.

### Unified file delivery and policy acceptance

| ID | Area | Reproduction / expected behavior | Previous behavior / fix | Verification |
| --- | --- | --- | --- | --- |
| UP-AC-029 | Workspace delivery and Core migration | Configure `foundryFile` workspace mode with a relative destination; upload via UI/CLI, run Freestyle and a remote Pipeline wrapper, rebuild and roll back a Core migration. Preserve bytes, original filename variables and path-like Core identities; omitted uploads retain existing workspace files. Reject traversal, drive/stream/device paths and symlinks. | Only Base64/stash existed; added workspace delivery, atomic agent placement and Core class B migration. | Passed 2026-10-09: `FoundryWorkspaceFileParameterTest` and desktop/mobile Chromium UI Lab |
| UP-AC-030 | Upload policy and mode changes | Round-trip all three modes with `maxUploadBytes=0` and explicit limits. Test UI/REST/CLI, downstream values and Rebuild; reject oversized content and reuse after a mode change. | Fixed caps became inherited defaults and explicit per-parameter policies enforced during ingestion/delivery/reuse. | Passed 2026-10-09: Harness plus browser configuration save and HTTP upload rejection |
| UP-AC-031 | Input and read-only binary simulation | Upload through Base64 Pipeline input; reject workspace/stash input without staging data. Simulate a source Core value whose getters throw: report manual binary verification without invoking those getters. | Binary inputs and replay now fail closed at their transport boundary. | `FoundryWorkspaceFileParameterTest`; message coverage and audit parser tests |
| UP-AC-032 | Migration property integrity | Clone or migrate a Job with equivalent duplicate parameter properties: produce one effective target property and roll back the complete backup. Refuse conflicting duplicates before any write. | Redundant source properties could shadow the migrated one; migration now validates and normalizes equivalent duplicates. | Passed 2026-10-09: Harness and real-browser regression |
| UP-AC-033 | Legacy Rebuild gate | Upload using Core, migrate the definition, submit Rebuild without a replacement: HTTP 400 and no new build. Rebuild with a fresh upload must succeed. | Rebuild fallback could reattach the legacy value; reject incompatible historical values before scheduling. | Passed 2026-10-09: Harness and real-browser regression |
| UP-AC-034 | Upload ownership and crash recovery | Submit a valid file followed by an unknown parameter over HTTP/CLI or veto queue admission: no pending file survives rejection. An admitted queued file survives request cleanup and recovery, then cancellation deletes it. An unused successful upload follows build retention. Recover old UUID orphans while protecting active uploads and restored queue/build references. | Rejected submissions could orphan controller files; HTTP/CLI scopes now transfer ownership only after admission, clean failures immediately, and recover crash orphans after initialization. | Passed 2026-10-09: five `FileUploadOwnershipTest` Harness regressions; queue recovery is simulated by clearing in-memory ownership, not a process restart |

[Delivery-mode verification](./file-delivery-modes-verification-2026-10-09.md).

Platform coverage for UP-AC-029–034: macOS Harness with remote agent channels;
Linux disposable-controller desktop/mobile-viewport Chromium UI Lab. UI, config
help, API and agent lifecycle are required. Physical devices, Windows execution,
manual keyboard/screen reader and other engines remain pending; no-JavaScript
binary transport uses the standard multipart API rather than claiming the normal
Jenkins JavaScript form works without scripts.

### Git ref sorting parity

| ID | Reproduction / expected result | Actual behavior / fix | Verification |
| --- | --- | --- | --- |
| UP-AC-037 | Normalize a frozen refs set in SMART ascending/descending order. Numerically equal runs use total name length, matching the source; Unicode numeric runs compare by numeric value. | Remaining-length tie handling made leading-zero branches depend on input order. Use source-compatible numeric comparison and total-length tie handling. | Full verify: 324 tests passed. Frozen source/candidate comparison: all 33 compared ordered option sets match after selecting full-ref output for the source full-ref case. Exact release Provider gate pending. |

- NONE regression: compare a native source's five full branch refs with an
  ascending short-name Provider. Both values and order differ. Previous migration
  mapping incorrectly converted `NONE` to `ascending`.
- Fix: explicit `sortOrder: none` uses the source category's default-sized
  HashSet iteration; the adapter maps NONE to that option. A full-ref source
  additionally requires reviewed `refType: ref` and category filtering.
- Added checks: a native-source ordered golden, category filtering, maximum-ref
  rejection and adapter sort mapping. Targeted verify passed 16 tests including
  field-help coverage, without failures, errors or skips. Complete `mvn -B -ntp verify`
  passed 421 tests. The exact candidate's five real full refs matched the native
  diagnostic source's ordered values and labels on isolated Jenkins 2.568.3.
  Desktop page comparison exposed the separate NONE/20-row selection regression
  recorded in UP-AC-038; full browser/form parity remains pending. No combined-category or production migration
  equivalence is claimed. An independently observed missing-credential source
  GUI failure must not be hidden by a repaired diagnostic source clone.

### Git transport compatibility

| ID | Reproduction / expected result | Actual behavior / fix | Verification |
| --- | --- | --- | --- |
| UP-AC-038 | Submit an unknown revision, empty/blank CLI or repeated standard fields, and form JSON strings/arrays/scalars. Preserve source scalar output; explicit values/defaults work without Git; omitted TOP resolves the first option. Submitted snapshots retain binding checks. | Strict option membership and eager Provider resolution change legacy behavior. Add source transport decoding and lazy option resolution without changing ordinary choice validation. | Decoder and Jenkins integration tests cover CLI/HTTP, literal defaults, typed strings/null, zero-fetch behavior and snapshot rejection. Final focused verify: 23 tests passed. Chromium 153: 13 checks passed for exact/approximate/missing defaults, TOP/NONE, configuration save, independent API default, unlisted form input and editable historical Rebuild without Provider loading. Full verify: 338 tests passed. Zero-TTL browser listings, regex/default filtering, full native options and loading submission parity pass the browser gate; failure contracts remain a per-Job migration gate. |

- NONE/list regression: configure a native source single list with
  `SelectedValue.NONE`, 20 visible rows and a nonblank API default; use an
  explicitly empty credential ID for an unauthenticated repository. The native
  list remains unselected, but the target currently selects its first option.
  This changes a default form submission even when option values/order agree.
- Expected: retain an empty UI selection in the multi-row single list while
  preserving the separate literal API default. One-row dropdown behavior needs
  its own unchanged regression check.
- Fix: clear NONE selection in multi-row lists and explicitly sized searchable
  Git lists, set their native selectedIndex after assigning rows, and retain
  one-row/unconfigured-searchable behavior.
- Verification: targeted verify passed 11 Harness/help tests, including a real
  empty-array HTTP form's queued parameter and environment default without Git.
  The public UI Lab passed 25 checks on Jenkins 2.568.3 / Chromium 153.0.8010.12,
  including list20/list1/searchable20 at desktop 1440×1000 and mobile 390×844 with
  real neutral form outputs, manual selection, zero-result/default submission,
  filter clearing and invalid regex preservation, plus existing dropdown,
  unconfigured-searchable, Rebuild and loading regressions. The exact candidate
  HPI matched a native diagnostic source at both viewports for all five full
  refs, ordered labels, 20 rows and empty selection. Thirteen actual HTTP
  source/target input pairs matched; all 26 neutral queue items were cancelled
  with no builds. Complete `mvn -B -ntp verify` passed 422 tests with no failures,
  errors or skips. This closes the listed selection regression; source failure
  and whole-Job migration/release gates remain separate.
  The repaired credential configuration is a diagnostic variant; the original
  null-credential source fails before listing and has no demonstrated GUI parity.

### Mixed file and reactive CLI acceptance

| ID | Reproduction and expected behavior | Actual behavior and fix | Verification |
| --- | --- | --- | --- |
| UP-AC-035 | Submit a stored upload and reactive choice in one Jenkins CLI build. The Provider resolves the declared dependency, the upload remains attached, and the build succeeds. | Jenkins marks consumed file arguments with null; immutable map copying previously raised a null-pointer exception. A defensive read-only copy now accepts unrelated consumed markers without changing canonical dependency validation. | `ReactiveSnapshotIntegrationTest` covers a consumed marker deterministically and an actual combined CLI build; macOS Harness on baseline Jenkins, production pilot follow-up pending |
| UP-AC-036 | Compare legacy static multiple selections over CLI comma input, repeated standard requests and form JSON string/array input. Standard requests must not expand a combined comma field. Preserve submitted export including supported duplicates while canonical values remain a set; reject compatibility mode for Providers and tables. | Delimited mode treated REST and CLI alike, while form JSON treated comma strings as one value. Add explicit static Extended Choice transport compatibility and retained repeated exports; expose duplicate policy so saving config preserves it. | Passed candidate 2026-10-09: six `ExtendedChoiceTransportCompatibilityTest` Harness cases and four Chromium/Linux-controller UI Lab checks; [record](./extended-choice-transport-verification-2026-10-09.md). Exact release browser verification remains required before deployment |

### Extended Choice literal and default fidelity

| ID | Reproduction / expected result | Actual behavior / fix | Verification |
| --- | --- | --- | --- |
| UP-AC-039 | Compare static single/multiple definitions across literal/blank defaults, absent/empty fields, CLI, standard requests, form strings/arrays and custom delimiters. Preserve ParameterValue existence and exact source environment export; verify Pipeline consumption separately. | Candidate parses source options/UI defaults using commas independently of the output delimiter. Shared default compatibility preserves `legacyDefaultLiteral`, distinguishes absent and empty values, and applies canonical constraints. Eight `ExtendedChoiceDefaultCompatibilityTest` cases cover real CLI/HTTP builds, typed omission, reload/default copy, config save, equality, constraints and ordinary-mode isolation. UI Lab waits for enhanced controls and checks literal configuration, supported UI defaults and omitted-input export. Single and literal-form transport work remains open. | Partial 2026-10-10: full 347 tests and eight browser checks passed. Actual source/candidate: 72 default/omission cases pass; multiple CLI 132/132 and standard 108/108 match. Overall 378/816 match; 438 differences remain. [Verified scope and limits](./extended-choice-default-verification-2026-10-10.md). Full source/consumer, single/form and history/Rebuild gates remain open. |

### Extended Choice literal forms

| ID | Reproduction / expected result | Actual behavior / fix | Verification |
| --- | --- | --- | --- |
| UP-AC-040 | Submit unknown/whitespace strings and arrays with unknown, empty and mixed scalar elements. Preserve exact environment strings, omitted-versus-empty form semantics, raw queue equality, reload and editable Rebuild. Ordinary and typed explicit inputs stay strict. | Shared compatibility retains raw export independently of the supported canonical set. Equality uses the literal, Rebuild preserves an editable historical string, and typed multiple export retains submission order. Contradictory canonical/JSON compatibility policies are rejected explicitly. A real previous-snapshot XML fixture verifies older history. | Passed for characterized multiple scope: final full 355 tests and 11 browser checks; all 408 source multiple cases match. Eight new Harness cases pass, including old XML, HTTP build and Rebuild. [Verification and limits](./extended-choice-form-verification-2026-10-10.md). Single, empty-option and per-Job consumer gates remain open. |

### Extended Choice single transport

| ID | Reproduction / expected result | Actual behavior / fix | Verification |
| --- | --- | --- | --- |
| UP-AC-041 | Omit a static single parameter with an unlisted default, submit joined/repeated known values over CLI/standard requests and literal strings/arrays over forms. Keep scalar output and editable Rebuild; distinguish API defaults from dropdown/radio selection. Typed explicit strings remain strict. | Shared single compatibility preserves source transports. Static dropdown/radio controls omit the added None option and retain all options; UI defaults use the last matching offered choice. Radio tests inspect ready native controls. Source compatibility rejects disabled choices explicitly. | Passed characterized scope: final 360 tests, 17 browser checks and all 816 source single/multiple cases match. Five new Harness cases include actual HTTP/CLI, config/reload and Rebuild. [Verification and limits](./extended-choice-single-verification-2026-10-10.md). Empty-option and per-Job source/consumer/history gates remain open. |

### Extended Choice empty transport

| ID | Reproduction / expected result | Actual behavior / fix | Verification |
| --- | --- | --- | --- |
| UP-AC-042 | Use a source option string with leading/internal empty entries. CLI/standard requests must keep accepted empty token positions; omitted input still defaults. Empty tokens remain absent from visible/canonical choices and strict typed input. | Filtering against visible choices previously dropped empty separators. A source-derived `legacyEmptyChoiceAllowed` flag preserves transport tokens while canonical evaluation ignores those tokens. Source browser inspection confirms empty entries are not rendered in select/radio/checkbox controls. | Candidate 0.8.295-SNAPSHOT: four Harness cases, adapter regression and 22 browser checks pass. All 9,792 characterized source cases match across single, multiple, Radio and Checkbox types. Initial full run has one stale Chinese UI assertion; it is updated to the new transport-preservation decision. Corrected localized UI and final full 365-test verification pass. All source/browser gates reran on the final candidate and release HPI. [Exact 0.8.296 release verification](./release-0.8.296-verification-2026-10-10.md). [Verification and limits](./extended-choice-empty-verification-2026-10-10.md). |

### Active Choices single transport

UP-AC-046 covers an empty inline option at the beginning, middle and end of an
unflagged single list. Reproduce by previewing the source list, loading the build
form and selecting the empty option in dropdown and radio renderers. Expected:
option positions, first API and first-dropdown/unselected-radio UI
default remain unchanged; selecting empty submits an existing empty string. Typed
empty selects the option and typed null clears it. Previous behavior: definition
validation rejected the empty option. Fix: permit it only in single Active Choices
compatibility and preserve membership checks in the shared evaluator. Verification:
Full Harness verify passes 380 tests. Exact 0.10.303-SNAPSHOT HPI passes 132/132
source factory cases, six dropdown/radio pairs, twelve empty-value form builds
and editable empty Core history. Release 0.10.304 repeats the exact artifact gates. [Verification and limits](./active-choices-empty-options-verification-2026-10-10.md). Ordinary, duplicate,
flagged and reactive configurations must retain their restrictions.

| ID | Reproduction / expected result | Actual behavior / required fix | Verification |
| --- | --- | --- | --- |
| UP-AC-043 | Compare unflagged radio/dropdown source defaults, literal CLI/standard/form values, label expressions, empty versus null, repeated fields and JSON arrays/objects. Preserve configured-node order and editable StringParameterValue history. | Ordinary Foundry single validation, sorting and optional empty handling do not reproduce the source. Add an explicit reviewed transport path with independent UI/API defaults and a governed configured-node source; keep ordinary/typed input strict. | Source characterization completed: 4/44 ordinary target factory cases match, source radio is unselected while API default is first, and dropdown selects first. Candidate 0.9.298-SNAPSHOT: source 44/44 matches; initial full 370 tests and runtime-focused four tests pass. Actual HTTP/CLI, cross-Job/unknown/expired/diagnostic/changed-definition snapshots, empty/failure policy, uppercase environment aliases and explicit zero-fetch are covered. Dynamic radio/dropdown, zero-cache snapshot omission, old UNO history Rebuild, escaped text editing and upstream Core String/scalar Pipeline consumer checks pass on isolated fixtures. Final full 374 tests and exact 0.9.299 release HPI gate pass. [Verification and limits](./active-choices-single-verification-2026-10-10.md). Production migration gates remain pending. Reproduce with `tools/ui-lab/scripts/check-active-choices-single.py`. Configured-node tests cover original order, zero-executor/offline membership and an empty source. [Observed contract and limits](../research/active-choices-single-transport-contract.md). |
| UP-AC-044 | Configure node names U+2003 EM SPACE, ASCII space and a name with leading/trailing spaces. Resolve configured-nodes through the shared service and inspect radio/dropdown forms. | Source scripts using `findAll { it?.trim() }` retain U+2003 and padded names, omit ASCII-only empty names, and never trim retained values. `isBlank()` incorrectly removes a legal Unicode name. Use Java `String.trim().isEmpty()` only for the configured-nodes source. | Release 0.9.301: eight Node Provider Harness tests and static checks pass, including the new shared-resolution case. The actual UNO trim sample and dynamic radio/dropdown browser checks preserve U+2003 and padded names and omit ASCII-only empty names; existing 44/44 transport cases pass. Full regression passes 375 tests with no failures/errors/skips. Exact release HPI browser gates pass. Per-Job production migration gates remain. [Verification and limits](./configured-node-whitespace-verification-2026-10-10.md). Existing nodes/labels modes retain their behavior. No business Job is executed. |
| UP-AC-045 | Preview a literal-only Active Choices dropdown/radio with two unflagged nonempty options and a description; inspect default and explicit literal values. Repeat with filtering or selected flags; empty-option extensions have separate UP-AC-046 coverage. | Previously the preview used ordinary strict choices and lost the description. Eligible previews must use Active Choices single compatibility, retain first API/default Core strings and first-dropdown/unselected-radio UI behavior, and keep descriptions. Unsupported shapes must not claim this compatibility. | Snapshot 0.9.302: full verify passes 378 tests; exact HPI adapter-generated radio/dropdown browser checks and 44/44 source transport cases pass. Generated Pipeline snippet preserves apostrophe/CR/LF description and Core String default. No business Job runs. [Verification and limits](./literal-active-preview-verification-2026-10-10.md). Per-Job and exact release gates remain. |

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

- Reproduction: open a Parameter Foundry build form with a long description.
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
  not compare the migrated Parameter Foundry control with the original.
- Fix: keep the legacy source parameters, add equivalent Parameter Foundry
  controls for Extended Choice and governed Git refs, print both values in the
  build log, and label the File Parameter case as a native-capability boundary
  instead of creating a false equivalent choice.
- Regression: verify old and new controls are present on the build page, no
  enhancement alert is shown, and both mapped values appear in build logs.
- Verification: passed on 2026-09-10 with real Chromium desktop checks and
  Linux-agent builds for Extended Choice source/clone and Git branch cases.

### UP-ISSUE-007: Foundry Choice multiple mode failed to resolve options

- Reproduction: open the build form of a `foundryChoice` parameter configured
  with `selectionMode: multiple`, or POST its name to
  `parameter-foundry/api/v1/resolve`.
- Actual: the request returned HTTP `500` and the build form could not load any
  option, because the resolve endpoint still cast the definition to the legacy
  multi-choice class. `java.lang.ClassCastException` was recorded in the
  controller log.
- Cause: unifying the entry point made `FoundryChoiceParameterDefinition` able
  to report `isMultiple()`, but the resolve endpoint kept a cast that only the
  legacy definition satisfied.
- Fix: evaluate through the shared base definition instead of casting, and
  cover the resolve path for a multiple-mode foundry choice.
- Verification: passed on 2026-09-11 with plugin `0.2.13-SNAPSHOT`. The resolve
  sweep returned `200` for all 55 Foundry choice parameters across the 32
  acceptance Jobs, and a real browser confirmed that a `selectionMode: multiple`
  field loads its options and keeps two chips synchronized.

### UP-ISSUE-008: Matrix cell values cannot be submitted

- Reproduction: open the matrix acceptance build form (rows Android/iOS/Web,
  columns A/B/C), select a cell such as `android-a`, then submit or validate it.
- Actual: the server rejects the value with
  `PARAMETER_FOUNDRY_UNKNOWN_VALUE: Unsupported value: android-a` and nothing is
  queued. Only the flat option values `android`, `ios`, and `web` are accepted,
  although the form renders selectable cells with `android-a`-style values.
- Cause: option resolution and validation read the flat `choices` list, while
  the table renderer builds its controls from `table.cells`. The two option
  models are never merged, so every rendered cell value is outside the accepted
  option set. This predates the foundry entry point.
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
  value still selected and generated its hidden value. The raw evidence is kept
  in the maintainer's lab overlay
  ([ADR 0018](../decisions/0018-public-hygiene-and-private-lab-overlay.md)).

### UP-ISSUE-011: The migrated table definition was overwritten by the Pipeline

- Reproduction: after migrating `demo-matrix`, open the build form.
- Actual: `DOCKER_REGISTRIES` was a plain text input holding the old
  comma-separated default, so the table never appeared; only `BUILD_NODE`, which
  had no Pipeline counterpart, still came from the migrated definition.
- Cause: the Job is a `WorkflowJob` whose inline Pipeline declares
  `parameters { string(...) }`. Declarative Pipeline re-applies every parameter
  it declares to the Job's `ParametersDefinitionProperty` when a build runs, so
  the first post-migration build replaced the table definition with the string
  definition the Pipeline declared. The earlier check only read the Job XML
  before that build, so it reported success while the definition had already
  been reset.
- Fix: delete the `parameters {}` block so the Job's parameter definitions stay
  the single source of truth, clear the declarative parameter tracker, and
  re-post the Job XML. Parameter definitions inside an inline Pipeline must be
  reconciled before a Job is marked migrated; the migration tooling reads the
  Job definition and cannot see this Pipeline block yet.
- Detection: read the controller-rendered build form, not only the Job XML; the
  rendered form shows which definition the controller actually serves.
- Verification: passed 2026-09-20. The saved Job XML keeps both
  `FoundryChoiceParameterDefinition` entries after a build, the rendered form
  contains the five-row table with the `Docker login` column, five chips, and
  `Selected 5 of 5`, and a submitted subset build logged in to exactly the
  selected registries.

### UP-ISSUE-012: Provider-backed single choice ignored `renderer: radio`

- Reproduction: configure a single-choice parameter with a shared Provider and
  `renderer: radio`, then open the build form (`BUILD_NODE` in production).
- Actual: the field rendered as a searchable multi-row select list under
  `8 matching options.` The configured renderer had no effect; `renderer: select`
  had the same problem and also produced a multi-row list instead of a dropdown.
- Cause: the enhanced single-choice path had exactly one control (a search field
  plus a size-N `<select>`), and every Provider-backed or reactive single choice
  was routed to it, so the renderer was only honoured by the server-rendered
  fallback that JavaScript replaces.
- Fix: the enhanced path builds a native radio group for `renderer: radio`
  (optional fields get an explicit None, required fields do not), keeps `select`
  a real dropdown, and only falls back to the searchable list when the
  definition asks for searching, allows custom values, or the group exceeds 60
  options - in that case the field states why.
- Regression: `tools/e2e/init.groovy` adds `TARGET_NODE` (optional radio),
  `LOCKED_NODE` (required radio), and `WIDE_RADIO` (70 options); `make e2e`
  asserts the control type, the None rule, the generated value after a click,
  the recorded build value, and the degradation notice.
- Verification: passed 2026-09-20 with `make e2e` against the containerized
  Jenkins and re-checked on the production build form after the deployment.

### UP-ISSUE-013: A minimum selection blocked its own default

- Reproduction: configure a multiple-choice parameter (or a table) with
  `minSelections: 1` and no default value, then open the build form.
- Actual: the form could not render; the resolve call failed with
  `PARAMETER_FOUNDRY_MIN_SELECTIONS: Select at least 1 value(s).` and the field
  stayed non-interactive. The user could not select anything to satisfy the
  minimum.
- Cause: resolving the *default* ran the same evaluation as a submission, so an
  empty default was rejected by the constraint it was supposed to enforce later.
- Fix: selection counts and the matrix row constraint apply to submitted values.
  The empty default resolves as an empty selection, the form renders, and every
  submit, typed-API call, and validation still enforces the constraint.
- Regression: `MatrixProviderDefinitionTest.aMinimumSelectionDoesNotBlockTheDefaultSoTheFormCanRender`
  asserts the empty default and the rejected empty submission.
- Verification: passed 2026-09-20 on plugin `0.3.216-SNAPSHOT`; the production
  table rendered with `Selected 0 of 35` and the following build succeeded.

### UP-ISSUE-014: Migration evidence blamed a sibling parameter

- Reproduction: migrate a Job whose parameter `X` is migrated while another
  parameter of the same Job carries a name that embeds `X` (the acceptance
  fixture keeps the source control and the migrated twin side by side), then run
  `parameter-foundry-migration-verify` with a source build from before the
  migration and a target build after it, both with the same input.
- Expected: the evidence compares the canonical value and the environment export
  of `X`. The twin's presence on one side only is a sibling input difference,
  which the summary already reports separately.
- Actual: the record was written as `FAIL` with
  `Environment export differs: UP_X 'null' -> ''` even though both runs recorded
  the same canonical value for `X`.
- Cause: `MigrationEvidenceService.isRelevantKey` treats every environment key
  that *contains* the parameter name as relevant, so a sibling parameter whose
  name embeds `X` is compared as if it were an export of `X`.
- Fix: the comparison reads the parameter's own variable only. The migrated
  adapters export the parameter under its own name, and a name that merely
  embeds it belongs to a different input, which the summary already reports as a
  sibling difference. `MigrationEvidenceServiceTest` pins the sibling case, the
  parameter's own change, and the key case-insensitivity; the sibling test fails
  against the previous implementation.
- Verification: passed 2026-09-22 on plugin `0.4.263` in production. Runs `#2`
  (source) and `#10` (target) of the acceptance fixture reported `FAIL` before
  the fix and `PASS` after it, with `Value, environment export, and downstream
  consumption agree ... Sibling inputs differ: UP_LEGACY_TARGETS`.

### UP-ISSUE-015: A Pipeline that declares its parameters reverts the migration

- Reproduction: migrate a Job whose Pipeline comes from SCM and whose Jenkinsfile
  declares the parameter, then run the Job. The Job configuration keeps the
  migrated definition until that build.
- Expected: either the migration is durable, or the tooling states that the Job
  cannot be migrated through its configuration.
- Actual: Declarative re-applies the parameters the Jenkinsfile declares, so the
  Job configuration silently returns to the source definition. The builds keep
  succeeding, which hides the revert: the Job simply never uses the migrated
  definition. Two production Jobs showed it 11 and 26 minutes after a successful
  migration, each time in the build that followed.
- Cause: for those Jobs the Jenkinsfile, not the Job configuration, is the source
  of truth for the parameter list. The migration preview reads Job
  configurations only, so it cannot see the declaration.
- Detection: the Job configuration carries the parameters the last build applied
  in `pipeline-model-definition`'s declarative job property tracker, so the
  preview can read the declared names from the stored configuration.
- Fix: the preview reads the tracker and reports a parameter the Pipeline
  declares as blocked with the reason that the Jenkinsfile has to change. The
  check runs after the adapter lookup, so parameters no adapter recognizes stay
  out of the report; without that order the shared controller produced 1,310
  blocked rows for 17 applicable parameters.
  `MigrationBatchCoordinatorTest.blocksParametersThatThePipelineDeclares` pins
  the block, `stillMigratesParametersThatThePipelineDoesNotDeclare` and
  `keepsDeclaredParametersWithoutAnAdapterOutOfTheReport` pin the boundaries.
  The first test fails without the check.
- Verification: passed 2026-09-23 on plugins `0.4.267` and `0.4.270` in
  production. The controller-wide inventory reports 86 source-plugin parameters
  as blocked for the Jenkinsfile reason and 17 applicable parameters across 14
  Jobs; the six Jobs that reverted in the previous wave (`ap_build_replay`,
  `ap_build_replay_dev`, `ap_build_locks`, `ap_job_ai_play_stable`,
  `preheat_apk`, `testToolScript`) now show 0 applicable parameters. The 17
  applicable parameters were migrated in the same window and verified: each is
  stored as a Foundry definition, resolves with options, and simulated as
  `EQUIVALENT` or `DECIDED_NO_DEFAULT` before the change.

## Recording Rule

For every new failure, add a case or issue before changing code. Record the
exact URL/job path only in local operational notes, not in committed files
when it exposes private infrastructure. Update the verification column only
after a real browser, CLI/API, or build-log check has passed.

## Extended Choice browser submission order

| ID | Reproduction / expected behavior | Actual behavior / planned fix | Verification |
| --- | --- | --- | --- |
| UP-AC-047 | Declare nonalphabetic multi-choice options `gamma,alpha,beta` in Extended Choice compatibility mode with submitted environment export. Select gamma and alpha in list and checkbox forms. Both JavaScript and fallback forms must export `gamma,alpha`; explicit API arrays keep their caller order and canonical values stay sorted. | Enhanced controls generated hidden inputs in alphabetic order, exporting `alpha,gamma`. Preserve declared option order when generating Extended Choice multi-choice form inputs. | Reproduced on 0.10.304; exact 0.11.306 candidate passes actual Chromium 151.0.7922.34 desktop/mobile list and checkbox forms, fallback submission, explicit API caller order and ordinary canonical export. Complete exact-version Harness verify passes 381 tests; publication and deployment remain pending. |

### UP-ISSUE-016: Extended Choice multi-choice form changes export order

- Reproduction: configure options in nonalphabetic order; select two options and submit the enhanced form.
- Expected: environment export matches the original control's declared option order.
- Actual: hidden submitted fields are alphabetically sorted before the shared evaluator receives them.
- Fix: pass the full resolved option order into list/checkbox submission only in Extended Choice multiple compatibility mode, while retaining canonical sorting and caller-supplied API order.
- Verification: exact 0.11.306 HPI passes real-browser desktop/mobile list/checkbox, fallback and API order checks; the targeted seven-test Harness suite passes. Exact-version full regression passes 381 tests; publication and deployment remain pending.

## Reactive Active Choices REST fidelity

| ID | Reproduction / expected behavior | Current limitation / required change | Verification |
| --- | --- | --- | --- |
| UP-AC-048 | Use a synthetic GET/JSON source returning `json.data ?: json`, with a dependency and literal exception fallback. Verify list/map responses, missing dependency, defaults, filtering, reactive refresh, empty/error payloads and explicit Core String inputs. A governed replacement must preserve the observed source behavior without executing source code. | Reactive legacy single transports are under implementation with Provider-backed dependencies and an explicit literal API default; generic REST mapping still does not preserve data-or-root truthiness and list/map source semantics. | Nine source-only cases characterized on UNO 2.8.10 in isolation; targeted Harness passes 20 tests, including reactive literal/default and snapshot-dependency binding/tamper rejection; full Harness passes 382 tests; a paired Foundry browser smoke verifies initial first choice, dependency fallback and restoration. Eleven additional non-sandbox source-browser metadata cases record last-selected, selected-disabled, and ordinary/cascade null value/label boundaries. The eleven-case source API oracle separately records selected-label defaults, comma-joined defaults in single mode, retained disabled suffixes and independent cascade fallback defaults. REST interpretation, full source/target filter parity and exact-release gates remain pending. See [source contract](../research/active-choices-reactive-rest-contract.md). |

## Active Choices JSON response interpretation

| ID | Reproduction / expected behavior | Implementation boundary | Verification |
| --- | --- | --- | --- |
| UP-AC-049 | Parse the reviewed `json.data ?: json` response idiom: preserve list/key order, map labels, nulls, empty root results, object-array data properties and raw flag suffixes; signal parsing/property-access errors for the configured fallback; successful scalar results produce empty choices for the reviewed non-sandbox source. | Data-only interpreter added; no script execution. Resource guards limit input to 10 MiB, nesting to 64 levels before parsing, and distinct output to 10,000 choices, retaining duplicate collapse. It is not yet wired into REST Provider or UI metadata conversion. | Eight unit tests cover source interpretation and exact resource boundaries; targeted Maven verification passes all eight tests and static checks. Complete Maven verification with these guards passes 390 tests, without failures, errors or skips. Five initial unit tests pass; the expanded fifteen-case non-sandbox source oracle exposed and corrected the scalar-return boundary. Corrected fifteen-case textual source/target comparison passes; complete Maven verification passes 387 tests with no failures, errors or skips. REST request handling and actual source/target browser parity remain separate gates. |

### REST single source metadata and defaults

| Case | Reproduction and expected behavior | Implementation / actual behavior | Verification |
|---|---|---|---|
| UP-AC-050 | Configure a governed REST Provider in ordinary/cascade single JSON response mode. Reproduce later/multiple selected markers, selected-disabled entries, key-only markers, all-disabled lists and null values/labels. Preserve independent Core String API defaults and source dropdown/filter/dependency behavior. Test actual REST queries, exception fallback, successful scalar/empty responses and snapshot metadata binding. Configure source filter lengths 0, 1 and 3; type shorter queries after a previous filter, reach the threshold, then clear it, preserving source options and selection. Verify XML, generated snippets, Rebuild and changed-threshold snapshot rejection. | Source-derived option metadata, live ordinary defaults, explicit reactive defaults and source dropdown regex refresh are under development. Jenkins XML initially dropped record metadata; an immutable class fixes metadata and literal-label round trips. Source option digests bind raw/selected API values using structured encoding. | Private prototype matches 11 source browser/API fixtures and 289 ordinary plus 289 reactive primitive cases. The three metadata Harness tests pass, including XML round trip and guarded UI default resolution. Two REST Harness tests pass: real HTTP with source UI/API defaults, missing/null/unknown dependency behavior, literal no-fetch, fallback, immediate live recovery, empty scalar result, duplicate display-value ordinal and global configuration round-trip. Combined targeted Maven verification passes 24 tests, with no failures/errors/skips, and static checks. The first candidate browser run exposed reactive array-label coercion (`[]` must display empty) beyond textual source-map equality. Raw JSON label types and independent UI/API marker bits now preserve ordinary Java vs reactive JavaScript conversion; 513 native JavaScript number-string goldens plus nested JSON coercion tests pass. The second real-browser run completed 27 paired initial/filter/dependency/fallback/recovery cases. A new rich-array marker case exposed a source client-side render failure; the target now flags it and retains prior UI options (using the reviewed initial fallback when no prior options exist). The corrected candidate passes all 28 paired browser cases and all 28 actual form pairs (56 isolated neutral builds), including exact Core String parameter values and types. Real HTTP snapshot submissions pass for missing, JSON-null and empty-string dependencies; tampered, undeclared and mismatched dependencies are rejected, while mapped Providers retain strict missing-dependency rejection. The related 12-test Maven verification passes. Native JavaScript confirms shadowed JSON toString hooks fail conversion, including nested arrays; source render-failure metadata preserves that behavior. The additional shadowed-coercion browser and form pair passes, bringing the total to 29 paired browser/form cases and 58 neutral builds. Resource tests pass for 10 MiB response bytes, 64 nesting levels, and 10,000 numeric/array-labelled choices. Production source intake exposes per-parameter filter thresholds (1 and 3). Threshold configuration, snippet binding and snapshot fingerprinting are being added; actual short/threshold/cleared-query browser and form tests and a new complete regression remain pending. The expanded run passed 31 cases, then exposed unchanged-empty-query keyup behavior: the source rebuilds and clears flags, while input-only target handling retained them. Reviewed source filters now use keyup; ordinary Foundry search remains on input. The corrected exact candidate passes all 32 browser/form pairs (64 neutral builds), including source keyup activation, minimum lengths 0/1/3, Unicode lowercasing and Escape query retention. Two linked NAME-to-ENV cases pass, including empty-selection propagation and filtered submission (4 neutral builds). Authenticated no-JavaScript API known/empty/omitted submissions match source Core String values and types (6 neutral builds), with real crumbs and CSRF enabled. Ordinary no-JavaScript controller forms reject submission on both sides; their native controls differ, so GUI/DOM parity is not claimed. Missing crumbs remain rejected. A new snapshot collision regression verifies structured threshold binding, bidirectional changes and absent/explicit-1 compatibility. The latest complete Maven verification passes 402 tests with no failures/errors/skips, including the structured-digest collision correction. Final exact release-artifact gates remain required before deployment. |

## UP-AC-051 — Active Choices mapping refresh selection

- **Scope:** Shared reactive single-choice behavior, not a platform-specific control.
- **Reproduction:** Use a radio MODE parent with development/production and a data mapping child. Development options are false,true; production options are true,false. Select each parent in turn, including after manually changing the child.
- **Expected:** Active Choices compatibility rebuilds the child selection from the new list default. A select renderer exposes no regex search for a mapping. The literal API default remains false; option resolution requires the declared MODE dependency. Ordinary Foundry mappings retain a valid user selection.
- **Actual before fix:** Dependency refresh retained false in the production list because false was still valid, rather than selecting its new first value true.
- **Fix:** Reselect the Active Choices single default on every successful Provider refresh; leave ordinary Provider selection retention unchanged.
- **Jenkins Test Harness:** `ActiveChoicesMappingIntegrationTest.mappingUiDefaultsRemainIndependentFromLiteralApiDefaults` (full Maven verify: 403 tests passed) covers actual resolve responses, independent defaults, missing dependencies, literal CLI values and config persistence.
- **Real-browser coverage:** Desktop and mobile source/target comparison, manual selection followed by parent changes, plus ordinary mapping retention. Passed on an isolated Jenkins 2.568.3 controller with Chromium 151.0.7922.34, desktop 1440×1000 and mobile 390×844. Both source-native/candidate pairs, manual choice changes, rapid changes, transport recovery, ordinary retention and none defaults passed. No forms were submitted. Production migration is not approved by this case.

- **UI Lab automation:** Seed an empty loopback UI Lab controller with `tools/ui-lab/scripts/active-choices-mapping-init.groovy` and UNO 2.8.10 installed, then run `uv run tools/ui-lab/scripts/check-active-choices-mapping.py`. The browser check covers both viewport sizes, valid-selection retention outside compatibility mode, empty defaults, unknown/null resolver dependencies, rapid changes and failed-request recovery without submitting builds.

### UP-AC-052 — Saved Pipeline parameter text

- Reproduction: configure a static multiple choice with Extended Choice compatibility,
  submit the literal `red-custom`, and call `foundryParameterText('CHOICES')` in a
  sandboxed Pipeline and a Declarative `when` expression. Override the environment
  separately and reload the saved value. Also try sensitive, file, unknown, missing,
  filtered and historical multiple values without original text.
- Expected: return the saved literal, preserving substring predicates, whitespace,
  empty strings and nullable singles, independently of the canonical list or environment.
  Reject inaccessible/unsupported values without exposing them or running a Provider.
- Actual before fix: consumers joining `params.CHOICES` lost unlisted literal text;
  environment-based consumers could observe unrelated environment overrides.
- Fix: a current-Run-only Pipeline step reads reviewed saved value types through
  Jenkins parameter security filtering.
- Verification: `FoundryParameterTextStepTest`: six Harness cases passed, including sandbox/CPS, Declarative when, saved-value reload/reuse, security filtering and descriptor help.
  Exact 0.14.320 HPI checks passed on Jenkins 2.568.3: five neutral Pipeline builds,
  including expected rejection builds, plus Chromium snippet registration and help-page checks.
  Shared backend/Pipeline case; no build-form renderer changes.

- Missing-action regression: start a minimal sandboxed Pipeline with an empty
  `ParametersAction` and a non-null definition default. Jenkins `params` contains
  the default although the saved action has no value. A consumer guarded only
  by `params.containsKey` therefore failed when reading saved text.
- Fix: optional `fallbackIfMissing` returns only an explicitly supplied literal
  when there is no accessible saved value. It never evaluates the definition.
  Omitted fallback still rejects missing values; saved null/empty text and
  sensitive/unsupported rejection remain unchanged.
- Added verification: real CPS default merging with an empty action, explicit
  empty fallback, saved null/empty precedence, sensitive/unsupported rejection
  and Structs binding/help. All eight step Harness cases passed; targeted verify
  passed ten tests including help coverage, and complete `mvn -B -ntp verify`
  passed 420 tests with no failures, errors or skips. The exact candidate HPI on
  isolated Jenkins 2.568.3 passed four minimal source/target parameter-only CPS
  builds with empty actions and non-null defaults, plus 44 native CLI text/export
  comparisons. Chromium desktop 1440×1000 and mobile 390×844 field-help pages
  were readable with no horizontal overflow; no business stages were executed.
  These checks do not approve production deployment or whole-Job migration.

## UP-AC-053: Extended Choice empty option lists

- Reproduction: configure a static multiple choice with no choices, Extended Choice transport compatibility, submitted export order and a literal API default. Try omitted CLI input, explicit empty or unknown text, and comma-only text with legacyEmptyOptionsOmitCli on and off; save and reload its Job configuration.
- Expected: the definition remains valid, omitted/comma-only CLI input preserves the literal default, explicit input returns no parameter with the flag enabled and an existing empty-string parameter otherwise, and no selectable choices appear. Normal Foundry modes continue to reject empty lists.
- Previous behavior: stored-definition validation rejected every empty static multiple-choice list.
- Fix: allow an empty configured list only in multiple-choice Extended Choice compatibility; preserve the source CLI null-parameter behavior separately from form literal input.
- Verification: the final candidate passed complete `mvn -B -ntp verify` (412 tests), 24 CLI factory comparisons against the exact legacy plugin, 48 real-plugin desktop/mobile pages with JavaScript on and off, and two real omitted HTTP requests returning 400 with no queueing. Harness also verifies flag persistence and rejects its use with nonempty choices or ordinary transport modes. The explicit flag removes the initially observed missing-versus-comma-only CLI presence mismatch. This proves the listed empty-option cases, not complete Job migration, historical Replay or every transport; duplicate configured options remain a separate open compatibility gap.

## UP-AC-054: Extended Choice duplicate entries

- Reproduction: migrate a static list or checkbox with options he,he,mi, legacy multiple-choice transport and default he. Select only one he entry; filter to mi and back; select both he entries; inspect submitted literals, save and reload configuration.
- Expected: repeated entries remain visible and independently selectable. Defaults select both matching entries, filtering preserves entry selection, and submission retains declared order and repeated values. Canonical values remain a sorted set. Ordinary modes, conflicting/disabled duplicates, custom values, chips and reject-duplicates policy remain invalid for this compatibility case.
- Previous behavior: definition validation rejected repeated options; browser selection used machine values as unique keys.
- Fix: accept identical enabled entries only in static legacy list/checkbox mode with canonical deduplication and no custom values/chips; track browser selections by entry position and retain exact ordered hidden values.
- Verification: complete verify passed 414 tests. Exact legacy-plugin versus new-HPI Chromium checks passed 8 desktop/mobile list/checkbox pages, independent duplicate selection, filter clearing/Escape and target keyboard checks. Five normal-renderer regressions and two no-JavaScript default-display checks passed. Four real HTTP requests rejected required/min/max/disabled inputs with 400. Eight real form-JSON submissions preserved source/target literals and distinct queue entries despite equal canonical sets; all were cancelled with zero builds. Eight real no-JavaScript submit-button clicks across desktop/mobile list/checkbox defaults and independent selection retained he,he versus he,mi and their canonical sets; those queue items were also cancelled without builds. Normal wheel scrolling placed controls above Jenkins' sticky footer; no forced click or CSS override was used for the successful run. Harness covers configuration roundtrip. Physical screen-reader, complete historical Replay and business-Job acceptance remain separate pending gates; this case does not approve production migration.


## UP-AC-055: Explicit choice list rows

Shared case, all supported platforms. Reproduce: configure a single or multiple list with `visibleItemCount: 16`; repeat with empty and duplicate options, JavaScript enabled and disabled. Expected: 16 visible rows, unchanged available values and submission semantics. Previous behavior: enhanced multiple lists capped at 10 rows and migration omitted the original count. Fix: persist the count, import it for Extended Choice multiple lists, and apply it to native and enhanced lists. Verification: focused Harness passed; exact-HPI Chromium checked 52 desktop/mobile, JavaScript/no-JavaScript pages with 10/16 rows, normal/empty/duplicate options and actual uncapped heights. Sixteen real duplicate form/button submissions preserved raw literals and canonical values; all neutral queue entries were cancelled with zero executed FreeStyle builds. Twenty join/helper and two generated single-list snippets bound their explicit row counts in sandboxed neutral Pipeline runs. Styling adds padding relative to the legacy list; pixel-identical styling is not claimed. Legacy source size 0 semantics are not covered by these positive-count proofs. Full release verification is a separate gate. Tests: `ChoiceVisibleRowsTest`, `ExtendedChoiceMigrationAdapterTest.multipleListPreservesConfiguredRowsEvenWithoutOptions`.

## UP-AC-056 — Active Choices multiple string transport

- **Reproduce:** Configure a multiple list with `standardApiInputMode: active-choices`, `environmentExport: string`, non-empty unflagged options and UI `defaultMode: none`. Compare native UNO 2.8.10 CLI/JSON values, missing standard fields and raw string history; compare typed array validation separately.
- **Expected:** Standard callers preserve Core StringParameterValue, including array order, escaping, JSON null and object literals. API omission uses the reviewed first/literal default while the UI remains unselected. Ordinary multiple modes retain their validation/export behavior.
- **Actual before the change:** Compatibility validation rejected all multiple definitions; ordinary multiple values would turn scalar source history into lists.
- **Fix:** Generalize the reviewed literal transport predicate, validate its multiple configuration independently, resolve omitted typed API defaults before list decoding, and preserve string history. Keep the existing single-only predicate for callers that depend on it.
- **Verification:** Targeted Harness transport/history/validation/snippet tests pass eight cases including existing single regressions. A pinned native factory fixture supplies eighteen CLI/JSON cases. Full verify passes 426 tests. Frozen candidate HPI passes two desktop/mobile control pairs, nine actual HTTP pairs and five real CLI/environment pairs; all 28 neutral queue items were cancelled, final queue empty and no builds. Controls were visually inspected and unobscured after scrolling. [Run record](./results/active-choices-multiple-ui-lab.json). Filtering, cascade refresh and formatted HTML remain separate migration gates; this case does not authorize production migration.
- **Tests:** `ActiveChoicesMultipleCompatibilityTest`, `ActiveChoicesSingleCompatibilityTest`; native fixture `active-choices-multiple-2.8.10.json`; UI Lab `active-choices-multiple-init.groovy` and `check-active-choices-multiple.py`. Desktop/mobile are separate required runs; no source business stages may execute.

## UP-AC-057 — Explicit Active Choices filter and refresh policies

- **Reproduction:** In an isolated zero-executor controller, configure native filtered multiple choices (minimum length5), a constant native cascade and paired Foundry controls. Test short queries, regular expressions, empty results, clearing, invalid expressions, escaped captions, unchanged keyup/Escape, an unfiltered list above160 items, and a selected child during parent change.
- **Expected:** Matching source filter results clear multiple selection only after a valid filter. The observed mixed-parent source cascade retains a valid child selection; Core-parent source and existing reset-to-default configurations keep resetting. Ordinary definitions cannot enable these compatibility policies.
- **Actual before change:** Multiple filtering used substring matching and retained selections; all compatibility refreshes reset to the default, including constant cascades whose source retains selection.
- **Fix:** Add explicit auto/none/regex filtering and default/retain-valid refresh policies; bind them into help/schema/snippets/history copies and snapshot digests. Preserve all existing defaults.
- **Verification:** Targeted Harness covers policy persistence, real snippet rebinding, validation and changed-policy snapshot rejection. Exact candidate passes two viewports, eleven source/target filter and keyboard cases, five cascade roles (including invalid-selection fallback), and a 181-item unfiltered list with selectable tail. Zero forms were submitted, no builds ran, and the final queue was empty. Mobile filtering and desktop invalid-child controls were visually inspected. Complete verify passed 427 tests with zero failures/errors/skips; 564 implementation/resource entries match the browser-tested candidate. [Run record](./results/active-choices-ui-policy-ui-lab.json). No production migration authorized by this case.
- **Tests:** `ActiveChoicesMultipleCompatibilityTest`; UI Lab `active-choices-ui-policy-init.groovy` and `check-active-choices-ui-policy.py`. Source scripts are synthetic literals only. Desktop/mobile runs are separate; actual business builds must never run.

## UP-AC-058 — Literal HTML reference transport

- **Reproduction:** Configure a literal HTML reference with a hidden empty string value. Compare six pinned native CLI and twelve JSON samples, including null, arrays and object literals; roundtrip configuration and inspect the build form.
- **Expected:** Core StringParameterValue and environment exports match the native source; API omission defaults to empty with a null description. Literal presentation is sanitized independently. Native historical copy does not change the hidden default.
- **Actual before change:** Structured content fields returned an object rather than the source string.
- **Fix:** Add a literal reference definition using the managed Safe HTML sanitizer and the shared native string codec; typed callers use a strict string input.
- **Verification:** Private draft probe passes eighteen transport cases and sanitizer/default/copy checks. Six targeted Harness cases pass, including real configuration save/render, eighteen pinned native CLI/JSON samples, Typed API strict strings and snapshot rejection, and migration snippet rebinding. Existing baseline Jenkins serves the parameter form body with HTTP405; the Harness inspects that body using the established non-throwing page client. The corrected exact HPI passes desktop1440/mobile390 native/target display, constant-parent-change and hidden-field checks, four real browser forms, nine HTTP source/target pairs, three typed HTTP submissions and four invalid typed inputs. All25 neutral queue items were cancelled with no builds and an empty final queue. Normal wheel scrolling exposed the text without CSS overrides; full mobile pages and desktop target control were visually inspected. The first occluded-control browser attempt is retained as failed evidence. XML-overlimit content is rejected before sanitizer invocation, confirmed by XStream Harness regression and independent review closure. Complete verify passes 433 tests with no failures/errors/skips. All570 implementation/resource entries match the browser-tested HPI; production acceptance remains pending. [Run record](./results/active-choices-reference-ui-lab.json).
- **Tests:** `FoundryReferenceParameterTest`, `ReferenceTypedApiTest`, `ConfigurationHelpCoverageTest`; UI Lab `active-choices-reference-init.groovy` and `check-active-choices-reference.py`; fixture `active-choices-reference-2.8.10.json`. Desktop/mobile and actual form submissions are separate required gates; no business stages may execute.

### UP-AC-059: Pre-migration Git string history in Rebuild

Shared case, all supported platforms. Configure a synthetic Job with an old
Git string parameter value, including an unlisted revision, padded comma text
or an empty string. Replace the current definition with a Provider-backed
Foundry Choice using `standardApiInputMode: list-git-branches` and a different
literal default. Open Rebuild, verify editable historical text, and submit.

Expected: preserve the historical literal in the copied definition and Rebuild
input without querying the Provider or mutating the configured default. Nonempty
submitted text remains literal. An empty form submission follows the source
transport fallback to the current configured default. Ordinary choice definitions
keep their existing behavior.

Actual before fix: `copyWithDefaultValue` accepted Foundry values but returned the
unchanged definition for source StringParameterValue subclasses, dropping history
in clients that use the definition copy. The new copy regression fails on the
unchanged instance. Initial form submissions without JavaScript also lacked the
serialized form JSON; the corrected actual Rebuild test enables JavaScript and
removes the previous parameter property before adding the migrated definition.

Fix: Git compatibility mode accepts the Core string inheritance contract when
copying a default, creates a transient editable Rebuild copy and preserves the
literal. It does not deserialize missing source classes or approve uninstall.

Targeted Harness passes five literal copy cases and three real Rebuild submissions
with a synthetic source-class fixture in `GitBranchTransportCompatibilityTest`;
ordinary-choice copy regression included (two test methods pass). Independent review also requires sensitive string subclasses to retain the original definition without reading or rendering their plaintext; a regression covers Git, Active Choices and ordinary modes. Complete `mvn -B -ntp verify` passes 436 tests, SpotBugs and Spotless. Jenkins 2.568.3 with the exact candidate HPI and installed source plugin passes Chromium 153.0.8010.12 at 1440×1000 and 390×844: six history pages, six editable Rebuild forms and six actual neutral submissions, all SUCCESS, zero Provider resolve requests. History uses readonly text and empty values display N/A. Normal scrolling and clicks are used; desktop padded history and mobile padded/empty forms were visually inspected. Earlier locator, readiness and empty-display failures are retained. The fixture is test-only; no legacy value class is shipped. Source-plugin uninstall, bridge loading and whole-Job acceptance remain separate gates. [Run record](./results/git-string-history-ui-lab.json).

### UP-AC-060: Isolated source history bundle

Shared backend case, independent of platform input/rendering. Build the history
source set and inspect its nested jar without activating it. Expected: only the
reviewed legacy Git string class, its passive view and manifest; an accompanying
SHA256 must match. The main output directory must not export the legacy class.
The isolated class keeps native UID, Core String inheritance, name/value constructor,
description, environment and variable resolver for empty, padded, quoted/XML and
Unicode literals.

Actual first build: inherited ExportedBean annotation processing created an extra
index entry. Exact-entry coverage rejected it. Fix: disable annotation processing
for this isolated source set and clean its generated classes on each build.

Verification: HistoryReaderPackagingTest passes two cases, including independent
child-loader ownership (rather than accidentally inspecting the same-name test
fixture). Complete `mvn -B -ntp verify` passes 438 tests, SpotBugs and Spotless. Two independent Jenkins 2.568.3 processes use the actual HPI without a same-name test fixture: native absent yields no legacy class; native present retains native ownership; Foundry exports neither class nor same-path view. HPI/main-library inspection finds no legacy FQN outside the fixed nested resource, and both processes match its sealed digest. Zero Jobs/builds. [Isolation record](./results/history-reader-packaging-isolation.json). This only packages an inactive
resource: no startup loader, opt-in setting or retirement capability is advertised.
Cold-switching, conflicts, JEP200 and reader UI require later acceptance cases.

### UP-AC-061: Opt-in history lifecycle and managed resources

Shared case. A fresh install must remain DISABLED without extracting a jar. Saving
legacyGitHistoryEnabled=true persists the choice but returns RESTART_REQUIRED in
the current JVM, with no class exposure. Only a cold start with genuine source
absence may verify the fixed resource, retain it safely, and expose the value class.
Native/disabled/failed/staged/other-exporter states must not silently activate.
Conflict after activation remains unavailable until a complete restart.

Focused verification: resource integrity, content and expanded-size bounds, altered
retained files, symlink directory/archive rejection, exact package entries/UID and
field help pass. Default-off, save/reload and same-JVM toggle state test passes.
Access checker first rejected Beta injection and NoExternalUse cache type; the
independent review permits only a method-local official annotation exception,
not disabling the checker. Nine focused tests now pass with enforcement enabled,
including a runtime ownership-check failure that must persist ERROR and invalidate
acceptance instead of returning stale ACTIVE status.

Isolated actual-HPI startup matrices pass seven cases each on Jenkins 2.528.3 and
2.568.3: default-off, opt-in/source-absent, native present, both archive disable
markers, corrupt staged source and failed source dependency. Hot source installation
after activation reports CONFLICT and invalidates acceptance on both versions;
this detects an unsupported transition and does not make hot installation safe.
On 2.528.3, an independently packaged neutral test exporter owning the same
historical type yields OTHER_EXPORTER, visible monitor and acceptance false,
without extracting the reader jar. This is a synthetic loader ownership check,
not a second business plugin migration.
Adding a source `.hpi.disabled` marker after ACTIVE yields RESTART_REQUIRED,
persists the status and invalidates acceptance. Removing the marker in the same
JVM does not reactivate acceptance or unload the existing class.
Disabling the saved setting and starting a new complete 2.528.3 process yields
DISABLED and no legacy class exposure, even though the managed jar is retained
on disk. The monitor is inactive and persisted state is DISABLED; no Jobs/builds
are present in this cold-disable fixture.
On a copied synthetic home, stopping Jenkins and disabling the Foundry plugin
before a fresh 2.528.3 process prevents both the reader component and historical
type from loading, even with opt-in true and the retained jar present. This is
plugin-disable isolation, not historical restoration without a reader.
Actual Jenkins 2.580.1, outside the reviewed Core allowlist, reports ERROR and a
visible monitor with no legacy class exposure and no compatibility acceptance.
It uses an otherwise loadable actual HPI and zero Jobs/builds.
On 2.568.3, a cold downgrade to Foundry 0.19.340 with the native Git plugin restored
retains the current Foundry definition and reads the neutral Job's historical
native value, description and completed result. Three existing builds remain;
no new build is scheduled, and the newer reader component is absent.
On both Core versions, one neutral Pipeline preserves its historical ParametersAction and
paused CPS local through full-process native-to-reader activation and reader-to-native
rollback, then completes SUCCESS with the same environment and resolver value.

With the source plugin absent and the reader ACTIVE, Chromium 153.0.8010.12 passes
historical rendering and actual neutral Rebuild submissions at 1440×1000 and
390×844. Padded edited input remains literal; empty input uses the current configured
default. Both viewports expose the configuration field and its help. Saving the
disabled setting reports RESTART_REQUIRED and invalidates acceptance while the
existing JVM retains its loaded class. Help and status screenshots were visually
inspected. An initial checkbox-overlay locator timeout is retained; the successful
run clicks the visible label with normal scrolling.

The first complete verify passes 444 tests but fails SpotBugs because resource
origin checks use URL.equals, which can perform DNS resolution. The fix compares
exact external-form text for the fixed local origin and resource. The corrected
complete verify passes 444 tests, zero SpotBugs bugs/errors and Spotless. The frozen
676226-byte candidate (SHA256 `6d3ab5fb6c5f9f01c052f21d395b54bc1711c3a26fbde0b1434e9a88a0f0b0ea`)
passes actual startup, Foundry ownership and JEP200 XML roundtrip on both reviewed
Core versions. Its nested payload is byte-identical to earlier runtime evidence;
the digest is bound to its repackaged jar. Library differences are the corrected
Archive class, nested jar/digest and relocated generated file-name validator
metadata (the same `name` argument). Independent code review finds no new
High/Medium findings. Production release/version/operational review, source
retirement and whole-Job gates remain. These are isolated synthetic cases, not
production Job acceptance. No release
or source-plugin uninstall approved by these checks alone.

[Isolated runtime evidence and remaining gates](./results/history-reader-runtime-isolated.json).

### UP-AC-062: Extended Choice post-migration string history

Shared case. Package Git and Extended Choice value classes into separate fixed
bundles outside the initial classpath. Reject cross-family substitution. Each
setting defaults off and save-only changes require restart; source-disabled,
failed/staged/exporter states do not silently activate. Native UID, passive
history view and literal environment/resolver must match the characterized
post-definition-migration boundary. Validate native JSON file consumers before
retirement; do not reinterpret them as plain text.

Actual prior prototype: bidirectional Java/River/three XML formats and a neutral
paused Pipeline cold switch/rollback pass. Two incomplete internal manifests
were rejected by JEP200; both failures retained. Product bundles require a plugin
version as well as Short-Name, without whitelist or Core modifications. Product
packaging/family/lifecycle and help coverage pass sixteen combined focused tests,
including incomplete-manifest rejection, family substitution, isolated UID/value
ownership, default-off and same-JVM restart-required state, and failure invalidation.
Independent review found a shared-root creation race between parallel family
initializers. Both retention and status persistence now use an idempotent creation
helper followed by nofollow directory validation. Ten barrier-synchronized
two-family fresh-root pairs pass; existing symlink rejection still passes. Code
review closes the finding. Actual frozen candidate dual-opt-in startup passes on
2.528.3 and 2.568.3: both states/status files ACTIVE, both classes owned by Foundry,
two independently retained jars and JEP200 ECP XML roundtrip, zero Jobs/builds.
Product source-state matrices, CPS/UI, final artifact and complete verify remain
pending.

The first product candidate passes seven source-state cases per reviewed Core:
off, absent, native, jpi/hpi disabled, corrupt/staged and failed mandatory
dependency. The failure fixture adds a nonexistent test-only dependency to the
source manifest; other cases use the exact source artifact. Git remains DISABLED
in each single-family case. Both Cores also pass a neutral paused Pipeline's
native-to-Foundry-to-native full-process sequence, preserving historical action,
CPS local, description, resolver and environment, ending SUCCESS.

UI preparation found a separate Rebuild gap: pre-migration Core string values
were not copied into editable Extended Choice inputs. The new single/multiple
copy preserves the literal and configured defaults, including empty/unlisted
text. Harness copy/form regressions are running. The superseded full verify was
intentionally terminated for this implementation change; it is not a passed gate.

The corrected Rebuild copy and combined history/transport/help tests pass 43
cases. Six literal forms in both single/multiple modes preserve current defaults;
blank factory defaults remain absent while form input stays literal. Unlisted
single history is retained as text without assigning an invalid selectable default.
XStream roundtrip clears the transient Rebuild presentation fields. Actual Harness
Rebuild prefill and ECP sensitive-value exclusion pass. Rebuilt product UI submits
and the restarted complete verify remain pending.

Rebuilt source-absent product HPI passes Chromium 153.0.8010.12 at 1440×1000 and
390×844: single/multiple historical text and empty/padded prefills, twelve actual
neutral Rebuild submissions, exact environment strings (including empty and
quote/XML characters), unchanged current default, configuration field and help.
Desktop padded and mobile empty forms plus mobile configuration/help screenshots
were visually inspected. Earlier system-Python dependency and empty-history locator
failures are retained. Empty readonly values use N/A rather than a pre node; normal
scrolling/clicks are used. Complete verify/final artifact remain pending.
[Product evidence and limits](./results/extended-choice-history-runtime-isolated.json).

Actual source hot reinstallation after ECP activation reports sticky CONFLICT,
visible monitor, acceptance false and persisted conflict on both reviewed Cores,
with zero Jobs/builds. This detects an unsupported transition; it does not permit
hot source rollback or make it safe.

Both reviewed Cores also pass a mixed cold configuration: ECP off, Git on,
retained jars still present. The ECP type is absent, state/status DISABLED and
monitor inactive; Git remains ACTIVE. This verifies independent cold deactivation,
not removal of historical dependencies.

The rebuilt candidate also passes OTHER_EXPORTER startup on both reviewed Cores
without extracting its reader jar, and unsupported 2.580.1 reports ERROR with no
legacy type exposure. These use synthetic exporter fixtures/zero Jobs and retain
ordinary class filtering.

Cold downgrade to Foundry 0.19.340 with native ECP restored on 2.568.3 reads the
neutral Job's original nonempty/empty source histories and current Foundry
definition, retains all fifteen existing idle builds and schedules none.

Full verify r2 passes 454 tests but flags the intentionally transient Rebuild
literal as not restored. A field-local SpotBugs annotation documents that the
copied textbox state must disappear on deserialization, already covered by the
XStream regression; enforcement remains enabled. Complete verify runs again,
so r2 is not a passed release gate.

Corrected complete verify r3 passes 454 tests, zero SpotBugs bugs/errors and
Spotless. Final frozen snapshot is 685926 bytes, SHA256
`0538c46f1bdacd9464bd8954427a236ad28ab55fe7635e1328f051346c545d15`.
Both reviewed Cores pass actual final dual activation/ownership/status and ECP
JEP200 XML roundtrip. Both nested bundles' class/view/manifest payloads are
identical to the source/CPS and UI candidates and their final digests match.
Changed Choice definition/descriptor instructions match the UI candidate after
constant-pool-index normalization; annotation/debug metadata accounts for the
class-byte change. Generated file-validator argument metadata relocates between
abstract/concrete descriptors and requires an actual endpoint check. Production
publication/version/operational and per-Job retirement gates remain open.

Final-HPI file-name validator endpoint passes actual UPLOAD=ok and empty=error on
2.568.3. Native empty ok markup is `<div/>`; the initial overly strict response
matcher and container-variable cleanup issue are retained and corrected in the
test runner. The known owned zero-job controller was explicitly inspected/removed;
successful fresh run exits zero and removes its own controller. No product change
was needed for the generated metadata relocation.


## UP-AC-063: Stored file stream budgets

- Scope: shared controller-side primitives for the pending File history reader;
  no upload surface, descriptor, activation switch or legacy class is exposed.
- Reproduction: run `HistoricalFileStreamsTest` through Maven. Read seeded binary
  values with padded/unpadded Base64, malformed encodings, exact byte limits,
  overflow through bulk reads or skips, and source closure after rejection.
- Expected: native JDK decoding semantics, no second encoded/decoded whole-file
  copy, exact-limit EOF accepted, first excess byte rejected, subsequent reads
  and positive skips rejected, reset unavailable, and close reaches the source.
- Previous behavior: these safeguards existed only in the isolated prototype;
  the formal plugin had no bounded historical streaming primitive.
- Fix: restricted internal `HistoricalFileStreams` in the history companion.
  The caller must supply authorization, live reader state and a reviewed limit;
  this utility grants none of those and never opens controller paths.
- Verification: `mvn -B -ntp verify` passes 457 tests with zero failures/errors,
  zero SpotBugs findings and a clean Spotless check. This includes all three new
  stream tests. Browser and agent checks are not applicable to this primitive;
  endpoint/lifecycle integration remains open.


## UP-AC-064: Run-bound historical stash extraction

- Shared scope: exact local artifact-manager ownership, fixed Run stash key,
  native single-file USTAR/GZIP format, file/expanded/compressed quotas,
  cooperative time/interrupt checks, and owned extraction lifetime.
- Reproduction: run `HistoricalStashSourceTest`, `HistoricalStashArchiveTest`,
  `HistoricalStashFileTest`, and `HistoryStreamCleanupTest`. Use Core
  `ArchiverFactory.TARGZ` fixtures with both ordinary and native fallback names.
  Mutate entry names/types, duplicate entries, CRC/trailer/tail, truncation and
  quotas; simulate checked/unchecked cleanup failures.
- Expected: no serialized temporary path or remote fallback, no symlink traversal,
  only the expected regular entry, full archive validation before returning
  content, preserved source bytes, no failed extraction leftovers, and owned
  file/directory removal on close with primary/suppressed failures preserved.
- Previous behavior: these safeguards existed only in isolated research code.
- Fix: restricted Run-bound source, archive parser and extraction facade in the
  formal history companion; shared cleanup also handles runtime/errors.
- Platform-specific coverage: Linux/Java 21 executes all 18 related stream/source/
  archive/cleanup cases without skips. macOS lacks SecureDirectoryStream in the
  observed provider, so six source/extraction cases were skipped there; this is
  not restore acceptance. Unsupported providers must reject; no fallback is
  added. Windows-controller restoration remains unsupported pending a reviewed adapter.
- Actual results: first test compile lacked an optional TAR dependency; fixtures
  now use Core directly. First Linux extraction assertion observed the wrong
  temporary root; the corrected test discovers the actual JDK temporary parent.
- Verification: corrected Linux targeted suite passes. First full Linux run
  executes 472 tests without failures/errors/skips, then fails SpotBugs on a
  possible null filesystem root. Explicit root/attribute-view rejection is added;
  corrected full `mvn -B -ntp verify` passes on Linux/Java 21 with 472 tests,
  zero failures/errors/skips, zero SpotBugs findings and clean Spotless. Runtime
  endpoint permissions/ACTIVE gates, metadata discovery, remote
  backend adapters and active native wrapper/pending upload retirement remain
  separate work. Cooperative timing does not interrupt a blocked I/O operation.
