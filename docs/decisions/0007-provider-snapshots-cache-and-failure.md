# ADR 0007: Provider Snapshots, Cache, and Failure Policy

- Status: Accepted
- Date: August 28, 2026

## Context

Dynamic options can change between page render, API validation, and build
submission. Caching must improve reliability without mixing Folder, Job,
credential, dependency, or configuration contexts.

## Decision

### Provider Request

Every Provider receives an immutable `ProviderRequest` containing:

- Provider ID and configuration digest;
- owning scope ID: global, Folder, or Job;
- Job full name and Job configuration digest;
- canonical dependency values;
- credential ID references, never secret values in cache keys or logs;
- deadline and cancellation token;
- requesting operation: preview, resolve, validate, build, or health test.

Core Providers are not user-personalized. A Provider whose output depends on
the current user requires a separately reviewed companion design.

### Cache Key

The cache key contains:

```text
provider type
provider configuration digest
owning scope ID
Job identity when Job-scoped
canonical dependency-value digest
credential reference/version digest when available
transform-chain digest
```

It never contains credentials or raw sensitive parameter values. It also
never omits the Folder/Job scope merely because two Providers have equal
display names.

### Snapshot

A successful resolution produces an immutable snapshot:

```text
snapshot ID
audience
provider/configuration digest
dependency digest
option-set digest
created time
expires time
failure-policy metadata
redacted provenance
```

The in-memory cache may hold the normalized options. Build records store the
canonical selected value plus bounded snapshot metadata: Provider,
configuration and option-set digests, generation time, fallback state and
redacted provenance. They do not store the complete response or credentials.

This supports traceability, not reconstruction of every historical unselected
option.

Snapshot IDs are opaque, high-entropy identifiers. A snapshot has one
audience:

| Audience | Created by | Accepted by |
|----------|------------|-------------|
| `BUILD_VALUE` | build-form/Typed API resolve | validate, Typed build, browser submit |
| `DIAGNOSTIC` | configuration preview or health test | preview/health only |

The server verifies the same Job, parameter definition, Provider/Profile,
configuration digest, dependency digest, and audience. A client cannot use a
snapshot from another Job or Folder, and a diagnostic snapshot cannot
authorize a build.

Core Providers are non-personalized, so a `BUILD_VALUE` snapshot is not bound
to the user who resolved it. Every use rechecks the current caller's
`Item.READ`/`Item.BUILD` permission. A future user-personalized companion
Provider must include principal identity in its cache key and snapshot binding
and requires a separate threat-model review.

### Submission Consistency

The default build policy is `fresh-or-resolve`:

1. use the submitted snapshot if it is valid and unexpired;
2. otherwise resolve atomically under the current definition;
3. validate the submitted value against that resolved option set;
4. reject the build if the Provider cannot be resolved or the value is no
   longer valid.

The UI may show a stale-state warning, but it cannot authorize a value that the
server rejects.

`trust-signed-input` is not part of the core `1.x` product. It requires a
future threat model and companion protocol.

### Failure Policies

| Policy | Meaning |
|--------|---------|
| `fail-closed` | default; resolution failure blocks validation/build |
| `last-known-good` | explicit opt-in with a configured maximum staleness |
| `empty` | allowed only for optional parameters and never converts an error into a required valid value |

Fallback never changes the recorded provenance. A build using
last-known-good records the snapshot age and fallback reason.

### Cache Lifecycle

- default Provider timeout is 5 seconds; the global maximum is 30 seconds;
- response size and normalized option count are bounded;
- identical in-flight requests are coalesced;
- unique cache keys remain subject to the principal, Job, Provider/Profile,
  controller, and queue budgets in ADR 0006;
- failed resolutions use short negative caching to prevent request storms;
- configuration changes invalidate matching entries by digest;
- Jenkins restart clears runtime option caches in `1.1`;
- persisted last-known-good option bodies are not a `1.1` feature;
- build-record snapshot metadata remains readable after restart;
- cancelled browser requests cancel work when safe, but server-side build
  validation always completes or fails deterministically.

### Health and Observability

Metrics and health records use Provider IDs and redacted targets. They may
record latency, result count, response size, cache hit, failure family, and
snapshot age. They do not record option values, credentials, headers, or raw
response bodies.

## Consequences

- UI and API can share a concrete option-set identity.
- Cache entries cannot cross Folder, Job, dependency, or configuration
  boundaries.
- Jenkins restart behavior and fallback semantics are explicit.
