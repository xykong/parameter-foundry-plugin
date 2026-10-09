# Version 0.8.296 release verification

Release artifact: `dist/parameter-foundry-0.8.296.hpi`, 614,668 bytes.
Manifest version: `0.8.296`. SHA-256:
`81ad27c92945a626eabf34c58372ffe3247a7dd61ef4723758238f2b6be9c2ce`.
The exact archive was loaded into a disposable controller and its version and
digest were asserted before factory, declaration and browser verification.

| Gate | Result |
| --- | --- |
| Full `mvn -B -ntp verify`, Java 21 container | 365 tests passed, zero failures/errors/skips; static checks passed |
| Actual Extended Choice source factories | 9,792/9,792 match ParameterValue existence, exact environment export and errors |
| Real browser | 22 checks passed on Chromium `153.0.8010.12`, macOS, 1280x720, disposable Linux Jenkins `2.568.2`; [result](./results/extended-choice-transport-ui-lab.json) |
| Neutral declaration and caller checks | Static declarations bind/reload; upstream Pipeline StringParameterValue calls retain the same scalar environment export |
| Independent security review | No new security blocker; stored-definition export-policy validation must pass before upgrading a controller |

The source matrix covers `PT_SINGLE_SELECT`, `PT_MULTI_SELECT`, `PT_RADIO`
and `PT_CHECKBOX`, six option strings, two delimiters, six default literals and
34 default/CLI/standard/form cases per configuration. The exact source archive
is Extended Choice `388.ve7b_d0b_920e10`, SHA-256
`29a02cabeb90c1c6d7973b60dbc641cf2b10a319a057259165c8a82c864fe428`.
See the [empty transport milestone](./extended-choice-empty-verification-2026-10-10.md)
for case details and the [observed contract](../research/extended-choice-static-transport-contract.md).

The browser gate covers literal defaults, form strings/arrays, scalar selection,
radio defaults, complete static options, editable Rebuild, saved empty-transport
configuration, repeated empty HTTP fields and dedicated no-JavaScript submission.
All browser Jobs only echo or archive synthetic parameter values.

The release retains old persisted value fields and their fallback export path.
It does not automatically convert source-plugin historical values. Controller
upgrade preflight must validate existing definitions: Extended Choice multiple
compatibility requires submitted order with delimited/disabled environment export.
Ordinary and explicit typed submissions remain strict.

These results establish the characterized release contract, not every source
version, UI metadata variant, Job consumer, generating source or historical
reference. Desktop checks do not replace applicable mobile/device/accessibility
coverage. Per-Job migration and source-plugin retirement gates remain separate.
