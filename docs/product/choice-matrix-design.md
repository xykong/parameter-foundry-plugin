# Foundry Choice Matrix Design

## Status

- Status: Accepted; foundry entry implemented in the current snapshot
- Date: September 9, 2026 (updated September 10, 2026)
- Scope: `foundryChoice` option selection with `selectionMode` `single`/`multiple`

## 1. Goal

Foundry Choice is one public parameter entry point. `selectionMode` expresses the
value cardinality, `renderer` the selection surface, and `selectedDisplay` the
presentation of selected values. Foundry Choice also supports structured
two-dimensional option matrices. A
matrix preserves the meaning of rows and columns instead of flattening every
combination into a long list.

Example: platform x build target:

| Platform | a | b | c |
|---|---|---|---|
| Android | checkbox | checkbox | checkbox |
| iOS | checkbox | checkbox | checkbox |
| Web | checkbox | checkbox | checkbox |

Every selectable cell has an explicit stable machine value. The plugin never
creates machine values by concatenating row and column labels.

## 2. Public Model

There is one public parameter entry point:

```groovy
foundryChoice(
    name: 'TARGETS',
    selectionMode: 'multiple',
    renderer: 'table',
    selectedDisplay: 'chips',
    table: [
        rows: [
            [value: 'android', label: 'Android'],
            [value: 'ios', label: 'iOS'],
            [value: 'web', label: 'Web']
        ],
        columns: [
            [value: 'a', label: 'A'],
            [value: 'b', label: 'B'],
            [value: 'c', label: 'C']
        ],
        cells: [
            [row: 'android', column: 'a', value: 'android-a'],
            [row: 'android', column: 'b', value: 'android-b'],
            [row: 'android', column: 'c', value: 'android-c']
        ]
    ],
    rowSelectionMode: 'any'
)
```

`selectionMode` controls the global value cardinality. `renderer` controls the
selection surface. `selectedDisplay` controls the display of selected values.
These properties are independent.

### 2.1 Common fields

| Field | Values | Meaning |
|---|---|---|
| `name` | identifier | Required Jenkins parameter name |
| `selectionMode` | `single`, `multiple` | Global cardinality constraint |
| `renderer` | `select`, `searchable`, `radio`, `checkbox`, `list`, `table`, external IDs | Selection surface |
| `selectedDisplay` | `none`, `chips` | Selected-value presentation |
| `choices` | Option list | One-dimensional options |
| `table` | Matrix model | Two-dimensional options |
| `provider` | governed Provider | Dynamic options or matrix |
| `dependsOn` | parameter names | Reactive dependencies |
| `defaultMode` | `none`, `first`, `explicit` | Default policy |
| `required` | boolean | Whether an empty selection is invalid |
| `environmentExport` | `string`, `delimited`, `json`, `none` | Environment serialization |

### 2.2 Matrix fields

- `rows`: ordered row headers with stable `value` and display `label`.
- `columns`: ordered column headers with stable `value` and display `label`.
- `cells`: explicit selectable cells. Missing cells are rendered as unavailable,
  not synthesized.
- A cell contains `row`, `column`, and an explicit machine `value`.
- Cell labels, descriptions, disabled state, and tags are optional presentation
  metadata.
- Row and column values must be unique within their respective axes.
- Each cell coordinate may occur at most once.
- Cell machine values must be globally unique.

## 3. Selection Semantics

### 3.1 Single selection

`selectionMode: 'single'` returns `String/null` externally. The table renders
radio controls, regardless of whether the requested option-group style was
`radio` or `checkbox`. At most one cell can be selected.

`selectedDisplay: 'chips'` is valid and displays at most one chip.

### 3.2 Multiple selection

`selectionMode: 'multiple'` returns `List<String>` externally. The table renders
checkbox controls. `selectedDisplay: 'none'` hides the selected summary chips;
`chips` shows one removable chip per selected cell.

### 3.3 Row constraints

The global selection mode is not overloaded to express row rules. Optional
matrix constraints are explicit:

