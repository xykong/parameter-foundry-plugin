# Extended Choice empty transport verification

Candidate `0.8.295-SNAPSHOT`, October 10, 2026. Full verification and the
source matrix/browser gates below passed on the identified artifact.

The characterized HPI is 614,761 bytes, SHA-256
`0061e83a9327d4268eba14adb3f50ad1f5e2a6abd22d7a76e58415fb1a763266`.
Full Manifest version: `0.8.295-SNAPSHOT (private-c83f3f06-root)`.
The disposable controller verifies the full version and archive digest before
invoking detached factories. Release artifacts require their own exact gate.

| Gate | Result |
| --- | --- |
| Initial focused verification | Passed before the fourth constraint/reload case was added |
| Initial full `mvn -B -ntp verify`, Java 21 container | 365 cases: 364 passed; one stale Chinese UI decision assertion failed. Assertion updated to the new transport-preservation text; corrected localized UI case passes |
| Final full verification | 365 tests passed, zero failures/errors/skips, static checks passed; final HPI reran all source/browser gates |
| Actual source/candidate factories | 9,792/9,792 match ParameterValue existence, exact environment export and errors; 2,448 cases per source type |
| Real browser | 22 checks passed on Chromium `153.0.8010.12`, macOS, 1280x720, disposable Linux Jenkins `2.568.2`; [result](./results/extended-choice-transport-ui-lab.json) |
| Independent review | No new security blocker; canonical constraints continue to apply to supported nonempty values |

Source plugin `388.ve7b_d0b_920e10` has SHA-256
`29a02cabeb90c1c6d7973b60dbc641cf2b10a319a057259165c8a82c864fe428`.
The expanded matrix extends the 816-case [source contract](../research/extended-choice-static-transport-contract.md)
to `PT_SINGLE_SELECT`, `PT_MULTI_SELECT`, `PT_RADIO` and `PT_CHECKBOX`, with
option strings `a,b`, `,a,b`, `a,,b`, `a,b,`, `,a,,b,` and `a, ,b`.
It retains two delimiters, six defaults and 34 cases per configuration.
Source browser inspection for `,alpha,,beta,` confirmed that select, multiple
select, checkbox and radio controls render only the two nonempty entries.
This inspection does not establish every whitespace-bearing UI variant.

The adapter derives `legacyEmptyChoiceAllowed` from leading/internal empty
source tokens after the source-style split. A trailing separator alone does not
enable it. CLI and repeated standard fields keep accepted empty token positions
in their literal export. Empty tokens are excluded from selectable/canonical
choices and strict typed input. A comma-only CLI split still invokes the default;
an explicit empty field still produces an existing empty ParameterValue.

Four `ExtendedChoiceEmptyTransportTest` cases cover both cardinalities, repeated
actual HTTP fields, config/reload persistence, required/minimum/duplicate
constraints, XML value reload and ordinary/typed isolation. The adapter regression
checks leading, internal, whitespace and trailing option separators. The five new
browser checks cover saved configuration, visible options, repeated empty HTTP
fields, historical Rebuild and the dedicated no-JavaScript submit button.

The first browser attempt used the ordinary Jenkins Build button with JavaScript
disabled and timed out. The test was corrected to use the existing Build without
JavaScript button; no production or plugin submission behavior was changed.

All definitions and Jobs are synthetic. Browser builds only print and archive a
parameter value. No production configuration or business Job was changed or run.
These gates do not prove every Job's consumer, generating source, historical
serialization, UI metadata or old-plugin retirement behavior. Source multiple
values remain strings while Foundry canonical multiple values are lists; consumer
review and exact-release production verification remain mandatory.
