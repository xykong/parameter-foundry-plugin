# Active Choices empty inline option verification — October 10, 2026

The unreleased `0.10.303-SNAPSHOT` extends single `active-choices` compatibility
with a selectable empty inline option. Its position is preserved. Choosing it
submits an existing empty string, rather than omitting the parameter or using the
first nonempty option. Ordinary modes still reject empty choices, and duplicate
empty options are invalid.

The shared evaluator distinguishes explicit typed `""` from typed `null`:
the first selects a declared empty option, while the second clears selection.
Typed membership validation, required/custom restrictions and migration shape
proofs remain in force. This does not enable reactive, filtered, flagged or
Provider-generated empty-option conversions.

## Harness

`mvn -B -ntp verify` passes 380 tests, with zero failures, errors or skips.
New regression cases cover leading, middle and trailing empty options, migration
preview order/defaults, CLI/standard/form literal values, typed empty versus null,
XML round trips, ordinary-mode rejection and duplicate-empty rejection.

## Browser and source oracle

The reproducible gate is:

```sh
uv run --script tools/ui-lab/scripts/check-active-choices-empty-options.py --skip-build
```

It compares UNO 2.8.10 synthetic literal-only sources with adapter-generated
Foundry definitions for dropdown and radio controls. It checks ordered options,
first dropdown versus unselected radio defaults, real empty-string form builds
and editable empty Core string history. Radio activation uses native keyboard
interaction, including empty-caption source controls.

Exact-HPI browser verification passes 132/132 source factory cases, six
source/target dropdown/radio pairs, twelve real empty-string form builds and
editable empty Core history. The HPI SHA-256 is
`6e55f4e607b1281e13f47e5df191232eeb359c4e9b8a1d5946fd6253a05e168f`; the browser is 153.0.8010.12.
The result is written to `docs/testing/results/active-choices-empty-options.json`.
No production Job or business build runs in this gate.

## Release and migration limits

This implementation is not deployed. Exact release artifact verification,
source/fallback review, source publication, controlled Job configuration changes
and normal business acceptance remain separate requirements. The neutral
fixtures do not establish an individual production Job's complete behavior.