- `rowSelectionMode: 'any'`: any number of cells per row.
- `rowSelectionMode: 'at-most-one'`: zero or one cell per row.
- `rowSelectionMode: 'exactly-one'`: exactly one cell per row.

Future column constraints use the same explicit form. Constraint errors are
validated identically by UI, REST, CLI, Pipeline, Rebuild, and downstream builds.

## 4. Internal Implementation

All one-dimensional and matrix selections use one normalized selection model:

```text
ChoiceModel
  - Option nodes
  - optional row/column coordinates
  - selectedValues: ordered set internally
  - selectionMode constraint
  - row/column constraints
  - shared Provider snapshot
  - shared validation and canonicalization
```

The UI renderer consumes a normalized model. It does not own validation or
canonicalization. A table is a view over cells and does not introduce a second
ParameterValue type.

External values remain ergonomic:

- single: `String/null`;
- multiple: deterministic `List<String>`;
- environment variables: direct string for single, configured delimiter/JSON
  serialization for multiple.

## 5. Provider Contract

A governed Provider may return either a flat option set or a matrix model.
Provider responses must include stable values and bounded row, column, and cell
counts. A matrix response is snapshot-bound exactly like a flat response.

Providers must not infer coordinates from labels, execute arbitrary Job-supplied
scripts in the core plugin, or return raw HTML/JavaScript.

## 6. Accessibility and Responsive Behavior

- Use native `radio` for single and native `checkbox` for multiple.
- Table headers use semantic `th` cells with row and column header associations.
- Every cell control has an accessible name containing row label and column label.
- Keyboard navigation follows table order and preserves visible focus.
- Disabled or missing cells expose a reason where configured.
- On narrow screens, the table may scroll horizontally; controls must remain
  operable and headers must remain discoverable.
- Selected chips remain removable by keyboard and expose localized names.

## 7. API and Automation

Schema/resolve APIs expose:

- `selectionMode`;
- `renderer` and effective control type;
- `selectedDisplay`;
- matrix rows, columns, cells, and constraints;
- canonical value shape.

CLI, REST, Pipeline `build`, Rebuild, Job DSL, and JCasC use the same normalized
model. A malformed matrix, duplicate coordinate, unknown cell, disabled cell,
row-constraint violation, or stale snapshot fails before queue scheduling.

## 8. Validation Rules

- Empty matrix axes are invalid when `renderer: table` is selected.
- Every cell row and column reference must exist.
- Duplicate coordinates and duplicate machine values are invalid.
- Defaults must reference existing, enabled cells.
- Single selection rejects more than one value.
- Multiple selection applies min/max, duplicate, disabled, and row constraints.
- Missing cells are not selectable and are never included in canonical values.
- Labels may change without changing machine values.

## 9. Migration

Existing one-dimensional definitions map to `choices`. A source matrix must be
mapped explicitly to rows, columns, and cells. The migration assistant must not
guess a matrix from delimiter-separated labels.

A migration preview shows the flattened source, proposed matrix coordinates,
all semantic decisions, and a verification fixture before apply.

## 10. Acceptance Matrix

At minimum, tests cover:

- static 3x3 matrix rendering;
- single radio behavior and direct String output;
- multiple checkbox behavior and List output;
- `selectedDisplay: none/chips` in both modes;
- missing cells and disabled cells;
- duplicate coordinates and duplicate machine values;
- default and explicit-empty behavior;
- each row constraint;
- search/filter preserving coordinates;
- Provider matrix resolve, snapshot reuse, expiry, and scope isolation;
- REST, CLI, Pipeline, Rebuild, environment export, and downstream parity;
- keyboard, screen-reader names, responsive horizontal scrolling, no-JS fallback;
- bounded matrix size and no-queue behavior for every invalid case.

## 11. Non-goals

- No automatic inference of row/column semantics from arbitrary legacy scripts.
- No raw HTML table supplied by a Job or Provider.
- No separate matrix-specific ParameterValue class.
- No change to the meaning of machine values when labels or coordinates are
  renamed.
