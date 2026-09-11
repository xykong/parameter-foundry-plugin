# Providers

A Provider turns "which options exist" into a shared, governed definition instead
of a list copied into every Job.

## Where Providers live

| Scope | Declared in | Visible to |
|---|---|---|
| Global | **Manage Jenkins → System → Unified Parameters** | Every Job |
| Folder | Folder → **Configure → Unified Parameters** | That Folder's Jobs, and nested Folders when *Available to nested Folders* is set |

Parameters reference a Provider by **Shared Provider ID**. Provider-backed
parameters resolve options at form load, bind the build to that option set
through a snapshot, and reject values that are no longer valid.

## Provider types

| Type | Use it when | Key fields |
|---|---|---|
| Static | The list rarely changes and belongs to Jenkins | Options |
| Managed config | The list lives in a Config File Provider file (YAML or JSON) | Config ID, format, path mapping |
| REST | The list comes from an HTTP API | Connection Profile, relative path, query parameters, format, path mapping |
| Script | The list needs logic, but must stay governed | Engine ID (`sandbox-groovy`), catalog source reference |
| Git refs | The list is branches, tags, or full refs of a repository | Connection Profile, ref type, include/exclude pattern, sort order, maximum refs |

Path mapping (`rootPath`, `valuePath`, `labelPath`, `descriptionPath`,
`groupPath`, `disabledPath`, `disabledReasonPath`, `tagsPath`) decides which part
of the payload becomes which option attribute. Only `valuePath` is required;
entries without a value are skipped.

## Shared runtime settings

| Setting | Meaning |
|---|---|
| Cache TTL | How long a resolved option set is reused before the Provider is called again (`0` disables caching) |
| Failure policy | `fail-closed` fails the request, `last-known-good` reuses the cached set while it is fresh enough, `empty` returns no options |
| Maximum staleness | Age limit for the `last-known-good` fallback |
| Timeout | Budget for one call (1–30 seconds) |
| Transforms | Ordered post-processing: `filter`, `sort`, `distinct`, `limit`, `map`, `group`, `mark-disabled` |

Fallbacks are never silent: a response that used a fallback is marked as such and
the reason is shown next to the parameter.

## Connection Profiles

REST and Git Providers call the network through a Connection Profile, which is
the only place a base URL, credential, and allow-list are declared.

| Field | Meaning |
|---|---|
| Base URL | Absolute `http`/`https` base; requests must stay on that authority and path |
| Credential | Username/password sent as HTTP Basic to that host |
| Allowed path prefixes | Paths the Provider may call; the default is `/` |
| Allowed query parameters | Query names the Provider may send |
| Allow private addresses | Permit private ranges; loopback, link-local, and cloud metadata stay blocked |
| Follow redirects | Up to three redirects, each re-checked against the profile and network policy |
| Use Jenkins proxy | Route through the Jenkins proxy configuration |
| Timeouts | Connect, read, and total budgets |
| Maximum response bytes | Reject oversized payloads (1 KiB – 10 MiB) |
| Headers | Extra request headers; prefer a credential for secrets |

Provider results are bounded: at most 10,000 options per resolve, bounded
response sizes, and per-principal, per-Job, and per-Provider request budgets.
Exceeding a budget returns `UNIFIED_PARAMETER_RATE_LIMITED` with `Retry-After`.

## Script Providers

The built-in engine is `sandbox-groovy`. A Script Provider never carries inline
Groovy in a Job; it references a reviewed catalog entry such as
`catalog:release_environments`. The script returns a list of maps
(`[value: 'production', label: 'Production']`) or plain strings, runs inside the
Script Security sandbox, and is bounded in time and output size. Disable the
feature switch to make such parameters fail closed.

## Reactive Providers

Set **Depends on** on the parameter to send other parameters' canonical values to
the Provider. The form reloads options when a dependency changes, keeps a
selection only when it is still valid, and blocks the Build button while the
refresh is in flight. Dependency cycles are configuration errors.

## Health and diagnostics

`…/unified-parameters/api/v1/providers/health` reports the last result per
parameter — healthy or not, duration, option count, and a redacted message. Use
it before blaming a Job configuration.

## Configuration as code

The plugin takes part in JCasC import and export. An exported global
configuration looks like this (values shortened):

```yaml
unifiedParameters:
  accessibleChoiceGridEnabled: true
  gitRefsProviderEnabled: true
  migrationAdaptersEnabled: true
  scriptProviderEnabled: true
  providers:
    - unifiedScriptProvider:
        id: "release-options"
        engineId: "sandbox-groovy"
        sourceReference: "catalog:release_environments"
```

Credential secrets are never exported; the exporter writes the credential ID
only.
