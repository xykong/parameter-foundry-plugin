# Companion Extension Boundary

Date: August 28, 2026

## Purpose

Unified Parameters covers business requirements without turning the core
plugin into a script console or arbitrary UI host. High-risk capabilities are
installed and reviewed independently while retaining the same canonical
evaluation, snapshot, validation, and audit contracts.

## Renderer SPI

A renderer companion contributes:

- a stable renderer ID;
- supported parameter families;
- a versioned `RendererSecurityContract`;
- names of plugin-packaged static adjuncts.

The SPI deliberately has no method that returns HTML or JavaScript source.
Companions use plugin-packaged Jelly and adjunct resources. A trusted v1
contract requires CSP-safe behavior, escaped untrusted content, no inline
script, no Job-supplied HTML, and syntactically bounded adjunct names.

Only an installed plugin other than `unified-parameters` may satisfy an
external renderer ID. Administrators therefore make an explicit installation
trust decision, and removing that companion makes definitions fail closed as
configuration errors.

## Script Provider SPI

A script companion contributes:

- a stable engine ID;
- a versioned `ScriptProviderSecurityContract`;
- a normalized option-array output schema;
- evaluation of an opaque governed source reference under the companion's own
  permission and audit implementation.

The contract declares a separate plugin boundary, dedicated configuration
permission, audit coverage, bounded output, schema validation, sandbox status,
and supported source kinds. Core never stores or executes source text through
this SPI.

The companion result remains subject to the core Provider runtime:

- deadline and concurrency budgets;
- normalized option limits;
- dependency and scope cache keys;
- snapshot binding;
- canonical parameter validation;
- redacted health and provenance.

## Compatibility

Both SPIs use API version `1`. An incompatible version is ignored rather than
loaded optimistically. Companion plugins must publish Jenkins and Unified
Parameters version ranges, upgrade fixtures, and their own threat-model
extension.

This repository includes two reference companions:

- `unified-parameters-companion-renderer` provides the trusted
  `accessible-choice-grid` renderer. It contributes only packaged CSS/JavaScript
  adjuncts and is loaded by the core renderer registry only when the companion
  is installed.
- `unified-parameters-companion-script-provider` provides the
  `sandbox-groovy` engine. It executes only administrator-managed catalog
  entries through Jenkins Script Security, requires sandbox mode, exposes only
  immutable dependency values and operation metadata, and normalizes bounded
  option output before it reaches the core Provider runtime.

These are reference implementations, not a substitute for Jenkins community
security review. Non-sandboxed scripts, unregistered references, unsupported
output shapes, and invalid option values fail closed.

## Domain Provider Migration

Source-specific adapters may inspect a definition through stable class names
and read-only getters, but never execute source scripts or copy credentials.
The Active Choices adapter emits Provider/Cascade or Unified Form skeletons.
The List Git Branches adapter emits a governed Git-ref Provider skeleton whose
repository authority and credential remain in an administrator-owned
Connection Profile or reviewed companion.

File upload parameters are transport capabilities rather than option
Providers. Native Jenkins multipart behavior remains available in mixed Jobs.
The JSON Typed API rejects unsupported file definitions atomically so it never
pretends that an omitted upload was accepted.
