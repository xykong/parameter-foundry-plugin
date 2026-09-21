# ADR 0017: Product Renaming to Parameter Foundry

- Status: Accepted
- Date: September 21, 2026
- Supersedes: [ADR 0001](./0001-product-name-and-identity.md)

## Context

The initial working name "Unified Parameters" accurately stated the technical
contract of cross-entry-point parity. However, evaluation of community adoption,
developer ergonomics, and Jenkins ecosystem positioning demonstrated that the
name had significant shortcomings: a 6-syllable cadence, an internal/abstract
re-engineering connotation, and weak natural search transmission.

To maximize adoption, user trust, and clarity, the project conducted a
comprehensive brand evaluation against four explicit criteria:
1. Cadence and ease of pronunciation (朗朗上口).
2. Intuitive clarity and domain recognition (望文生义).
3. Industrial-grade trust and enterprise credibility (让人信任).
4. Viral discoverability and developer transmission (提高传播能力).

Detailed evaluation notes and discarded candidate analyses are archived in
[`docs/research/naming-and-brand-evaluation.md`](../research/naming-and-brand-evaluation.md).

## Decision

Adopt **Parameter Foundry** as the permanent public identity across all layers:

| Item | Value |
|------|-------|
| Display name | Parameter Foundry |
| Plugin short name / artifactId | `parameter-foundry` |
| Repository | `jenkinsci/parameter-foundry-plugin` |
| Java package | `io.jenkins.plugins.parameterfoundry` |
| Primary Symbols | `foundryChoice` (single/multiple via `selectionMode`), `foundryForm` |
| Tagline | Forge resilient parameters for modern Jenkins. |

### Architectural Consistency Rule: No Abbreviation Dual-Track

The project explicitly rejects mixing `Parameter` in user-facing branding with
abbreviated `ParamFoundry` in Java packages or artifact IDs.

1. **Cognitive Consistency**: Discarding abbreviation ambiguity prevents user
   confusion between package imports (`io.jenkins.plugins.parameterfoundry`) and
   the product name.
2. **Package Length**: The full-spelling package `io.jenkins.plugins.parameterfoundry`
   is 16 characters—shorter than the former `io.jenkins.plugins.unifiedparameters`
   (17 characters) and aligned with Jenkins community standard conventions.
3. **DSL Namespace**: Pipeline DSL symbols use `foundryChoice` and `foundryForm`,
   providing a concise, distinct namespace without verbosity.

## Consequences

- The project identity shifts from an abstract technical descriptor to an
  industrial-strength platform metaphor ("Foundry" = precision molds, typed
  schemas, safe governance, elimination of insecure ad-hoc scripts).
- Codebase coordinates (POM artifactId, root package, resources directory,
  and `@Symbol` annotations) will be migrated according to the execution plan.
- Documentation, guidebooks, and sample configurations will reflect the
  `foundryChoice` and `foundryForm` DSL symbols.
