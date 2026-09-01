# `0.1 Technical Preview` Verification Report

- Verification date: August 31, 2026
- Core artifact: `plugin/target/unified-parameters.hpi`
- Migration artifacts:
  - `migration-extended-choice/target/unified-parameters-migration-extended-choice.hpi`
  - `migration-active-choices/target/unified-parameters-migration-active-choices.hpi`
  - `migration-list-git-branches/target/unified-parameters-migration-list-git-branches.hpi`
  - `migration-additional-sources/target/unified-parameters-migration-additional-sources.hpi`

## Gate Results

| Requirement | Evidence | Result |
|-------------|----------|--------|
| UP-SPIKE-001 binding/persistence | Jenkins Test Harness save/reload/copy/rename and Pipeline Symbol tests | Passed |
| UP-SPIKE-002 loaded validation | Modified XStream fixture remains loadable, returns configuration error, and does not queue | Passed |
| UP-SPIKE-003 Pipeline values | Scripted `properties`, `params`, and downstream `build` tests | Passed |
| UP-SPIKE-004 presence/decoding | Standard REST absent, presence-only empty, repeated/JSON-array/delimited values, and browser metadata tests | Passed |
| UP-SPIKE-005 standard REST status | Invalid value returns HTTP 400 with unchanged queue on Jenkins `2.528.3` and `2.568.2` | Passed |
| UP-SPIKE-006 CLI | Jenkins CLI `build -p` accepts JSON-array and configured delimited formats and records the canonical list | Passed |
| UP-SPIKE-007 mixed Typed API | Core String/Boolean/Choice plus Unified String/Array/Object/null shapes validate and schedule atomically; unsupported Password returns HTTP 409 | Passed |
| UP-SPIKE-008 Rebuild | Rebuild uses the current definition Renderer and historical values | Passed |
| UP-SPIKE-009 CSP/no-JS | External adjunct only, bounded 200-option fallback, real Chromium enhanced interaction | Passed |
| UP-SPIKE-010 reactor | Core and source-specific migration adapters build as separate HPIs with one-way dependencies | Passed |

## Automated Runs

```text
make verify-container
```

- Core/test-harness suite: 120 tests, 0 failures, 0 errors, 1 generated skip.
- Extended Choice migration suite: 11 tests, 0 failures, 0 errors, 1 generated skip.
- Active Choices migration suite: 7 tests, 0 failures, 0 errors, 1 generated skip.
- List Git Branches migration suite: 6 tests, 0 failures, 0 errors, 1 generated skip.
- Additional source migration suite: 6 tests, 0 failures, 0 errors, 1 generated skip.
- SpotBugs and Spotless: passed for all HPI modules.
- Format compatibility fixture: 14 tests covering Standard REST, Typed REST,
  CLI, browser metadata, Provider snapshot/dependency JSON, native multipart,
  stable errors, and queue invariants.

Target-LTS compatibility was run in a JDK 21 Maven container:

```text
mvn -B -ntp test \
  -Djenkins.version=2.568.2 \
  -Dmaven.compiler.release=21 \
  -Dmaven.compiler.testRelease=21
```

All 150 core, integration, and migration tests passed on Jenkins `2.568.2`.
The compatibility run uses Java 21 bytecode because that Jenkins line requires
Java 21; the baseline build continues to emit Java 17 bytecode for Jenkins
`2.528.3`.

Plugin Compatibility Tester coverage was then run with:

```text
make pct
```

The official PCT checkout at
`8a4e8125284289f5af87a91c717ada7f022b2daf` tested the core plugin and all four
migration adapters against both supported Core entries. Each entry ran 150
tests with 0 failures and 0 errors; 5 generated harness tests were skipped per
entry. The Docker runner uses Java 17 bytecode for Core `2.528.3` and Java 21
bytecode for Core `2.568.2`, and records the result in
[`pct-2026-08-31.json`](./results/pct-2026-08-31.json).

