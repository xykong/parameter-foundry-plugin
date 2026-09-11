# ADR 0001: Product Name and Identity

- Status: Accepted
- Date: August 28, 2026

## Decision

Use the following public identity:

| Item | Value |
|------|-------|
| Display name | Unified Parameters |
| Plugin short name / artifactId | `unified-parameters` |
| Repository | `jenkinsci/unified-parameters-plugin` |
| Java package | `io.jenkins.plugins.unifiedparameters` |
| Primary Symbols | `unifiedChoice` (single/multiple via `selectionMode`), `unifiedForm` |
| Tagline | One parameter model. Every Jenkins entry point. |

## Rationale

“Unified” describes the durable product promise: one model and one evaluation
contract across Jenkins UI and automation entry points. “Modern” remains a
product quality goal rather than part of the permanent name.

Since September 2026 `unifiedChoice` is the single choice entry point for both
single and multiple selection (`selectionMode: 'single'|'multiple'`). The former
`unifiedMultiChoice` definition and symbol were removed; there is no
compatibility alias, and every fixture and stored configuration uses the unified
entry.
