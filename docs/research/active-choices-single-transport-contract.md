# Active Choices single transport contract

Status: migration fidelity gap observed in `0.8.296`; compatibility implementation
and release verification remain open.

A disposable Jenkins `2.568.2` controller compared Active Choices `2.8.10` with
ordinary Foundry single choice factories. The source archive matches the installed
archive fingerprint: SHA-256
`3875250959e41c849894eb719b06b8e4a5fee4c6dae15d92a34b8415d9e0d35f`.
The source UI/factories used an operator-owned sandboxed constant list, not a
production script. No business build was scheduled and no production configuration
was changed. The installed controller's core patch level requires its own gate.

## Reproduction

Define a parameter `NODE` of type `PT_RADIO` or `PT_SINGLE_SELECT` with the
constant ordered list `built-in, agent-z, agent-a`, without selected/disabled flags.
Compare an ordinary Foundry single definition using the same option domain sorted
as the Node Provider does: `agent-a, agent-z, built-in`, default mode `first`.

For each source type, run one default factory, six CLI values, six standard field
arrays and nine form JSON payloads. Include absent/null input, empty strings,
whitespace, known values, unlisted label expressions, repeated fields, arrays,
booleans, numbers and objects. Request proxies model parameter fields and Core
StringParameterValue binding; this is not an actual HTTP build gate.

Across 44 cases, only four match ParameterValue existence, exact environment
export and error class. Differences are default factory 2, CLI 10, standard 10
and form JSON 18. These counts characterize the synthetic contract; they are not
an estimate of how often real users encounter a failure.

## Observed behavior

| Entry point | Source behavior in the characterized cases |
| --- | --- |
| Default factory | With no selected markers, use the first source option (`built-in`), independently of radio UI selection. |
| CLI | Retain strings literally, including whitespace and unlisted label expressions. Null/empty input produces an existing empty string value. |
| Standard request | Absent fields use the default. Exactly one field remains literal, including an empty field. Zero or multiple fields are rejected by the inherited SimpleParameterDefinition method. |
| Form JSON | Missing value produces an existing empty string; explicit JSON null produces the string `null`. Strings remain literal. Arrays use the source JSON library's comma join; numbers, booleans and objects become strings. |

Real Chromium inspection of the source form confirmed that an unflagged radio
list has no checked value and adds no empty/None radio. The unflagged dropdown
selects the first option. Both retain the source option order. The radio API
default is therefore different from its initial visible selection.

The ordinary Foundry path can normalize empty input to a null scalar, reject
unlisted label expressions, reject/judge array cardinality differently and choose
a different first option after sorting. Offering the same node-name set is not
proof of transport or default equivalence. Requiring a node selection would also
narrow the optional source behavior.

## Required implementation and verification

An explicit single Active Choices compatibility path must preserve literal
string transports, distinguish missing form values from JSON null, and keep
API defaults separate from UI defaults. It must not execute source scripts or
copy source HTML/JavaScript. Keep ordinary and typed explicit input strict.

A governed configured-node source must preserve the source's built-in-first
order and configured-node membership, including offline nodes where the source
includes them. Preserve the existing sorted Node Provider mode for its current
consumers. Verify zero-executor/absent-computer nodes separately; they were not
covered by this fixture.

Before migration, cover dynamic Provider ownership, default/failure behavior,
actual HTTP/CLI submissions, upstream Core StringParameterValue callers, old
StringParameterValue history, editable Rebuild, persistence and UI selection.
Selected/disabled markers, cached defaults, multiple choices, reactive dependencies
and source file-form payloads remain separate contracts. Each Job still needs its
source/generator and consumer audit; this record does not establish their parity.
