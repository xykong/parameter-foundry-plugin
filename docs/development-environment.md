# Development Environment

## Baseline

The repository was generated on August 28, 2026 with the Jenkins
`empty-plugin` archetype `1.37`.

| Component | Baseline |
|-----------|----------|
| Jenkins plugin parent | `6.2138.v03274d462c13` |
| Jenkins core baseline | `2.528.3` |
| Jenkins BOM | `6237.v4da_61a_4a_19e5` |
| Java bytecode target | 17 |
| Build runtime | JDK 21 |
| Maven | 3.9+ |

## Commands

```bash
make verify
make verify-container
make test
make format
make run
```

The current Jenkins plugin parent runs Maven with JDK 21 while producing Java
17-compatible plugin bytecode. `make verify-container` is the reproducible
fallback when the host only has JDK 17.

The local Jenkins instance started by `make run` uses the standard Jenkins HPI
development environment.

## Releases

Until hosting completes, built HPIs and their digests are retained outside
the public source repository. Historical `dist/` references identify archived
release artifacts and do not refer to files included in the public checkout. The public release path
is Jenkins continuous delivery to `repo.jenkins-ci.org`: the version format,
the tag convention, and the steps that depend on hosting are recorded in
[`ADR 0019`](./decisions/0019-release-publication-mechanism.md).

## UI Lab

```bash
make gallery-prepare
make gallery
make gallery-clean
```

The UI Lab starts isolated Jenkins controllers bound only to localhost and
uses synthetic data. Generated artifacts are written to `docs/ui-gallery/`.

## Repository identity

The public history uses `xy.kong <xy.kong@gmail.com>` as the maintainer
identity. Do not put access tokens or deployment credentials in commits.

Contributors should use their own public identity. Configure it locally if needed:

```bash
git config user.name "Your Name"
git config user.email "you@example.com"
```
