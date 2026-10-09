# Unified file delivery modes verification

Date: October 9, 2026. Development snapshot: `0.5.274-SNAPSHOT`. All Jobs and
uploads are synthetic. No production installation or Job migration was performed.

| Gate | Result |
| --- | --- |
| Full `mvn -B -ntp verify` | 309 tests passed, zero failures/errors, SpotBugs and Spotless passed |
| Final file/API/migration `verify` | 54 tests passed after the duplicate-property and legacy-Rebuild fixes; zero SpotBugs findings |
| Final preview/help/message check | Six tests passed, including file preview mode/path/limit and complete locale/help coverage |
| Audit parser | Five tests passed, including all unified delivery modes |
| Migration fixtures | Eight fixtures covering fourteen source families validated |
| [Real-browser UI Lab](./results/foundry-file-modes-ui-lab.json) | Passed on Chromium 153.0.8010.12, macOS, 1440x1000 desktop and 412x915 mobile viewport; Jenkins 2.568.2 Linux controller |

The final focused gates cover the changes made while inspecting the full
regression and browser results. They include twelve workspace/migration/input
cases, the ten earlier file lifecycle cases and all existing migration
coordinator tests. The help/preview gate covers the final explanatory text.

Verified contracts:

- `foundryFile` selects workspace, Base64 or stash; earlier aliases remain usable.
- `maxUploadBytes=0` inherits the delivery default; positive byte limits override
  it. UI/API/CLI, downstream values, wrapper delivery and Rebuild enforce policy.
- Workspace mode places automatically in Freestyle and through the Pipeline
  wrapper on a remote agent. Relative destinations and Core filename environment
  keys survive migration; independently declared `_FILENAME` variables survive.
- Workspace omission retains existing files; ordinary targets are replaced.
  Traversal, absolute/drive/stream/device paths and symbolic links are refused;
  staged files are removed after success or failure.
- Base64 supports Pipeline input. Workspace/stash input is refused without
  creating pending uploads. Binary simulation does not evaluate source file
  values or their environment and requests manual lifecycle verification.
- Core clone/apply/build/rollback works on actual Jenkins. Equivalent duplicate
  parameter properties become one effective target; conflicting duplicates are
  refused without changing the original configuration. Rollback restores backup.
- Legacy Rebuild without a replacement is rejected before scheduling; uploading
  a replacement succeeds. Own retained uploads survive restart and Rebuild.
- Typed JSON remains explicitly unsupported for binary definitions and reports
  Foundry ownership; standalone operation does not require File Parameters.

[Desktop](../ui-gallery/images/parameter-foundry/file-delivery-modes.png),
[mobile](../ui-gallery/images/parameter-foundry/file-delivery-modes-mobile.png),
[configuration](../ui-gallery/images/parameter-foundry/file-delivery-configuration.png)
and [Core preview](../ui-gallery/images/parameter-foundry/core-file-migration-preview.png)
screenshots were captured from the real-plugin controller. The configuration
capture uses a 1440x2600 viewport so all file-policy controls remain visible
without the sticky navigation or save bar covering them.

Physical devices, Windows execution, other browser engines and manual keyboard /
screen-reader sign-off remain pending in the [acceptance matrix](./acceptance-cases.md).
A production release also requires the [companion security review](../security/companion-review-checklist.md).

## Release verification: 0.5.276

- Full regression: 316 tests passed with zero failures/errors.
- Final lifecycle and delivery regression after the visibility/nullability repair:
  27 tests passed; `mvn -B -ntp verify` with the three file test classes passed,
  including SpotBugs and Spotless. The complete earlier run passed all tests but
  reported three static findings, which this final gate cleared.
- Independent [security review](../security/file-upload-review-2026-10-09.md)
  cleared the release blocker after the submission cleanup and startup recovery
  guard were repaired.
- Exact release HPI browser evidence and artifact digest are recorded when the
  final package is pinned in `dist/`.
