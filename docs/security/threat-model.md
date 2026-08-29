# Unified Parameters Threat Model

## 1. Scope

This threat model covers the core plugin, typed Job API, configuration and
build UI, Provider cache/snapshots, migration framework, and first-party
Providers.

Companion Script, Git, artifact, registry, cloud, and custom Renderer plugins
must publish their own threat-model extension.

## 2. Assets

- Jenkins Credentials and credential identifiers;
- Job, Folder, and global Provider configuration;
- build authorization and queue integrity;
- parameter values and option data;
- Provider endpoint topology and health data;
- migration backups and source configuration;
- controller availability and executor resources;
- audit and build-record integrity.

## 3. Actors

| Actor | Expected access |
|-------|-----------------|
| anonymous/unauthorized caller | no protected Job or Provider data |
| user with `Item.READ` | Job existence and ordinary readable metadata |
| user with `Item.BUILD` | schema, options required to build, validation and scheduling |
| Job configurator | configure Job-scoped definitions without escalating to unrelated credentials or network targets |
| Folder administrator | manage Providers in the delegated Folder boundary |
| Jenkins administrator | global Provider, migration batch, and companion-plugin administration |
| malicious Provider endpoint | returns oversized, malformed, slow, redirected, or hostile data |
| compromised companion plugin | outside core trust promise; limited by SPI validation where possible |

## 4. Trust Boundaries

```text
Browser / API client
  -> Jenkins authentication, permission and CSRF boundary
  -> Typed DTO / standard input decoder
  -> Evaluation service
  -> Provider and cache boundary
  -> outbound network / managed configuration
  -> normalized Option model
  -> ParameterValue and build queue
```

Migration crosses an additional boundary from source-plugin configuration into
plugin-owned normalized intent and backup storage.

## 5. Permission Matrix

The authoritative endpoint permissions are defined in ADR 0006.

Additional rules:

- `Item.CONFIGURE` alone does not grant use of every Jenkins credential;
- credentialed network Providers must reference a governed Connection Profile;
- a Folder Provider cannot reference or reveal a Provider in a sibling Folder;
- Job authors cannot choose arbitrary cache scope IDs or Provider owner IDs;
- migration batch operations require `Jenkins.ADMINISTER`;
- health details are more restricted than build-time option resolution.

## 6. Threats and Controls

### 6.1 Credentials Exfiltration

Threats:

- configure a Provider to send a privileged credential to an attacker URL;
- leak Authorization headers in logs, errors, migration records, or health
  output;
- reuse a global credential from an untrusted Folder.

Controls:

- Connection Profiles bind destination, credential, headers, TLS, proxy and
  request-shape policy;
- Job configuration references a profile and cannot override its authority or
  credentials;
- credential type/domain/context filtering at profile creation and use;
- administrator-controlled outbound target policy;
- no secret interpolation into user-visible URLs or cache keys;
- redaction before logging and API serialization;
- no raw request/response capture by default;
- audit Provider ID, credential ID, actor, and redacted target separately.

### 6.2 SSRF and Network Pivoting

Threats:

- loopback, link-local, cloud metadata, private network, Unix-socket proxy, or
  Jenkins controller access;
- DNS rebinding between validation and connection;
- redirect to a prohibited target;
- alternate IPv4/IPv6 representations;
- abuse of the Jenkins proxy configuration.

Controls:

- scheme allowlist; HTTP and HTTPS only for the first-party REST Provider;
- parse and normalize host/port before policy checks;
- resolve all addresses and reject any prohibited address;
- connect only to the checked address set and recheck every redirect target;
- bounded redirect count; no HTTPS-to-HTTP downgrade by default;
- block loopback, link-local, multicast, unspecified, metadata, and configured
  private ranges unless an administrator allowlist explicitly permits them;
- apply policy after proxy selection and prohibit proxy mechanisms that bypass
  destination enforcement;
- response-size, connect-timeout, read-timeout, and total-deadline limits;
- no URL schemes that access controller files.

The normative construction and delegation rules are defined by
[ADR 0010](../decisions/0010-governed-connection-profiles.md).

### 6.3 XSS and UI Injection

Threats:

- hostile option labels, descriptions, groups, disabled reasons, or Provider
  error text;
- custom Renderer HTML or inline JavaScript;
- CSP bypass through event attributes or unsafe URLs.

Controls:

