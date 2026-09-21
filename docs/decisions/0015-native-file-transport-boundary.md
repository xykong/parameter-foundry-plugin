# ADR 0015: Native File Transport Boundary

- Status: Accepted
- Date: September 3, 2026

## Context

File parameters are multipart transport capabilities. They are not option
Providers and cannot be represented safely as JSON strings, option values, or
ordinary Foundry Form fields without changing upload, storage, workspace,
stash, retention, and Rebuild behavior.

The project previously left a possible reviewed File companion open as a
future alternative. The implemented and tested Jenkins Core File and File
Parameters Stashed File paths already satisfy the current upload requirements.
Creating another upload implementation would add a larger attack surface
without a demonstrated business capability gap.

## Decision

Parameter Foundry does not introduce a File companion in the current roadmap.

- Jenkins Core `FileParameterDefinition` remains the recommended native upload
  parameter.
- File Parameters `StashedFileParameterDefinition` remains native when stash
  lifecycle behavior is required.
- Mixed Jobs may use native File/Stashed File definitions beside Foundry
  choice and form definitions.
- Standard Jenkins multipart endpoints remain the supported upload transport.
- The Foundry Typed JSON API rejects a Job containing a file definition with
  HTTP `409` and `PARAMETER_FOUNDRY_UNSUPPORTED_DEFINITION`; it never drops or
  fabricates file content.
- Migration classifies Stashed File as class D with a "retain native"
  resolution. It must not convert an upload into a string, choice, Provider, or
  JSON field.
- A source plugin cannot be disabled or uninstalled while an active Job,
  generated source, queued/running build, or required historical workflow
  still depends on its file parameter class.

## Future Reconsideration

A File companion requires a new ADR and a concrete unmet requirement, such as
a separately authenticated upload API or a new cross-controller storage
contract. That design must define:

- multipart and streaming limits;
- filename, path, archive, MIME, and content validation;
- Controller/Agent/storage ownership and cleanup;
- credential and authorization boundaries;
- remote trigger, Pipeline, Upstream, and Rebuild behavior;
- build-record retention and redaction;
- malware/content scanning integration;
- compatibility and rollback behavior.

Typed JSON convenience alone is not sufficient justification.

## Consequences

- The current File interoperability gate is closed by retaining the proven
  native transport rather than duplicating it.
- Users can migrate all non-file parameter requirements while retaining a
  native upload parameter in the same Job.
- "Functionally migrated" and "safe to uninstall File Parameters" remain
  separate administrator decisions.