## Real-Browser E2E

```text
uv run tools/e2e/run-e2e.py
```

The isolated Jenkins `2.568.2-lts-jdk21` run verified:

- select, searchable, radio, list, chips, and checkbox renderers;
- grouping, option details, disabled reasons, chips, and batch actions;
- 10,000-option server resolution with at most 160 rows for one parameter;
- label, value, description, group, tag, Unicode, and no-result search;
- canonical browser submission;
- Rebuild of the historical value;
- CSRF-protected no-JavaScript submission with 200 bounded controls;
- configuration import preview/diff/snippets/apply;
- Extended Choice migration classification and preview UI;
- migration clone/apply/rollback execution;
- governed Provider resolution, health, custom values, reactive dependency
  refresh, snapshot replacement, and build-record provenance;
- Unified Form typed fields, conditional layout, canonical browser submission,
  and JSON no-JavaScript fallback;
- source-plugin retirement reporting for active, generated, queued/running, and
  historical references;
- light, Dark Theme system mode, forced-colors, and keyboard focus;
- 390px responsive layout;
- repeatable performance and accessibility gates.

Generated evidence:

- `docs/ui-gallery/images/unified-parameters/desktop-build-result.png`
- `docs/ui-gallery/images/unified-parameters/rebuild.png`
- `docs/ui-gallery/images/unified-parameters/mobile.png`
- `docs/ui-gallery/images/unified-parameters/light.png`
- `docs/ui-gallery/images/unified-parameters/dark.png`
- `docs/ui-gallery/images/unified-parameters/high-contrast.png`
- `docs/ui-gallery/images/unified-parameters/configuration-preview.png`
- `docs/ui-gallery/images/unified-parameters/migration-preview.png`
- `docs/ui-gallery/images/unified-parameters/provider.png`
- `docs/ui-gallery/images/unified-parameters/unified-form.png`
- `docs/ui-gallery/images/unified-parameters/retirement-dashboard.png`
- `docs/testing/results/1.0-e2e-benchmark.json`
- `docs/testing/results/1.0-accessibility.json`

## Source Plugin Docker Canary

The disposable source-plugin canary ran against real HPI files on Jenkins
`2.568.2-lts-jdk21` and passed:

- real HPI loading for Extended Choice, Active Choices, List Git Branches, and
  File Parameters;
- Extended Choice migration preview, clone/apply, audit record, Standard REST,
  webhook-style REST, Typed REST, CLI, Pipeline upstream/downstream, invalid
  input no-queue, Rebuild, and rollback checks;
- Active Choices and List Git Branches governed migration boundaries;
- native Stashed File multipart submission;
- no unexpected Controller plugin-load, init-script, or `SEVERE` errors.

Detailed redacted evidence is in
[`source-plugin-canary-2026-08-31.json`](./results/source-plugin-canary-2026-08-31.json).

The reference Renderer and sandboxed Script Provider companions were added to
the follow-up canary on September 1, 2026. The companion-backed parameter
resolved and built successfully, and the Renderer ID was exposed through the
typed schema. Detailed evidence is in
[`source-plugin-canary-2026-09-01.json`](./results/source-plugin-canary-2026-09-01.json).

The source Extended Choice plugin emits known legacy browser warnings in the
isolated migration environment. Jenkins `2.568.2` also has a breadcrumb
overflow-menu `xmlEscape` defect during responsive or large-layout
transitions. The E2E verifier filters only those exact, stack-attributed
external errors and still fails on any Unified Parameters script error.

## Decision

All repository-owned checks for the implemented `2.0` foundation are passing.
The final product release gates remain open: production-safe source-specific A/B
fixtures, companion-plugin security review, manual screen-reader sign-off,
Jenkins release review, and real-organization migration pilots. The follow-up
September 1 verification closed the repository-owned companion integration,
migration fixture coverage, and expanded PCT gates.
