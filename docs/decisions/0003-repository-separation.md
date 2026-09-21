# ADR 0003: Repository Separation

- Status: Accepted
- Date: August 28, 2026

## Decision

Parameter Foundry product development is maintained in this independent
repository.

The `example-ops` repository remains responsible only for production operations,
deployment configuration, upgrade evidence, and organization-specific Jenkins
runtime work.

Product design documents and the competitor UI Lab were migrated here on
August 28, 2026. Production upgrade and validation records remain in the
operations repository.
