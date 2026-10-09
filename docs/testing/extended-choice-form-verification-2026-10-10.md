# Extended Choice literal form verification

Candidate: `0.8.292-SNAPSHOT`, October 10, 2026. This verifies multiple-selection
literal forms and historical values, not a complete source-plugin retirement.

The exact HPI is 609,027 bytes, SHA-256
`f8bebb646c0a1fc3c145bb501cc389bc426b8b07abe37f4246b8495352206305`.
Its full Manifest version is `0.8.292-SNAPSHOT (private-ca9d6b8a-root)`; it was
built before this milestone commit. The disposable controller checked the full
version and archive digest before comparing factories. Release artifacts need
their own exact verification.

| Gate | Result |
| --- | --- |
| Focused Maven verification before the old-XML case | 40 tests passed, including seven new form integration cases |
| Final full `mvn -B -ntp verify`, Java 21 container | 355 tests passed; zero failures, errors or skips; static checks passed |
| Actual source/candidate factory comparison | All 408 multiple-selection cases match ParameterValue existence and environment export |
| Real browser | 11 checks passed on Chromium `153.0.8010.12`, macOS browser and disposable Linux Jenkins `2.568.2`; [result](./results/extended-choice-transport-ui-lab.json) |
| Independent read-only review | No new security blocker; canonical-versus-raw consumer semantics remain a migration audit requirement |

The eight `ExtendedChoiceFormCompatibilityTest` cases cover exact strings and
joined arrays, mixed scalar and empty elements, explicit empty forms, raw queue
equality across entry points, strict ordinary/typed inputs, actual HTTP builds,
reload, editable Rebuild, canonical constraints and typed export order. The
historical fixture was extracted from a synthetic build made by the previous
verified `0.8.291` HPI. It contains only the older `legacyDefaultLiteral` field,
not `legacySubmissionLiteral`, and verifies loading, export, equality/hash and
Rebuild copying. Its SHA-256 is
`5279bbedd80d5ec3b0839686e9e11beedc9911b55f0bc0133016537fd16f07d4`.

Source comparison uses Extended Choice `388.ve7b_d0b_920e10` (archive SHA-256
`29a02cabeb90c1c6d7973b60dbc641cf2b10a319a057259165c8a82c864fe428`).
Across the full [816-case matrix](../research/extended-choice-static-transport-contract.md),
432 cases match. The 384 remaining differences are single-selection cases.
The multiple subset has 12 default, 132 CLI, 108 standard request and 156 form
cases, all matching. The matrix has options `a,b`, default constraints and
`quoteValue: false`; it does not cover empty options or every source variant.

Compatibility preserves raw strings independently of supported canonical lists.
Typed explicit inputs still require known strings. The mode now requires
submitted order and delimited or disabled environment export; canonical/JSON
export policies use ordinary input modes. Disabled, required, min/max and
duplicate constraints apply through shared canonical evaluation.

Legacy equality uses the raw string, including order, whitespace and unknown
content. The same raw string can have different canonical views across entry
points with a custom delimiter. Source multiple values are strings and Foundry
multiple values remain lists, so source consumers must be reviewed and adapted
where they need the legacy string. Environment equality alone cannot prove that
all Job functions are unchanged.

The browser gate also rebuilds a recorded unlisted literal through an editable
textbox and checks the resulting artifact. All Jobs, inputs and build steps in
these gates are synthetic. No production configuration or business Job was
modified or triggered; single and empty-option compatibility remain open.
