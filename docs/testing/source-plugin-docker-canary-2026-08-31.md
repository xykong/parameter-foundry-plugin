# Source Plugin Docker Canary

- Verification date: August 31, 2026
- Jenkins: `jenkins/jenkins:2.568.2-lts-jdk21`
- Target commit: `b450175`
- Scope: synthetic Docker Controller only; no production Job, credential, or
  source-plugin configuration was used

## Command

```bash
make source-canary
```

Detailed redacted output:
[`source-plugin-canary-2026-08-31.json`](./results/source-plugin-canary-2026-08-31.json).

## Passed Checks

- Required real source-plugin, core, and migration HPI files loaded and were
  active.
- Extended Choice Class B migration preview, clone/apply, target persistence,
  and audit record passed.
- Standard REST delimited input and webhook-style request passed.
- Typed REST array validate/build passed.
- Jenkins CLI JSON-array build passed inside the Jenkins Java 21 container.
- Pipeline upstream triggered a downstream build with the canonical sorted
  multi-choice value.
- Invalid Typed REST and invalid webhook-style requests returned stable
  `400` errors without changing build number or queue length.
- Rebuild parameter rendering passed.
- Real Stashed File native multipart submission passed.
- Active Choices and List Git Branches migration skeleton boundaries passed,
  including source-value redaction.
- No unexpected Controller plugin-load, init-script, or `SEVERE` errors.

## Boundary

This closes the repeatable local source-plugin Docker Canary for the tested
synthetic fixtures. It does not close production-like verification of ingress,
OIDC/API-token policy, real Agents, organization-specific Job behavior,
source-plugin lifecycle retention, or the final companion-plugin and Jenkins
community security reviews.
