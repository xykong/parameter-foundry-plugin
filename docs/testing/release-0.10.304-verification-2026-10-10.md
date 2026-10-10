# Release 0.10.304 verification — October 10, 2026

`mvn -B -ntp verify` passes 380 tests with zero failures, errors or skips.
The exact release HPI passes 132/132 UNO 2.8.10 source factory cases, six
adapter-generated dropdown/radio pairs, twelve empty-string form builds and
editable empty Core string history. All fixtures are neutral; no business Job
runs in the browser gate.

Artifact: `dist/parameter-foundry-0.10.304.hpi`, SHA-256
`b586c0690922f35d23298eb14abe8d340dc0919fad6672dc20e772203de8a6e5`, 621971 bytes.

Machine evidence: [exact release browser result](./results/active-choices-empty-options-release-0.10.304.json).
Reproduce with `uv run --script tools/ui-lab/scripts/check-active-choices-empty-options.py --skip-build`
after building this release.

Inline empty options require the new single Active Choices compatibility support.
Before downgrading to a version that rejects these definitions, restore their
configuration and generating source. Source/fallback, transport, consumer and
normal-operation checks remain per-Job requirements; these generic fixtures do
not establish a production Job's complete behavior.
