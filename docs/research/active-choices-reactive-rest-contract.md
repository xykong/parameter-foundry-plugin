# Active Choices reactive REST response contract

This record characterizes UNO Choice 2.8.10 using isolated synthetic definitions.
It is evidence for a governed replacement, not a claim that reactive migration
is implemented or accepted in production.

## Source idiom

The reviewed synthetic source performs a GET, parses JSON, and returns
`json.data ?: json`. Exceptions return a configured literal list. A cascade
references a dependency before the request, so an absent dependency takes the
exception fallback. No production script or business Pipeline is executed.

## Observed results

| Synthetic response | Source choices |
| --- | --- |
| `{"data":["gamma","alpha"]}` | `gamma`, then `alpha`, labels equal values |
| `{"data":{"gamma":"Gamma label","alpha":"Alpha label"}}` | `alpha`, then `gamma`, with mapped labels |
| `["gamma","alpha"]` | Exception fallback; property access on list entries fails |
| `{"code":1,"message":"Synthetic error"}` | `code` and `message` become option values, with string labels |
| `{"data":[],"code":0}` | The empty data list is falsey; the root map becomes choices |
| `{"data":null,"code":0}` | The root map becomes choices; its null label stays null |
| `[]` | Empty choices; no exception fallback |
| Invalid JSON | Exception fallback |
| `{"data":["gamma:selected","alpha:disabled"]}` | Suffixes remain in the returned choice map; UI interpretation is a separate gate |

For each case, a missing dependency and a synthetic transport exception returned
the configured fallback list. The cascade API default was its fallback's first
value, even when a dependency-bound UI request returned another list. Explicit
CLI values retained unknown strings and empty strings as Core String values.

Do not infer map order from JSON source order: the characterized Groovy JSON map
returned alphabetic keys in the synthetic example. Do not infer HTTP failure
from an application error object: the source script does not inspect a status
field inside a successful JSON response.

## Required replacement gates

- Keep response selection, list/map ordering, value/label conversion, empty
  responses, and exception fallback distinct. Generic JSON path mapping alone
  does not establish equivalence.
- Govern the endpoint, allowed path, query names, credentials and request/output
  limits through a Connection Profile; do not copy executable source scripts.
- Preserve literal Core String transports and the cascade's independent API
  default. Explicit inputs must not begin requiring live membership validation.
- Preserve dependency refresh, filtering and default UI selection. Resolve
  metadata, omitted dependencies, literal inputs, and optional snapshot behavior
  need separate browser and transport tests.
- Verify valid dependency values, missing values, unusual strings, application
  error responses, network failures and fallback recovery against the exact
  source plugin and candidate artifact.

Released Foundry 0.11.306 Active Choices single compatibility rejects dependencies, and
its generic REST parser does not implement the source idiom above. These are
capability gaps; changing Job configuration alone is insufficient.

The maintainer's private operational overlay retains the nine-case source oracle
and authenticated live read-only response shapes. It contains no replacement
production configuration or scheduled business builds. See UP-AC-048 in the
[acceptance index](../testing/acceptance-cases.md).

## Actual source browser characterization

Nine isolated Chromium forms also verify the visible choices. List responses
retain their order, maps use the characterized sorted key order, and the first
selectable choice is selected when no selected flag is returned.

- `gamma:selected` renders value/label `gamma` and selects it.
- `alpha:disabled` renders value/label `alpha` and disables it.
- A map's null label renders as an empty caption in the ordinary Choice control
  but as `null` in the Cascade control. These are separate source UI behaviors.
- An empty root array renders no options. Invalid JSON renders the exception
  fallback. Application error maps remain visible as choices.

The corrected browser run uses visible filter controls, excluding a hidden template
input. On `gamma,alpha`, uppercase `ALPHA` selects the `alpha` match, `.*` retains
both entries, and invalid `[` retains the preceding list. The source therefore
supports case-insensitive regular-expression filtering; plain substring search
alone is insufficient. Switching the synthetic dependency to an exception produces
`fallback`; restoring it re-creates the original options and first UI selection.
These source behaviors are verified. Target response parsing and source/target
filter/reactive browser parity remain pending.

