# Source Plugin Docker Canary Report

- Date: September 22, 2026
- Jenkins image: `jenkins/jenkins:2.568.2-lts-jdk21`
- Target commit: `423b2df3b1251b4aef756dc0ad4266ab2876060f`
- Plugin artifact: `plugin/target/parameter-foundry.hpi`
- Scope: synthetic Docker canary only; no production configuration involved
- Machine-readable result: [`results/source-plugin-canary-2026-09-22.json`](./results/source-plugin-canary-2026-09-22.json)

This is the first canary run of the renamed plugin. The September 4 and
September 12 records were measured while the plugin was still published as
`unified-parameters`, so they neither prove the new plugin identifier nor the
renamed error codes.

## Result

`passed` — every check succeeded on the first run, with the disposable
controller loading `parameter-foundry` next to Extended Choice, Active Choices,
List Git Branches, and File Parameters.

## Checks

| Check | Result |
|---|---|
| Plugin and companion load (`parameter-foundry`, four source plugins) | Passed |
| Companion renderer and script engine | Passed |
| Migration previews (Extended Choice, Active Choices, List Git Branches, stashed file) | Passed |
| Sensitive source values stay redacted in previews | Passed |
| Extended Choice clone and apply | Passed |
| Standard REST, delimited transport | Passed (`SUCCESS`) |
| Webhook-style standard REST | Passed (`SUCCESS`) |
| Typed REST array transport | Passed (`SUCCESS`) |
| CLI JSON array | Passed (`SUCCESS`) |
| Pipeline upstream to downstream typed values | Passed (`SUCCESS`) |
| Typed invalid input rejected without queueing | Passed (`400`, `PARAMETER_FOUNDRY_UNKNOWN_VALUE`, unchanged build number and queue) |
| Webhook invalid input rejected without queueing | Passed (`400`, `PARAMETER_FOUNDRY_UNKNOWN_VALUE`, unchanged build number and queue) |
| Rebuild | Passed |
| Stashed file multipart boundary | Passed (`SUCCESS`) |
| Migration rollback | Passed |
| Controller logs free of plugin errors | Passed |

## Notes

- The stable error-code prefix under test is `PARAMETER_FOUNDRY_`; the two
  rejection cases assert both the code and the unchanged queue state.
- The run uses synthetic fixture values bundled with the repository. The
  maintainer's lab overlay repeats the same suite against the real values
  ([ADR 0018](../decisions/0018-public-hygiene-and-private-lab-overlay.md)).
