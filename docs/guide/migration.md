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
| Active Choices choice | Provider plus `foundryChoice` |
| Active Choices reactive choice/reference | `dependsOn`, or a read-only form field |
| Active Choices Groovy list/map | Inline, REST, managed config, or Script Provider |
| Active Choices `:selected` / `:disabled` | Option defaults and disabled metadata |
| Active Choices formatted HTML | Structured content or a reviewed renderer; raw HTML is not carried over |
| List Git Branches | Built-in Git refs Provider behind a Connection Profile |
| HTML parameters | `foundryForm` sections, grids, and conditional layouts |
| JSON Editor / JSON parameter | `foundryForm` typed schema |
| Multiselect, Custom Checkbox, Editable Choice, Extensible Choice | `foundryChoice` variants |
| Validating String | `foundryForm` string field with regex and length |
| REST List | REST Provider plus `foundryChoice` |
| File Parameters Base64 / Stashed File | Class B target: `foundryBase64File` / `foundryStashedFile`; review limits, wrappers and binary lifecycle |
| Jenkins Core File | Class B `foundryFile` workspace target; preserve relative destination and original filename environment key |

## Boundaries

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
