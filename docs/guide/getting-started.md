# Getting started

This walkthrough creates one choice parameter, builds it, and reads the value
back. It takes about five minutes.

## Before you start

Jenkins update-center publication is pending. Build and install the HPI using
the [repository instructions](../../README.md#build-and-install) first.

- Jenkins `2.528.3` or newer.
- The `parameter-foundry` plugin installed and enabled
  (**Manage Jenkins → Plugins → Installed plugins**).
- Permission to configure a Job (`Job/CONFIGURE`) and to build it (`Job/BUILD`).

## 1. Add the parameter in the UI

1. Open a Freestyle or Pipeline Job and select **Configure**.
2. Enable **This project is parameterized**.
3. Select **Add Parameter → Foundry Choice Parameter**.
4. Fill in the form:

| Field | Value in this example |
|---|---|
| Name | `CHANNEL` |
| Selection mode | `Single` |
| Choices | `staging` (label *Staging*), `production` (label *Production*) |
| Renderer | `Searchable` |
| Default mode | `Explicit value` |
| Default value | `staging` |

5. Select **Save**.

The same definition is available in Pipeline and Job DSL, using their respective
configuration syntax:

```groovy
// Declarative Pipeline
pipeline {
  agent any
  parameters {
    foundryChoice(
      name: 'CHANNEL',
      choices: [[value: 'staging', label: 'Staging'], [value: 'production', label: 'Production']],
      renderer: 'searchable',
      defaultMode: 'explicit',
      defaultValue: 'staging'
    )
  }
  stages {
    stage('Deploy') {
      steps { echo "Deploying ${params.CHANNEL}" }
    }
  }
}
```

```groovy
// Job DSL
job('deploy') {
  parameters {
    foundryChoice('CHANNEL') {
      option('staging', 'Staging')
      option('production', 'Production')
      renderer('searchable')
      defaultValue('staging')
    }
  }
}
```

## 2. Build it and read the value

1. Open **Build with Parameters**, pick a value, and select **Build**.
2. The build page shows the parameter and its value.
3. Environment export is on by default: a single choice exports its string to the
   variable named after the parameter, so a shell step can use `$CHANNEL`.
4. In Pipeline the same value is available as `params.CHANNEL`.

## 3. Try multiple selection

1. Add another parameter and set **Selection mode** to `Multiple`.
2. Choose renderer `Checkbox group` and **Selected value display → Chips**.
3. Set **Default mode → Explicit values** and give one value per line.

In ordinary typed mode, the value is now an unordered list, canonicalised by sorting machine values, so
`ios,android` and `android,ios` always record the same build value. Environment
export for multiple selection defaults to a comma-separated list; switch
**Environment export** to `json` if downstream code prefers a JSON array.

## 4. Drive it from a Provider instead of a static list

Use a Provider when the option set lives outside the Job — a REST endpoint, a
managed configuration file, a Git repository, or a reviewed script:

1. Define the Provider once in **Manage Jenkins → System → Parameter Foundry**,
   or on a Folder so it is scoped to that Folder.
2. In the parameter, set **Shared Provider ID** to that Provider's ID and leave
   the inline **Choices** list empty.
3. If the Provider needs another parameter as input, list it under **Depends on**.
   The form then refreshes options when that dependency changes.

See [Providers](./providers.md) for connection profiles, caching, and failure
policies.

## 5. Where values are available

| Where | What you get |
|---|---|
| Build page / build record | The canonical value with its selection mode |
| Pipeline | `params.NAME` — a string, a list, or a map depending on the parameter |
| Environment | Configured export variable(s); secret form fields are never exported |
| CLI | `build` with a JSON array for ordinary multiple selection; explicit compatibility modes have source-specific decoding |
| REST | `buildWithParameters` plus the typed API at `parameter-foundry/api/v1/…` |
| Rebuild (requires the Rebuild plugin) | Supported historical values are offered again and validated against the current definition |

## Next steps

- [Parameter reference](./parameters.md) — every field, default, and transport.
- [Cookbook](./examples.md) — copy-pasteable configurations.
- [Migration](./migration.md) — moving from Active Choices, Extended Choice, or
  other parameter plugins.
