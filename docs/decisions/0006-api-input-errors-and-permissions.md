# ADR 0006: API Input, Errors, and Permissions

- Status: Accepted
- Date: August 28, 2026

## Context

Unified Parameters supports the standard Jenkins build endpoint and a typed
JSON API. Those APIs must have explicit decoding, authorization, error, and
queueing behavior rather than relying on client-specific conventions.

## Decision

### Endpoint Versioning

Job-scoped typed endpoints use:

```text
GET  /job/<job>/unified-parameters/api/v1/schema
POST /job/<job>/unified-parameters/api/v1/resolve
POST /job/<job>/unified-parameters/api/v1/validate
POST /job/<job>/unified-parameters/api/v1/build
GET  /job/<job>/unified-parameters/api/v1/providers/health
```

The `/api/v1` contract remains backward compatible for the lifetime of the
major version. Additive response fields are allowed. Removing fields,
changing canonical types, or changing error meanings requires `/api/v2`.

### Permissions

| Operation | Required permission |
|-----------|---------------------|
| schema, resolve, validate, build | `Item.BUILD` on the Job |
| Job Provider health and connection test | `Item.CONFIGURE` on the Job |
| Folder Provider/Connection Profile administration | delegated Folder configuration permission under global policy |
| Global Provider/Connection Profile administration and health | `Jenkins.ADMINISTER` in `1.1` |
| single-Job migration preview/apply | `Item.CONFIGURE` |
| batch migration or source-plugin retirement | `Jenkins.ADMINISTER` |

Endpoints must also verify `Item.READ` before returning Job-identifying
information. A caller without permission receives the normal Jenkins
authorization response; the API does not reveal whether Provider data exists.

POST requests use Jenkins' normal authentication and CSRF/crumb behavior.
API-token authentication follows Jenkins' standard crumb exemption. The
plugin does not create an independent authentication mechanism.

### Typed Build Request

```json
{
  "values": {
    "CHANNEL": "staging",
    "TARGETS": ["android", "ios"]
  },
  "providerSnapshots": {
    "TARGETS": "snapshot-01J..."
  },
  "delaySeconds": 0
}
```

Rules:

- maximum request body is 1 MiB in `1.0`;
- unknown top-level fields are rejected;
- unknown parameter names are rejected;
- typed String, Array, Boolean, and Object values are not delimiter-decoded;
- omitted parameter names are `ABSENT` and use the canonical default rules;
- every current Job parameter definition must have a typed adapter;
- duplicate parameter names or unsupported definitions reject the entire
  request with HTTP 409;
- `delaySeconds` is bounded by the same policy as standard Jenkins scheduling.

Mixed Jenkins Core/Unified behavior and unsupported secret/domain parameter
boundaries are defined by
[ADR 0012](./0012-typed-api-parameter-adapters.md).

Successful scheduling returns `201 Created`, a queue-item `Location` header,
and:

```json
{
  "queueItem": 123,
  "location": "/queue/item/123/",
  "configurationDigest": "sha256:...",
  "providerSnapshots": {}
}
```

The `1.0` build endpoint is intentionally not idempotent: repeating a request
schedules another build. This is stated in API documentation. A future
idempotency-key feature requires a separate ADR and durable replay policy.

### Standard Jenkins API

The plugin continues to support:

```text
POST /job/<job>/buildWithParameters
Content-Type: application/x-www-form-urlencoded
```

Entry adapters decode raw form fields according to ADR 0005 and pass
`DecodedInput` to `ParameterEvaluationService`. They do not create
`ParameterValue` directly.

The standard API returns a safe HTTP 4xx summary when validation fails. It
cannot promise the full typed error envelope in every Jenkins integration, so
clients requiring stable machine-readable errors use `/api/v1/validate` or
`/api/v1/build`.

For Jenkins CLI, omission is handled by Jenkins Core's `BuildCommand` through
`ParameterDefinition.getDefaultParameterValue()`. Unified Parameters resolves
omitted Reactive defaults with the CLI dependency context. A required Reactive
parameter configured with `defaultMode=none` remains valid configuration, but
the caller must submit it explicitly. If it is omitted, Jenkins CLI returns the
native `No default value for the parameter ...` message on stderr with a
non-zero exit status before queue scheduling.