## Non-sandbox response boundary

The target migration profiles use non-sandbox source scripts. Repeating the
response oracle with explicitly approved synthetic non-sandbox scripts establishes
a distinct boundary: a successful scalar return such as `data: true` produces
empty choices, rather than running the fallback. Invalid JSON and failed property
access still invoke the script's exception fallback.

The non-sandbox map may retain numeric label objects; the replacement compares
their displayed string labels, preserving null separately. This does not claim
identical internal map value classes.

For the pinned Groovy/UNO source, both `data: 1e-1000` and `data: -1e-1000` are
falsey and select the root map, producing the `data` option with `1E-1000` or
`-1E-1000` label; zero likewise selects the root. Do not replace this observed
truthiness with a different mathematical nonzero rule. The expanded oracle has
15 response cases.

## Additional non-sandbox browser metadata boundaries

Eleven additional isolated Chromium forms distinguish UI behavior from response
map interpretation. A selected marker on a later list item selects that item;
when multiple items carry the marker, the final item is selected in the single
select. A disabled first item without a selected marker gives the next enabled
item the initial selection. An item marked both selected and disabled stays
selected and disabled, in either tested marker order. Do not silently replace
that state with the first enabled item.

For a null map label, ordinary Choice shows an empty caption whereas Cascade
shows `null`. For an object array whose entries lack `data`, the deduplicated
null key renders as an empty value/caption in ordinary Choice and literal
`null` value/caption in Cascade. These differences also recur after dependency
failure and restoration. They require separate governed response modes rather
than applying the current Option label fallback to both controls.

Discriminating map cases show that state markers are interpreted from labels,
not keys. A label marker on the later option selects it, whereas the same marker
on its key alone does not. The ordinary renderer strips a key-only `:selected`
marker but retains a key-only `:disabled` marker; Cascade strips both without
disabling the option. If every option is disabled and none is explicitly selected,
no option is selected. Dependency exception fallback and restoration are verified
for all eleven cases.

These source-only observations were repeated on Jenkins 2.568.3 and UNO 2.8.10,
with all eleven browser results identical to the 2.568.2 baseline. Complete
target metadata, filtering, browser submission parity and exact production-core
release verification remain required before migration.

## Non-sandbox ordinary API default boundary

The same eleven synthetic responses on Jenkins 2.568.3 expose a separate ordinary
Choice default contract. The later selected item gives API default `alpha`; two
selected list entries give Core String default `gamma,alpha`, although the UI
single-select chooses only `alpha`. Both tested selected/disabled marker orders
give API default `alpha`. A label marker on the later map entry gives `gamma`,
whereas a key marker alone does not select it.

Without a selected marker on any label, a disabled first list entry gives API
default `gamma:disabled`; the all-disabled case also gives that literal. A
key-only disabled first map entry gives `alpha:disabled` even though neither
renderer disables that option. A null label gives default `alpha`; the null key
case gives an empty Core String. Every cascade omission independently gives the
synthetic exception fallback `fallback`. No business build is scheduled.

The replacement therefore needs source-derived API defaults separately from
UI values, enabled-state selection and the ordinary unflagged first-item rule.
Do not silently collapse multiple selected source defaults to one item, remove
all suffixes from omission defaults, or freeze a live ordinary response to a
literal. Target integration and form submission parity are still pending.

The expanded API oracle is also supported by the pinned source bytecode:
`findDefaultValue` tests labels for selection, joins the corresponding keys
after selected/disabled escaping, and otherwise returns the first raw key
unchanged. Thus a first key-only marker is not a reason to normalize an omission
default. Null keys use the empty string. Default-value caching is optional and
requires a separate migration configuration check.

The data-only response interpreter passes the fifteen-case non-sandbox textual
source comparison and a complete 387-test Maven verification. This establishes
the response interpretation component only; it is not yet connected to REST or
the required source-derived UI/API metadata.
