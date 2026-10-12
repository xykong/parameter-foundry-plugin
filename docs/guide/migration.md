# Migration

The plugin ships non-executing migration adapters for the parameter plugins teams
most often want to retire. An adapter reads a source definition, classifies how it
can be expressed here, and generates a target — it never runs the source script and
never changes the Job on its own.

## Workflow

1. **Preview** — open the Job and select **Parameter Foundry** in the side panel.
   Each recognised parameter shows its classification, the semantic decisions
   that need a human answer, a target summary, the generated Pipeline definition,
   and a verification fixture.
2. **Decide** — resolve every listed decision (ordering, empty semantics,
   duplicate handling, transport mode). The preview explains the impact of each
   one.
3. **Apply or clone** — apply the target to the Job, or clone the Job so source
   and target can be compared side by side. The source definition is preserved and
   a backup of the previous configuration is written before any change.
4. **Verify** — build both controls with representative values and compare the
   recorded values, environment export, CLI/REST behaviour, and downstream usage.
5. **Retire** — check the retirement dashboard before uninstalling a source
   plugin.

## Classification

| Class | Meaning | What you get |
|---|---|---|
| A | Deterministic equivalent | Complete target definition and a verification fixture |
| B | Equivalent after an explicit decision | Target alternatives plus the decision that must be recorded |
| C | Safe target capability exists, source intent needs refactoring | Generated Provider/Form skeleton and the inputs you still owe |
| D | Source mechanism is unsafe or its intent cannot be proven | Business questions and safe redesign options; no automatic apply |

Class C and D are not missing plugin features. They mean the source used a
mechanism (arbitrary script, raw HTML, unknown file path) whose business intent
has to be stated before an equivalent can be generated.

## Source mapping

| Source | Target |
|---|---|
| Extended Choice single/multi select, radio, checkbox | `foundryChoice` with the matching renderer and `selectionMode` |
| Extended Choice static values | Inline `choices` |
| Extended Choice property file | Managed config Provider |
| Extended Choice Groovy script | Provider refactor (the script is not copied) |
| Extended Choice JSON editor | `foundryForm` |
| Extended Choice custom delimiter | `standardApiInputMode` / `environmentExport` |
| Extended Choice static multiple-selection transports | `standardApiInputMode: 'extended-choice'`, submitted export and canonical duplicate deduplication |
| Active Choices choice | Provider plus `foundryChoice` |
| Active Choices reactive choice/reference | `dependsOn`, or a read-only form field |
| Active Choices Groovy list/map | Inline, REST, managed config, or Script Provider |
| Active Choices `:selected` / `:disabled` | Option defaults and disabled metadata |
| Active Choices formatted HTML | Structured content or a reviewed renderer; raw HTML is not carried over |
| List Git Branches | Built-in Git refs Provider behind a Connection Profile; `standardApiInputMode: list-git-branches` preserves literal inputs, with `gitSelectionMode` separate from API/CLI defaults |
| HTML parameters | `foundryForm` sections, grids, and conditional layouts |
| JSON Editor / JSON parameter | `foundryForm` typed schema |
| Multiselect, Custom Checkbox, Editable Choice, Extensible Choice | `foundryChoice` variants |
| Validating String | `foundryForm` string field with regex and length |
| REST List | REST Provider plus `foundryChoice` |
| File Parameters Base64 / Stashed File | Class B target: `foundryBase64File` / `foundryStashedFile`; review limits, wrappers and binary lifecycle |
| Jenkins Core File | Class B `foundryFile` workspace target; preserve relative destination and original filename environment key |

Extended Choice multiple-choice browser controls preserve declared option order for
submitted environment export. Explicit API values keep caller order; canonical values
remain the sorted set.

The next snapshot permits an empty Inline option list in static multiple-choice
Extended Choice compatibility mode. The UI has no selectable options. Omitted CLI
input and comma-only input use the literal default. Set `legacyEmptyOptionsOmitCli: true`
for missing or blank source option configuration: explicit input, including an
empty string, returns no parameter. For a nonblank comma-only source configuration,
leave the flag off so explicit input returns an existing empty-string parameter.
The migration adapter and generated snippet retain this distinction. The flag
requires empty static multiple choices in Extended Choice compatibility.
Form JSON continues to retain its submitted
literal. Other selection modes still require at least one selectable option.
Identical duplicate Inline options are retained in legacy multiple-selection lists
and checkboxes when selectedDisplay is none, duplicatePolicy is deduplicate
and custom values are disabled. Each entry can be selected independently, including
when filtering the list; forms export repeated values in declared order while
canonical values remain a set. Duplicate options with different metadata, disabled
duplicate entries and duplicates in ordinary modes remain configuration errors.

## Boundaries

Literal-only Active Choices single selections without selection markers,
disabled choices, filtering, cached defaults or dependencies use
`standardApiInputMode: 'active-choices'` in the preview target. Dropdowns retain
their first UI selection; radio groups remain unselected. Both retain the first
API default and Core string parameter values. The target also keeps the source
description. Empty inline options retain their positions and submit an empty string.
Review generating source and fallback behavior before applying.

- Version `0.8.296` adds exact omitted-input default
  exports through `legacyDefaultLiteral` and corrects option parsing for custom
  output delimiters, literal form submission and editable historical Rebuild.
  Static single transport compatibility is also supported.
  Leading/internal empty source entries enable `legacyEmptyChoiceAllowed` for
  CLI/standard transport only; visible choices omit those entries, as the source
  UI does. A trailing separator alone does not enable this flag. Candidate
  source/browser gates pass within the [characterized scope](../testing/extended-choice-empty-verification-2026-10-10.md).
  Exact-release verification and per-Job consumer/source review remain gates.
