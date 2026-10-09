# File upload release security review

Date: October 9, 2026. Scope: the unified workspace/Base64/stash implementation
and its upload lifecycle repair. An independent read-only reviewer inspected the
implementation and Jenkins Core lifecycle contracts. Production configuration
and credentials were outside the review scope.

## Findings and disposition

| Finding | Severity | Disposition |
| --- | --- | --- |
| A parsed stored upload could survive a rejected HTTP/CLI submission | High | Repaired: ingestion must belong to a submission scope; scope exit deletes unclaimed uploads. Queue admission claims ownership synchronously; cancellation deletes it. |
| Crash recovery could precede queue restoration during controller initialization | Medium | Repaired: periodic recovery waits for `InitMilestone.COMPLETED`; restored queue items and running builds protect their upload IDs. |
| Workspace ancestor checks and final replacement have a residual filesystem race | Low | Requires a concurrent writer with the agent operating-system identity. Keep the documented trusted-agent-filesystem boundary; no independent privilege escalation established in this review. |
| Existing single-parameter migration does not restore its backup after a save failure | Medium, pre-existing | Separate migration follow-up; batch migration has restoration. Not introduced by this release. |

The reviewer confirmed the original High finding is resolved structurally and
reported no reproducible blocker after the startup guard and passing lifecycle
regressions. File storage fails closed without an HTTP/CLI submission scope.
Recovery is a crash fallback, not the normal rejected-submission cleanup path.
Unused admitted uploads are retained with their completed build; retained copies
use staged atomic replacement and follow build retention.

## Verification scope

`FileUploadOwnershipTest` covers multipart rejection after a valid upload,
CLI rejection and queue veto, admitted queue ownership across scope cleanup,
cancelled uploads, unused completed uploads, and old-orphan recovery while
protecting active ingestion and restored queue references. The full release
verification and disposable real-browser canary must pass before deployment.
The queue recovery regression clears in-memory ownership to simulate recovery;
it is not a process restart. The browser canary separately restarts Jenkins and
verifies retained-upload Rebuild. Dependency/advisory checks below were performed
by the release operator, separately from the read-only independent code review.

Dependency review uses exact runtime versions and the controller update-site
warning monitor, rather than treating minimum dependency declarations as an
instruction to downgrade installed plugins. In particular, Script Security's
September 16 fixes require `1422.v06869826dd9b_` or newer; the checked deployment
uses a newer release. See the
[official Jenkins advisory](https://www.jenkins.io/security/advisory/2026-09-16/).

This review does not claim community security approval, a new PCT matrix run,
manual screen-reader approval or physical-device coverage. Those gates remain
separate in the [companion checklist](./companion-review-checklist.md) and
[acceptance matrix](../testing/acceptance-cases.md).
