# ADR 0014: Technical Preview and GA Gates

- Status: Accepted
- Date: August 28, 2026

## Context

The `1.0` promise combines Jenkins persistence, Pipeline, standard REST, Typed
REST, CLI, third-party triggers, Rebuild, migration, accessibility, and large-
list UI behavior. Several depend on Jenkins integration details that must be
proved before the GA model and compatibility promise are frozen.

## Decision

### `0.1 Technical Preview`

`0.1` is a non-GA engineering milestone. It is not published as a stable
Update Center release and carries no persisted-model or API compatibility
promise.

It must produce executable Jenkins Test Harness/browser fixtures proving:

1. flat public data binding, XStream save/reload, copy, and rename;
2. authoritative validation of malformed XStream-loaded definitions;
3. Declarative/Scripted Pipeline Symbols, `params`, and downstream `build`;
4. browser presence markers plus standard REST repeated/empty decoding;
5. standard `buildWithParameters` invalid-input HTTP 4xx and unchanged queue
   state on the minimum baseline and release-target LTS;
6. CLI multi-value decoding and stable errors;
7. Typed API scheduling for mixed Core/Unified Jobs and rejection of
   unsupported definitions;
8. Rebuild Renderer preservation and current-definition validation;
9. CSP-safe JavaScript enhancement and bounded no-JavaScript fallback;
10. the Maven reactor boundary for the Extended Choice migration adapter.

The preview may use minimal styling and synthetic Jobs. It does not need the
complete `1.0` Gallery, migration UX, 10,000-option performance, or community
hosting.

### Standard Endpoint Decision Gate

The project does not claim `1.0` standard REST parity until item 5 proves that
all expected user validation failures:

- return a stable HTTP 4xx rather than HTTP 500;
- do not enqueue a build;
- work through public Jenkins extension/integration points;
- behave consistently on the declared Jenkins matrix.

If Jenkins does not expose a sufficient public integration point, the project
must contribute an upstream extension or revise the GA contract through a new
ADR. It must not install a global exception handler, patch Jenkins internals,
or silently fall back to HTTP 500.

### GA Freeze

Only after all `0.1` gates pass may the project:

- freeze `modelVersion: 1`;
- freeze API `/v1`;
- declare the `1.0` public Symbols stable;
- start the complete `1.0` feature and release-gate implementation.

Failed preview findings update the relevant ADR and scope before GA code is
treated as compatible.

## Consequences

- High-risk Jenkins integration assumptions are closed before compatibility is
  promised.
- `1.0` remains ambitious but is no longer the first executable milestone.
- Preview code and fixtures can be discarded or reshaped without migration
  support.
