#!/usr/bin/env bash
#
# Composite tablet (tenInch) screenshots onto the brand backdrop with a
# headline — NO device bezel. frameit ships no modern Android-tablet frame
# (only a 2014 Nexus 9, 4:3), so the tablet marketing treatment is done here
# with ImageMagick instead of frameit. Titles are read from the phone bucket's
# title.strings so phone + tablet stay in sync on one source.
#
# Input : <metadata>/<locale>/images/tenInchScreenshots/NN_name.png  (raw captures)
# Output: <metadata>/<locale>/images/tenInchScreenshots/NN_name_framed.jpg
#
# Usage: compose_tablet.sh [locale]   (locale defaults to en-US)
# Requires ImageMagick 7 (`magick`) — `brew install imagemagick`.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
META="$(cd "$HERE/../metadata/android" && pwd)"
FONT="$HERE/fraunces.ttf"
LOCALE="${1:-en-US}"
TEN="$META/$LOCALE/images/tenInchScreenshots"
TITLES="$META/$LOCALE/images/phoneScreenshots/title.strings"

# Per-invocation scratch dir so concurrent runs (e.g. two locales on a shared
# machine) can't clobber each other's intermediates. Auto-removed on exit.
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

rm -f "$TEN"/*_framed.png "$TEN"/*_framed.jpg
shopt -s nullglob
for f in "$TEN"/[0-9]*.png; do
  [[ "$f" == *_framed* ]] && continue
  name="$(basename "$f" .png)"
  title="$(grep "\"$name\"" "$TITLES" 2>/dev/null | sed -E 's/.*= *"(.*)";/\1/' || true)"
  if [ -z "$title" ]; then
    echo "skip (no title in title.strings): $name"
    continue
  fi
  W="$(identify -format %w "$f")"
  H="$(identify -format %h "$f")"
  # Rounded corners via a roundrectangle alpha mask.
  magick -size "${W}x${H}" xc:none -draw "roundrectangle 0,0,$((W - 1)),$((H - 1)),36,36" "$WORK/mask.png"
  magick "$f" "$WORK/mask.png" -alpha set -compose DstIn -composite "$WORK/rounded.png"
  magick "$WORK/rounded.png" -resize 2200x "$WORK/scaled.png"
  # Backdrop (matches the phone Framefile navy background: a Sky-blue radial
  # glow fading to near-black — keeps the white title legible up top and lets
  # the dark-themed screenshot separate from the backdrop), screenshot centered
  # below a top title band. 2560x2080 keeps the ratio under Play's 2:1.
  # Render to a lossless composite first, then JPG-encode below.
  magick -size 2560x2080 radial-gradient:'#0A4E8C'-'#000D1F' \
    "$WORK/scaled.png" -gravity north -geometry +0+560 -composite \
    -gravity north -font "$FONT" -pointsize 104 -fill white -annotate +0+180 "$title" \
    "$WORK/composite.png"
  # Encode under the repo's 500 KB asset cap — the busier shots (feed list +
  # thread) exceed it at q90. Step quality down from the lossless composite (no
  # compounding re-encode loss) until it fits.
  out="$TEN/${name}_framed.jpg"
  q=90
  magick "$WORK/composite.png" -quality "$q" "$out"
  while [ "$(wc -c < "$out")" -gt 500000 ] && [ "$q" -gt 70 ]; do
    q=$((q - 4))
    magick "$WORK/composite.png" -quality "$q" "$out"
  done
  echo "framed: ${name}_framed.jpg  <-  \"$title\"  (q$q)"
done
