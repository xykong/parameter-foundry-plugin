# Parameter Foundry — User Guide

This guide is for people who **use** the plugin: configuring parameters in a Job,
reading values in a build, driving it from Pipeline, CLI, or REST, and migrating
away from older parameter plugins.

If you want the internal design instead, start at
[`../product/product-spec.md`](../product/product-spec.md),
[`../architecture/core-architecture.md`](../architecture/core-architecture.md),
and [`../decisions/`](../decisions/).

## Start here

| Goal | Read |
|---|---|
| Create your first parameter | [Getting started](./getting-started.md) |
| Look up every field and its default | [Parameter reference](./parameters.md) |
| Copy a working example | [Cookbook](./examples.md) |
| Upload and consume a file | [File parameters](./file-parameters.md) |
| Preserve legacy string predicates in Pipeline | [Saved parameter text](./pipeline-parameter-text.md) |
| Connect a backend (REST, source JSON modes, Git, script, managed config) | [Providers](./providers.md) |
| Move off Active Choices, Extended Choice, Git branches | [Migration](./migration.md) |
| Understand retired Git/Extended Choice history and File history development controls | [Historical parameter values](./history-compatibility.md) |
| Decide how to model a real Job | [Best practices](./best-practices.md) |
| Understand what the plugin is and is not | [Design goals](./design-goals.md) |
| Fix an error you just hit | [Troubleshooting](./troubleshooting.md) |

## One minute summary

- Choice and structured input entry points: **`foundryChoice`** for value selection
  (single, multiple, or a two-dimensional table) and **`foundryForm`** for typed
  structured input.
- Binary upload entry point: **`foundryFile`** (workspace, Base64 or stash),
  compatible **`foundryBase64File`**, **`foundryStashedFile`**
  and **`withFoundryFileParameter`**, using Jenkins multipart transport.
- The same definition behaves the same way in the browser, CLI, REST, Pipeline,
  Rebuild, and downstream builds, because every entry point calls one server-side
  evaluation service.
- Options come from an inline list or from a **Provider** (static, managed
  configuration, REST, sandboxed script, Git refs). Provider-backed parameters
  bind a build to the option set the user actually saw.
- Values are canonical: single selection is a string or `null`, multiple selection
  is an unordered, sorted list, and a form is a typed map or JSON object.
- The core plugin never executes arbitrary Groovy, JavaScript, HTML, or reads
  arbitrary controller paths. Script and custom rendering capabilities live
  behind reviewed, declared contracts.

## Configuration help inside Jenkins

Every field in the configuration UI has a help popup; this guide expands on the
same material. If you are unsure what a field does while configuring a Job, open
the `?` next to it.

## For AI assistants

- Machine-readable index: [`/llms.txt`](../../llms.txt) at the repository root.
- Requirements and planned tests: [`../testing/requirements.yaml`](../testing/requirements.yaml).
- Acceptance cases and known issues: [`../testing/acceptance-cases.md`](../testing/acceptance-cases.md).
