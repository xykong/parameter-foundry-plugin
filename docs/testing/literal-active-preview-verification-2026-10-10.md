# Literal Active Choices preview verification

October 10, 2026. Snapshot `0.9.302-SNAPSHOT` with a private build qualifier.
HPI SHA-256: `445e3af19d25d4c64319a68464936bf4b6daab97d02f8473faf5b3ca51b1ab98` (621,707 bytes).
This snapshot is not a published or deployed release.

Full `mvn -B -ntp verify` passes 378 tests without failures, errors or skips.
Adapter regressions cover dropdown/radio defaults, Core string values,
descriptions, generated snippets, and exclusion of empty/flagged/filtered
choices, maps, interpolation and unsupported escapes.

The exact snapshot HPI passes 44/44 source/target transport cases with targets
constructed by `ActiveChoicesMigrationAdapter`, not handwritten equivalents.
Real Chromium 153.0.8010.12 on macOS verifies the two adapter-generated controls:
radio remains unselected and dropdown selects the first option. The isolated
controller is Jenkins 2.568.2 and the source UNO plugin is pinned to 2.8.10.
The shared configured-node, literal HTTP, editable Core String Rebuild and
neutral upstream caller gates also pass.

A neutral Pipeline executes only the generated parameter snippet and an echo.
It verifies description apostrophe/CR/LF round-trip and a Core String default
through actual Pipeline symbol binding. Adapter inspection does not execute
source Groovy; source oracle samples are operator-owned constants. No business
Job or production configuration is changed.

[Machine-readable result](./results/literal-active-preview-ui-lab.json).
The base browser contract is reproducible with
`tools/ui-lab/scripts/check-active-choices-single.py --skip-build`; this record
adds adapter-generated definitions and generated-snippet binding to that gate.

Compatibility is intentionally limited to proven plain string lists without
maps, interpolation, escapes, markers, empty options, filtering, cached defaults
or dependencies. Excluded shapes need their own source contracts. These checks
do not certify per-Job migration, shared-source publication, rollback, historical
plugin removal, mobile or accessibility coverage. Exact release gates remain.
