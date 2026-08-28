# Unified Parameters

## Introduction

Unified Parameters is a community-oriented Jenkins plugin project that aims to
provide one secure, typed, extensible parameter model across the Jenkins UI,
Pipeline, REST API, CLI, webhooks, upstream builds, remote triggers, and
Rebuild.

The project is in the design and bootstrap phase. Implementation has not
started yet.

Product and technical design documents are under [`docs/`](./docs/).
The reproducible competitor UI benchmark is under
[`tools/ui-lab/`](./tools/ui-lab/), with generated results in
[`docs/ui-gallery/`](./docs/ui-gallery/).

## Development

Requirements:

- Java 17
- Maven 3.9+
- Docker and Google Chrome for the UI Lab

Verify the plugin scaffold:

```bash
make verify
```

Run a local Jenkins development instance:

```bash
make run
```

Regenerate the competitor UI Gallery:

```bash
make gallery
```

## Issues

Report issues and enhancements in the [Jenkins issue tracker](https://issues.jenkins.io/).

## Contributing

Refer to the Jenkins project
[contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md).

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)
