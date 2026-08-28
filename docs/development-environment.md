# Development Environment

## Baseline

The repository was generated on August 28, 2026 with the Jenkins
`empty-plugin` archetype `1.37`.

| Component | Baseline |
|-----------|----------|
| Jenkins plugin parent | `6.2138.v03274d462c13` |
| Jenkins core baseline | `2.528.3` |
| Jenkins BOM | `6237.v4da_61a_4a_19e5` |
| Java | 17 |
| Maven | 3.9+ |

## Commands

```bash
make verify
make test
make format
make run
```

The local Jenkins instance started by `make run` uses the standard Jenkins HPI
development environment.

## UI Lab

```bash
make gallery-prepare
make gallery
make gallery-clean
```

The UI Lab starts isolated Jenkins controllers bound only to localhost and
uses synthetic data. Generated artifacts are written to `docs/ui-gallery/`.
