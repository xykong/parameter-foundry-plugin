# Whole-response REST choices

Use `foundryRestProvider` with `responseMode: 'active-choices-raw-scalar'` when a
reviewed source treats the entire HTTP response as one choice. This mode decodes
UTF-8, including replacement of malformed bytes, regardless of Content-Type or
its charset. It does not parse JSON, trim text, or split lines. A JSON document
is one literal value. An empty response is an empty source value.

Configure the Provider globally or on a Folder, then reference its ID from a
single `foundryChoice` with `renderer: 'select'`, `defaultMode: 'first'`, and
`standardApiInputMode: 'active-choices'`. Source selection/disabled suffixes and
the separate UI/API default rules use the existing Active Choices interpreter.
Copy source filterability with `activeChoicesFilterMode: 'none'` or `'regex'`
and copy the original filter length for a filtered source.
Set `cacheTtlSeconds: 0` for an uncached source: the next evaluation retries even
after a fallback, rather than reusing a previous failure for five seconds.

## Connection and interpretation

The Connection Profile must use direct, credential-free requests: no credential
ID, custom headers or Jenkins proxy. Redirects require the separate finite
destination permissions described below. Normal network, TLS, path/query,
connect/read/total deadline and response-byte policies still apply. Loopback,
link-local and metadata targets remain blocked. There is no script execution.
Raw connections use the same immutable DNS address set that passed network policy;
the request Host and TLS hostname remain the configured origin.
Set `format: 'auto'` and leave transforms empty. JSONPath/XPath fields are unused.

| Field | Configuration |
| --- | --- |
| `rawStatusPolicy` | Defaults to `http-status`, preserving failure mapping for every non-2xx response. Explicit `native-nonredirect-body` reads 304, 306, 308 and 302 without a Location header as bodies instead. |
| `rawRedirectTargets` | Optional complete static `foundryRawLiteral` URI rows. Following also requires the Profile redirect setting; every destination must pass Profile and network policy. |
| `rawIgnoredResponses` | `foundryRawLiteral(value: 'null')` ignores exactly `null`, but not `null` followed by a newline. An empty list ignores nothing. |
| `rawTrailingChoices` | Ordered `foundryRawLiteral` rows appended to a body or a mapped failure value. |
| `rawFailureChoices` | Optional `foundryRawFailureChoice(kind: ..., value: ..., status: ..., fingerprint: ..., matchUri: ...)` rows for classified transport failures. |

Literal rows preserve whitespace, empty strings and line breaks. Duplicate raw
values retain their first map position. For example, ignoring `null` and adding
the tail `topic/stable` yields only `topic/stable` for the exact sentinel, but
`topic/test` followed by `topic/stable` for a `topic/test` body.

## Failure choices and fallback

Failure choices are explicitly configured data. They never use an exception's
message or an HTTP error body. Supported kinds are `HTTP_STATUS`, `DNS_UNRESOLVED`,
`TLS_REJECTED`, `CONNECT_TIMEOUT`, `READ_TIMEOUT`, `CONNECTION_REFUSED`, `EARLY_CLOSE`,
`PREMATURE_EOF`, and `REDIRECT_LIMIT`. For `HTTP_STATUS`, `status: 0` matches any non-success status;
an exact 100–599 status overrides that generic row regardless of row order.
With `rawStatusPolicy: 'native-nonredirect-body'`, the four body-bearing cases
listed above bypass HTTP failure rows. Their body is subject to the same byte
limits, literal interpretation and trailing choices as a 2xx response. An empty
304 body can therefore become an empty first/default choice. Enable this only
after reviewing the source's status semantics.

This status policy alone does not follow redirects or enable Connection Profile
redirects. Following 300/301/302/303/307 requires the separate permissions below.
HTTP 305 proxy behavior and cross-origin redirects remain unsupported. Other
non-2xx responses retain their
existing classified failure choices. This is not complete URLConnection
compatibility. For example, a Provider snippet may include
`responseMode: 'active-choices-raw-scalar', rawStatusPolicy: 'native-nonredirect-body'`;
omit `rawStatusPolicy` to keep the existing behavior.

