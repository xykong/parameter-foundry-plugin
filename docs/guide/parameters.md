# Parameter reference

Two public parameter types form the whole user-facing surface:

| Symbol | Purpose |
|---|---|
| `unifiedChoice` | Select one value, several values, or cells of a two-dimensional table |
| `unifiedForm` | Typed, structured input validated as one JSON object |

Both extend the Jenkins `ParameterDefinition` and expose their configuration
through `@DataBoundConstructor`/`@DataBoundSetter`, so they work identically in
the UI, Pipeline, Job DSL, and JCasC.

## Naming rules

- Parameter names must be non-empty and must not be
  `__unified_parameters_presence` (reserved for the presence marker).
- Option values must be unique, non-empty, and must not contain control
  characters.
- Provider, profile, and layout IDs use letters, digits, dot, underscore, or
  hyphen and start with a letter or digit.

## `unifiedChoice`

### Fields

| Field | Values | Default | Notes |
|---|---|---|---|
| `name` | identifier | — | Parameter name |
| `selectionMode` | `single`, `multiple` | `single` | Decides the value shape and the valid renderers |
| `description` | text | — | Shown under the parameter title |
| `choices` | option list | empty | Inline options; mutually exclusive with a Provider |
| `providerId` | provider ID | empty | Shared Provider that supplies options (or a matrix) |
| `dependsOn` | parameter names | empty | Reactive inputs; requires a Provider |
| `renderer` | see below | `select` / `checkbox` | Selection surface |
| `selectedDisplay` | `none`, `chips` | `none` | Multiple selection only |
| `defaultMode` | `none`, `first`, `explicit` | `none` | How an omitted value resolves |
| `defaultValue` | value | — | Single selection, `explicit` only |
| `defaultValues` | value list | empty | Multiple selection, `explicit` only |
| `required` | boolean | `false` | Reject an empty value |
| `minSelections` / `maxSelections` | integer / integer | `0` / `-1` | Multiple selection; `-1` means unlimited |
| `duplicatePolicy` | `reject`, `deduplicate` | `reject` | Multiple selection |
| `standardApiInputMode` | `repeated`, `json-array`, `delimited` | `repeated` | How `buildWithParameters` decodes the parameter |
| `delimiter` | text | `,` | Used by `delimited` input and export |
| `allowCustomValue` | boolean | `false` | Single selection: accept a value outside the option list |
| `customValuePattern` | regex | empty | Full-match rule for custom values |
| `customValueMaxLength` | 1–4096 | `256` | Length bound for custom values |
| `environmentExport` | `string`, `delimited`, `json`, `none` | `string` / `delimited` | Environment variable format |
| `environmentExportOrder` | `canonical`, `submitted` | `canonical` | Multiple selection: order used for the environment export. `canonical` writes the sorted set, `submitted` keeps the caller's order so a migrated Job exports the same string as its source control. The canonical value itself is always the sorted set |
| `table` | matrix | empty | Rows, columns, cells; cells are selectable values |

### Renderers

| Mode | Built-in renderers | Notes |
|---|---|---|
| `single` | `select`, `searchable`, `radio`, `list` | `searchable` adds search, grouping, and custom values; `list` shows the options in a list box |
| `multiple` | `list`, `checkbox`, `table` | `table` renders the matrix model |
| either | `accessible-choice-grid`, trusted external IDs | Reviewed companion renderers only |

Switching a renderer never changes the submitted value; every renderer shares one
server-side evaluation path.

### Selection semantics

- `single` returns one string, or `null` when the parameter is optional and left
  empty. `radio` offers an explicit **None** entry when the parameter is not
  required.
- `multiple` returns an immutable list sorted by machine value, so membership —
  not click order — is the value. Duplicates are rejected by default; set
  `duplicatePolicy: deduplicate` to collapse them instead.
- `minSelections`, `maxSelections`, and `required` are validated on every
  transport before a build is queued.
- Disabled options and disabled matrix cells are rejected even when submitted
  directly through REST or CLI.

