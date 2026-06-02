#!/usr/bin/env bash
# Generate deterministic, license-free animated GIF fixtures for the :app bench
# flavor feed.
#
# Why synthetic instead of a downloaded GIF:
#   - Real Bluesky GIFs (Klipy/Tenor/Giphy) are ~250-580 KB, ~256-480 px, and
#     20-50 frames of high-entropy photographic content. GIF decode cost scales
#     with frame count x dimensions x content entropy, so a flat gradient (the
#     old loop.gif: 32 KB, 3 frames) decodes for free and makes the GIF feed
#     benchmark meaningless -- it would report "GIFs add zero jank" even if real
#     ones drop frames.
#   - A downloaded GIF carries someone's copyright and trips the 500 KB
#     check-added-large-files pre-commit hook. These plasma-fractal loops have NO
#     third-party content (no licensing) and are tuned to stay under 500 KB while
#     matching the real decode profile (high-entropy, ~256 px, ~32 frames).
#
# Five DISTINCT variants (loop-a..e) so each feed post points at a different
# file: distinct Coil cache keys => genuinely independent decodes, not one cached
# AnimatedImageDrawable reused across every post (which would under-measure).
#
# Deterministic: ImageMagick `-seed` fixes the plasma RNG, and motion is a
# wrap-around roll over a tiled base (seamless loop). Re-running overwrites.
#
# Output: feature/feed/impl/src/bench/assets/img/gifs/loop-{a..e}.gif
#
# Dependencies: magick (ImageMagick 7.x) or convert (6.x).
#
# Usage: ./scripts/generate-benchmark-gifs.sh

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${REPO_ROOT}/feature/feed/impl/src/bench/assets/img/gifs"
TMPDIR="$(mktemp -d -t nubecita-bench-gif-XXXX)"
trap 'rm -rf "${TMPDIR}"' EXIT

if command -v magick >/dev/null 2>&1; then
  IM="magick"
elif command -v convert >/dev/null 2>&1; then
  IM="convert"
else
  echo "ImageMagick not found. Install with 'brew install imagemagick' or 'apt-get install imagemagick'." >&2
  exit 1
fi

# Frame window (the committed GIF) and the larger base it pans across.
# Window 224x192 (aspectRatio ~1.167) at 128 colors keeps each file ~300-350 KB
# -- representative of a Tenor/Klipy preview, comfortably under the 500 KB hook.
W=224; H=192
BW=448; BH=384
FRAMES=32
STEP_X=$((BW / FRAMES))   # 14 -> 32 * 14 = 448 = BW => seamless X wrap
STEP_Y=$((BH / FRAMES))   # 12 -> 32 * 12 = 384 = BH => seamless Y wrap (diagonal drift)

mkdir -p "${OUT}"
rm -f "${OUT}"/loop*.gif

variants=(a b c d e)
for idx in "${!variants[@]}"; do
  name="${variants[$idx]}"
  seed=$((1000 + idx))
  hue=$((40 + idx * 50))   # rotate the palette per variant so they read as different clips

  base="${TMPDIR}/base-${name}.png"
  "${IM}" -size "${BW}x${BH}" -seed "${seed}" plasma:fractal \
    -blur 0x1.2 -modulate 100,150,"${hue}" "${base}"

  frames=()
  for ((i = 0; i < FRAMES; i++)); do
    dx=$(((i * STEP_X) % BW))
    dy=$(((i * STEP_Y) % BH))
    f="${TMPDIR}/f-${name}-$(printf '%02d' "${i}").png"
    "${IM}" "${base}" -virtual-pixel tile -roll "+${dx}+${dy}" \
      -gravity center -crop "${W}x${H}+0+0" +repage "${f}"
    frames+=("${f}")
  done

  out="${OUT}/loop-${name}.gif"
  "${IM}" -delay 6 -loop 0 "${frames[@]}" -colors 128 -layers Optimize "${out}"
  echo "  loop-${name}.gif  $(du -h "${out}" | cut -f1)"
done

echo "done -> ${OUT}"
