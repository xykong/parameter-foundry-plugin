# Migration Support Gaps and Adjustments

- Date: September 13, 2026
- Status update: September 14, 2026 — G1–G5 and G8–G11 are implemented; G6
  and G7 stay documented boundaries
- Input: the controller-wide replacement readiness inventory
  ([`../testing/replacement-readiness-2026-09-13.md`](../testing/replacement-readiness-2026-09-13.md))
  and a shape analysis of the source parameters actually in use
- Purpose: list what the plugin does not yet support well enough for a
  single-wave, lossless replacement, and what to adjust before that wave starts

Migration happens once, for every Job, after the preparation is complete.
Nothing below requires a partial migration; each item is a preparation task.

## Status

| Gap | Status | Evidence |
| --- | --- | --- |
| G1 smart sort | Implemented | `sortOrder: smart-ascending \| smart-descending` in the Git refs Provider with a version-aware comparison, mapped from the source `*_SMART` modes, with config help and tests |
| G2 filter translation | Implemented | `translateFilter()` converts full-ref filters to short-name include patterns and the preview shows both patterns |
| G3 default facts | Implemented | The preview lists source default, source selected value, resolved target sort, and translated filter per parameter |
| G4 literal Active Choices | Implemented | `ActiveChoicesScriptAnalysis.literalChoices()` turns literal scripts into inline `choices` with selected markers as defaults; Class A/B instead of Class C |
| G5 network guidance | Implemented | Redacted endpoint and detected response fields in the facts, a generated Provider skeleton, and explicit Class D items for controller/shell scripts |
| G6 nested form editing | Documented boundary | Unchanged: nested values are configured through Pipeline, Job DSL, or JCasC |
| G7 provider matrices | Documented boundary | Unchanged: `providerId` plus `table` remains rejected until a real requirement exists |
| G8 batch migration | Implemented | `MigrationCoordinator.previewBatch/applyBatch/rollbackBatch`, a Job-page batch section with the exact diff, and the `unified-parameters-migration` CLI |
| G9 localized surfaces | Implemented | Migration page, retirement dashboard, fact labels, reasons, decisions, and verification cases resolve through `Messages`; English, Chinese, Japanese, and Korean bundles are complete |
| G10 repository scan | Implemented | `RetirementScanner.scanRepository()` reports Jobs that read their Pipeline from SCM plus multibranch and organization folders; the dashboard, the JSON report, and the Job page state that those definitions are unseen |
| G11 A/B verification | Implemented | `MigrationEvidenceService` compares source and target builds (canonical value, environment export, sibling inputs) and stores the result on the migration record; `unified-parameters-migration-verify` records it |

## What is already good enough

- Every repository in use is reachable over HTTPS (46 of 46 List Git Branches
  parameters), so the built-in Git refs Provider can reach all of them.
- Nine migratable source families are unused on this controller, so the
  preparation scope is four families, not thirteen.
- Core `String`, `Boolean`, `Choice`, `Text`, and native `File` parameters are
  untouched by the migration.

## Capability gaps

### G1. Git ref sorting does not implement the legacy SMART order

Evidence: 41 parameters use `DESCENDING_SMART` and 4 use `ASCENDING_SMART`; the
Git refs Provider sorts with `Comparator.naturalOrder()`, which is
lexicographic. With names such as `1.9` and `1.10`, the visible order and the
"top branch" differ from what users see today.

Impact: option order changes, and `selectedValue: TOP` (8 parameters) selects a
different branch.

Adjustment: add `sortOrder: smart-ascending | smart-descending` to the Git refs
Provider, implementing the version-aware comparison the source plugin uses, and
map `*_SMART` to it in the migration preview so the generated target is
order-equivalent.

### G2. Legacy ref filters are full-ref regular expressions with capture groups

Evidence: all 46 parameters define `branchFilter` and `tagFilter`, e.g.
`.*refs/heads/(?!release/)(.*)`. The Provider matches `includePattern` and
`excludePattern` against the short name with `matches()`.

Impact: filters cannot be copied verbatim; each parameter needs a translated
pattern, and a wrong translation silently hides branches.

Adjustment: ship a filter translator in the adapter (strip the
`.*refs/(heads|tags)/` prefix and the capture group, keep the negative
lookahead), show the translated pattern in the preview, and add a preview line
that contrasts the source and target match counts.

### G3. Defaults that no longer exist in the option set

Evidence: 38 of 46 parameters carry a `defaultValue`; Active Choices and
Extended Choice sources also contain stale defaults (20 parameters need the
non-selectable-default decision).

Impact: Unified Parameters validates explicit defaults against the option set,
so a stale default would make the migrated configuration invalid instead of
silently building with something else. That is the safer behaviour, but the
decision must be recorded per parameter.

Adjustment: the preview already lists the decision; add the concrete source
default value and the resolved target default to the preview so a reviewer can
decide `first`, `none`, or a corrected explicit default in one pass.

### G4. Active Choices static lists are treated as refactoring work

Evidence: 18 of 54 Active Choices scripts are short literal list returns; the
current classification is Class C for all 54.

Impact: a third of the Active Choices work is mechanical, yet it is queued as
design work.

Adjustment: extend the Active Choices adapter to recognise literal-only scripts
(`return ['a', 'b']`, `return [value: 'x', label: 'X']`) and emit inline
`choices` as Class A/B. Keep Class C for anything that reads the environment,
the network, or the controller.

### G5. Active Choices network scripts need a guided path to a Provider

