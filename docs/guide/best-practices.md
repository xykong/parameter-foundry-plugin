# Best practices

## Model the value, not the widget

Pick `selectionMode` first, then the renderer. The mode decides the value shape,
which renderers are valid, and how the value is exported; switching the renderer
later never changes the submitted value.

- One choice that feeds one field → single selection.
- A set of independent flags → multiple selection with `checkbox` or `chips`.
- Two coordinates that carry meaning, such as platform x target → matrix with
  `renderer: table`, and cell values that name both coordinates.

## Prefer Providers over copied lists

A list copied into ten Jobs drifts. Put it in a Provider once (global or Folder
scoped) and reference it by ID. You then change the list in one place, and every
Job form picks it up on the next resolve.

Provider-backed parameters bind a build to the option set the user saw, so a
value cannot silently change between form load and build.

## Name things for the Job author

- Parameter names are part of the Job contract: `ENVIRONMENT`, `TARGETS`, not
  `param1`.
- Option values are the machine contract. Keep them stable; change labels, not
  values.
- Give disabled options a reason. It is shown next to the control and read by
  screen readers.
- Use Groups and Tags so searchable lists stay navigable at a few hundred
  options, and so group-level selection works in bulk.

## Be explicit about defaults, required, and empty

- `defaultMode: none` means "submit nothing" — the safest default for anything
  destructive.
- `required: true` is enforced on every entry point, including REST and CLI.
- Explicit defaults are validated when the Job is saved, so a stale default fails
  the configuration instead of silently building with nothing.
- Multiple selection treats no selection as an empty list, which is different from
  an omitted parameter.

## Choose transports deliberately

- Browser and Pipeline are always available.
- For `buildWithParameters`, pick `repeated` unless the caller already sends a
  JSON array or a delimited string; the mode must match what the caller sends or
  the request fails with `PARAMETER_FOUNDRY_MALFORMED_INPUT`.
- CLI users pass a JSON array for multiple selection.
- Environment export: `delimited` is easy for shells, `json` is safer when values
  may contain the delimiter, and `none` is right when the value should stay out of
  the environment.

## Security

- Keep secrets out of choice parameters and out of Connection Profile headers; use
  Jenkins credentials.
- The core plugin never executes Job-supplied Groovy, JavaScript, or HTML, and
  never reads controller file paths. If a Job needs logic, put it behind a
  reviewed Script Provider catalog entry.
- Grant `Job/CONFIGURE` only to people who should change option lists, and grant
  the Script Provider permission sparingly.

## Performance and scale

- Searchable lists stay responsive at 10,000 options because only a bounded
  window is rendered.
- Providers are cached per configuration digest and dependency values; raise the
  cache TTL for slow backends instead of copying data into Jobs.
- Choose a failure policy deliberately: `fail-closed` for correctness,
  `last-known-good` for availability with a bounded staleness window.

## Accessibility and localization

- Prefer native `radio`/`checkbox` renderers when the option count is small; they
  keep the expected keyboard and screen-reader behaviour.
- `chips` add removable selections with accessible names; keep the selected-value
  summary visible for multi-step forms.
- Messages follow the browser locale (English, Chinese, Japanese, Korean today);
  option text is yours to localize.
- The no-JavaScript fallback still submits and still shows readable errors.

## Test the Job, not just the plugin

- Give each migrated or bespoke parameter one build that proves the value, one
  that proves a rejection, and one rebuild.
- Record acceptance cases with reproduction steps, expected and actual behaviour,
  and the verification you ran — the repository keeps them in
  [`../testing/acceptance-cases.md`](../testing/acceptance-cases.md).
