# Extended Choice default compatibility verification

Candidate: `0.8.291-SNAPSHOT`, October 10, 2026. This is a verified development
milestone, not a production deployment or complete Extended Choice replacement.

The exact HPI has SHA-256
`2c91880b93e86b6afd5dc343b34536393087eedadb53040245deaf724e54b035`
and is 605,632 bytes. Its full Manifest version is
`0.8.291-SNAPSHOT (private-4a33493c-root)`: it was built before the milestone
commit. The disposable controller checked the complete version and archive
digest before evaluating any source/target comparisons. A tagged release needs
its own exact-artifact verification.

| Gate | Result |
| --- | --- |
| Full `mvn -B -ntp verify`, pinned Java 21 container | 347 tests passed; zero failures, errors or skips; static checks passed |
| New default integration cases | Eight passed, covering actual CLI/HTTP builds, typed omission versus explicit arrays, configuration save, build reload/default copy, canonical constraints, equality and ordinary-mode isolation |
| Actual source plugin comparison | Source `388.ve7b_d0b_920e10`, SHA-256 `29a02cabeb90c1c6d7973b60dbc641cf2b10a319a057259165c8a82c864fe428`; 72 multiple-selection default/omitted-input cases all match |
| Real browser | Eight checks passed on Chromium `153.0.8010.12`, macOS browser with disposable Linux Jenkins `2.568.2`, Java 21; [result](./results/extended-choice-transport-ui-lab.json) |
| Independent read-only review | No new security blocker; a canonical-constraint inconsistency was fixed and rechecked before final verification |

All inputs and Jobs in these gates are synthetic. The source/target factory
comparison does not schedule source business logic. Browser builds only write
and archive their parameter environment string.

## Expanded comparison and limits

The [source characterization](../research/extended-choice-static-transport-contract.md)
matrix has 816 cases: two selection types, two delimiters, six default strings
and 34 entry-point cases per configuration. Equality here requires both matching
ParameterValue existence and exact environment export. It does not assert
Pipeline `params` value-type equivalence.

| Source type | Entry point | Matches / cases |
| --- | --- | --- |
| Multiple | Default factory | 12 / 12 |
| Multiple | CLI | 132 / 132 |
| Multiple | Standard request | 108 / 108 |
| Multiple | Form JSON | 102 / 156 |
| Single | Default factory | 0 / 12 |
| Single | CLI | 12 / 132 |
| Single | Standard request | 12 / 108 |
| Single | Form JSON | 0 / 156 |

The candidate matches 378 cases overall, leaving 438 differences. Single
transports and literal form strings/arrays remain implementation work. Source
multiple values are strings; Foundry canonical multiple values remain lists,
so each Job's consumers still need review and adaptation where necessary.
The matrix uses `quoteValue: false`, options `a,b` and default constraints; it
does not establish parity for quoted exports, empty options or arbitrary
source variants. Configured Foundry canonical constraints are checked separately.

The browser gate preserves existing transport checks and adds exact whitespace
preservation during configuration save, supported-only UI defaults, native
selection export and exact omitted-standard-input export. It targets enhanced
controls after they become visible; the server fallback is hidden during UI
initialization. The disposable fixture configures its local Jenkins URL so CLI
WebSocket inspection can run.

The production migration gate remains closed for the remaining static Jobs
until their complete source and consumer contracts pass. No production
configuration or business Job was modified or executed by this verification.
