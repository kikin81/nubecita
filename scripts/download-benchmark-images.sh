#!/usr/bin/env bash
# Download deterministic image fixtures for the :app bench flavor.
#
# Pulls 8 avatars, 8 post images, and 2 external-link thumbnails from
# CC-licensed sources, then resizes each to a fixed bench-friendly size
# (256x256 for avatars, 800x600 / 800x800 / 800x1200 for posts depending
# on aspect, 600x400 for external-link cards). Output lands under
# app/src/bench/assets/img/. Idempotent — re-running overwrites.
#
# Provenance and licensing notes per asset are inline below. All sources
# are Unsplash (license: free to use, no attribution required, no
# modification restriction). The named photographer credits are preserved
# as a courtesy.
#
# Dependencies:
#   - curl  (any recent version)
#   - magick or convert  (ImageMagick 7.x or 6.x respectively)
#
# Usage:
#   ./scripts/download-benchmark-images.sh
#
# Re-target Unsplash CDN URLs by appending ?w=1600&q=80 — adjust below if
# the upstream API changes.

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="${REPO_ROOT}/app/src/bench/assets/img"
TMPDIR="$(mktemp -d -t nubecita-bench-img-XXXX)"
trap 'rm -rf "${TMPDIR}"' EXIT

mkdir -p "${ASSETS}/avatars" "${ASSETS}/posts" "${ASSETS}/external"

# Prefer ImageMagick 7 (`magick`); fall back to 6 (`convert`).
if command -v magick >/dev/null 2>&1; then
  IM="magick"
elif command -v convert >/dev/null 2>&1; then
  IM="convert"
else
  echo "ImageMagick not found. Install with 'brew install imagemagick' or 'apt-get install imagemagick'." >&2
  exit 1
fi

# fetch <src-url> <tmp-name>
fetch() {
  local url="$1" out="${TMPDIR}/$2"
  echo "  fetch  $2"
  curl -fsSL -o "${out}" "${url}"
}

# resize_square <tmp-name> <dest-path> <size>
resize_square() {
  local src="${TMPDIR}/$1" dst="$2" size="$3"
  echo "  resize $(basename "${dst}")  (${size}x${size})"
  "${IM}" "${src}" -resize "${size}x${size}^" -gravity center -extent "${size}x${size}" -quality 82 "${dst}"
}

# resize_landscape <tmp-name> <dest-path> <w> <h>
resize_landscape() {
  local src="${TMPDIR}/$1" dst="$2" w="$3" h="$4"
  echo "  resize $(basename "${dst}")  (${w}x${h})"
  "${IM}" "${src}" -resize "${w}x${h}^" -gravity center -extent "${w}x${h}" -quality 82 "${dst}"
}

UNSPLASH_OPTS="w=1600&q=80&auto=format&fit=crop"

# ------------------------------------------------------------------
# Avatars — 256x256, square, ~30-50 KB each after resize.
# Source: Unsplash, license: free, no attribution required.
# ------------------------------------------------------------------
echo "==> Avatars"

# alice — portrait, software engineer vibe.
# Photo by Christina @ wocintechchat.com, https://unsplash.com/photos/0Zx1bDv5BNY
fetch "https://images.unsplash.com/photo-1573497019940-1c28c88b4f3e?${UNSPLASH_OPTS}" "alice.jpg"
resize_square "alice.jpg" "${ASSETS}/avatars/alice.jpg" 256

# bob — portrait, casual.
# Photo by Jurica Koletic, https://unsplash.com/photos/7YVZYZeITc8
fetch "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?${UNSPLASH_OPTS}" "bob.jpg"
resize_square "bob.jpg" "${ASSETS}/avatars/bob.jpg" 256

# carmen — portrait.
# Photo by Christina @ wocintechchat.com, https://unsplash.com/photos/Q80LYxv_Tbs
fetch "https://images.unsplash.com/photo-1580489944761-15a19d654956?${UNSPLASH_OPTS}" "carmen.jpg"
resize_square "carmen.jpg" "${ASSETS}/avatars/carmen.jpg" 256

# diego — outdoors-y portrait.
# Photo by Brooke Cagle, https://unsplash.com/photos/g1Kr4Ozfoac
fetch "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?${UNSPLASH_OPTS}" "diego.jpg"
resize_square "diego.jpg" "${ASSETS}/avatars/diego.jpg" 256

# elena — portrait.
# Photo by Houcine Ncib, https://unsplash.com/photos/a1aJpwgKHsg
fetch "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?${UNSPLASH_OPTS}" "elena.jpg"
resize_square "elena.jpg" "${ASSETS}/avatars/elena.jpg" 256

# fede — portrait.
# Photo by Andrea Piacquadio (Pexels mirror via Unsplash), https://unsplash.com/photos/IF9TK5Uy-KI
fetch "https://images.unsplash.com/photo-1599566150163-29194dcaad36?${UNSPLASH_OPTS}" "fede.jpg"
resize_square "fede.jpg" "${ASSETS}/avatars/fede.jpg" 256

# gabe — portrait.
# Photo by Stefan Stefancik, https://unsplash.com/photos/QXevDflbl8A
fetch "https://images.unsplash.com/photo-1494790108377-be9c29b29330?${UNSPLASH_OPTS}" "gabe.jpg"
resize_square "gabe.jpg" "${ASSETS}/avatars/gabe.jpg" 256

