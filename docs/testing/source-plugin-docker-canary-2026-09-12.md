# Source Plugin Docker Canary Report

- Date: September 12, 2026
- Jenkins image: `jenkins/jenkins:2.568.2-lts-jdk21`
- Target commit: `a7b446985072d5e84fcb1292880e732572e9f5a8`
- Scope: synthetic Docker canary only; no production configuration involved
- Machine-readable result: [`results/source-plugin-canary-2026-09-12.json`](./results/source-plugin-canary-2026-09-12.json)

## Result

`passed` — every check in the suite succeeded on the first run after the harness
was updated for the unified entry point.

## Checks

| Check | Result |
|---|---|
| Plugin and companion load | Passed |
| Companion build | Passed (`SUCCESS`) |
| Migration previews (Extended Choice, Active Choices, List Git Branches, stashed file) | Passed |
| Extended Choice clone and apply | Passed |
| Standard REST, delimited transport | Passed (`SUCCESS`) |
| Webhook-style standard REST | Passed (`SUCCESS`) |
| Typed REST array transport | Passed (`SUCCESS`) |
| CLI JSON array | Passed (`SUCCESS`) |
| Pipeline upstream to downstream typed values | Passed (both builds `SUCCESS`) |
| Typed invalid input rejected without queueing | Passed |
| Webhook invalid input rejected without queueing | Passed |
| Rebuild | Passed |
| Stashed file multipart boundary | Passed (`SUCCESS`) |
| Migration rollback | Passed |
| Controller logs free of plugin errors | Passed |

## Harness changes required

The canary fixture and assertions still referenced the removed
`UnifiedMultiChoiceParameterDefinition` and `UnifiedMultiChoiceParameterValue`.
They now assert the unified entry instead: the Extended Choice preview generates
`unifiedChoice` with `selectionMode: 'multiple'`, the clone persists that
definition, and the upstream Pipeline passes `UnifiedChoiceParameterValue`.

## Notes

- This canary proves the plugin behaves correctly against a disposable Jenkins
  with the source plugins installed; it does not replace a real-organization
  migration pilot.
- Production verification of the same commit is recorded in the maintainer's
  lab overlay
  ([ADR 0018](../decisions/0018-public-hygiene-and-private-lab-overlay.md)).
