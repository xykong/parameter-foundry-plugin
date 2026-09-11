# Design goals

## Why the plugin exists

Jenkins grew a parameter type per problem: one plugin for cascading choices, one
for checkbox lists, one for JSON, one for uploads, and so on. Each has its own
value semantics, its own API behaviour, and its own idea of which entry points it
supports. Teams end up with parameters that cannot be migrated, cannot be tested,
or behave differently in the browser and in Pipeline.

Unified Parameters provides **one parameter model with one evaluation path** for
choices and typed input, so the value a user picks in the browser is the value
Pipeline, CLI, REST, Rebuild, and downstream builds see.

## Principles

| Principle | What it means in practice |
|---|---|
| One semantics, many entry points | The browser, CLI, REST, Pipeline, Rebuild, and downstream builds call the same evaluation service. UI and API parity is a release gate, not a nice-to-have. |
| Values are canonical | Single selection is a string or `null`; multiple selection is an unordered list sorted by machine value; a form is a typed map or JSON object. Order of clicks is never part of the value. |
| Labels are presentation | Renaming a label, description, group, or matrix coordinate never changes a machine value, and never invalidates build history. |
| Fail closed by default | Unknown, disabled, stale, duplicate, or out-of-range values are rejected before a build is queued, with a stable error code. |
| Providers are governed | Network access goes through Connection Profiles with explicit allow-lists, credentials, timeouts, and size bounds. Provider results are cached, bounded, and attributed. |
| Core is inert | The core plugin never executes Job-supplied Groovy, JavaScript, HTML, or reads controller file paths. Scripting and custom rendering live behind reviewed companion contracts. |
| Documentation ships with the product | Every configuration field carries help inside Jenkins, and this guide explains the workflows around it. |

## What you get

- `unifiedChoice`: single, multiple, and matrix selection with searchable,
  list, checkbox, radio, and table renderers, inline or Provider-backed options,
  reactive dependencies, custom values, and per-mode transports.
- `unifiedForm`: typed schema with validation, layouts, conditional visibility,
  canonical JSON output, and governed environment export.
- Migration assistance for twelve source parameter families, with preview,
  classification, decisions, apply/clone, rollback, and a retirement dashboard.
- Typed API (`schema`, `resolve`, `validate`, `build`, `providers/health`,
  `import/preview`) that mirrors the UI.

## Non-goals

- No arbitrary script execution in the core plugin.
- No raw HTML or JavaScript supplied by a Job or Provider.
- No controller file paths read on behalf of a Job.
- No automatic conversion of business intent: where source logic cannot be proven
  equivalent, the migration assistant asks rather than guesses.
- No replacement of Jenkins’ native upload transport; file parameters stay native
  and keep working alongside Unified parameters.

## Release scope

| Version line | Scope |
|---|---|
| `0.1` | Technical preview: core types, providers, renderers, migration adapters, single-HPI packaging |
| `0.2` | Unified choice entry (`selectionMode`), matrix tables, localization, provider runtime bounds, unified value type |
| `0.3` | Public documentation and in-plugin help |

See [`CHANGELOG.md`](../../CHANGELOG.md) for the released changes and
[`../decisions/`](../decisions/) for the reasoning behind each contract.
