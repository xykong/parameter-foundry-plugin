#!/bin/sh
# Assemble actual UI states; no synthetic interaction frames are introduced.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
RAW="$ROOT/tools/e2e/.cache/docs-media-capture"
mkdir -p "$RAW/padded"
for frame in "$RAW"/0*.png; do
  ffmpeg -y -i "$frame" -vf 'pad=640:600:20:20:color=white' "$RAW/padded/$(basename "$frame")"
done
ffmpeg -y -framerate 1/2 -pattern_type glob -i "$RAW/padded/0*.png" \
  -vf 'split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer' \
  -loop 0 "$ROOT/docs/media/matrix-demo.gif"
