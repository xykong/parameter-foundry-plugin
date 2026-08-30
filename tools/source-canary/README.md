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
docs/testing/results/source-plugin-canary-2026-08-30.json
```

The tool verifies real HPI loading, migration previews, Extended Choice
clone/apply, standard REST delimited input, Typed REST arrays, Jenkins CLI JSON
arrays, Rebuild rendering, Active Choices/List Git Branches migration
boundaries, and native Stashed File multipart submission.
