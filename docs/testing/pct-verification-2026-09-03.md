# Plugin Compatibility Tester Verification

- Verification date: September 3, 2026 (UTC)
- PCT commit: `8a4e8125284289f5af87a91c717ada7f022b2daf`
- Docker image: `maven:3.9.11-eclipse-temurin-21`
- Target commit: `8372217`
- Command: `make pct`
- Evidence: [`pct-2026-09-03.json`](./results/pct-2026-09-03.json)

The matrix covers the single Unified Parameters HPI, including four migration
adapters, the accessible choice-grid Renderer, the sandboxed Script Provider,
and the Git refs Provider.

| Jenkins Core | Modules | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| `2.528.3` | 1 | pending refresh | 0 | 0 | pending |
| `2.568.2` | 1 | pending refresh | 0 | 0 | pending |

The local reactor is compiled to Java 17 bytecode for Core `2.528.3` and Java
21 bytecode for Core `2.568.2`. The same Core and compiler properties are
passed to PCT's original-POM build. The run uses a disposable Git checkout,
requires a clean repository, and does not modify the working tree.
