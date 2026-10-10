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
