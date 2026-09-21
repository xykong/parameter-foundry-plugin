# Cookbook

Copy-pasteable configurations for the situations that come up most often. Every
snippet uses the public symbols, so it works in Declarative Pipeline, Scripted
Pipeline, and Job DSL unless noted.

## 1. Static single choice

```groovy
foundryChoice(
  name: 'CHANNEL',
  choices: [[value: 'staging', label: 'Staging'], [value: 'production', label: 'Production']],
  renderer: 'radio',
  defaultMode: 'explicit',
  defaultValue: 'staging',
  required: true
)
```

## 2. Long list with search

Searchable keeps 10,000-option lists usable: the form renders a bounded window,
filters as you type, and exposes grouping and bulk actions.

```groovy
foundryChoice(
  name: 'ARTIFACT',
  providerId: 'artifacts',
  renderer: 'searchable',
  defaultMode: 'first'
)
```

## 3. Multiple selection with chips

```groovy
foundryChoice(
  name: 'TARGETS',
  selectionMode: 'multiple',
  choices: [[value: 'web'], [value: 'android'], [value: 'ios']],
  renderer: 'checkbox',
  selectedDisplay: 'chips',
  defaultMode: 'explicit',
  defaultValues: ['web', 'android'],
  minSelections: 1,
  maxSelections: 3,
  environmentExport: 'json'
)
```

## 4. Platform x target table

Cells carry the machine values, so labels and coordinates can be renamed without
invalidating builds.

```groovy
foundryChoice(
  name: 'MATRIX_TARGETS',
  selectionMode: 'multiple',
  renderer: 'table',
  selectedDisplay: 'chips',
  defaultMode: 'explicit',
  defaultValues: ['android-a'],
  table: [
    rows: [[value: 'android', label: 'Android'], [value: 'ios', label: 'iOS']],
    columns: [[value: 'a', label: 'A'], [value: 'b', label: 'B']],
    cells: [
      [row: 'android', column: 'a', value: 'android-a'],
      [row: 'android', column: 'b', value: 'android-b'],
      [row: 'ios', column: 'a', value: 'ios-a']
    ]
  ]
)
```

The `ios/b` coordinate has no cell, so it renders as unavailable and can never be
submitted.

## 5. Custom value next to a fixed list

```groovy
foundryChoice(
  name: 'IMAGE_TAG',
  choices: [[value: 'latest']],
  allowCustomValue: true,
  customValuePattern: 'v[0-9]+\\.[0-9]+\\.[0-9]+',
  customValueMaxLength: 32,
  renderer: 'searchable'
)
```

## 6. Reactive Provider

Options reload whenever `ENVIRONMENT` changes; the parameter keeps a valid
selection and blocks the build while refreshing.

```groovy
foundryChoice(
  name: 'ENVIRONMENT',
  choices: [[value: 'staging'], [value: 'production']],
  renderer: 'searchable'
)

foundryChoice(
  name: 'SERVICE',
  providerId: 'services',
  dependsOn: ['ENVIRONMENT'],
  renderer: 'searchable'
)
```

## 7. Typed form

```groovy
foundryForm(
  name: 'DEPLOYMENT',
  renderer: 'schema',
  outputMode: 'map',
  environmentExport: 'json'
) {
  field('service', 'string') {
    required(true)
    stringRange(2, 32, '[a-z][a-z0-9-]+')
    environmentVariable('SERVICE_NAME')
  }
  field('replicas', 'integer') {
    required(true)
    numericRange(1, 20)
    defaultValue('2')
  }
  field('token', 'string') {
    secret(true)
  }
  layout('primary', 'grid') {
    columns(2)
    fields('service', 'replicas')
  }
}
```

The secret field is masked in output and never exported; `service` is additionally
exported to `SERVICE_NAME`.

## 8. Trigger a downstream Job with typed values

```groovy
build job: 'deploy', wait: true, parameters: [
  [$class: 'FoundryChoiceParameterValue', name: 'TARGETS', value: ['web', 'android']]
]
```

The short class name works for both selection modes; the value shape (string or
list) decides the mode.

## 9. Build from the CLI

```bash
# multiple selection: JSON array
java -jar jenkins-cli.jar -s "$JENKINS_URL" build deploy \
  -p TARGETS='["web","android"]' -p CHANNEL=production -s

# single selection: plain value
java -jar jenkins-cli.jar -s "$JENKINS_URL" build deploy -p CHANNEL=production -s
```

## 10. Build over REST

```bash
# repeated fields (default for multiple selection)
curl -u user:token -X POST "$JENKINS_URL/job/deploy/buildWithParameters" \
  --data-urlencode CHANNEL=production \
  --data-urlencode TARGETS=web --data-urlencode TARGETS=android

# JSON array for a parameter configured with standardApiInputMode=json-array
curl -u user:token -X POST "$JENKINS_URL/job/deploy/buildWithParameters" \
  --data-urlencode TARGETS='["web","android"]'
```

Invalid input returns `400` with a stable code such as
`PARAMETER_FOUNDRY_UNKNOWN_VALUE` and never queues a build.

## 11. Validate before scheduling

```bash
curl -u user:token -X POST \
  -H 'Content-Type: application/json' \
  -d '{"values":{"CHANNEL":"production","TARGETS":["web","android"]}}' \
  "$JENKINS_URL/job/deploy/parameter-foundry/api/v1/validate"
```

The response contains the canonical values that a build would record.

## 12. Rebuild with historical values

Open a past build and select **Rebuild**. The form is prefilled with the values
that build used, still validated against the current definition.

## 13. Import an option list

In the parameter configuration, paste YAML or JSON into **Import YAML or JSON**,
select **Preview import**, then **Apply import**:

```yaml
- value: staging
  label: Staging
- value: production
  label: Production
  group: Environments
```

Nothing changes until Apply, and applying only rewrites the option editor.

## 14. Without JavaScript

If JavaScript is unavailable, the parameter renders native controls and a
**Build without JavaScript** button, so builds still work and errors stay
readable.
