# ADR 0004: Release Scope and Migration Promise

- Status: Accepted
- Date: August 28, 2026

## Context

Earlier documents used “100% replacement” to describe both product capability
and migration tooling. That wording can be mistaken for binary compatibility,
zero-touch conversion, or preservation of old implementation details.

The `1.0`, `1.x`, and `2.0` scope also differed between the product,
functional, and research documents.

## Decision

### Migration Promise

Unified Parameters guarantees **business-requirement migration**, not drop-in
implementation compatibility.

For every supported use case from a source plugin, Unified Parameters must
provide a target design that:

- implements the same user intent and business outcome;
- preserves required input, validation, default, output, and automation
  semantics, unless the source behavior is unsafe or incorrect;
- is at least as usable, secure, observable, and maintainable;
- has a documented mapping and verification procedure;
- does not require retaining an unsafe implementation mechanism such as
  arbitrary Groovy, JavaScript, HTML, or controller file access.

“1:1” therefore refers to **user requirements and observable business
behavior**. It does not mean:

- binary compatibility with source Java classes;
- unchanged Job XML or Pipeline `$class` definitions;
- zero-configuration or zero-review migration;
- automatic conversion of code whose intent cannot be proven;
- guaranteed rendering of historical builds serialized with source-plugin
  classes after that plugin is uninstalled.

Migration friction is considered removed when the product supplies the target
capability, a deterministic mapping, generated target configuration, explicit
decisions for semantic differences, and a verification fixture. Reusing the
old implementation format is not required.

### Release Roadmap

| Release | Committed scope |
|---------|-----------------|
| `0.1 Technical Preview` | non-GA executable spikes for persistence/data binding, malformed definition validation, Pipeline/downstream values, form presence, standard REST 4xx/no-queue, CLI, mixed Core/Unified Typed API, Rebuild, CSP/no-JS, and migration reactor packaging |
| `1.0 Static Choice Foundation` | `unifiedChoice`, `unifiedMultiChoice`, static `choices` adapted to Inline Provider, select/search/radio/list/chips/checkbox renderers, explicit defaults and empty semantics, known/disabled/required/min/max validation, all applicable submission-entry parity, and an installable Extended Choice static migration adapter for discovery/preview/target generation |
| `1.1 Governed Provider Platform` | Global/Folder/Managed Config/REST JSON/XML Providers, Connection Profiles, editable/custom values, cache/health/fallback/security controls, Job DSL/JCasC integration, and safe apply/clone/rollback for static migration |
| `1.2 Reactive and Cascade` | dependency graph, Provider snapshots, reactive resolution, cascade/tree parameters, and dynamic migration adapters |
| `2.0 Complete Requirement Coverage` | Unified Form, controlled Renderer and Script Provider companions, remaining source adapters, and retirement dashboard |

The following are explicitly **not `1.0` scope**:

- Global or Folder Provider;
- REST, XML, JSONPath, or XPath Provider;
- editable/custom values;
- reactive or cascading parameters;
- `unifiedForm`;
- standalone `unifiedText` and `unifiedBoolean`;
- in-place or batch production Job migration;
- automatic source repository modification.

Jenkins Core String, Text, and Boolean parameters remain the recommended
implementation until a later Unified Parameters type provides a measurable
benefit.

## Release-Gate Rule

A release gate references the requirement IDs explicitly assigned to that
release. It must not rely on phrases such as “all entry points” or a numeric
count without a maintained test matrix.

The roadmap in this ADR is the source of truth. Other documents summarize it
and must link back here rather than define a competing scope.

Public data binding is fixed by ADR 0009, REST governance by ADR 0010, and
migration adapter packaging by ADR 0011. Mixed Typed API behavior is fixed by
ADR 0012, loaded-definition validation by ADR 0013, and GA integration gates
by ADR 0014.

## Consequences

- `0.1` validates high-risk Jenkins integration assumptions before public
  model/API compatibility is frozen.
- `1.0` implements the static product promise only after the preview gates
  pass.
- Functional replacement remains the long-term product promise.
- Historical build compatibility and source-configuration conversion are
  handled as explicit migration concerns, not implied by the product promise.
