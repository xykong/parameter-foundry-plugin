# Unified Parameters

## Introduction

Unified Parameters is a community-oriented Jenkins plugin project that aims to
provide one secure, typed, extensible parameter model across the Jenkins UI,
Pipeline, REST API, CLI, webhooks, upstream builds, remote triggers, and
Rebuild.

The current development branch implements the roadmap through the `2.0`
foundation: static and governed dynamic choices, reactive dependencies and
Provider snapshots, typed Unified Forms, migration execution and rollback,
trusted companion extension contracts, reference Renderer and sandboxed Script
Provider companions, source migration boundaries, and retirement reporting.
The artifact version remains `0.1-SNAPSHOT` until the release and community
review gates are completed.

Product and technical design documents are under [`docs/`](./docs/).
The reproducible competitor UI benchmark is under
[`tools/ui-lab/`](./tools/ui-lab/), with generated results in
[`docs/ui-gallery/`](./docs/ui-gallery/).

## Development

Requirements:

- JDK 21 for the build; the plugin emits Java 17 bytecode
- Maven 3.9+
- Docker and Google Chrome for the UI Lab

Run the complete verification in the pinned JDK 21 container:

```bash
make verify-container
```

Run a local Jenkins development instance:

```bash
make run
```

Regenerate the competitor UI Gallery:

```bash
make gallery
```

Run the real-browser Unified Parameters E2E suite:

```bash
make e2e
```

## Issues

Report issues and enhancements in the [Jenkins issue tracker](https://issues.jenkins.io/).

## Contributing

Refer to the Jenkins project
[contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md).

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)
