# Providers

A Provider turns "which options exist" into a shared, governed definition instead
of a list copied into every Job.

## Where Providers live

| Scope | Declared in | Visible to |
|---|---|---|
| Global | **Manage Jenkins → System → Parameter Foundry** | Every Job |
| Folder | Folder → **Configure → Parameter Foundry** | That Folder's Jobs, and nested Folders when *Available to nested Folders* is set |

Parameters reference a Provider by **Shared Provider ID**. Provider-backed
parameters resolve options at form load, bind the build to that option set
through a snapshot, and reject values that are no longer valid.

## Provider types

| Type | Use it when | Key fields |
|---|---|---|
| Static | The list rarely changes and belongs to Jenkins | Options |
| Node / label | The list is the controller's agents or labels (the governed replacement for an Active Choices script that reads `Jenkins.instance.nodes`) | Source (agent or label names), include/exclude pattern, built-in node, online only |
| Configured node order (unreleased) | Reproduce a built-in-first configured node list without executing source code | `source: configured-nodes`; built-in first, configured order, offline/zero-executor nodes unless online-only is requested |
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
foundryMatrixProvider(
    id: 'registries-on-agents',
    rowAxisLabel: 'DOCKER_REGISTRIES',
    rows: [[value: 'harbor.example.net', label: 'Registry B']],
    columnsProviderId: 'agent-nodes',
    columnAxisLabel: 'BUILD_NODE',
    cellValueTemplate: '{row}@{column}',
    rowSelectionMode: 'at-most-one'
)

foundryChoice(
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

A long selected-value list collapses itself: above twelve chips the list is replaced by a
`Show selected (n)` toggle, and an expanded list scrolls inside a bounded box, so a filled table
stays visible.

A rendered table can be filled in one click: click a row or column caption to fill
every cell of that line, click it again to clear the line, and use **Select all** /
**Invert** / **Clear all** above the table for the whole grid. With `at-most-one` or
`exactly-one` a row keeps a single cell, so a row shortcut fills the row's rank and
the toolbar re-applies the rank after inverting instead of producing a selection
that cannot be submitted.
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

## Provider or inline choices?

A Provider is **not required**. Every renderer, transport, validation rule, and Pipeline
binding works the same whether the options come from a Provider or from the parameter's own
list, so the question is only *where the list lives*:

| | Inline choices | Provider |
|---|---|---|
| Declared in | The Job (parameter configuration, Job DSL, or the Pipeline's `parameters` block) | Manage Jenkins -> System -> Parameter Foundry, or a Folder property |
| Changes when an agent, branch, or environment is added | The Job must be edited | Nothing to change; the next resolve returns the new list |
| Shared by several Jobs | No; each Job keeps its own copy | Yes, one definition for every Job that references it |
| Can be live | No, the list is whatever the Job says | Yes: nodes, labels, Git refs, REST, a reviewed script, a config file |
| Needs an administrator to maintain | No | Yes, for a Global or Folder definition |
| Needs a Jenkins shared library | No | No |

```groovy
// Inline: the list is part of the Job configuration.
foundryChoice(
  name: 'BUILD_NODE',
  choices: [[value: 'agent-a.example.net'], [value: 'agent-b.example.net']],
  renderer: 'list',
  required: true
)

// Provider: the list lives outside the Job and stays live.
foundryChoice(name: 'BUILD_NODE', providerId: 'agent-nodes', renderer: 'list', required: true)
```

`demo-node-choice` deliberately carries both: `BUILD_NODE` is backed by a
Node/Label Provider and `BUILD_NODE_INLINE` repeats the same list inline, so the two can be
compared side by side.

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
Exceeding a budget returns `PARAMETER_FOUNDRY_RATE_LIMITED` with `Retry-After`.

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
foundryMappingProvider(
    id: 'build-type',
    dependencyParameter: 'BUILD_MODE',
    defaultOptions: [[value: 'debug'], [value: 'release']],
    cases: [
        foundryMappingCase(value: 'production', options: [[value: 'store']])
    ]
)
foundryChoice(name: 'BUILD_TYPE', providerId: 'build-type', dependsOn: ['BUILD_MODE'])
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

`…/parameter-foundry/api/v1/providers/health` reports the last result per
parameter — healthy or not, duration, option count, and a redacted message. Use
it before blaming a Job configuration.

## Configuration as code

The plugin takes part in JCasC import and export. An exported global
configuration looks like this (values shortened):

```yaml
parameterFoundry:
  accessibleChoiceGridEnabled: true
  gitRefsProviderEnabled: true
  migrationAdaptersEnabled: true
  scriptProviderEnabled: true
  providers:
    - foundryScriptProvider:
        id: "release-options"
        engineId: "sandbox-groovy"
        sourceReference: "catalog:release_environments"
```

Credential secrets are never exported; the exporter writes the credential ID
only.
