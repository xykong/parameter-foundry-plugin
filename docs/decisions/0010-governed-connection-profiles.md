# ADR 0010: Governed Connection Profiles

- Status: Accepted
- Date: August 28, 2026

## Context

Filtering credential IDs and checking destination IPs does not prevent a Job
configurator from combining a privileged credential with an attacker-chosen
URL. Credentialed REST access needs an ownership boundary that binds identity,
destination, transport policy, and permitted request shape.

## Decision

### Connection Profile

Every credentialed first-party REST Provider references a named
`ConnectionProfile`. A profile owns:

```text
stable profile ID
global or Folder scope
base scheme, host, port and path prefix
allowed relative-path templates
allowed query parameter names
credential ID and credential type
fixed secret/non-secret headers
TLS and certificate policy
Jenkins proxy usage policy
redirect policy
connect/read/total timeouts
maximum response size
administrator outbound-policy decision
```

The Job parameter definition stores only:

- profile ID;
- an allowed relative path template;
- typed dependency bindings;
- response parsing and Option mapping.

It cannot store or override an absolute URL, credential ID, secret header,
proxy, TLS policy, scheme, host, or port.

### Scope and Permissions

Global profiles are created and changed by `Jenkins.ADMINISTER`.

Folder profiles may be created by delegated Folder administrators only when:

- the global outbound policy permits delegated profiles;
- the credential is visible in that Folder context;
- the destination is within the global administrator allowlist;
- the profile cannot widen a parent restriction.

Users with `Item.CONFIGURE` may reference profiles visible to their Job but
cannot read credential material or hidden headers.

Profiles have an explicit `usableByChildren` policy. Sibling Folders cannot
discover or reference each other's profiles.

### URL Construction

- Provider configuration supplies a relative path, never an authority.
- Path variables are encoded as individual URI path segments.
- Query variables are encoded as individual query values.
- Raw string interpolation into a URL is prohibited.
- `..`, encoded path traversal, authority replacement, fragments, and scheme
  changes are rejected.
- The final normalized URL must remain below the profile base path.
- Every redirect repeats the profile and SSRF policy checks.

### Credentials and Headers

- Credentials are resolved at request time in the profile's scope.
- Secret headers are profile-owned and never visible in Job XML, Pipeline,
  schema, health, or logs.
- Job configuration may add only allowlisted non-secret headers.
- Credential rotation invalidates profile cache entries by event when
  available and always by bounded TTL.
- A profile cannot use a credential type not declared by its Provider
  implementation.

### Non-Credentialed Direct URLs

The core product does not allow ordinary Job configurators to define direct
absolute REST URLs.

An administrator may create a no-credential Connection Profile for public
services. This keeps SSRF, redirects, proxy, timeout, and response limits
governed by the same model.

### JCasC and Audit

Profiles are configurable through UI and JCasC using stable IDs. Exports
contain credential IDs but no secret values.

Audit records include actor, profile ID, owning scope, redacted destination,
credential ID, configuration digest, and action. They do not include request
headers, secrets, query values, or response bodies.

## Consequences

- A Job author cannot pair an arbitrary destination with a privileged
  credential.
- Folder delegation remains possible without bypassing global network policy.
- REST Provider configuration is more reusable and smaller.
- A profile becomes the cache, health, and credential-rotation boundary.
