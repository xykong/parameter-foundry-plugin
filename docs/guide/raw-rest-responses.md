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
ID, custom headers, Jenkins proxy or redirects. Normal network, TLS, path/query,
connect/read/total deadline and response-byte policies still apply. Loopback,
link-local and metadata targets remain blocked. There is no script execution.
Raw connections use the same immutable DNS address set that passed network policy;
the request Host and TLS hostname remain the configured origin.
Set `format: 'auto'` and leave transforms empty. JSONPath/XPath fields are unused.

| Field | Configuration |
| --- | --- |
| `rawIgnoredResponses` | `foundryRawLiteral(value: 'null')` ignores exactly `null`, but not `null` followed by a newline. An empty list ignores nothing. |
| `rawTrailingChoices` | Ordered `foundryRawLiteral` rows appended to a body or a mapped failure value. |
| `rawFailureChoices` | Optional `foundryRawFailureChoice(kind: ..., value: ..., status: ...)` rows for classified transport failures. |

Literal rows preserve whitespace, empty strings and line breaks. Duplicate raw
values retain their first map position. For example, ignoring `null` and adding
the tail `topic/stable` yields only `topic/stable` for the exact sentinel, but
`topic/test` followed by `topic/stable` for a `topic/test` body.

## Failure choices and fallback

Failure choices are explicitly configured data. They never use an exception's
message or an HTTP error body. Supported kinds are `HTTP_STATUS`,
`CONNECT_TIMEOUT`, `READ_TIMEOUT`, `CONNECTION_REFUSED`, `EARLY_CLOSE`, and
`PREMATURE_EOF`. For `HTTP_STATUS`, `status: 0` matches any non-success status;
an exact 100–599 status overrides that generic row regardless of row order.
Other kinds require status zero. Duplicate kind/status pairs are rejected.

The value supports only the fixed tokens `{status}` and `{uri}`. For example,
an explicitly reviewed HTTP row could use `HTTP {status}: {uri}`. URI-bearing
rows expose the configured URL to parameter users, require a query-free target,
and should only be used for public, non-secret URLs. There is no general template
language. A mapped failure becomes an ordinary first source choice, followed by
the trailing values, and can therefore become the API default.

Unmapped outcomes, policy refusals, TLS verification failures and budget/size
rejections fail the Provider. Existing `fallbackOptions` and `failurePolicy`
handle those failures; arbitrary diagnostics do not become selectable values.
Fallback options are a separate reviewed list, not a trailing-choice list.

Connection failures, DNS and deadline behavior are bounded by the transport's
existing contract; no hard DNS/JVM-wide deadline is promised. A known fixed-length
short-body framing case retains received bytes; malformed chunked framing is a
typed failure. This mode does not make every native runtime/network exception
equivalent. Verify the original source's choices, defaults, errors, recovery,
browser behavior and consumers before replacing a production definition.
