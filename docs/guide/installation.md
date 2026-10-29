# Installation and prerequisites

Official Jenkins hosting is under review in [request #5412](https://github.com/jenkins-infra/repository-permissions-updater/issues/5412).
The plugin is not yet distributed through the Jenkins update center, and no
GitHub Release binary is provided. Build from the public source in the meantime.

## Choose a source version

| Source | Jenkins baseline | Runtime requirement | Artifact location |
|---|---|---|---|
| Current 1.28 development branch | 2.555.3 | Java 21 or a newer runtime supported by Jenkins | `target/parameter-foundry.hpi` |
| Tag `parameter-foundry-1.27.386` | 2.528.3 | A Java runtime supported by that Jenkins version; plugin bytecode targets Java 17 | `plugin/target/parameter-foundry.hpi` |

Build with JDK 21 and Maven 3.9+, or use the pinned Docker build in the repository.
The POM defines the current baseline; the [development guide](../development-environment.md)
records the toolchain. Archived tags retain their own layout and prerequisites.

## Build the development branch

```sh
git clone https://github.com/xykong/parameter-foundry-plugin.git
cd parameter-foundry-plugin
mvn -B -ntp verify
```

Alternatively, `make verify-container` runs the pinned JDK 21/Maven container.
To build the historical tag, run `git checkout parameter-foundry-1.27.386` before
building and use the artifact location in the table above.

## Install on Jenkins

Upload the built HPI through **Manage Jenkins → Plugins → Advanced settings →
Deploy Plugin**. Install required dependencies and restart Jenkins when prompted.
The single HPI contains first-party capabilities but still requires its declared
Jenkins plugin dependencies. Job DSL is needed for the optional Job DSL integration;
the Rebuild workflow requires the Rebuild plugin.

After installation, confirm **Parameter Foundry** appears under Installed plugins,
then follow [Getting started](getting-started.md). Use a test controller before
migrating existing Jobs; [migration](migration.md) and [history compatibility](history-compatibility.md)
describe separate verification and retirement gates.

Once an official release is available, this guide will make installation through
the Jenkins plugin manager the primary path. Source builds remain useful for
contributors and exact-version testing.
