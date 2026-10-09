# Parameter reference

Two public parameter types form the whole user-facing surface:

| Symbol | Purpose |
|---|---|
| `foundryChoice` | Select one value, several values, or cells of a two-dimensional table |
| `foundryForm` | Typed, structured input validated as one JSON object |

Both extend the Jenkins `ParameterDefinition` and expose their configuration
through `@DataBoundConstructor`/`@DataBoundSetter`, so they work identically in
the UI, Pipeline, Job DSL, and JCasC.

## Naming rules

- Parameter names must be non-empty and must not be
  `__parameter_foundry_presence` (reserved for the presence marker).
- Option values must be unique, non-empty, and must not contain control
  characters.
- Provider, profile, and layout IDs use letters, digits, dot, underscore, or
  hyphen and start with a letter or digit.

## `foundryChoice`

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
| `legacyDefaultLiteral` | exact string | unset | Static multiple Extended Choice compatibility: preserve omitted-input environment default separately from selectable UI defaults |
| `required` | boolean | `false` | Reject an empty value |
| `minSelections` / `maxSelections` | integer / integer | `0` / `-1` | Multiple selection; `-1` means unlimited |
| `duplicatePolicy` | `reject`, `deduplicate` | `reject` | Multiple selection |
| `standardApiInputMode` | `repeated`, `json-array`, `delimited`, `extended-choice`, `list-git-branches` | `repeated` | Transport decoding; Extended Choice mode requires static choices; List Git Branches mode requires a Provider-backed single choice without dependencies |
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
| `buildWithParameters` | `repeated` | `repeated`, `json-array`, `delimited`, or `extended-choice` per `standardApiInputMode` |
| Jenkins CLI | one argument | JSON array or configured delimiter; compatibility mode follows the source comma-split contract |
| Typed API (`api/v1/build`, `api/v1/validate`) | JSON string | JSON array |
| Pipeline `build` step | `foundryChoice` value | `foundryChoice` value with a list |

`extended-choice` supports a subset of the source plugin's static multi-select transports.
Standard requests accept repeated fields, trim each value and omit values outside
the configured choices; a combined comma string is one field and is not expanded.
CLI input splits on commas and applies the same trimming/filtering. Form JSON
retains a string literally or joins array elements with the delimiter, including
unlisted values, whitespace, empty elements and mixed scalar array elements.
Missing/null and non-string/non-array scalar or object values become an existing
empty value. Other modes keep their existing
decoding and validation behavior; the typed API continues to use arrays.

Compatibility requires `environmentExportOrder: 'submitted'` with
`environmentExport: 'delimited'` or `'none'`. Use `duplicatePolicy: 'deduplicate'`
for migration: canonical values remain a sorted set, while
repeated supported submissions can remain repeated in the legacy exported string.
This mode is unavailable to Providers and tables.
Exact literal defaults, blank-default omission and unlisted forms are gaps in
`0.7.288`; the unreleased `0.8.292-SNAPSHOT` candidate addresses those multiple
transports. Verify each source contract before
migration; see [the observed compatibility matrix](../research/extended-choice-static-transport-contract.md).

The `0.8.291-SNAPSHOT` milestone added `legacyDefaultLiteral` for exact omitted-input
exports. The migration adapter copies the source's nonblank default verbatim,
including order, whitespace, duplicates and unavailable placeholders. UI
`defaultValues` remain selectable values; canonical values remain lists. With
the literal unset, explicit default values supply their comma-joined string;
`defaultMode: none` produces no ParameterValue. Null/zero-length standard fields
and absent CLI/typed input use that factory. An explicit empty field or form
submission produces an existing empty value. Explicit typed arrays retain strict
validation. Literal history uses an
editable Rebuild textbox, matching the source plugin. Compatibility values use
their exact string for queue equality; canonical lists still drive typed output.
Typed multiple submissions honor the configured environment export order.
Configured disabled options, required/min/max selection and duplicate policies
still apply to the supported canonical default; a conflicting constraint rejects
the default rather than producing a value that fails later validation.

The `0.8.294-SNAPSHOT` candidate also supports static single source parameters.
CLI and standard requests filter/trim supported fields and join them into one
scalar, even when several values were supplied. Form strings remain literal and
arrays join without filtering; other scalar/object forms become an existing empty
string. A blank API default produces no ParameterValue even when the dropdown
selects its first option. Known UI defaults select the last matching DOM option;
radio controls without a matching default remain unselected. Compatibility does
not add a None option or truncate static options. Required/custom controls and
Providers/dependencies are unavailable. Typed explicit strings stay strict;
typed omission uses the source default. Source and browser gates remain required.

`list-git-branches` preserves literal revision submission for a Provider-backed single
choice without dependencies. CLI accepts non-empty strings literally; empty input uses
the API default. Standard requests read only the first field; a blank first field uses
the default even if later fields are nonblank. Form JSON joins arrays with commas and
uses the stored `defaultValue` literal when the result is empty. Explicit values and
explicit defaults do not query Git or require membership in the current ref list.
Only an omitted `defaultMode: first` needs options. Supplied snapshots still undergo
binding and expiry checks; literal submissions create no Provider provenance.
The typed API accepts string or null; null clears the value and omission uses the default.
Required validation and custom-value controls are unavailable in this compatibility mode.
Rebuild uses the historical revision in an editable text field, matching the source,
and does not query the Provider.
The compatibility browser form submits without a snapshot token because its literal
value is independent of the dropdown lifetime. Set Provider `cacheTtlSeconds: 0` to
match the source's fresh listing on each page. Explicitly supplied API snapshots still
require valid binding and expiry.

`gitSelectionMode` controls the browser dropdown separately: `default` selects an exact
stored-default match, then the first containing match, then the first option; `top`
selects the first option; `none` retains the source native first-option behavior.
The searchable Git compatibility renderer filters option text with a trimmed,
case-insensitive regular expression. Invalid expressions retain the previous list.
Filtering applies the source dropdown selection again and synchronizes the submitted
value. The native list retains all resolved refs, and literal/default form submissions
remain available while its advisory Git listing loads. Ordinary Provider choices keep
their existing search, rendering limits and build gate.

A source nonblank API default remains `defaultMode: explicit`, including when its
source dropdown is TOP. Review empty sets, unavailable repositories and renamed refs
against the source before migrating a Job.

## `foundryForm`

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

Every Job with Foundry parameters exposes a JSON API under
`…/parameter-foundry/api/v1/`:

| Endpoint | Purpose |
|---|---|
| `schema` | Parameter types, modes, renderers, and form schema |
| `resolve` | Current options and defaults for a Provider-backed parameter |
| `validate` | Canonical values for submitted input, without scheduling |
| `build` | Validate and schedule in one call |
| `providers/health` | Last Provider result per parameter |
| `import/preview` | Preview of a YAML/JSON option import |

Errors use stable codes such as `PARAMETER_FOUNDRY_UNKNOWN_VALUE`; see
[Troubleshooting](./troubleshooting.md).

## Binary uploads

Use `foundryFile` with workspace, base64 or stash delivery for file content. The earlier `foundryBase64File` and `foundryStashedFile` definitions remain compatible. Their Name, Description, limits, transports and Pipeline wrapper are documented in [File parameters](./file-parameters.md).