- Extended Choice compatibility in `0.7.288` covers a supported-value subset.
  Exact literal defaults, blank-default omission, unlisted form values, single
  selection transports and custom output delimiters still have known gaps.
  Verify these before applying a migration; see the
  [observed source contract](../research/extended-choice-static-transport-contract.md).
- Source scripts, markup, repository URLs, and credentials are never copied into
  the generated target.
- SCM Jenkinsfiles receive a patch or snippet; the plugin never pushes to the
  repository.
- Multibranch child Jobs are report-only: the generating Jenkinsfile or
  organization configuration has to change.
- Applying a migration writes a record (source Job, parameter, adapter, actor,
  digests, decisions, backup file) that can be rolled back.

## Batch migration

The Job page previews every recognized parameter of the Job, shows the exact
configuration diff, and can migrate all of them in one atomic write. The batch
refuses to run when a digest changed after the preview, when a parameter needs a
decision that nobody accepted, or when a parameter is Class C/D; those
parameters are listed separately as blocked.

The same flow is scriptable:

```bash
# Inventory every Job that an installed adapter recognizes
jenkins-cli -s "$JENKINS_URL" parameter-foundry-migration -json

# Dry run for one Job, with its decisions and the exact diff
jenkins-cli -s "$JENKINS_URL" parameter-foundry-migration -job folder/job

# Apply the batch once the decisions are accepted
jenkins-cli -s "$JENKINS_URL" parameter-foundry-migration -job folder/job -apply -accept-decisions
```

The inventory is scoped to the Jobs the CLI user may configure; it reports how
many Jobs were skipped (no configure permission, no parameter definitions) or
could not be scanned, so an incomplete inventory is never mistaken for a
complete one.

A Job whose Pipeline declares the parameter is reported as blocked with the
reason that the Jenkinsfile has to change. Declarative re-applies the
parameters its Jenkinsfile declares at every build, so a configuration
migration on such a Job would be reverted by the next build; the block keeps
the wave from writing a definition that cannot survive.

Rolling back a batch restores the Job XML captured before the batch. When the
Job changed after the batch, the rollback is refused, so a manual edit is never
silently overwritten.

## Verification evidence

A migration is only proven when the same input produces the same result before
and after the change:

```bash
jenkins-cli -s "$JENKINS_URL" parameter-foundry-migration-verify \
  -job folder/job -record <record-id>
```

The command picks the newest build before the migration and the newest build
after it, compares the canonical value, the environment export, and the sibling
inputs, and stores the result on the migration record. Exit code `0` means
verified, `1` means a mismatch, and `2` means the Job still needs a comparable
build. The Job page shows the stored evidence and can re-run the comparison.

## Simulated replacement of every Job

Before the wave starts, prove the replacement against the values production
already recorded. The simulation is read-only: it builds the migrated definition,
takes the values the Job's builds actually used, and compares what the source
control produced with what the target control would produce for the same input.

```bash
curl -u "$USER:$TOKEN" "$JENKINS_URL/parameter-foundry-replacement-simulation/report?builds=5"
```

Per parameter the report answers:

- `verdict`: `EQUIVALENT`, `EQUIVALENT_ADDED_ENV` (the target also exports an
  environment variable the source never set), `MISMATCH` (a recorded value is no
  longer representable, or the export changed), `NEEDS_PROVIDER` (the target
  resolves through a Provider, so the value contract needs the Provider in
  place), `NEEDS_MANUAL_CHECK`, `MANUAL_REDESIGN`, `UNVERIFIED` (the Job never
  recorded a value) or `BLOCKED`.
- `observations`: one row per recorded build with the source value, the source
  environment export, the simulated target value, the target environment export,
  and a note when they differ.
- `decisions`: the semantic decisions that still need an owner before the
  parameter can be migrated.

The Job page exposes the same simulation at
`<job>/parameter-foundry/simulation`, and a controller-wide run only needs
administrative read access, so a Job that the migration operator cannot
configure is still verified.

## Retirement dashboard

Administrators get a dashboard that reports, per source plugin: active Jobs,
generated sources, queued and running builds, and historical builds that still
reference it. Use it to prove a source plugin is unused before uninstalling it.

The dashboard also reports what it cannot see: Jobs whose parameters are defined
in a repository Jenkinsfile, and the multibranch or organization folders that
generate them. Those definitions are invisible to a configuration scan, so keep
the source plugins installed until the repository side has been reviewed after a
full build cycle.

File migrations preserve binary transport. Read [File parameters](./file-parameters.md) for wrapper changes, limits, retention and the separate binary verification gate.

### Reactive Active Choices single transports

Reactive single Active Choices compatibility requires a shared Provider and an explicit
`activeChoicesApiDefaultMode: literal` with the reviewed missing-dependency API default.
Explicit CLI and standard/form inputs remain Core strings without fetching choices.
Supplied snapshots must still match the dependency values. Typed explicit inputs stay strict.
REST response interpretation and browser parity need separate migration verification.

See the [characterized REST response contract](../research/active-choices-reactive-rest-contract.md)
before replacing a source cascade. Transport support alone does not establish response, filter or UI parity.

Choice list renderers accept `visibleItemCount` (0 for automatic sizing, 1–1000 for an explicit row count). It affects display height only, applies with and without JavaScript, and does not limit options or selections. Extended Choice multiple-list migration preserves this setting.
