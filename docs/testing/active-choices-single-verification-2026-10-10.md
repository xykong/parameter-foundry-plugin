# Active Choices single compatibility verification

Unreleased candidate `0.9.298-SNAPSHOT`, October 10, 2026.
Final candidate HPI SHA-256:
`1d3887b98129c21e272c62d874b71b014256dbc6c9419e177ca4383f7054e85a`.
Manifest version: `0.9.298-SNAPSHOT (private-c38bf059-root)`.
The artifact predates the milestone commit; release artifacts require their own
exact gate. The public browser script verifies loaded version and archive digest.

| Gate | Result |
| --- | --- |
| Final full `mvn -B -ntp verify`, Java 21 container | 374 tests passed, no failures/errors/skips; static checks passed |
| Actual source factory comparison | 44/44 match ParameterValue existence, environment string and error class |
| Runtime Harness | Four cases cover actual HTTP/CLI, repeated-field rejection, uppercase aliases, strict typed input, cross-Job/unknown/expired/diagnostic/changed-definition snapshots, explicit zero-fetch and empty failure policy |
| Node Provider | Configured order and zero-executor/offline membership preserved; empty configured source invents no option; existing nodes/labels behavior retained |
| Browser/runtime lab | Source/static/dynamic radio and dropdown match; zero-cache token omitted; real HTTP literal and escaped/editable Core String Rebuild pass; old UNO history works after definition replacement; upstream Core String caller and scalar Pipeline consumption pass |
| Independent review | No remaining concrete security or target-shape blocker; heuristic script recognition still requires source/consumer review |

Reproduce with `tools/ui-lab/scripts/check-active-choices-single.py --skip-build`
after building the candidate. [Machine-readable result](./results/active-choices-single-ui-lab.json).
The browser is Chromium `153.0.8010.12`, macOS host, disposable Linux Jenkins
`2.568.2`. Source is UNO `2.8.10`, archive SHA-256
`3875250959e41c849894eb719b06b8e4a5fee4c6dae15d92a34b8415d9e0d35f`.
Only operator-owned sandboxed constant samples run; no source Job script or
business build is executed. Neutral echo/Pipeline fixtures are confined to the
loopback lab.

The successful source radio has no selected value while its API default is first;
the dropdown selects first. Source empty/unsupported-return samples and the
candidate empty-policy Provider produce an existing empty Core String value.
Literal/default values retain Core StringParameterValue type, environment aliases,
queue equality and old-history textbox semantics. They carry no Provider provenance;
supplied snapshot validation is independent of literal construction.

Scope remains unflagged single choices. Selected/disabled flags, filters/sorting,
cached source defaults, multiple choices, reactive dependencies and file-form
payloads require separate contracts. Do not treat the node skeleton's string
recognition as proof for arbitrary scripts. Exact release, production definition
preflight, fresh source/node/consumer checks, idle/trigger guards and rollback
remain required. Desktop checks do not substitute for applicable device/a11y
coverage. This candidate evidence does not establish complete plugin retirement.