- Jelly and DOM text APIs escape untrusted text;
- Option fields are data, never trusted markup;
- no arbitrary Job HTML/JavaScript/CSS;
- no inline JavaScript or event handlers;
- strict URL allowlist for any future link field;
- Renderer SPI deferred until a separate review;
- CSP and stored-XSS browser tests.

### 6.4 Input and Deserialization Abuse

Threats:

- polymorphic JSON class selection;
- oversized arrays, deeply nested forms, invalid Unicode, duplicate fields;
- error paths exposing stack traces;
- unsafe XML entity expansion.

Controls:

- strict typed DTOs and unknown-field rejection;
- maximum body, option count, nesting depth, and string length;
- no class-name fields in public APIs;
- secure JSON/XML parser configuration;
- stable safe errors for expected invalid input;
- fuzz tests for standard and typed decoders.

### 6.5 Cache Poisoning and Cross-Scope Leakage

Threats:

- reuse options across Jobs, Folders, credentials, dependencies, or
  configuration versions;
- submit another Job's snapshot ID;
- stale cache silently authorizes a removed value.

Controls:

- scoped cache keys and snapshot ownership from ADR 0007;
- high-entropy opaque snapshot IDs;
- server verification of Job, definition, dependency, and digest binding;
- fail-closed default;
- explicit bounded last-known-good policy;
- no user-controlled cache keys.

### 6.6 Availability and Resource Exhaustion

Threats:

- slow Provider, huge option set, regex denial of service, request storm,
  migration scan of all Jobs, or repeated health tests.

Controls:

- bounded executor, timeout, cancellation, coalescing, and negative cache;
- principal, Job, Provider/Profile, and controller-wide concurrency/rate
  budgets that do not include dependency values in their identity;
- bounded Provider queue, validate/build reserved capacity, and HTTP 429 with
  `Retry-After`;
- response and normalized-option limits;
- safe regex policy and length limits;
- virtualized DOM;
- paginated/batched migration scans;
- permission-protected manual refresh;
- metrics for timeout, queue depth, cache miss, and fallback.

### 6.7 Build Authorization Bypass

Threats:

- validation endpoint schedules a build;
- UI-only validation bypass;
- disabled/unknown value submitted through CLI, Rebuild, or webhook;
- snapshot replay after Job configuration changes.

Controls:

- only the build action schedules;
- every entry adapter invokes the same evaluation service;
- configuration digest included in snapshot/build evaluation;
- authorization checked immediately before scheduling;
- no build on any validation error.

### 6.8 Migration Corruption or Secret Exposure

Threats:

- source configuration changes between preview and apply;
- backup contains scripts, endpoints, or secret literals;
- rollback overwrites a newer user change;
- mass migration affects generated Multibranch Jobs.

Controls:

- precondition digest and compare-before-write;
- plugin-owned protected backup storage and retention policy;
- redacted UI diff with privileged raw recovery path;
- apply through Jenkins save APIs and audit events;
- no direct modification of SCM sources or Multibranch children;
- rollback also checks the current digest;
- batch operations are resumable and stop on policy threshold.

## 7. Privacy and Logging

By default the plugin does not log or emit telemetry for:

- submitted parameter values;
- Option machine values;
- Credentials or headers;
- complete endpoint URLs with sensitive query strings;
- raw Provider response bodies;
- source scripts.

Allowed aggregated data includes counts, durations, error families, cache
status, Renderer type, and migration classification.

## 8. Required Security Tests

- permission tests for every endpoint and scope;
- CSRF tests for every POST action;
- stored/reflected XSS tests for every Option display field;
- SSRF tests covering redirects, DNS rebinding simulation, IPv6, metadata,
  proxy, and encoded addresses;
- credential-domain and Folder-isolation tests;
- JSON/XML parser and request-size fuzzing;
- snapshot replay and cross-Job tests;
- cache-scope collision tests;
- Provider timeout/request-storm tests;
- unique-dependency rate-budget and 429/Retry-After tests;
- migration compare-before-write and rollback-concurrency tests;
- Jenkins security scanner and dependency scanning.

## 9. Residual Risks

- Jenkins administrators can install companion plugins with a larger attack
  surface.
- Administrators may explicitly allow internal Provider targets.
- Parameter values stored in ordinary Jenkins build records are visible
  according to Jenkins permissions; Unified Parameters is not a secret
  parameter replacement.
- Historical source-plugin build records retain the source plugin's original
  security and serialization characteristics.