That HTTP 4xx/no-queue behavior is a mandatory `0.1` integration gate on the
declared Jenkins matrix. The project does not freeze the `1.0` claim until the
gate in [ADR 0014](./0014-technical-preview-and-ga-gates.md) passes.

### Resolve and Validate

`resolve` accepts a parameter name plus canonical dependency values and
returns options, default candidates, warnings, and a snapshot ID.

`validate` accepts the same `values` object as `build` and returns:

```json
{
  "valid": false,
  "canonicalValues": {},
  "errors": [
    {
      "code": "UNIFIED_PARAMETER_UNKNOWN_VALUE",
      "parameter": "TARGETS",
      "path": "$[1]",
      "message": "Value is not present in the resolved option set.",
      "hint": "Resolve options again or submit a supported value."
    }
  ]
}
```

Messages are localizable presentation text. Clients depend on `code`,
`parameter`, and `path`, not the English message.

### Error Families

Stable `v1` families include:

- `UNIFIED_PARAMETER_REQUIRED`;
- `UNIFIED_PARAMETER_UNKNOWN_VALUE`;
- `UNIFIED_PARAMETER_DISABLED_VALUE`;
- `UNIFIED_PARAMETER_DUPLICATE_VALUE`;
- `UNIFIED_PARAMETER_INVALID_TYPE`;
- `UNIFIED_PARAMETER_MIN_SELECTIONS`;
- `UNIFIED_PARAMETER_MAX_SELECTIONS`;
- `UNIFIED_PARAMETER_PROVIDER_UNAVAILABLE`;
- `UNIFIED_PARAMETER_SNAPSHOT_EXPIRED`;
- `UNIFIED_PARAMETER_DEPENDENCY_CYCLE`;
- `UNIFIED_PARAMETER_CONFIGURATION_CHANGED`;
- `UNIFIED_PARAMETER_REQUEST_TOO_LARGE`;
- `UNIFIED_PARAMETER_UNSUPPORTED_DEFINITION`;
- `UNIFIED_PARAMETER_RATE_LIMITED`.

User input, Provider data, and expected stale-snapshot errors must not produce
HTTP 500 or schedule a build.

HTTP status families:

| Condition | Status |
|-----------|--------|
| malformed/unknown input | 400 |
| authentication/permission/crumb failure | normal Jenkins 401/403 |
| unsupported or conflicting Job definition | 409 |
| expired/configuration-changed snapshot | 409 |
| request/provider budget exhausted | 429 with `Retry-After` |
| expected Provider unavailable/timeout | 422 or 503 according to whether the failure is definition-specific or service-wide |
| unexpected plugin defect | 500 |

### Confidentiality and Load Controls

- schema responses do not include credentials, secret headers, internal
  exception traces, or unrestricted endpoint URLs;
- Provider health returns redacted target identity and bounded diagnostics;
- response option counts and payload sizes are bounded;
- concurrent identical Provider requests are coalesced according to ADR 0007;
- unique dependency values share principal, Job, and Provider/Profile budgets
  and therefore cannot bypass load controls;
- no API request may select arbitrary Java classes or Renderer implementations
  by class name.

### Rate and Concurrency Budgets

Provider-consuming `resolve`, `validate`, `build`, preview, and health requests
are controlled before outbound or expensive Provider work.

Default in-flight limits are:

| Scope | Limit |
|-------|-------|
| authenticated principal | 8 |
| Job | 16 |
| Provider or Connection Profile | 8 |
| controller-wide Provider work | 64 |

Default rolling request budgets are 60/minute per principal, 120/minute per
Job, and 240/minute per Provider/Profile. Dependency values and cache keys are
not part of the budget identity.

The global Provider executor has a bounded queue of 64 and reserves 25% of its
permits for validate/build work so preview and health traffic cannot consume
all scheduling capacity.

Administrators may tune limits globally within implementation hard bounds.
Folder/Job configurators may only lower their local limits. Rejection returns
HTTP 429, `UNIFIED_PARAMETER_RATE_LIMITED`, and an integer-seconds
`Retry-After`. It does not execute a Provider or schedule a build.

## Consequences

- The typed API has a versioned contract suitable for automation.
- The standard Jenkins API remains compatible without pretending it can expose
  every typed error detail.
- Permission and confidentiality behavior are testable per endpoint.
