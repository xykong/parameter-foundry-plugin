# Migration

The plugin ships non-executing migration adapters for the parameter plugins teams
most often want to retire. An adapter reads a source definition, classifies how it
can be expressed here, and generates a target — it never runs the source script and
never changes the Job on its own.

## Workflow

1. **Preview** — open the Job and select **Unified Parameters** in the side panel.
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
| Extended Choice single/multi select, radio, checkbox | `unifiedChoice` with the matching renderer and `selectionMode` |
| Extended Choice static values | Inline `choices` |
| Extended Choice property file | Managed config Provider |
| Extended Choice Groovy script | Provider refactor (the script is not copied) |
| Extended Choice JSON editor | `unifiedForm` |
| Extended Choice custom delimiter | `standardApiInputMode` / `environmentExport` |
| Active Choices choice | Provider plus `unifiedChoice` |
| Active Choices reactive choice/reference | `dependsOn`, or a read-only form field |
| Active Choices Groovy list/map | Inline, REST, managed config, or Script Provider |
| Active Choices `:selected` / `:disabled` | Option defaults and disabled metadata |
| Active Choices formatted HTML | Structured content or a reviewed renderer; raw HTML is not carried over |
| List Git Branches | Built-in Git refs Provider behind a Connection Profile |
| HTML parameters | `unifiedForm` sections, grids, and conditional layouts |
| JSON Editor / JSON parameter | `unifiedForm` typed schema |
| Multiselect, Custom Checkbox, Editable Choice, Extensible Choice | `unifiedChoice` variants |
| Validating String | `unifiedForm` string field with regex and length |
| REST List | REST Provider plus `unifiedChoice` |
| Stashed File / File parameter | Stay native — uploads are not converted into choices |

## Boundaries

- Source scripts, markup, repository URLs, and credentials are never copied into
  the generated target.
- SCM Jenkinsfiles receive a patch or snippet; the plugin never pushes to the
  repository.
- Multibranch child Jobs are report-only: the generating Jenkinsfile or
  organization configuration has to change.
- Applying a migration writes a record (source Job, parameter, adapter, actor,
  digests, decisions, backup file) that can be rolled back.

## Retirement dashboard

Administrators get a dashboard that reports, per source plugin: active Jobs,
generated sources, queued and running builds, and historical builds that still
reference it. Use it to prove a source plugin is unused before uninstalling it.
