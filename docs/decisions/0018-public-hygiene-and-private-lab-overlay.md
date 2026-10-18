# ADR 0018: Public Repository Hygiene and the Private Lab Overlay

- Status: Accepted
- Date: September 21, 2026

## Context

Parameter Foundry is heading for public hosting, and its maintainers also run
it as the parameter platform of a real Jenkins installation. Both facts are
permanent, and they pull the same files in opposite directions:

- a public repository must not name the maintainer's infrastructure, internal
  systems, or the Jobs and agents that depend on them;
- the regression value of the fixtures and acceptance cases comes precisely
  from the real registries, agents, and Jobs they exercise.

Today the repository serves both audiences at once. The tree and the commits
already pushed to the private remote contain real container-registry host
names, build-agent names, production Job names, an internal controller URL,
and a cluster target. None of that may survive into a public repository.

## Decision

Split the material into a public core and a private lab overlay that share one
codebase and one contract.

### Public core

Product source, tests, tooling, and documentation. No deployment-specific
value is ever a literal; fixtures use a reserved example namespace.

| Kind | Public value |
|------|--------------|
| Container registry | `registry.example.com`, `harbor.example.net` |
| Build agent | `agent-a.example.net`, `agent-b.example.net` |
| Job | `demo-choice`, `demo-form`, `demo-migration` |
| Controller | `https://jenkins.example.com/` |

### Private lab overlay

A private repository, `private-lab-overlay`, that holds the real
values, the placeholder-to-real mapping, the dated acceptance and regression
records, and the denylist. It contains no product source, so the two
repositories never diverge in code and the overlay cannot drift into a fork.

The overlay is never published and is never a dependency of the public build:
it contributes data at run time and nothing at build time. Access to it is
deliberately closed: the overlay has a single owner, no collaborators, and is
never shared or transferred. Maintaining it does not depend on the public
repository's collaborator list, and publishing the plugin never grants anyone
access to the environment data behind it.

```text
private-lab-overlay/            (private, never published)
  fixtures/lab.properties       real values for the fixture keys
  fixtures/MAPPING.md           placeholder -> real value, for reviewers
  profiles/e2e-production.json  the data set the e2e harness replays
  acceptance/YYYY-MM-DD-*.md    dated records with real values
  acceptance/results/           raw measured results
  deploy/                       image and controller wiring
  hygiene/denylist.txt          the plain-text list of forbidden tokens
  hygiene/scan.py               scans a checkout of the public repository
```

### Contract between the two

1. **Fixture resolution.** Tests and harnesses never hard-code a deployment
   value. They resolve it through one helper that reads `PF_LAB_FIXTURES`
   first and falls back to the bundled synthetic defaults.
2. **Tool targets.** Every tool takes its target from configuration
   (`PF_JENKINS_URL`, `PF_JENKINS_AUTH`, `PF_LAB_PROFILE`). Operational wiring
   belongs to the overlay, not to the public tree.
3. **Evidence split.** The public repository keeps the acceptance-case index
   and the results stated in generic terms ("the reference controller",
   "agent A"). The overlay keeps the dated records with real values and the
   mapping table.
4. **Enforcement.** The overlay owns the plain-text denylist and scans a
   checkout of the public repository with it. The public repository ships only
   the scanner contract and generic rules (private address ranges, reserved
   example domains, credential shapes), so the forbidden words never appear in
   the public tree, not even inside a checker.
5. **History.** The public repository is published from a sanitized history.
   The private repository keeps the complete record, including every commit
   that named real infrastructure.

## Execution

| Phase | Work | Status |
|-------|------|--------|
| 0 | Inventory the tree and the history for identifiers | Done: 122 hits in 19 files, each classified |
| 1 | Introduce the fixture resolver, the bundled synthetic defaults, and the configuration contract | Done: `LabFixtures` plus `PF_LAB_FIXTURES`; the matrix test passes with synthetic and with real values |
| 2 | Sanitize the public tree and move the operational tooling and the dated records to the overlay | Done: 17 records, 7 result files, and 6 controller scripts moved out |
| 3 | Stand up the overlay with real values, the mapping, the denylist, and the acceptance records | Done in a private overlay |
| 4 | Enforce: pre-push hook, overlay scan, and the public generic rules | Done for the hook and the overlay scanner; the overlay also runs a daily scan |
| 5 | Sanitize all repository history and re-scan every object | Completed October 18 with explicit authorization: rewrite every local ref, remove historical binaries, synchronize linked worktree indexes, and purge old objects. The original Git data is backed up outside this repository. |
| 6 | Publish and keep the overlay as the internal archive | Open: the hosting request starts from the public repository and the Jenkins hosting team forks it into `jenkinsci` ([ADR 0019](./0019-release-publication-mechanism.md)) |

The earlier history scan was not a guarantee for later commits or binary
artifacts. The current repository has now been sanitized in place, preserving
commit ancestry, branch names, tag names, and the public maintainer identity.
Rewritten commit and tag object IDs differ from the external private backup.
Verification covers every stored Git object, not only objects reachable from
branch tips. See the [publication preparation workflow](../security/open-source-preparation.md).

## Consequences

- The public repository can be reviewed by anyone without exposing the
  maintainer's environment.
- The regression suite keeps its real coverage: the overlay supplies the real
  values at run time instead of the repository carrying them.
- Every new test must declare its fixture keys, so deployment-specific data
  cannot re-enter the public tree by habit.
- Reports published from the public repository are phrased generically; the
  version with real values stays in the overlay.
- The overlay becomes a required part of the maintainer workflow: a release is
  verified publicly with synthetic fixtures and internally with real ones.
