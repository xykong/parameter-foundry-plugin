# Configured-node whitespace verification

Release `0.9.301`, October 10, 2026. HPI SHA-256:
`e66ac6bf6415ad5eb29595b43f0e2240b5b3b29ab6471939593da6eb86e8d24b` (620,907 bytes).

Full `mvn -B -ntp verify` passes 375 tests without failures, errors or skips.
Eight Node Provider Harness tests include actual legal node names U+2003 EM SPACE,
ASCII space and a name with leading/trailing spaces. Both direct loading and the
shared Provider resolution retain U+2003 and the original padded name, and omit
the ASCII-only empty name. Existing nodes/labels modes are unchanged.

The exact release HPI passes the real-browser source/target gate: 44/44 transport
cases, configured-node radio/dropdown order and whitespace, distinct UI/API
defaults, literal HTTP input, editable old Core String history and a neutral
upstream Pipeline caller. The isolated controller is Jenkins 2.568.2; source
UNO is 2.8.10. Chromium 153.0.8010.12 runs on the macOS host. No business Job
or Job-supplied source script runs; source samples are operator-owned constants.

Reproduce after building with
`tools/ui-lab/scripts/check-active-choices-single.py --skip-build`.
[Machine-readable browser result](./results/configured-node-whitespace-ui-lab.json).

The filter checks Java `String.trim().isEmpty()` without transforming retained
values. This matches the characterized source contract; it does not grant
selected/disabled, reactive, cached-default or arbitrary script compatibility.
Per-Job source/consumer, configuration drift, rollback and retirement gates remain
required. Desktop checks do not establish device or accessibility coverage.
