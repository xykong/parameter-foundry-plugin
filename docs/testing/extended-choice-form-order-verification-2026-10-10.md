# Extended Choice form submission order

## Reproduced defect

An Extended Choice multiple-choice form with nonalphabetic options submitted
selected values in declared option order. Foundry's enhanced form instead sorted
the generated fields alphabetically. With options `gamma,alpha,beta`, selecting
gamma and alpha exported `alpha,gamma` rather than `gamma,alpha`.

The shared input evaluator already preserved caller order. The difference arose
before evaluation, when the browser generated hidden form fields.

## Fix and boundaries

Extended Choice list and checkbox forms now generate selected fields in the full
resolved option order. Canonical values remain sorted sets. Explicit API arrays
retain caller order, including supported repetitions; ordinary canonical export
continues to sort values. No provider, permission or network capability changes.

## Development verification

The unpublished `0.11.305-SNAPSHOT` artifact is pinned by SHA-256
`467c7abec3fc4a39879d4d02aa64aa878d8f9a78e0f62ea24b855cd6d7ce5e36`.

- The [repository-owned browser suite](./results/extended-choice-transport-ui-lab.json)
  passes 25 checks on Chromium 151.0.7922.34 in an isolated Linux controller.
  Nonalphabetic list/checkbox cases cover desktop and mobile viewports,
  no-JavaScript submission, explicit API order and the canonical-export boundary.
- The targeted `ExtendedChoiceTransportCompatibilityTest` passes seven tests,
  including native list/checkbox option order, actual repeated-field submission,
  canonical values and build environment export.
- A private operational overlay verifies eight stored/producer variants with
  264 factory cases, eight neutral source-binding builds and eight real
  source/target form pairs with 32 neutral form builds. No business code is run.

Complete corrected `mvn -B -ntp verify` passes 381 tests with zero failures,
errors or skips. Exact tagged-release verification remains pending. These development results do not establish production deployment
or normal business operation acceptance. See UP-AC-047 and UP-ISSUE-016 in the
[acceptance index](./acceptance-cases.md).
