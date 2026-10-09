# Extended Choice single transport verification

Candidate `0.8.294-SNAPSHOT`, October 10, 2026. Static single transport and
selection behavior are verified for the characterized source contract.

The exact HPI is 612,948 bytes, SHA-256
`f6a29bc547f6c4021c83c4c553b485317ffe06f6b26872650795be3b1bce3047`.
Full Manifest version: `0.8.294-SNAPSHOT (private-af025078-root)`. The artifact
predates the milestone commit; release artifacts require their own exact gate.
The disposable controller checked its full version and archive digest.

| Gate | Result |
| --- | --- |
| Initial focused verification | 35 tests passed, including four new single cases |
| Final full `mvn -B -ntp verify`, Java 21 container | 360 tests passed, zero failures/errors/skips and static checks passed |
| Actual source/candidate factories | 816/816 match ParameterValue existence and exact environment export: 408 single and 408 multiple cases |
| Real browser | 17 checks passed on Chromium `153.0.8010.12`, macOS browser, disposable Linux Jenkins `2.568.2`; [result](./results/extended-choice-transport-ui-lab.json) |
| Independent review | No new security blocker; disabled-option configuration is rejected explicitly and misleading truncation indicators were corrected |

Source plugin `388.ve7b_d0b_920e10` has SHA-256
`29a02cabeb90c1c6d7973b60dbc641cf2b10a319a057259165c8a82c864fe428`.
The [matrix](../research/extended-choice-static-transport-contract.md) uses
options `a,b`, two delimiters, six default literals, default constraints and
`quoteValue: false`. Single CLI/standard requests trim/filter supported fields
and join them into a scalar. Forms preserve literal strings and joined arrays.
Blank omitted defaults produce no ParameterValue, while explicit empty forms
produce an existing empty scalar. Typed explicit strings remain strict.

Five `ExtendedChoiceSingleCompatibilityTest` cases cover default/UI separation,
joined CLI and repeated standard fields, forms, strict typed input, actual
HTTP/CLI builds, configuration save/reload and editable historical Rebuild.
Single compatibility rejects Provider/table/dependency, required, custom-value
and disabled-option configurations. Ordinary single modes retain strict behavior.

The six added browser checks verify first-option UI selection independent of an
unlisted API default, last matching DOM option selection for dropdown/radio,
radio with no default remaining unselected, all 240 static options and editable
Rebuild preserving a quoted HTML literal as text. Static radio uses ready native
controls; the test no longer waits for an enhanced container that is intentionally
absent. Source radio markup was inspected to confirm its default-map behavior.

All inputs and Jobs are synthetic. Source factories do not execute source business
logic; browser builds only print and archive their parameter value. No production
configuration or business Job was changed or triggered.

This does not establish empty-option or all-source-variant compatibility, every
Job's consumer/generator behavior, or retirement/history safety. Multiple source
values are strings and Foundry canonical multiple values are lists; consumer
review remains necessary. Remaining migration and plugin removal gates stay open.
