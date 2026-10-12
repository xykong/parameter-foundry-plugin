# Saved parameter text in Pipeline

`foundryParameterText(name: 'CHOICES')` reads a supported parameter's saved text
from the current build. It needs no node or workspace.

```groovy
def original = foundryParameterText('CHOICES')
if (original.contains('red')) {
    echo 'The original text contains red'
}
```

Use this step when migrating a Pipeline that interpreted an Extended Choice
value as a string. `params.CHOICES` remains the canonical list for a multiple
Foundry Choice. That list can omit values from a legacy literal form input:
`red-custom` can have an empty canonical list while its original string still
contains `red`. Joining the list cannot restore that text.

The step reads the existing `ParameterValue`, including the original literal
saved by Extended Choice compatibility transports and defaults. It does not
evaluate a default or contact a Provider. It preserves whitespace, empty strings
and nullable single values. Environment overrides do not affect its result.

Supported values are Foundry Choices with saved legacy text, single Foundry
Choices, Core String parameters, and the reviewed Extended Choice string value
type. Sensitive, file and unknown parameter types are rejected. A multiple
value without saved original text is rejected; earlier history that lost the
text cannot be reconstructed. A missing or security-filtered parameter is an
error. The step respects Jenkins `ParametersAction` filtering and never reads
another build or Job.

Jenkins `params` may include a definition default even when the build has no
saved value. Therefore, `params.containsKey('CHOICES')` does not prove that this
step can read saved text. When migrating a consumer with a verified literal
default, supply it explicitly:

```groovy
def original = foundryParameterText(name: 'CHOICES', fallbackIfMissing: 'red,blue')
```

`fallbackIfMissing` is used only when no accessible saved value exists. Omitting
it retains the missing-value error. An explicit empty string is allowed. Saved
null and empty strings remain unchanged; sensitive, unsupported and historical
values without original text still fail. Security-filtered values remain
inaccessible. The fallback never evaluates a definition default or Provider.

For Declarative Pipeline, call it inside an expression or a `script` block:

```groovy
stage('Legacy condition') {
    when {
        expression { foundryParameterText('CHOICES').contains('red') }
    }
    steps { echo 'Condition matched' }
}
```

This is a consumer compatibility tool. Verify the source input decoder, defaults,
raw values, historical records and predicates before changing a real Pipeline.