Evidence: 31 of 54 scripts perform network calls; 3 touch the controller or a
shell; 1 is an `ET_FORMATTED_HTML` reference. None declare
`referencedParameters`, and none define a fallback script.

Impact: these cannot be converted automatically, and today the preview only says
"choose a reviewed Provider".

Adjustment: extract the endpoint (redacted), the response handling hints, and
the referenced fields from each script, and generate a Provider skeleton plus a
field-mapping checklist. Treat the controller/shell scripts and the formatted
HTML reference as explicit redesign items with their own owners.

### G6. Browser editing of nested form values

Evidence: nested objects and arrays are configured through Pipeline, Job DSL, or
JCasC only; the browser editor intentionally stops at one level.

Impact: none for the four families currently in use, but it blocks a JSON
Editor or JSON Parameter migration if those families appear later.

Adjustment: keep the limitation documented, and if a nested family is adopted,
add a bounded one-level nested editor rather than a recursive form.

### G7. Provider-supplied matrices are not implemented

Evidence: the design describes Providers returning a matrix model; the current
validation rejects `providerId` together with `table`.

Impact: none today (no provider-backed matrix in use), but a matrix that must be
dynamic has no supported shape.

Adjustment: keep the explicit rejection, and design the provider matrix contract
only when a real requirement appears.

## Engineering gaps for a single migration wave

### G8. No batch migration

Evidence: migration is per parameter (`cloneMigration`, `applyMigration`,
`rollbackMigration`); the wave covers 112 Jobs and 181 parameters.

Adjustment: add a Job-level and list-level API that accepts the decisions for
all parameters of a Job, runs a dry-run that reports the exact configuration
diff, applies atomically, and can roll back the batch. A CLI entry point keeps it
scriptable for the migration window.

Delivered: `MigrationBatchPreview` reports every recognized parameter of a Job
with a line-level diff of the serialized definition, the decisions that still
need an owner, and the parameters that must stay manual. `applyBatch` validates
all digests and decisions first, writes one backup, replaces every parameter in
a single Job write, and restores the backup when the write fails.
`rollbackBatch` reverts the whole batch and refuses when the Job changed
afterwards; a single record of a batch can no longer be rolled back on its own.
The Job page renders the batch diff and the apply button, and
`unified-parameters-migration` exposes scanning, dry-run, and apply to scripts.

### G9. The migration and retirement surfaces are English-only

Evidence: the migration page and the retirement dashboard contain hardcoded
English strings, while the parameter UI ships English, Chinese, Japanese, and
Korean messages.

Adjustment: route migration and dashboard text through `Messages` and add at
least Chinese, so the business owners who must accept decisions read them in
their own language.

Delivered: the view text lives in per-view bundles
(`api/UnifiedParametersJobAction/index*.properties` and
`retirement/RetirementDashboardAction/index*.properties`), and the adapter
narration moved to `MigrationText`, which resolves the plugin `Messages` bundle
when a page renders. Adapter reasons, decisions, fact labels, and verification
cases are translatable with arguments, and English, Chinese, Japanese, and
Korean are complete. `MigrationMessageCoverageTest` fails the build when a
referenced key is missing or a locale is incomplete.

### G10. Parameters defined in repositories are invisible

Evidence: 318 Jobs load their Jenkinsfile from SCM and 5 namespaces are
multibranch or organization folders; only materialised parameters appear in the
Job configuration.

Adjustment: add a repository-side scan (source classes and Pipeline symbols) or
an explicit "unscanned" state in the retirement dashboard, and re-run the
inventory after a full build cycle before uninstalling any source plugin.

Delivered: the retirement dashboard reports the Jobs whose `getDefinition()` is
SCM-backed and the multibranch or organization folders that generate them, the
JSON report adds a `repositoryScan` block with `unscanned`, and the Job migration
page warns that its parameters come from the Jenkinsfile. Uninstalling a source
plugin still requires a reviewed scan after a full build cycle; the plugin
reports the state instead of guessing it.

### G11. Migration verification is manual

Evidence: each migration must be proven by building the source and the target
control with the same input.

Adjustment: provide a verification command that builds both controls, compares
the recorded value, environment export, and downstream consumption, and stores
the result as the migration record's evidence.

Delivered: `MigrationEvidenceService` selects the newest pre-migration and
post-migration builds that carry the parameter, compares the canonical value,
the environment export (the parameter's own variable and any variable that
carries its name), and the sibling inputs, then stores `PASS`, `FAIL`, or
`PENDING` on the migration record. `unified-parameters-migration-verify` records
it from the CLI, and the Job page shows the evidence with a re-run button. The
plugin never schedules a build: the operator runs the two builds, so no migration
tool can trigger production work by itself.

## Verification and process gaps

- Physical iOS and Android devices and the human screen-reader sign-off are still
  open; the current evidence is emulation and accessibility-tree based.
- Rebuild must be part of every migrated Job's acceptance, because historical
  values are revalidated against the new definition.

## Suggested preparation order

1. G1 and G2 (Git refs sorting and filter translation): unblock 42 Jobs.
2. G4 (static-list Active Choices): removes a third of the Active Choices work.
3. G8 and G9 (batch migration and Chinese surface): make the wave operable.
4. G5 and G3 (guided Provider generation and default decisions): finish the
   Active Choices and Extended Choice decisions.
5. G10 and G11 (repository scan and A/B verification): close the blind spot and
   produce per-Job evidence.
6. G6 and G7 stay documented until a real requirement appears.
