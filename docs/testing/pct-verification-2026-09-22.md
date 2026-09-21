# Plugin Compatibility Tester Verification

- Verification date: September 22, 2026
- PCT commit: `8a4e8125284289f5af87a91c717ada7f022b2daf`
- Docker image: `maven:3.9.11-eclipse-temurin-21`
- Target commit: `2c74c6c`
- Command: `make pct`
- Evidence: [`pct-2026-09-22.json`](./results/pct-2026-09-22.json)

This is the first PCT run of the renamed plugin, so it is the first record that
proves the `parameter-foundry` plugin identifier and the renamed classes
against both supported Jenkins cores.

| Jenkins Core | HPI modules | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| `2.528.3` | 1 | 269 | 0 | 0 | 0 |
| `2.568.2` | 1 | 269 | 0 | 0 | 0 |

The suite now reports 269 tests per core, up from 197 in the September 12
record. The local HPI is compiled to Java 17 bytecode for Core `2.528.3` and
Java 21 bytecode for Core `2.568.2`, and the same Core and compiler properties
are passed to PCT's original-POM build. The run uses a disposable Git checkout,
requires a clean repository, and does not modify the working tree.

## Notes

- Run with the repository at `2c74c6c`, which carries the completed rename and
  the September 22 canary record. The published `0.4.255` artifact and this
  commit differ only in documentation.
- PCT is a compatibility build and test gate. It does not replace browser E2E,
  the source-plugin Docker canary, production-like ingress and authentication
  tests, manual screen-reader verification, or organization migration pilots.
