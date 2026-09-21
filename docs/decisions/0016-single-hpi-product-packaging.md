# ADR 0016: Single HPI Product Packaging

- Status: Accepted
- Date: September 4, 2026
- Supersedes: ADR 0011

## Context

Parameter Foundry is one product from the administrator's perspective. The
former multi-HPI layout required separately installing the core plugin,
companions, and migration adapters before the complete internal requirement
could be accepted. That installation boundary is an unnecessary barrier.

The code still needs internal boundaries for migration, script execution, Git
network access, and custom rendering. Those boundaries do not require separate
Jenkins plugin artifacts.

## Decision

The repository produces one Jenkins HPI:

```text
io.jenkins.plugins:parameter-foundry
```

The HPI contains the core parameter and Provider platform, migration adapters,
the accessible choice-grid Renderer, the governed Git refs Provider, and the
sandboxed Script Provider catalog.

Source plugins remain runtime-optional. Migration adapters inspect stable class
names and public getters without forcing legacy source plugins to be installed
when the corresponding source family is not used.

## Feature Switches

The global Parameter Foundry configuration exposes fail-closed switches for:

- migration adapters;
- sandboxed Script Provider;
- Git refs Provider;
- accessible choice-grid Renderer.

All switches default to enabled so a fresh installation has no feature
fragmentation. Disabling a switch prevents the capability from being used or
advertised; it does not remove persisted configuration or silently change
existing values.

## Security Boundaries

The single HPI does not weaken capability boundaries:

- core code still does not execute arbitrary Job-supplied Groovy or
  JavaScript;
- Script Provider execution remains sandboxed, catalog-scoped, audited, and
  bounded;
- Git refs access remains restricted to governed Connection Profiles and core
  outbound policy;
- Renderer adjuncts remain plugin-packaged and CSP-safe;
- migration adapters remain non-executing and do not copy secrets.

The single HPI changes packaging and installation ergonomics, not the
permission, validation, or fail-closed contracts.

## Consequences

- Administrators install and upgrade one product HPI.
- Internal acceptance and production rollout have one plugin lifecycle.
- A faulty optional capability is disabled by configuration rather than
  requiring a separate plugin uninstall.
- The core HPI has a larger dependency and code surface, so full-plugin
  startup, dependency, and upgrade tests become mandatory.
- A future external companion can still implement the existing SPI, but it is
  not required for the first-party capabilities.
