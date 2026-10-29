# Parameter Foundry — User Guide

Configure Jenkins parameters, consume their values, integrate governed data
sources, and migrate existing Jobs with explicit compatibility checks.

The source repository is public. Jenkins community hosting and update-center
publication are pending; see [build and installation instructions](../../README.md#build-and-install).
The current tagged version is `1.27.386` (Jenkins `2.528.3`). Development on
`1.28` targets Jenkins `2.555.3` for official hosting.

## Start here

- [Installation and prerequisites](./installation.md)

| Goal | Read |
|---|---|
| Explore all implemented capabilities and their boundaries | [Feature overview](./features.md) |
| Create your first parameter | [Getting started](./getting-started.md) |
| Look up fields, defaults, and transports | [Parameter reference](./parameters.md) |
| Copy a working configuration | [Cookbook](./examples.md) |
| Upload and consume a file | [File parameters](./file-parameters.md) |
| Read exact saved strings in Pipeline | [Saved parameter text](./pipeline-parameter-text.md) |
| Use REST, Git, nodes, mappings, matrices, managed config, or scripts | [Providers](./providers.md) |
| Preserve a whole HTTP response as a choice | [Whole-response REST choices](./raw-rest-responses.md) |
| Migrate from another parameter plugin | [Migration](./migration.md) |
| Review historical values before retiring a source plugin | [History compatibility](./history-compatibility.md) |
| Choose safe defaults and scalable models | [Best practices](./best-practices.md) |
| Understand scope and non-goals | [Design goals](./design-goals.md) |
| Diagnose an error | [Troubleshooting](./troubleshooting.md) |

## Choose an entry point

- **`foundryChoice`**: select one value, multiple values, or matrix cells, using
  inline options or a shared Provider. `dependsOn` declares reactive inputs.
- **`foundryForm`**: collect related typed fields with validation and layouts.
- **`foundryReference`**: display sanitized literal HTML with a separate string value.
- **`foundryFile`**: upload a file for workspace, Base64, or stash delivery.
  `foundryBase64File` and `foundryStashedFile` remain supported.
- **`withFoundryFileParameter`** and **`foundryParameterText`**: consume files or
  supported saved literal strings in Pipeline.

Ordinary choices use canonical strings/lists and forms use maps/JSON. Legacy
compatibility modes deliberately retain selected source string/default behavior.
Environment export is configured separately, and file bytes have their own
multipart transport. Consult the relevant guide before assuming all input
surfaces accept the same shape.

## Help and support

Every configuration field has a `?` help popup inside Jenkins. For bugs and
feature requests, use [GitHub Issues](https://github.com/xykong/parameter-foundry-plugin/issues)
with synthetic examples, versions, and reproduction steps.

Contributors can start at the [documentation index](../README.md),
[core architecture](../architecture/core-architecture.md), and
[acceptance cases](../testing/acceptance-cases.md).
AI assistants should start with [llms.txt](../../llms.txt).