Introducing this policy conservatively changes raw Provider digests, including
the default policy: cached raw resolutions and old submission snapshots must
be refreshed. Mapped Provider digests are unaffected by this raw-only field.

Other kinds require status zero. An optional `matchUri` selects the complete URI
of the actual failed request. A blank selector matches any attempted URI. Exact
HTTP status takes priority over generic status, then exact URI takes priority over
a blank selector. Duplicate kind/status/fingerprint/URI declarations are rejected.
A selector grants no network permission.

TLS and REDIRECT_LIMIT rows require nonempty fixed text without braces.
For other rows, the value supports only the fixed tokens `{status}` and `{uri}`. For example,
an explicitly reviewed HTTP row could use `HTTP {status}: {uri}`. Rows using
`{uri}` interpolation expose the configured URL to parameter users,
require the original target and every declared redirect destination to be query-free, and should only be used for public, non-secret URLs.
This restriction is specific to `{uri}` interpolation. Administrator-declared
literal text is displayed as written: review its disclosure to parameter users
and never include credentials, sensitive query values or other secrets. A fixed
URI literal is not permission to interpolate arbitrary query-bearing URLs.
There is no general template language. A mapped failure becomes an ordinary first source choice, followed by
the trailing values, and can therefore become the API default.

`DNS_UNRESOLVED` classifies only a genuine `UnknownHostException` from the
resolver. Its value is the declared literal, never the resolver's diagnostic.
An empty address set, prohibited addresses and invalid configuration remain
refusals. A late DNS outcome or interruption cannot become a selectable failure
value. DNS cannot be forcibly interrupted, but its elapsed time counts against
the raw transport's total outcome budget. Uncached evaluations retry normally;
underlying JVM DNS caches retain their normal behavior.

Unmapped outcomes, policy refusals, unmatched TLS rejections and budget/size
rejections fail the Provider. Existing `fallbackOptions` and `failurePolicy`
handle those failures; arbitrary diagnostics do not become selectable values.
Fallback options are a separate reviewed list, not a trailing-choice list.

Connection failures, DNS and deadline behavior are bounded by the transport's
existing contract; no hard DNS/JVM-wide deadline is promised. A known fixed-length
short-body framing case retains received bytes; malformed chunked framing is a
typed failure. This mode does not make every native runtime/network exception
equivalent. Verify the original source's choices, defaults, errors, recovery,
browser behavior and consumers before replacing a production definition.

## Finite redirect destinations

Redirects are disabled by default. To opt in, enable `followRedirects` on the
Connection Profile and declare every possible destination in `rawRedirectTargets`.
These are exact, complete static HTTP(S) ASCII URIs, including the query. Up to
64 declarations of at most 8192 characters each are accepted. Wildcards, templates,
userinfo, fragments, non-normalized paths and encoded path separators/traversal
are rejected. Every declaration is checked against the Profile before initial DNS.
Changed or dropped queries require explicit reviewed destination declarations;
an allowed query key alone is insufficient permission.

For example, a Provider can declare
`rawRedirectTargets: [foundryRawLiteral(value: 'https://example.invalid/api/final?method=read')]`.
The Profile must independently permit that origin, path and query keys. Review
query values and disclosure before using this pattern with real services.

For HTTP 300/301/302/303/307, the last Location header is resolved using native
URL relative-reference semantics. Each next URI must match a declaration exactly
and pass the same-origin Profile checks and a fresh checked DNS resolution. Only
the validated addresses are used for that hop. No credentials, cookies or headers
are forwarded. Cross-origin or protocol changes, proxy responses (305), and
unsupported destination forms fail the Provider; this is not unrestricted native
URLConnection equivalence.

