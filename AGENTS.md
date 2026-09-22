# Parameter Foundry Agent Rules

## Project Scope

This repository owns the community Jenkins plugin product, implementation,
tests, architecture, migration tooling, and UI research for Parameter Foundry.

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

- Keep `CHANGELOG.md` current: every release adds its version section and the
  entries describe user-visible changes only.
- User-facing documentation lives in `docs/guide/`. Keep it, `README.md`, and
  `llms.txt` aligned whenever the public parameter surface, symbols, transports,
  or workflows change.
- Every configuration field must document itself inside Jenkins: a
  `help-<field>.html` next to the owning `config.jelly`.
  `ConfigurationHelpCoverageTest` enforces this, so add the help file in the same
  change as the field.
- `README.md`, `docs/README.md`, and `docs/guide/README.md` are the entry points
  for users; `llms.txt` is the entry point for AI assistants. Update the index
  when adding a guide page.
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
- Plugin versions follow `<major>.<minor>.<commit-count>`.
- Increment the second field when the release scope changes.
- The third field is the number of commits on `master` at the released commit,
  so `git rev-list --count master` returns that number for the version commit.
- From the first Jenkins CD release the version becomes
  `<major>.<minor>.<git-depth>.v<hash>` and the CD workflow computes it; see
  [ADR 0019](./docs/decisions/0019-release-publication-mechanism.md).
- Release tags are `parameter-foundry-<version>`.
- Keep the `-SNAPSHOT` suffix while the build is unpublished and drop it for a
  tagged release.
- Every publish/release deployment must set the plugin version in `pom.xml`
  accordingly; never deploy a new release using the previous version number.
- Do not push without explicit user authorization.
- Do not rewrite or amend commits unless explicitly requested.
