# ADR 0013: Definition Validation Lifecycle

- Status: Accepted
- Date: August 28, 2026

## Context

Jenkins/XStream deserialization may populate fields without calling
`@DataBoundSetter`. Constructor and setter validation therefore improves
authoring feedback but is not a persistence trust boundary.

## Decision

### Authoritative Validator

The core owns one pure `DefinitionValidationService`:

```text
validateStoredDefinition(ParameterDefinition, ValidationContext)
  -> DefinitionValidationResult

validateJobDefinitions(List<ParameterDefinition>, ValidationContext)
  -> JobDefinitionValidationResult
```

It checks:

- model version and required fields;
- reserved names, including `__unified_parameters_presence`;
- exactly one of static `choices` or dynamic `provider`;
- Renderer/cardinality compatibility;
- default mode/value invariants;
- empty, duplicate, disabled, delimiter, min/max, and API-mode invariants;
- bounded Option, tag, metadata, and string sizes;
- Provider/Profile references that can be checked without executing a
  Provider.

Job-level validation checks:

- duplicate parameter names;
- any definition, including a Core/third-party definition, named
  `__unified_parameters_presence`;
- typed-adapter coverage when a Typed API operation is requested;
- cross-parameter dependency references and cycles when applicable.

Stored-definition validation is deterministic and performs no network,
credential lookup, Provider execution, filesystem access, or cache mutation.

### Invocation Points

The same validator runs:

1. during UI/Pipeline/Job DSL/JCasC authoring before save;
2. when a loaded Job definition is first displayed or inspected;
3. before rendering Build with Parameters;
4. before schema/resolve/validate/build API processing;
5. before CLI, upstream, remote, webhook, and Rebuild evaluation;
6. immediately before `ParameterEvaluationService` resolves defaults or a
   Provider.

An optional load listener may precompute diagnostics after Jenkins startup,
but it is not authoritative. Every evaluation revalidates because load hooks
and setters can be bypassed.

### Invalid Loaded Configuration

An invalid definition:

- remains loadable and visible where Jenkins permits;
- preserves its raw stored fields until an explicit user save/migration;
- exposes bounded actionable errors to configurators;
- exposes a safe “parameter configuration invalid” state to builders;
- rejects every build entry before Provider access or queue scheduling;
- never repairs semantic values silently in `readResolve`.

`readResolve` remains limited to structural initialization and identical-
semantics field mapping as defined by the configuration lifecycle document.

### Validation Layers

| Layer | Purpose |
|-------|---------|
| data-binding validation | immediate author feedback |
| stored-definition validation | authoritative configuration safety |
| Provider result validation | dynamic Option/schema safety |
| submitted-value validation | canonical build input safety |

No layer substitutes for a later layer.

## Consequences

- XStream or old-fixture field injection cannot bypass build safety.
- Invalid historical configuration remains diagnosable instead of crashing
  Job load.
- All entry points share the same configuration error codes.
