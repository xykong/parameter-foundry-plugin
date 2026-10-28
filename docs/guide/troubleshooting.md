# Troubleshooting

## Where to look first

1. The parameter's own message in the build form (it names the failing field).
2. The build log — validation failures appear before scheduling.
3. `…/job/<name>/parameter-foundry/api/v1/providers/health` for Provider-backed
   parameters.
4. **Manage Jenkins → System Log** for Provider or migration stack traces.

## Error codes

Errors from browsers and REST return a stable code of the form
`PARAMETER_FOUNDRY_<CODE>`.

| Code | Meaning | Usual fix |
|---|---|---|
| `CONFIGURATION_INVALID` | The stored definition no longer validates | Open the Job configuration; the message names the field |
| `CONFIGURATION_CHANGED` | The definition changed since the option set was resolved | Reload the form |
| `REQUIRED` | No value where one is required | Pick a value, or clear *Required* |
| `UNKNOWN_VALUE` | Submitted value is not in the option set | Re-resolve options; the value may have been removed |
| `DISABLED_VALUE` | Value exists but is disabled | Choose another value |
| `DUPLICATE_VALUE` | The same value was submitted twice | Send it once, or set `duplicatePolicy: deduplicate` |
| `INVALID_TYPE` | Typed API received the wrong JSON shape | String for single, array for multiple, object for a form |
| `MIN_SELECTIONS` / `MAX_SELECTIONS` | Selection count outside the allowed range | Adjust the selection or the bounds |
| `MALFORMED_INPUT` | Transport shape does not match `standardApiInputMode` | `repeated` = one field per value, `json-array` = one JSON array field, `delimited` = one field split by the delimiter |
| `CUSTOM_VALUE_INVALID` | Custom value failed its pattern or length rule | Fix the value or the rule |
| `DEPENDENCY_INVALID` | A reactive parameter was submitted without its dependency values | Submit the dependency, or resolve options first |
| `DEPENDENCY_CYCLE` | Parameters depend on each other in a cycle | Break the cycle |
| `PROVIDER_UNAVAILABLE` | Provider call failed, timed out, or was rejected by policy | Check Connection Profile, allow-lists, and the health endpoint |
| `SNAPSHOT_INVALID` | Submitted option snapshot no longer matches the Job | Reload the form |
| `RATE_LIMITED` | Provider request budget exceeded | Retry after `Retry-After` seconds |
| `REQUEST_TOO_LARGE` | Payload exceeded the request bound | Send fewer values |
| `UNSUPPORTED_DEFINITION` | Typed API called for an unsupported parameter type | Native file and Core parameters are not part of the typed API |

## Common situations

**The build form shows "Loading options…" and never finishes.**
The Provider is slow, unreachable, or blocked by policy. Check the health
endpoint; if the failure is a policy rejection, compare the target address with
the Connection Profile allow-lists.

**REST returns 400 although the form works.**
The transport shape does not match. `repeated` expects one field per value,
`json-array` expects exactly one field containing a JSON array, and `delimited`
expects one field split on the delimiter.

**A value that used to work is now rejected.**
The option set changed: the value was removed or disabled. Either restore it or
update the caller. Provider-backed parameters refresh their snapshot on the next
form load.

**A default fails the configuration.**
Explicit defaults must reference an existing, enabled option or matrix cell.
After removing an option, update or clear the default.

**Rebuild shows a value that no longer exists.**
Rebuild prefills historical values but still validates them against the current
definition; the message tells you which value is no longer valid.

**A migration preview stops at class C or D.**
That is a statement about the source, not about this plugin: the preview lists the
business questions that must be answered before an equivalent can be generated.

**Options look different after switching the renderer.**
Only the presentation changes. The accepted value set, validation, and export stay
the same — check the *Selection mode* if the value shape itself changed.

## Reporting a problem

Open a [GitHub issue](https://github.com/xykong/parameter-foundry-plugin/issues)
with the plugin and Jenkins versions, input transport, synthetic reproduction
steps, expected and actual behaviour, and the verification you ran. Remove
credentials, real Job XML, private addresses, and uploaded business files. The repository keeps these in
[`../testing/acceptance-cases.md`](../testing/acceptance-cases.md).
