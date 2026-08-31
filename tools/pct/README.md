# Docker PCT Verification

This tool runs the official Jenkins Plugin Compatibility Tester (PCT) in
Docker against the repository's supported Jenkins Core versions.

The repository is cloned into a disposable local checkout so PCT may run its
Maven compatibility build without modifying the working tree. The Docker
container reuses the user's Maven cache for dependency downloads and local
SNAPSHOT installation. No production credentials, Jenkins data, or
source-plugin configuration are used.

Run:

```bash
make pct
```

The report is written to:

```text
docs/testing/results/pct-YYYY-MM-DD.json
```

The check covers the Core plugin and all four migration adapter modules. It
first installs the local reactor snapshot into the Docker Maven cache, then
runs PCT with the Jenkins WAR generated for each supported Core version.

PCT itself runs on Java 21, while the local Unified Parameters artifacts are
explicitly compiled to Java 17 bytecode for every matrix entry. This matches
the repository's Jenkins plugin baseline and prevents the local snapshot from
being unreadable by PCT's original-POM Java 17 compilation step. The reactor
is cleaned before each install so the result does not depend on stale
`target/` output.

If a matrix entry fails, the JSON report includes the failing module's test
summary and the first Maven compilation or build diagnostic when available.

PCT is a compatibility build/test gate. It does not replace browser E2E,
source-plugin Docker Canary, production-like ingress/authentication tests,
manual screen-reader verification, or organization migration pilots.
