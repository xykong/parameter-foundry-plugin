# ADR 0012: Typed API Parameter Adapters

- Status: Accepted
- Date: August 28, 2026

## Context

Jenkins Jobs commonly mix Parameter Foundry with Jenkins Core parameter
definitions. A typed build endpoint that accepts only Foundry definitions
cannot schedule such a Job completely, while silently ignoring other
definitions would apply unknown defaults and violate the API contract.

## Decision

### Complete-Job Contract

`/parameter-foundry/api/v1/build` is a complete Job scheduling API for Jobs
whose current parameter definitions all have a registered typed adapter.

The core plugin includes adapters for:

| Jenkins definition | Typed JSON | Omitted value |
|--------------------|------------|---------------|
| Core String | String | Core default |
| Core Text | String | Core default |
| Core Boolean | Boolean | Core default |
| Core Choice | String | Core default and Core choice validation |
| Foundry Choice | String/null | ADR 0005 |
| Foundry Multi-choice | Array | ADR 0005 |

Core adapters preserve Jenkins Core semantics. They do not reinterpret Core
Choice as Foundry Choice or change its implicit/default behavior.

The schema identifies for every parameter:

- name;
- definition class family;
- semantic owner: `jenkins-core` or `parameter-foundry`;
- typed JSON shape;
- whether the definition is supported;
- default availability without revealing secret values.

### Unsupported Definitions

The core plugin does not adapt:

- Password or Credentials parameters;
- File parameters;
- Run, Node, Label, or other domain parameter definitions;
- third-party parameter definitions without an installed reviewed adapter.

If any current Job definition is unsupported or two definitions have the same
name:

- `schema` reports the incompatibility without exposing secret defaults;
- `validate` and `build` return HTTP 409 with
  `PARAMETER_FOUNDRY_UNSUPPORTED_DEFINITION`;
- no build is scheduled, even when the request omits that parameter.

The endpoint never silently applies defaults from an unsupported definition.
Users use the standard Jenkins endpoint or install an explicit companion
adapter.

Unknown names in the request remain HTTP 400 input errors. A known but
unsupported Job definition is HTTP 409 configuration incompatibility.

### Adapter SPI

A typed adapter:

```text
supports(ParameterDefinition)
schema(ParameterDefinition)
decode(typed JSON)
defaultValue(ParameterDefinition)
validate(ParameterDefinition, decoded value)
createValue(ParameterDefinition, canonical value)
```

Adapters return typed values and stable errors to the scheduling coordinator.
They cannot schedule directly.

Companion adapters:

- declare supported definition classes and plugin version ranges;
- cannot replace a built-in Core or Foundry adapter;
- require their own security review for secret or credential-bearing types;
- must provide mixed-Job, default, invalid, and upgrade fixtures.

Password/Credentials adaptation is not accepted through the general SPI
without a separate ADR defining secret transport, redaction, audit, and build
record behavior.

### Request Rules

- `values` may include any supported Core or Foundry parameter by exact name.
- Omitted supported definitions use their owning implementation's default
  semantics.
- `providerSnapshots` may reference only Foundry dynamic definitions.
- Supplying a snapshot for a Core definition is a typed input error.
- Scheduling occurs only after all definitions decode and validate.

## Consequences

- Mixed Core/Foundry Jobs are first-class Typed API users.
- Unsupported third-party or secret definitions fail explicitly instead of
  being ignored.
- Core behavior remains owned by Jenkins Core while scheduling remains atomic.
