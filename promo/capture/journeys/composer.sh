#!/usr/bin/env bash
# Journey: Composer + GIF picker. Feed → compose FAB → type → GIF (floating
# toolbar) → KLIPY picker (animated grid) → pick a GIF → the composer shows the
# GIF card. Nubecita_Bench_Phone (1280x2856); targets come from accessibility
# labels (../lib.sh), resolved in a dry pass so the take has no lookup pauses.
#
# NOTE: the bench KLIPY fake serves URLs that don't load offline. Point it at
# real GIFs for the capture and revert afterwards (promo/README.md, "Composer
# journey needs real GIFs").
#
#   promo/capture/journeys/composer.sh <bench-phone-serial> [out.mp4]
set -euo pipefail
# shellcheck disable=SC2034  # used by lib.sh
SERIAL="${1:?usage: composer.sh <bench-phone-serial> [out.mp4]}"
OUT_DEVICE="/sdcard/composer.mp4"; OUT_LOCAL="${2:-composer_raw.mp4}"
# shellcheck source=../lib.sh
source "$(dirname "$0")/../lib.sh"

cleanup(){ SH pkill -INT screenrecord >/dev/null 2>&1 || true; demo_off; }
trap cleanup EXIT
need(){ [[ -n "$2" ]] || { echo "not found: $1" >&2; exit 1; }; }

echo "[app] demo status bar; resolve the targets in a dry pass"
demo_on; relaunch 6
FAB="$(node content-desc "Compose new post")"; need "compose FAB" "$FAB"
# shellcheck disable=SC2086
tap $FAB; pause 2.5
GIF="$(node text GIF)"; need "GIF button" "$GIF"
# shellcheck disable=SC2086
tap $GIF; pause 3.0
TILE="$(node content-desc GIF 2)"; need "second GIF tile" "$TILE"   # the bird
relaunch 6

echo "[record] start"
SH screenrecord --size 640x1428 --bit-rate 16000000 --time-limit 60 "$OUT_DEVICE" &
REC_PID=$!
pause 1.0

echo "[beat 1] compose + type"
# shellcheck disable=SC2086
tap $FAB; pause 2.0                            # compose FAB → composer
SH input text "good%smorning"; pause 1.9       # type a message

echo "[beat 2] GIF picker — animated grid"
# shellcheck disable=SC2086
tap $GIF; pause 3.2                            # KLIPY picker (grid animates)

echo "[beat 3] pick a GIF → composer shows the card"
# shellcheck disable=SC2086
tap $TILE; pause 3.8

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
A pull "$OUT_DEVICE" "${OUT_LOCAL%.mp4}.vfr.mp4" >/dev/null
SH rm -f "$OUT_DEVICE"
cfr "${OUT_LOCAL%.mp4}.vfr.mp4" "$OUT_LOCAL" 1
rm -f "${OUT_LOCAL%.mp4}.vfr.mp4"
echo "done: $OUT_LOCAL"
