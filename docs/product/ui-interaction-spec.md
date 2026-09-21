# Parameter Foundry `1.0` UI Interaction Specification

## 1. Scope

This specification covers:

- Freestyle/Pipeline parameter configuration;
- Build with Parameters;
- `foundryChoice` with `selectionMode`;
- Inline Provider editing;
- select, searchable combobox, radio, list, chips, and checkbox renderers;
- loading, empty, invalid, disabled, and configuration-error states.

Reactive, REST Provider, cascade, and Foundry Form interactions are later
specifications.

The interactive design companion is
[`../ui-prototype/index.html`](../ui-prototype/index.html).

## 2. Design Direction

The UI uses Jenkins page structure and design tokens with progressive
enhancement. It should feel like a first-party Jenkins capability, not an
embedded independent application.

The visual hierarchy is:

1. parameter name, required state, and concise description;
2. current/default selection;
3. search or available options;
4. validation and actionable recovery;
5. advanced details such as machine values and export preview.

No-JavaScript mode remains safe and submittable for Inline static options.

## 3. Configuration Page

### 3.1 Information Architecture

```text
Identity
  name
  description
Data
  Inline Provider option table
Behavior
  cardinality fixed by parameter type
  default mode and values
  empty/required/min/max/duplicate policy
Presentation
  Renderer and Renderer-specific appearance
Output
  environment export mode and preview
Compatibility
  standard API input mode and migration warnings
Preview
  live build-form preview, Pipeline snippet, API examples
```

Advanced sections are collapsed initially but show a warning badge when they
contain a non-default compatibility setting.

### 3.2 Inline Option Editor

Columns:

- value;
- label;
- description;
- group;
- disabled;
- disabled reason;
- tags;
- default-candidate hint for import only.

Behavior:

- stable row reorder;
- paste/import YAML or JSON into a preview, never directly into saved state;
- duplicate, empty, delimiter-conflict, and disabled-default errors shown on
  the affected rows;
- machine value changes show an API/build-history compatibility warning;
- label changes do not warn about value compatibility;
- 1,000+ rows use a virtualized editor or import workflow rather than a large
  unbounded Jenkins repeatable block.

### 3.3 Default Editor

The user chooses exactly one mode:

- no default;
- first enabled option;
- explicit option(s);
- Provider default, when supported after `1.0`.

The UI displays the canonical result. Option-level import hints never silently
override the chosen mode.

### 3.4 Preview

The preview uses the same server evaluation endpoint as the build form.
It displays:

- selected/default canonical value;
- environment export;
- standard REST example;
- Typed REST example;
- Pipeline snippet;
- validation errors and warnings.

Preview is never the only validation performed before save.

## 4. Build Page

### 4.1 Shared Structure

Every renderer displays:

- visible label and optional description;
- required marker;
- default indicator;
- current selected count for multiple choice;
- validation area with stable error association;
- optional details control exposing machine values.

Changing Renderer does not change selected values or submission encoding.
The generated form includes one
`__parameter_foundry_presence=<parameter-name>` metadata marker per Foundry
parameter. The marker is transport metadata and is not an Option value.

### 4.2 Single Choice

Select:

- recommended for short lists;
- includes an explicit empty option when optional with no selection.

Searchable combobox:

- search label, value, description, and tags;
- clearly separates selecting an existing option from creating a custom value;
- custom creation is unavailable in `1.0`;
- exact machine value remains visible in details when label differs.

Radio:

- recommended for a small set;
- optional/no-default parameters include a clear “None” action;
- disabled choices remain visible with a reason.

### 4.3 Multiple Choice

List:

- supports Shift/Control platform conventions where practical;
- always provides non-pointer keyboard actions.

Checkbox:

- options are presented in Provider order; the selected business value is an
  unordered set;
- chips are display/removal affordances, not an ordering control;
- Pipeline and environment output use stable machine-value lexical order, not
  the displayed chip order;