# hugo — portrait.
# Photo by Ethan Hoover, https://unsplash.com/photos/0gI1Ax9speA
fetch "https://images.unsplash.com/photo-1531123897727-8f129e1688ce?${UNSPLASH_OPTS}" "hugo.jpg"
resize_square "hugo.jpg" "${ASSETS}/avatars/hugo.jpg" 256

# ------------------------------------------------------------------
# Post images — 800px on the long edge, 60-80 KB each.
# Resized to honour each post's declared aspectRatio in timeline.json.
# ------------------------------------------------------------------
echo "==> Post images"

# coffee-yirgacheffe — 4:3 landscape, kitchen/coffee.
# Photo by Nathan Dumlao, https://unsplash.com/photos/6VhPY27jdps
fetch "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?${UNSPLASH_OPTS}" "coffee-yirgacheffe.jpg"
resize_landscape "coffee-yirgacheffe.jpg" "${ASSETS}/posts/coffee-yirgacheffe.jpg" 800 600

# ridge-overlook — 3:2 mountain landscape.
# Photo by Eberhard Grossgasteiger, https://unsplash.com/photos/y2azHvupCVo
fetch "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?${UNSPLASH_OPTS}" "ridge-overlook.jpg"
resize_landscape "ridge-overlook.jpg" "${ASSETS}/posts/ridge-overlook.jpg" 800 533

# ridge-trail — 3:4 portrait forest path.
# Photo by Sebastian Unrau, https://unsplash.com/photos/sp-p7uuT0tw
fetch "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?${UNSPLASH_OPTS}" "ridge-trail.jpg"
resize_landscape "ridge-trail.jpg" "${ASSETS}/posts/ridge-trail.jpg" 600 800

# ridge-flower — 1:1 macro wildflower.
# Photo by Brittney Burnett, https://unsplash.com/photos/wM5pBd0d0aE
fetch "https://images.unsplash.com/photo-1444930694458-01babf71870c?${UNSPLASH_OPTS}" "ridge-flower.jpg"
resize_square "ridge-flower.jpg" "${ASSETS}/posts/ridge-flower.jpg" 800

# tomato-truss — 4:3 garden close-up.
# Photo by Markus Spiske, https://unsplash.com/photos/dpVeLkrxq6E
fetch "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?${UNSPLASH_OPTS}" "tomato-truss.jpg"
resize_landscape "tomato-truss.jpg" "${ASSETS}/posts/tomato-truss.jpg" 800 600

# topo-map — 1:1 stylized map graphic.
# Photo by Annie Spratt, https://unsplash.com/photos/dWYU3i-mqEo
fetch "https://images.unsplash.com/photo-1524661135-423995f22d0b?${UNSPLASH_OPTS}" "topo-map.jpg"
resize_square "topo-map.jpg" "${ASSETS}/posts/topo-map.jpg" 800

# sketchbook-page — 7:10 portrait, pencil work.
# Photo by Kelly Sikkema, https://unsplash.com/photos/-1_RZL8BGBM
fetch "https://images.unsplash.com/photo-1513475382585-d06e58bcb0e0?${UNSPLASH_OPTS}" "sketchbook-page.jpg"
resize_landscape "sketchbook-page.jpg" "${ASSETS}/posts/sketchbook-page.jpg" 560 800

# video-poster-1 — 16:9, neutral tech aesthetic. Used as the poster
# for clip-1 in timeline.json until the real clip ships.
# Photo by Daniel Romero, https://unsplash.com/photos/px-Bz5cPL0Y
fetch "https://images.unsplash.com/photo-1592434134753-a70baf7979d5?${UNSPLASH_OPTS}" "video-poster-1.jpg"
resize_landscape "video-poster-1.jpg" "${ASSETS}/posts/video-poster-1.jpg" 800 450

# video-poster-2 — 16:9, coffee/cafe aesthetic. Poster for clip-2.
# Photo by Battle Creek Coffee Roasters, https://unsplash.com/photos/CTBzkBYAEt0
fetch "https://images.unsplash.com/photo-1559056199-641a0ac8b55e?${UNSPLASH_OPTS}" "video-poster-2.jpg"
resize_landscape "video-poster-2.jpg" "${ASSETS}/posts/video-poster-2.jpg" 800 450

# ------------------------------------------------------------------
# External-link thumbnails — 600x400, ~40 KB each.
# ------------------------------------------------------------------
echo "==> External-link thumbnails"

# article-db — abstract data / code aesthetic.
# Photo by Markus Spiske, https://unsplash.com/photos/iar-afB0QQw
fetch "https://images.unsplash.com/photo-1555066931-4365d14bab8c?${UNSPLASH_OPTS}" "article-db.jpg"
resize_landscape "article-db.jpg" "${ASSETS}/external/article-db.jpg" 600 400

# article-audio — studio monitor / acoustic treatment vibe.
# Photo by Drew Patrick Miller, https://unsplash.com/photos/_qVPARwitVI
fetch "https://images.unsplash.com/photo-1518972559570-7cc1309f3229?${UNSPLASH_OPTS}" "article-audio.jpg"
resize_landscape "article-audio.jpg" "${ASSETS}/external/article-audio.jpg" 600 400

echo
echo "Done. Asset tree:"
find "${ASSETS}" -type f -name '*.jpg' | sort
