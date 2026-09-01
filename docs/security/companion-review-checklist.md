# Companion Plugin Review Checklist

This checklist is required before a reference companion is treated as a
Jenkins community release. Automated tests demonstrate the local contract but
do not approve the larger attack surface of an installed companion.

## Script Provider

- [ ] The plugin declares an explicit Jenkins and Unified Parameters
  compatibility range.
- [ ] Catalog configuration requires the dedicated Script Provider
  configuration permission.
- [ ] Every persisted script is sandboxed; non-sandboxed scripts are rejected.
- [ ] Script execution uses Jenkins Script Security and records an audit event
  without recording script text, credentials, or parameter values.
- [ ] The script binding exposes only the documented immutable dependency map
  and operation name.
- [ ] Source references are catalog IDs, not controller paths, URLs, or
  caller-supplied executable source.
- [ ] Output shape, option count, value length, tag count, and failure behavior
  are bounded and fail closed.
- [ ] Timeout, cancellation, Provider cache, snapshot, and permission behavior
  are verified with the core Provider runtime.
- [ ] The Jenkins security scanner and dependency advisories have been
  reviewed for the exact release artifacts.

## Renderer

- [ ] The plugin declares supported parameter families and API version `1`.
- [ ] The Renderer security contract is CSP-safe, escapes untrusted content,
  rejects Job-supplied HTML, and uses no inline script.
- [ ] Adjunct names resolve only to plugin-packaged static resources.
- [ ] JavaScript enhancement preserves native fallback submission and canonical
  server-side evaluation.
- [ ] Keyboard, screen-reader, forced-colors, mobile, and no-JavaScript
  behavior are verified in a real browser.
- [ ] The plugin cannot register a Renderer owned by the core plugin or bypass
  the trust registry.

## Release Evidence

- [ ] PCT passes for the declared Jenkins Core matrix.
- [ ] Disposable Docker canary passes with the exact HPI artifacts.
- [ ] Manual screen-reader review is signed off.
- [ ] Jenkins community/security review is complete.
- [ ] Upgrade, removal, and fail-closed behavior are documented.

The current reference companion artifacts have automated and Docker evidence in
[`pct-verification-2026-09-01.md`](../testing/pct-verification-2026-09-01.md)
and
[`source-plugin-docker-canary-2026-09-01.md`](../testing/source-plugin-docker-canary-2026-09-01.md).
