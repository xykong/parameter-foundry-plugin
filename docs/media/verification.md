# Visual documentation verification

Date: 2026-10-29. Scope: public README, feature overview, final media assets,
and the dedicated synthetic documentation capture workflow.

## Product evidence

- Jenkins 2.568.2 with the current built 1.27.386 HPI; binary SHA-256 recorded
  in `manifest.json`. Controller bound only to localhost with synthetic fixtures.
- Five genuine matrix states captured: generated submitted values asserted as
  0, 5, 7, 15, 0. No builds submitted and no real services contacted by the scenario.
- Form capture: advanced section enabled through the control; replica input
  verified as integer `4` in the synchronized JSON value.
- Reproduction commands and pinned dependency supplements documented in
  [media provenance](README.md). Archived gallery crops explicitly identified.

## Asset and privacy checks

- GIF inspected with ffprobe: 640×600, five frames, ten-second cycle. Static
  alternative and operation transcript linked from both document entry points.
- Final raster/vector assets total approximately 1.25 MB. AI source artwork is
  compressed WebP; screenshots retain PNG text clarity.
- Known-private-identifier checks passed for public text, SVG, and screenshot/
  animation-source OCR. No OCR errors. OCR does not replace visual review.
- PNG chunk inspection found no text/EXIF metadata. Integrity hashes verified.
- Gitleaks found no leaks in the new media and capture-tool directories.
- Local document/image link checks and `git diff --check` passed; migration
  fixtures validated (8 fixtures, 14 source families).

## Actual GitHub rendering

The public repository README and `docs/guide/features.md` were inspected in
Chromium at 1440px desktop and 390px mobile widths, with light and dark appearance.
Every embedded media URL was loaded successfully, including GitHub's animated
image handling. Screenshots were inspected for headline placement, image bounds,
flow-diagram labels, and surrounding explanatory copy. README images stayed within
the mobile content width. Technical screenshot details can be opened at full size;
accessible text descriptions remain in the document rather than only in pixels.

The review does not certify a screen reader or every browser/device combination.
The GIF is a sequence of verified UI states, not a latency benchmark or continuous
video recording. The dark/light document review does not claim that archived
product screenshots themselves change theme. No product implementation changed,
so the complete Maven suite was not rerun for this media-only update.
