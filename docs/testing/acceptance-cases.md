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
| Migration operations | Required | N/A | N/A | N/A | N/A | Required | N/A |
| Mixed file and reactive CLI (UP-AC-035) | N/A | N/A | N/A | N/A | N/A | Required | Controller and build retention |
| Extended Choice compatibility (UP-AC-036) | Required | Existing renderer coverage | Existing renderer coverage | Existing renderer coverage | Repeated fields | Required | Build environment |
| Extended Choice literal/default fidelity (UP-AC-039) | Required | Required | Existing renderer coverage | Existing renderer coverage | Required | Required | Omission, Pipeline values, Rebuild and persistence |
| Extended Choice literal forms (UP-AC-040) | Required | Required | Existing renderer coverage | Existing renderer coverage | Existing repeated field coverage | Required | Raw equality, persistence and editable Rebuild |
| Extended Choice single transport (UP-AC-041) | Required | Required | Existing renderer coverage | Existing renderer coverage | Required | Required | Scalar defaults, UI selection and Rebuild |
| Extended Choice empty transport (UP-AC-042) | Required | Required | Existing renderer coverage | Existing renderer coverage | Required | Required | Separator positions, omitted defaults and persisted flag |
| Active Choices single transport (UP-AC-043) | Required | Required | Existing renderer coverage | Existing renderer coverage | Required | Required | Literal strings, distinct UI/API defaults and configured-node order |
| Configured-node whitespace parity (UP-AC-044) | Required | Required | Existing renderer coverage | Existing renderer coverage | N/A | Required | Original values, source trim filtering and shared Provider resolution |
| Git ref sorting parity (UP-AC-037) | Existing Provider coverage | Existing Provider coverage | N/A | N/A | N/A | Required | Controller normalization |
| Git transport compatibility (UP-AC-038) | Required | Required | Existing renderer coverage | Existing renderer coverage | Repeated fields | Required | Build environment and snapshot binding |

Platform-specific runs must record browser, browser version, OS, viewport,
input method, and screen-reader/device details. A shared case is not complete
until every applicable column passes.

## Shared Cases

| ID | Area | Reproduction and expected result | Verification |
| --- | --- | --- | --- |
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

### Git transport compatibility

| ID | Reproduction / expected result | Actual behavior / fix | Verification |
| --- | --- | --- | --- |
| UP-AC-038 | Submit an unknown revision, empty/blank CLI or repeated standard fields, and form JSON strings/arrays/scalars. Preserve source scalar output; explicit values/defaults work without Git; omitted TOP resolves the first option. Submitted snapshots retain binding checks. | Strict option membership and eager Provider resolution change legacy behavior. Add source transport decoding and lazy option resolution without changing ordinary choice validation. | Decoder and Jenkins integration tests cover CLI/HTTP, literal defaults, typed strings/null, zero-fetch behavior and snapshot rejection. Final focused verify: 23 tests passed. Chromium 153: 13 checks passed for exact/approximate/missing defaults, TOP/NONE, configuration save, independent API default, unlisted form input and editable historical Rebuild without Provider loading. Full verify: 338 tests passed. Zero-TTL browser listings, regex/default filtering, full native options and loading submission parity pass the browser gate; failure contracts remain a per-Job migration gate. |

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

| ID | Reproduction / expected result | Actual behavior / required fix | Verification |
| --- | --- | --- | --- |
| UP-AC-043 | Compare unflagged radio/dropdown source defaults, literal CLI/standard/form values, label expressions, empty versus null, repeated fields and JSON arrays/objects. Preserve configured-node order and editable StringParameterValue history. | Ordinary Foundry single validation, sorting and optional empty handling do not reproduce the source. Add an explicit reviewed transport path with independent UI/API defaults and a governed configured-node source; keep ordinary/typed input strict. | Source characterization completed: 4/44 ordinary target factory cases match, source radio is unselected while API default is first, and dropdown selects first. Candidate 0.9.298-SNAPSHOT: source 44/44 matches; initial full 370 tests and runtime-focused four tests pass. Actual HTTP/CLI, cross-Job/unknown/expired/diagnostic/changed-definition snapshots, empty/failure policy, uppercase environment aliases and explicit zero-fetch are covered. Dynamic radio/dropdown, zero-cache snapshot omission, old UNO history Rebuild, escaped text editing and upstream Core String/scalar Pipeline consumer checks pass on isolated fixtures. Final full 374 tests and exact 0.9.299 release HPI gate pass. [Verification and limits](./active-choices-single-verification-2026-10-10.md). Production migration gates remain pending. Reproduce with `tools/ui-lab/scripts/check-active-choices-single.py`. Configured-node tests cover original order, zero-executor/offline membership and an empty source. [Observed contract and limits](../research/active-choices-single-transport-contract.md). |
| UP-AC-044 | Configure node names U+2003 EM SPACE, ASCII space and a name with leading/trailing spaces. Resolve configured-nodes through the shared service and inspect radio/dropdown forms. | Source scripts using `findAll { it?.trim() }` retain U+2003 and padded names, omit ASCII-only empty names, and never trim retained values. `isBlank()` incorrectly removes a legal Unicode name. Use Java `String.trim().isEmpty()` only for the configured-nodes source. | Release 0.9.301: eight Node Provider Harness tests and static checks pass, including the new shared-resolution case. The actual UNO trim sample and dynamic radio/dropdown browser checks preserve U+2003 and padded names and omit ASCII-only empty names; existing 44/44 transport cases pass. Full regression passes 375 tests with no failures/errors/skips. Exact release HPI browser gates pass. Per-Job production migration gates remain. [Verification and limits](./configured-node-whitespace-verification-2026-10-10.md). Existing nodes/labels modes retain their behavior. No business Job is executed. |

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
