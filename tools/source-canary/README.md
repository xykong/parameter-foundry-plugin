# Source Plugin Docker Canary

This tool runs a disposable Jenkins Controller with real source-plugin HPI
files and the locally built Unified Parameters artifacts.

It is intentionally limited to synthetic Jobs and binds Jenkins to a random
localhost port. It does not read production credentials, connect to production
Jenkins, modify business Jobs, or uninstall source plugins.

Run:

```bash
make source-canary
```

The direct command is equivalent:

```bash
python3 tools/source-canary/run-source-canary.py
```

The default report is written to:

```text
docs/testing/results/source-plugin-canary-YYYY-MM-DD.json
```

The tool verifies real HPI loading, migration previews, Extended Choice
clone/apply/rollback, standard REST and webhook-style delimited input, Typed
REST arrays, Jenkins CLI JSON arrays, Pipeline upstream/downstream execution,
invalid-input no-queue behavior, Rebuild rendering, Active Choices/List Git
Branches migration boundaries, Git refs companion loading, and native Stashed
File multipart submission. The canary pins Git Client `6.6.1`.
