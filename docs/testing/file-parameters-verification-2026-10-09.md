# Built-in file parameters verification

Date: October 9, 2026. Scope: development snapshot `0.5.273-SNAPSHOT`, synthetic
Jobs and disposable local controllers. No production installation or migration
was performed.

| Gate | Result |
| --- | --- |
| `mvn -B -ntp verify` | 297 tests, zero failures/errors; SpotBugs and Spotless passed |
| Final focused `verify` | 20 tests passed: ten file lifecycle/symbol tests, five API security tests, three message coverage tests, two adapter tests; SpotBugs/Spotless passed |
| Audit parser | Four tests passed, including source/Foundry file family separation |
| Migration fixtures | Eight fixtures covering thirteen source families validated |
| [Real-source-plugin Canary](./results/source-plugin-canary-2026-10-09.json) | Passed; source Stashed File preview is class B with a Foundry target |
| [Real-browser UI Lab](./results/foundry-files-ui-lab.json) | Passed on Chromium 153.0.8010.12, macOS, desktop 1440x1000 and mobile viewport 412x915 |

The focused gate includes the final schema ownership assertion and the additional
Declarative file-symbol case. The Canary report records the pre-commit HEAD;
it exercised the working-tree snapshot, rather than a tagged release.

Verified behavior includes binary multipart/CLI upload, Base64 downstream values,
Declarative symbols, named stash and remote-agent access, basename sanitization,
bounded input, missing/wrong-type handling, normal/failed temporary-directory
cleanup, cancelled-queue cleanup, download bytes and attachment headers, actual
Rebuild submission, and chained Rebuild after deleting the earliest build.
Migration preview/apply/rollback preserves parameter names and descriptions.
File simulation requires manual binary verification instead of claiming option
value equivalence. Typed JSON returns 409 without queuing and the schema correctly
identifies Foundry ownership.

The browser gate also proves operation without the File Parameters plugin,
controller restart followed by retained-upload Rebuild, Freestyle wrappers and
actual mobile-viewport upload submission. [Desktop](../ui-gallery/images/parameter-foundry/file-upload.png)
and [mobile](../ui-gallery/images/parameter-foundry/file-upload-mobile.png)
screenshots come from this real-plugin run.

Uploads are bounded to 1 MiB decoded bytes for Base64 and 100 MiB for Stashed
File. Historical source-plugin values are not converted; rebuilding an older
source-plugin build against a migrated Job requires a fresh upload. Source
plugins remain installed while historical/generated/queued references need them.

Physical-device, other-browser-engine and manual screen-reader sign-off remain
pending in the [acceptance matrix](./acceptance-cases.md). Production release also
requires the [file companion security review](../security/companion-review-checklist.md).
