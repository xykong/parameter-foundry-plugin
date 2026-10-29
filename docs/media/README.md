# Documentation media

Editorial assets for the public README and feature overview. Product captures
use synthetic fixtures; concept artwork is explicitly labeled. No image changes
the displayed values, feature states, or validation results of a product capture.

## Provenance

| Asset | Source | Treatment |
|---|---|---|
| `foundry-art.webp` | AI-generated concept art, GPT Image 2.5 Flare, 2026-10-29 | No product UI; resized to 1200×800 and encoded as WebP |
| `hero.png` | Concept artwork plus `tools/docs-media/hero.html` | Browser-rendered editorial title; explicitly labeled concept art |
| `matrix.png`, `matrix-demo.gif` | Current 1.27.386 HPI in isolated Jenkins 2.568.2; synthetic Provider matrix | Element captures; GIF holds each real state for two seconds; no interpolated interaction |
| `typed-form.png` | Same isolated controller; synthetic form | Element capture after actual input and conditional-panel interaction |
| `retirement.png` | Same isolated controller; retirement page | Element capture; report scope is not certification of uninstall safety |
| `choices.png` | `docs/ui-gallery/images/parameter-foundry/light.png` | Crop `(350,130,1000,850)`; archived gallery example |
| `file-delivery.png` | `docs/ui-gallery/images/parameter-foundry/file-delivery-modes.png` | Crop `(350,130,1000,750)`; archived file-acceptance example |
| `migration-preview.png` | `docs/ui-gallery/images/parameter-foundry/migration-preview.png` | Crop `(20,1945,1400,1100)`; archived migration example, not a new-version golden |
| `evaluation-flow.svg` | Hand-authored architecture illustration | Editable SVG; types and compatibility boundaries cross-checked against user guides |
| `migration-flow.svg` | Hand-authored workflow illustration | Distinguishes configuration migration from history retirement |

The capture manifest records hashes and the exact HPI used. Archived gallery
images retain the provenance of their original acceptance scenarios; crops must
not be described as captures from a newer binary. AI concept art is not evidence
of a feature, certification, performance result, or runtime behavior.

## Reproduce

Prerequisites: the UI Lab dependency cache, a built HPI, Maven-resolved pinned
plugin dependencies, Docker, `uv`, Chromium for Playwright, and `ffmpeg`.

```sh
uv run tools/docs-media/start-lab.py
uv run tools/docs-media/capture.py
sh tools/docs-media/animate.sh
uv run tools/docs-media/render-hero.py
docker stop parameter-foundry-docs-media
```

The dedicated controller binds only `127.0.0.1:18081`. It uses synthetic fixture
defaults, ignores private fixture overrides, and owns its separate home/plugin
cache. Do not run while another UI Lab controller is using the same port.
The seed normalizes a legacy fixture's registry-row separators before loading.
The pinned dependency supplements are listed in `start-lab.py`.

The GIF illustrates state changes rather than real-time timing: clear → staging
column (5) → add first row (7) → select all (15) → invert (0). The capture script
asserts the number of generated submitted values at each step. The static matrix
is its accessible alternative. Motion is contained in the feature page and the
README's interaction section; the hero is static.

Keep raw captures, generation responses, full recordings, and review screenshots
in ignored caches or outside the repository. Only final compact assets belong
here. `manifest.json` provides integrity hashes and the concept-art prompt.

See the [completed verification record](verification.md) for checks and limitations.

## Review criteria

- Inspect the actual GitHub README and feature page at desktop and mobile widths,
  in light and dark appearance; open images at full resolution for technical detail.
- Check every referenced image loads, SVG labels are legible, and motion has a
  static alternative and a textual explanation.
- Re-scan text, SVG, PNG metadata and OCR output for private identifiers.
- Do not include internal generation endpoints, authentication metadata, or
  provider job IDs in public provenance.
- When changing the capture scenario, record its assertions and platform scope
  in `docs/testing/acceptance-cases.md`.
