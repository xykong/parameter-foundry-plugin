# ADR 0001: Product Name and Identity

- Status: Superseded by ADR 0017
- Date: August 28, 2026
- Superseded by: [ADR 0017: Product Renaming to Parameter Foundry](./0017-product-renaming-to-parameter-foundry.md)

## Historical Context

This ADR records the original product name and identity adopted during early
development. It is retained for history and is superseded by
[ADR 0017: Product Renaming to Parameter Foundry](./0017-product-renaming-to-parameter-foundry.md).
Detailed evaluation of candidate names and discarded proposals is documented in
[`docs/research/naming-and-brand-evaluation.md`](../research/naming-and-brand-evaluation.md).

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
