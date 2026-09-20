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
| Node / label | The list is the controller's agents or labels (the governed replacement for an Active Choices script that reads `Jenkins.instance.nodes`) | Source (agent or label names), include/exclude pattern, built-in node, online only |
| Matrix | A table crosses two governed lists, for example the registries of a Job against the agents that must log into them | Rows, columns, cell templates, cells per row |
| Dependency mapping | The option list depends on the selected value of another parameter (the governed replacement for an Active Choices cascade that branches on a parameter) | Dependency parameter, cases (value → options), options for every other value |
| Managed config | The list lives in a Config File Provider file (YAML or JSON) | Config ID, format, path mapping |
| REST | The list comes from an HTTP API | Connection Profile, relative path, query parameters, format, path mapping |
| Script | The list needs logic, but must stay governed | Engine ID (`sandbox-groovy`), catalog source reference |
| Git refs | The list is branches, tags, or full refs of a repository | Connection Profile, ref type, include/exclude pattern, sort order, maximum refs |

Path mapping (`rootPath`, `valuePath`, `labelPath`, `descriptionPath`,
`groupPath`, `disabledPath`, `disabledReasonPath`, `tagsPath`) decides which part
of the payload becomes which option attribute. Only `valuePath` is required;
entries without a value are skipped.

## Matrix Providers

A **Matrix** Provider builds a table from two axes. Each axis is either an inline
list or the options of another Provider, so a column list can stay live:

```groovy
unifiedMatrixProvider(
    id: 'registries-on-agents',
    rowAxisLabel: 'DOCKER_REGISTRIES',
    rows: [[value: 'harbor.example.net', label: 'Registry B']],
    columnsProviderId: 'agent-nodes',
    columnAxisLabel: 'BUILD_NODE',
    cellValueTemplate: '{row}@{column}',
    rowSelectionMode: 'at-most-one'
)

unifiedChoice(
    name: 'DOCKER_LOGINS',
    selectionMode: 'multiple',
    renderer: 'table',
    providerId: 'registries-on-agents',
    selectedDisplay: 'chips'
)
```

| Field | Meaning |
|---|---|
| Rows / Rows Provider ID | One row per entry; the Provider ID wins over the inline list when both are set |
| Columns / Columns Provider ID | One column per entry |
| Cell value template | Cell value from `{row}`, `{rowLabel}`, `{column}`, and `{columnLabel}`; the default is `{row}@{column}` |
| Cell label template | Cell caption; empty means "row caption / column caption" |
| Row axis label | Caption of the row axis, shown in the table corner |
| Column axis label | Caption above the table, so the columns read as the values of another parameter |
| Cells per row | `any`, `at-most-one`, or `exactly-one`; the server rejects a violation before the build is scheduled |

Because a cell value is an ordinary option value, the Pipeline sees the selected
cells as a list and can act on both coordinates:

```groovy
params.DOCKER_LOGINS.each { cell ->
    def (registry, agent) = cell.split('@', 2)
    // node(agent) { ... docker login registry ... }
}
```

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

`dependsOn` makes a parameter re-resolve when another parameter changes. A **Dependency
mapping** Provider is the declarative way to change the *option list itself* per dependency
value:

```groovy
unifiedMappingProvider(
    id: 'build-type',
    dependencyParameter: 'BUILD_MODE',
    defaultOptions: [[value: 'debug'], [value: 'release']],
    cases: [
        unifiedMappingCase(value: 'production', options: [[value: 'store']])
    ]
)
unifiedChoice(name: 'BUILD_TYPE', providerId: 'build-type', dependsOn: ['BUILD_MODE'])
```

The parameter offers the case options while the dependency has that value, and the default list
otherwise (including when the build form has no dependency value yet). Nothing is executed: the
mapping is configured data that an administrator reviews. An Active Choices script of the form
`if (DEP == "value") { return [...] } else { return [...] }` migrates to exactly this shape, and
the migration preview proposes the mapping with the literal option lists the script returned.

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
