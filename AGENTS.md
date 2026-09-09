# Unified Parameters Agent Rules

## Project Scope

This repository owns the community Jenkins plugin product, implementation,
tests, architecture, migration tooling, and UI research for Unified Parameters.

Production Jenkins operations, deployment configuration, credentials, cluster
changes, and organization-specific Job data do not belong in this repository.

## Language

- Public user and contributor documentation defaults to English.
- Internal design records may use Chinese while the project is in its initial
  design phase.
- Code, identifiers, commit messages, and comments use English.

## Development

- Use Java 17 or newer as permitted by the configured Jenkins baseline.
- Use the Jenkins plugin parent and BOM; avoid unmanaged dependency versions.
- Run `mvn -B -ntp verify` before committing implementation changes.
- UI behavior must be tested in the Jenkins Test Harness and the real-browser
  UI Lab where applicable.
- UI and API parameter semantics must share the same evaluation service.

## Security

- The core plugin must not execute arbitrary Groovy or JavaScript.
- The core plugin must not read arbitrary controller file paths.
- Do not commit credentials, tokens, cookies, production Job XML, or internal
  service endpoints.
- High-risk capabilities belong in reviewed companion plugins.

## Documentation

- Product specifications live in `docs/product/`.
- Research and competitor analysis live in `docs/research/`.
- Architecture decisions live in `docs/decisions/`.
- Generated real-plugin screenshots live in `docs/ui-gallery/`.
- UI Lab automation lives in `tools/ui-lab/`.
- Acceptance cases and user-reported UI issues must be recorded in
  `docs/testing/acceptance-cases.md`. Keep shared cross-platform cases
  separate from platform/device-specific cases, and include reproduction
  steps, expected behavior, actual behavior, fix, and verification status.
- When adding or changing a test, update the index and platform coverage
  matrix in `docs/testing/acceptance-cases.md`; do not rely only on generated
  screenshots or CI output.

## Git

- Keep commits task-scoped.
- Commit completed, coherent work promptly after each logical milestone and
  relevant verification; do not accumulate a long development session into one
  large uncommitted change.
- Every publish/release deployment must increment the plugin version in
  `pom.xml`; never deploy a new release using the previous version number.
- Do not push without explicit user authorization.
- Do not rewrite or amend commits unless explicitly requested.
