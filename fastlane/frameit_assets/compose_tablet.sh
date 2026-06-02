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
FONT="$HERE/Arial-Bold.ttf"
LOCALE="${1:-en-US}"
TEN="$META/$LOCALE/images/tenInchScreenshots"
TITLES="$META/$LOCALE/images/phoneScreenshots/title.strings"

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
  magick -size "${W}x${H}" xc:none -draw "roundrectangle 0,0,$((W - 1)),$((H - 1)),36,36" /tmp/_tabmask.png
  magick "$f" /tmp/_tabmask.png -alpha set -compose DstIn -composite /tmp/_tabrounded.png
  magick /tmp/_tabrounded.png -resize 2200x /tmp/_tabscaled.png
  # Backdrop gradient (matches the phone Framefile background), screenshot
  # centered below a top title band. 2560x2080 keeps the ratio under Play's 2:1.
  magick -size 2560x2080 gradient:'#4A7DFF'-'#1B2A6B' \
    /tmp/_tabscaled.png -gravity north -geometry +0+560 -composite \
    -gravity north -font "$FONT" -pointsize 104 -fill white -annotate +0+180 "$title" \
    -quality 90 "$TEN/${name}_framed.jpg"
  echo "framed: ${name}_framed.jpg  <-  \"$title\""
done
rm -f /tmp/_tabmask.png /tmp/_tabrounded.png /tmp/_tabscaled.png
