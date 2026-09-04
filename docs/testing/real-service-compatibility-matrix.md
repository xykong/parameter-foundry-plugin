# Real-Service Parameter Compatibility Matrix

Date: August 31, 2026

This matrix records the parameter families observed during a read-only
inventory of the real Jenkins service and the isolated verification required
before any production migration. It contains no Job names, Job XML,
credentials, tokens, option values, or production endpoints.

## Compatibility Policy

- Inventory and behavioral validation are separate activities.
- Production validation is read-only unless an explicitly approved isolated
  Job or clone is provided.
- No source plugin script is executed by the Unified Parameters migration
  adapters.
- A mode is not considered migrated until its canonical value, default,
  empty, invalid, trigger, Rebuild, and failure behavior has a fixture.

## Matrix

| Source family/mode | Real-service inventory | Isolated verification | Unified target | Status |
|---|---|---|---|---|
| Jenkins Core String/Text/Boolean/Choice | Observed | Typed API and standard Jenkins parameter semantics | Native Core or Unified equivalent | Passed |
| Jenkins Core File | Observed | Native definition round-trip; Typed JSON rejection | Native multipart only; Typed API returns `409` | Passed |
| Stashed File | Observed | Real HPI Class D preview and native multipart smoke | Retain native Stashed File transport | Passed with native-retention decision |
| Extended Choice `PT_SINGLE_SELECT` | Observed | Static adapter, default, empty, invalid, Rebuild | Unified single choice | Passed |
| Extended Choice `PT_RADIO` | Observed | Static adapter and renderer mapping | Unified single choice with radio renderer | Passed |
| Extended Choice `PT_MULTI_SELECT` | Observed | Order, null/empty, stale default, delimiter, Rebuild | Unified multiple choice with explicit decision | Passed with decision |
| Extended Choice `PT_CHECKBOX` | Observed | Checkbox, empty, duplicate, disabled, Rebuild | Unified multiple choice with checkbox renderer | Passed with decision |
| Active Choices `ChoiceParameter` | Observed | Real HPI UI smoke and non-executing Provider skeleton | Governed Provider or static Unified choice | Skeleton ready; HPI smoke passed |
| Active Choices `CascadeChoiceParameter` | Observed | Real HPI class loading, dependency extraction, and cycle fixture | Governed Provider with `dependsOn` | Skeleton ready; HPI smoke passed |
| Active Choices `DynamicReferenceParameter` | Observed | Five-mode, omit-value, dependency, and HTML-to-Form decision fixtures | Unified Form/content or reviewed Renderer | Mode semantics implemented; single-HPI Docker canary passed |
| List Git Branches `PT_BRANCH`/`PT_TAG`/`PT_BRANCH_TAG` | Observed | Mode-specific redacted `unifiedGitRefProvider` skeleton | Built-in Git refs Provider | Adapter and Provider implemented; single-HPI Docker canary passed |
| List Git Branches `ORG_BRANCH` | Observed | One-branch-string and organization-authority decisions | Branch candidate through built-in Git refs Provider | Semantics resolved; business confirmation remains |

## Executed Evidence

The following repository-owned checks have been executed:

- Core mixed-parameter, native File, Provider, Reactive, Snapshot, Form, and
  migration tests in `plugin`.
- Extended Choice adapter and migration coordinator tests.
- Active Choices adapter tests for Choice, Cascade, all five Dynamic Reference
  modes, omit-value state, and dependencies.
- List Git Branches adapter tests for `PT_BRANCH`, `PT_TAG`,
  `PT_BRANCH_TAG`, `ORG_BRANCH`, and unknown modes.
- Git refs Provider tests for filtering, canonical values, output limits,
  redirect/header policy, and metadata-address rejection.
- Additional source adapter tests for Editable, Extensible, Custom Checkbox,
  Multiselect, JSON Editor, HTML Parameters, JSON Parameter, REST List, and
  Validating String.
- Real-browser E2E for Core-compatible parameters, governed Provider,
  Reactive refresh, Snapshot replacement, Unified Form, migration controls,
  and retirement reporting.
- Real source-plugin Docker smoke for Active Choices `2.8.10` and Extended
  Choice `388.ve7b_d0b_920e10`, including synthetic UI builds and disposable
  Extended Choice clone/apply/rollback verification; see
  [`Source Plugin Docker Smoke Verification`](./source-plugin-docker-smoke-2026-08-30.md).
- Real source-plugin Docker smoke for List Git Branches
  `76.v7a_a_7a_5065278` and File Parameters `425.v3fa_801681b_5e`, including
  Class C/D migration boundaries and a successful native Stashed File
  multipart build.
- Latest Docker canary with the built-in Git refs Provider and refreshed migration
  semantics; see
  [`Source Plugin Docker Canary`](./source-plugin-docker-canary-2026-09-04.md).
- Latest PCT matrix with the single HPI and all first-party capabilities; see
  [`PCT Verification`](./pct-verification-2026-09-04.md).
- Repeatable source-plugin Docker canary covering real HPI loading, migration
  clone/apply/rollback, Standard REST, webhook-style REST, Typed REST, CLI,
  Pipeline upstream/downstream, invalid-input no-queue, Rebuild, and native
  multipart; see
  [`source-plugin-canary-2026-08-31.json`](./results/source-plugin-canary-2026-08-31.json).

## Remaining Production Gate

The matrix does not authorize production Job modification. Before migration,
an administrator must approve an isolated canary or clone and provide the
expected business behavior for:

- the eight legacy no-default multi-choice cases;
- stale prompt-like defaults;
- generated Pipeline/Job DSL sources;
- Webhook, Upstream, Remote, CLI, and Rebuild clients;
- File and Stashed File transport requirements, using the native-retention
  decision in [ADR 0015](../decisions/0015-native-file-transport-boundary.md).

After the canary, scan active configurations, generated sources, queues,
running builds, and historical records independently. Only then can source
plugin disable/uninstall be considered.
