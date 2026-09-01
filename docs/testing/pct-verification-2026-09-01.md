# Plugin Compatibility Tester Verification

- Verification date: September 1, 2026
- PCT commit: `8a4e8125284289f5af87a91c717ada7f022b2daf`
- Docker image: `maven:3.9.11-eclipse-temurin-21`
- Command: `make pct`
- Evidence: [`pct-2026-09-01.json`](./results/pct-2026-09-01.json)

The matrix covers the Unified Parameters core plugin, four migration adapters,
the accessible choice-grid Renderer companion, and the sandboxed Script
Provider companion.

| Jenkins Core | Modules | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| `2.528.3` | 7 | 165 | 0 | 0 | 6 |
| `2.568.2` | 7 | 165 | 0 | 0 | 6 |

The local reactor is compiled to Java 17 bytecode for Core `2.528.3` and Java
21 bytecode for Core `2.568.2`. The same Core and compiler properties are
passed to PCT's original-POM build. The run uses a disposable Git checkout and
does not modify the working tree.