### Matrix (`renderer: table`)

- `rows`, `columns`, and `cells` define the grid; each cell carries an explicit
  machine value.
- Missing cells are rendered as unavailable and can never be submitted.
- Disabled cells stay unselectable and expose their configured reason.
- Labels may change without changing machine values; renaming a coordinate is a
  visual change only.
- The accepted option set is the inline options plus one entry per cell, so a
  matrix-only parameter needs no inline list.

### Defaults

| `defaultMode` | Single | Multiple |
|---|---|---|
| `none` | `null` | empty list |
| `first` | first enabled option | list with the first enabled option |
| `explicit` | `defaultValue` | `defaultValues` |

Explicit defaults are validated when the Job is saved and again before a build;
a default that no longer exists makes the configuration invalid rather than
silently empty.

### Environment export

| Mode | Single | Multiple |
|---|---|---|
| `string` | the value, omitted when `null` | not applicable |
| `delimited` | not applicable | values joined by `delimiter` |
| `json` | not applicable | JSON array |
| `none` | nothing exported | nothing exported |

`environmentExportOrder` only changes the string written to the environment; the canonical value
stays the unordered, sorted set. Migrations from a control that exported the submitted order set it
to `submitted`, and a value without the field behaves as `canonical`.

### Input decoding

| Entry point | Single | Multiple |
|---|---|---|
| Browser form | the selected control | repeated fields, one per value |
| `buildWithParameters` | `repeated` | `repeated`, `json-array`, or `delimited` per `standardApiInputMode` |
| Jenkins CLI | one argument | JSON array argument, or the configured delimiter |
| Typed API (`api/v1/build`, `api/v1/validate`) | JSON string | JSON array |
| Pipeline `build` step | `unifiedChoice` value | `unifiedChoice` value with a list |

## `unifiedForm`

Structured input for cases where several related values belong together.

| Field | Values | Default | Notes |
|---|---|---|---|
| `name` | identifier | — | Parameter name |
| `fields` | field list | empty | Typed schema |
| `layouts` | layout list | empty | Presentation only |
| `renderer` | `schema` | `schema` | Built-in CSP-safe renderer |
| `outputMode` | `map`, `json` | `map` | Pipeline value shape |
| `environmentExport` | `json`, `none` | `json` | Complete-form export |
| `description` | text | — | Shown under the title |

### Field types

`string`, `text`, `integer`, `decimal`, `boolean`, `choice`, `multi-choice`,
`date`, `time`, `content`, `object`, `array`.

Per-field constraints: `required`, `secret`, `defaultValue`, `minLength`,
`maxLength`, `pattern`, `minimum`, `maximum`, `minItems`, `maxItems`, `itemType`,
`choices`, and `environmentVariable`.

- Secret fields are masked in output and can never be exported, individually or
  as part of the complete-form export.
- Nested objects and arrays are configured through Pipeline, Job DSL, or JCasC;
  the browser editor intentionally avoids an unbounded recursive form.
- Validation errors name the field that failed and are localized in the same
  way as choice parameters.

### Layouts

`section`, `grid`, `tabs`, and `conditional` layouts arrange fields for
presentation. `fieldNames` selects which fields a layout shows, `columns` sizes a
grid, `collapsed` starts a collapsible layout closed, and
`visibleWhenField`/`visibleWhenValue` hide a conditional layout until the
controlling field matches.

## Typed API

Every Job with Unified parameters exposes a JSON API under
`…/unified-parameters/api/v1/`:

| Endpoint | Purpose |
|---|---|
| `schema` | Parameter types, modes, renderers, and form schema |
| `resolve` | Current options and defaults for a Provider-backed parameter |
| `validate` | Canonical values for submitted input, without scheduling |
| `build` | Validate and schedule in one call |
| `providers/health` | Last Provider result per parameter |
| `import/preview` | Preview of a YAML/JSON option import |

Errors use stable codes such as `UNIFIED_PARAMETER_UNKNOWN_VALUE`; see
[Troubleshooting](./troubleshooting.md).
