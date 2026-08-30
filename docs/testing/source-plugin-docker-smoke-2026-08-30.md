# Source Plugin Docker Smoke Verification

- Verification date: August 30, 2026
- Jenkins: `jenkins/jenkins:2.568.2-lts-jdk21`
- Target commit: `1886b4d`
- Scope: synthetic source definitions only; no production Job, credential, or
  source-plugin configuration was used

## Source Plugin UI Smoke

The existing UI Lab profiles were run with the real source plugin HPI and a
disposable Jenkins Home:

| Source plugin | Version | Configuration | Build | Result |
|---|---:|---:|---:|---|
| Active Choices | `2.8.10` | 122 controls loaded | synthetic Freestyle build succeeded | Passed |
| Extended Choice | `388.ve7b_d0b_920e10` | 314 controls loaded | synthetic Freestyle build succeeded | Passed with known legacy browser warnings |
| List Git Branches Parameter | `76.v7a_a_7a_5065278` | real source class loaded | preview-only | Passed with governed Provider boundary |
| File Parameters | `425.v3fa_801681b_5e` | real Stashed File class loaded | native multipart build succeeded | Passed with native transport boundary |

The Active Choices fixture verified four-option multi-select, checkbox, radio,
and single-select controls, interaction, and a successful build. The Extended
Choice fixture verified the same control families and a successful build.

Extended Choice emitted the already documented legacy browser errors:

- `Cannot read properties of undefined (reading 'extend')`
- `jQuery is not defined`

These warnings belong to the source plugin's legacy browser scripts. They did
not prevent the synthetic source Job from rendering or building.

## Migration Adapter Smoke

A second disposable Controller loaded:

- the real Extended Choice HPI;
- the real Active Choices HPI;
- the Unified Parameters core HPI;
- the Extended Choice and Active Choices migration adapter HPIs;
- the required Jenkins plugin dependencies.

Synthetic source Jobs were created directly in the disposable Controller.
The migration adapter then verified:

| Source | Verification | Result |
|---|---|---|
| Extended Choice `PT_CHECKBOX` | Class B preview and `UnifiedMultiChoiceParameterDefinition` target | Passed |
| Extended Choice `PT_CHECKBOX` | Clone/apply, persisted target definition, audit record, and rollback restoration | Passed |
| Active Choices `ChoiceParameter` | Class C preview and governed Provider skeleton | Passed |
| Active Choices `CascadeChoiceParameter` | Class C preview and preservation of `ACTIVE_BASE` dependency in the skeleton | Passed |
| List Git Branches `PT_BRANCH` | Class C preview, governed Git-ref Provider skeleton, and redaction of the synthetic repository URL | Passed |
| Stashed File | Class D preview and native multipart build using the real `StashedFileParameterDefinition` | Passed |

The migration adapter inspected the persisted source definitions without
executing their Groovy source. The disposable Controller started without
plugin-load or init-script errors after all declared source-plugin
dependencies were installed.

## Boundary

This smoke run proves real source-plugin class loading, representative UI
rendering, source Job construction, migration preview, and disposable
clone/rollback behavior. It does not close the production gate for:

- real organization-specific Job semantics;
- List Git Branches or Stashed File actual-plugin fixtures;
- production ingress, OIDC, API-token policy, or Agent behavior;
- source-plugin uninstall or business Job migration.

Those remain separate isolated-canary or source-version fixture activities.

## Environment Findings

- The first manually assembled Active Choices bundle was incomplete: it used
  an older `jquery3-api` and omitted `antisamy-markup-formatter`. Rebuilding
  the bundle from the UI Lab prepared dependencies fixed the issue; the final
  Controller started without plugin-load or init-script errors.
- Jenkins Plugin Installation Manager reported existing security warnings for
  the transitive `git-client:6.1.0` dependency used by the List Git Branches
  fixture. This is recorded as a source-plugin environment risk and is not
  treated as a Unified Parameters test failure.
