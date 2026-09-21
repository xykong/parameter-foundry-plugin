# Project Origin and Migrated Design History

Unified Parameters started during a Jenkins `2.568.2` production upgrade and
plugin remediation effort in the `example-ops` operations repository.

The product work was separated into this repository on August 28, 2026 so
future plugin development, architecture, tests, migration tooling, and UI
research do not mix with production operations.

## Migrated Content

- competitor capability research;
- product naming and identity decisions;
- product specification;
- complete replacement and migration design;
- UI/API semantic parity contract;
- reproducible real-plugin UI Lab;
- generated screenshots and Gallery.

## Original Local Milestones

The following local commits in the operations repository captured the design
history before migration:

| Commit | Milestone |
|--------|-----------|
| `24b42a9` | Initial modern parameter plugin research |
| `6f13400` | Active Choices compatibility validation |
| `931163f` | Parameter plugin UI Lab |
| `d59dad6` | First real-plugin UI Gallery |
| `e03c729` | Full candidate coverage |
| `23922f0` | Expanded 63-state Gallery |
| `aa36835` | Initial product specification |
| `99fea56` | Unified Parameters functional design |
| `9096ebb` | UI/API parity release guarantee |

Production Job names, credentials, cluster configuration, and upgrade evidence
were intentionally not migrated. They remain operational records in
`example-ops`.

## Naming History

The project was developed and released under the working name **Unified
Parameters** from August 28, 2026. On September 21, 2026 it was renamed to
**Parameter Foundry**; see
[ADR 0017](../decisions/0017-product-renaming-to-parameter-foundry.md) for the
decision and [`docs/research/naming-and-brand-evaluation.md`](../research/naming-and-brand-evaluation.md)
for the evaluation that replaced it.

This file and the dated verification records under `docs/testing/` keep the
names, artifact names, and identifiers that were current when they were
written. The rename is complete in the live product surface: code, resources,
build metadata, tooling, the user guide, and the current requirement
documents.
