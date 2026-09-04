# Plugin Compatibility Tester Verification

- Verification date: September 4, 2026
- PCT commit: `8a4e8125284289f5af87a91c717ada7f022b2daf`
- Docker image: `maven:3.9.11-eclipse-temurin-21`
- Target commit: `db6e3b2`
- Command: `make pct`
- Evidence: [`pct-2026-09-04.json`](./results/pct-2026-09-04.json)

The matrix covers the single Unified Parameters HPI, including the core
platform, migration adapters, accessible choice-grid Renderer, sandboxed
Script Provider, and Git refs Provider.

| Jenkins Core | HPI modules | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| `2.528.3` | 1 | 157 | 0 | 0 | 0 |
| `2.568.2` | 1 | 157 | 0 | 0 | 0 |

The local HPI is compiled to Java 17 bytecode for Core `2.528.3` and Java 21
bytecode for Core `2.568.2`. The same Core and compiler properties are passed
to PCT's original-POM build. The run uses a disposable Git checkout, requires
a clean repository, and does not modify the working tree.
