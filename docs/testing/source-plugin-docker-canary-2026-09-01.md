# Source and Companion Plugin Docker Canary

- Verification date: September 1, 2026
- Jenkins image: `jenkins/jenkins:2.568.2-lts-jdk21`
- Scope: disposable synthetic Controller only
- Evidence: [`source-plugin-canary-2026-09-01.json`](./results/source-plugin-canary-2026-09-01.json)

The canary loaded the core plugin, four migration adapters, the reference
accessible choice-grid Renderer companion, and the sandboxed Script Provider
companion together with the pinned source plugins.

Passed checks include:

- all required plugins active and enabled;
- `accessible-choice-grid` exposed through the Unified typed schema;
- `sandbox-groovy` resolved a catalog entry into bounded options;
- a companion-backed parameter resolved and built successfully;
- existing migration preview, clone/apply, rollback, REST, Typed API, CLI,
  Pipeline, Rebuild, native multipart, invalid-input/no-queue, and controller
  log checks.

This canary does not constitute Jenkins community/security approval, manual
screen-reader sign-off, or authorization to change a production Job.