- group selection ignores disabled options and reports the resulting count.

Chips:

- selected chips have remove buttons with accessible names;
- available options remain searchable;
- removing the last chip creates an explicit empty selection, not absence;
- Backspace removes a chip only when search text is empty.

### 4.4 Batch Actions

Available actions:

- select all filtered enabled options;
- clear filtered enabled options;
- invert filtered enabled options.
- clear all selected options as a separate explicitly named action.

Select, clear-filtered, and invert operate on the current filtered result, not
hidden options outside the filter. Clear-all is the only action that affects
hidden selections. The confirmation/status message reports affected and
skipped-disabled counts.

## 5. State Machine

```text
INITIAL
  -> READY
  -> VALIDATING
  -> READY | INVALID

Provider-capable renderers:
  INITIAL -> LOADING -> READY | EMPTY | ERROR | STALE
  STALE -> REFRESHING -> READY | ERROR
```

State requirements:

| State | UI behavior |
|-------|-------------|
| loading | preserve existing safe selection, disable submit when validity is unknown |
| empty | distinguish valid empty result from Provider failure |
| error | actionable summary, retry when authorized, no stack trace |
| stale | show age/fallback reason and whether submission is allowed |
| invalid | focusable summary plus field-level message |
| disabled option | visible, not selectable, reason available |
| configuration error | block build and link configurators to the invalid setting |

`1.0` Inline Provider normally enters READY directly, but the shared state
model is implemented early to avoid Renderer rewrites in `1.1`.

## 6. Keyboard Interaction

Minimum behavior:

- Tab reaches the field, search, batch actions, selections, and error help in a
  logical order;
- arrow keys navigate listbox/combobox/radio options;
- Space toggles checkbox/radio where appropriate;
- Enter selects the active combobox option;
- Escape closes popups without discarding committed selection;
- Home/End move within the virtualized option set;
- focus remains visible in Jenkins light, dark, and high-contrast themes;
- validation moves focus to the first invalid parameter only after submit.

The implementation follows the appropriate WAI-ARIA listbox, combobox, radio,
and checkbox patterns and is verified with browser accessibility tooling plus
manual screen-reader checks.

## 7. Large Lists

For up to 10,000 Inline options:

- render at most 200 option DOM nodes;
- search uses a precomputed normalized index;
- updates do not replace the focused element unnecessarily;
- selected values remain accessible when filtered out;
- group headers participate in virtualization without becoming selectable;
- initial HTML has a bounded fallback representation and enhanced mode loads
  the full model through a protected endpoint when necessary.

Larger remote data sets require a future paged/search Provider contract rather
than sending an unbounded response.

No-JavaScript fallback is fully supported for basic static definitions with at
most 200 options. Larger definitions display a safe explanatory state and API
example rather than rendering an unbounded DOM. The enhanced browser UI is
required for interactive selection above that threshold.

## 8. Responsive Behavior

At 390 CSS pixels:

- parameter controls use one column;
- batch actions wrap without horizontal scrolling;
- option details remain reachable;
- chips wrap and preserve remove controls;
- configuration preview moves below fields;
- no fixed-width modal is required to submit.

## 9. Error Content

User-facing errors contain:

- what is invalid;
- why it is invalid;
- how to correct it;
- stable error code in expandable technical details.

Provider or parser internals, credentials, raw response bodies, and stack
traces are never shown.

## 10. Visual and Browser Evidence

Target-plugin Gallery coverage must include:

- each Renderer default and interaction state;
- optional empty and required error;
- disabled option and disabled default configuration error;
- duplicate configuration error;
- 10,000-option virtualized list;
- Rebuild with valid and stale historical values;
- no-JavaScript fallback;
- light, dark, high-contrast, and 390px views;
- keyboard focus sequence.

Competitor screenshots remain research evidence. Target screenshots are
generated from the real plugin and are release artifacts.