A chain has one total time budget, including DNS and cleanup; timeout cancellation
targets the currently active request. Resources close before the next hop. Up to
19 followed responses may precede a terminal response. A twentieth redirect yields
`REDIRECT_LIMIT` without sending a twenty-first request; only an explicit fixed
failure declaration can turn that outcome into a choice. An empty Location may
loop only if the resolved URI is explicitly permitted, and uses the same limit.
Structured REST retains its existing three-redirect limit.

Failures are matched and rendered against the actual final attempted URI.
Use `matchUri` with separate reviewed literals when initial and redirected errors
have different business meaning. Permission declarations never relax the
query-free `{uri}` rule. Redirect permissions and URI selectors participate in the
frozen raw configuration and digest, so changing them requires fresh resolution.
Browser forms, native source behavior and Job consumers need independent
migration acceptance; transport checks alone do not establish Job equivalence.

## Finite TLS rejection declarations

`TLS_REJECTED` is an opt-in compatibility mapping after default TLS validation
has rejected a connection. It never disables certificate or hostname validation.
Each row requires `status: 0`, a `fingerprint` of exactly 64 lowercase hexadecimal
characters, and a nonempty fixed `value` without `{` or `}`. There is no wildcard,
missing-fingerprint fallback or interpolation for a TLS row. Other outcome kinds
must leave the fingerprint empty.

For example, an administrator can declare a reviewed fixed value with
`foundryRawFailureChoice(kind: 'TLS_REJECTED', status: 0,
fingerprint: '<reviewed 64-character lowercase hex>', value: 'TLS unavailable')`.
The placeholder must be replaced with reviewed evidence before saving the row.
A mapped value can become the first choice and default, so review its business
meaning and disclosure to every parameter user.

A key hashes strict UTF-8 encoding of `tls-rejected-v1`, NUL, the exact exception
class name, NUL, and its message. Only exact `javax.net.ssl.SSLHandshakeException`
and `javax.net.ssl.SSLPeerUnverifiedException` at the default TLS upgrade boundary
qualify. Null messages, invalid Unicode and messages exceeding 2048 UTF-16 code
units do not produce a key. The original diagnostic, cause chain and stack frames
are not returned. Normal parameter resolution, errors and Provider health do not
provide fingerprint discovery; obtain declarations from administrator-owned,
reviewed evidence.

A fingerprint is a finite class/message compatibility key, not anonymization or
a classification of the verification reason. Identical class/message pairs may
have different underlying causes. Changes to the JDK, TLS/library implementation,
certificate or session behavior may invalidate a declaration. No cross-runtime
stability is promised. With multiple addresses, only the final connection outcome
is considered; an earlier TLS rejection cannot override later success or a
connection refusal. Interruptions and expired budgets remain rejections.

Unknown TLS errors still use the configured Provider failure policy. To ensure
that an unknown TLS error stops evaluation, use `failurePolicy: 'fail-closed'`
and leave `fallbackOptions` empty. A generic fallback or last-known-good policy
can otherwise return a previously reviewed option, including a production branch;
this must be an explicit source-specific decision.

Raw resolutions freeze failure/query rows and Connection Profile configuration
before computing the runtime digest and issuing asynchronous work. Policy, URI,
transport and final matching use that snapshot. Later administrator edits apply
to subsequent resolutions. Profile digests now include proxy selection, all
transport timeouts and the response-byte limit, for all Providers consuming that
Profile. Raw failure digests also include fingerprints. Existing caches and
submission snapshots may therefore be invalidated and require fresh resolution;
existing mapped-response parsing is unchanged.

The [real-plugin UI gallery](../ui-gallery/README.md#explicit-nonredirect-response-bodies)
shows the opt-in policy and empty/body defaults. Its browser report records the
exact artifact and separate desktop/mobile coverage; no production migration is
implied by component acceptance.

The [finite-redirect UI gallery](../ui-gallery/README.md#finite-whole-response-redirects)
records real destination-permission saving and final-failure/refusal screens.
The linked report separates fixed desktop/mobile viewport acceptance from
unverified touch/assistive behavior and from production migration.
