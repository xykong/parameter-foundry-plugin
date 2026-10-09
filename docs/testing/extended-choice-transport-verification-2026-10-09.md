# Extended Choice transport compatibility verification

Development candidate: 0.6.281-SNAPSHOT, October 9, 2026. All controller data and
inputs in this record are synthetic; production configuration and Job names stay
outside this repository.

The installed source contract distinguishes transports: standard requests use
repeated fields and filter unavailable choices, CLI comma-splits and trims, and
form JSON accepts a delimited string or an array. A delimited-only migration does
not preserve that distinction. Explicit `extended-choice` mode applies the
legacy decoding to static multiple choices while retaining canonical evaluation.

| Gate | Result |
| --- | --- |
| Full Maven verify | 322 tests passed with zero failures/errors and static checks passed, before the final UI policy field and scalar/snippet cases |
| Final related Maven verify | 16 tests passed after all changes, including policy configuration round-trip, reload, repeated export, legacy form types and generated Pipeline settings; SpotBugs and Spotless passed |
| Real browser | Four [checks](./results/extended-choice-transport-ui-lab.json) passed on Chromium 153.0.8010.12 and disposable Jenkins 2.568.2 Java 21 |

Browser verification saved the compatibility mode and preserved duplicate policy,
submitted selected values through native controls, submitted a legacy delimited
JSON string with repeated export, and verified that standard requests do not
expand a combined comma field. Each build archived its environment string for
exact comparison. No source Extended Choice plugin is needed for target behavior.

The compatibility mode is rejected for Providers, matrices and single selection.
Normal modes retain their existing decoding. Canonical values remain a sorted
set; compatible submitted environment export can preserve repeated supported
values. Source-compatible non-string/non-array form scalars remain empty.

Automation: `tools/ui-lab/scripts/check-extended-choice-transport.py --skip-build`
uses the current HPI and `extended-choice-transport-init.groovy`. Exact tagged
release verification must run again on the published artifact before production
deployment. Production migration and normal-operation acceptance are separate
gates; this candidate report does not prove that all production Jobs were migrated.

Release 0.6.282 from commit `fc6a13e` repeated all four browser checks with the
exact published HPI. Artifact SHA-256:
`ce9fcd6ee26675519e8a8079d970d000280c78db62066974254fb400dde5e99b`
(596,422 bytes). The release evidence is included in the linked JSON result.
