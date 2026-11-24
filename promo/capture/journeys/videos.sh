#!/usr/bin/env bash
# Journey: Vertical video feed. Discover → the Trending Videos carousel → open a
# portrait video → double-tap to like (heart burst) → swipe up to the next one →
# tap to pause, tap to resume. Nubecita_Bench_Phone (1280x2856) with the bench's
# bundled clips (app/src/bench/assets/video/). Targets come from accessibility
# labels (../lib.sh), resolved in a dry pass. All fake data.
#
# The carousel tiles have no label: tile 3 is placed from the "Trending Videos"
# header above it. Tile 3, not 1 or 2: the bench's item 2 declares 16:9 for a
# portrait clip on purpose (nubecita-mfac), so it opens small and then snaps.
# Players cache by URL, so after replacing a bundled clip, clear the app's
# video cache or the old clip keeps playing:
#   adb -s <serial> shell run-as net.kikin.nubecita rm -rf cache/nubecita-video-cache
#
#   promo/capture/journeys/videos.sh <bench-phone-serial> [out.mp4]
set -euo pipefail
# shellcheck disable=SC2034  # used by lib.sh
SERIAL="${1:?usage: videos.sh <bench-phone-serial> [out.mp4]}"
OUT_DEVICE="/sdcard/videos.mp4"; OUT_LOCAL="${2:-videos_raw.mp4}"
# shellcheck source=../lib.sh
source "$(dirname "$0")/../lib.sh"

cleanup(){ SH pkill -INT screenrecord >/dev/null 2>&1 || true; demo_off; }
trap cleanup EXIT
need(){ [[ -n "$2" ]] || { echo "not found: $1" >&2; exit 1; }; }

SCREEN=(640 1400)                 # the video surface: double-tap likes, a tap pauses
NEXT=(640 2000 640 700 250)       # swipe up to the next video

echo "[app] demo status bar; resolve the targets in a dry pass"
demo_on; relaunch 6
DISCOVER="$(node text Discover)"; need "Discover tab" "$DISCOVER"
# shellcheck disable=SC2086
tap $DISCOVER; pause 3
HEADER="$(node text "Trending Videos")"; need "Trending Videos carousel" "$HEADER"
TILE=(759 $(( ${HEADER#* } + 312 )))   # tile 3 (624..894 x, 444..924 y under a header at y 372)
relaunch 6

echo "[record] start"
SH screenrecord --size 640x1428 --bit-rate 16000000 --time-limit 60 "$OUT_DEVICE" &
REC_PID=$!
pause 1.0

echo "[beat 1] Discover → the Trending Videos carousel → open a portrait video"
# shellcheck disable=SC2086
tap $DISCOVER; pause 2.2
tap "${TILE[@]}"; pause 3.0

echo "[beat 2] double-tap to like"
double_tap "${SCREEN[@]}"; pause 2.2

echo "[beat 3] swipe up to the next video"
SH input swipe "${NEXT[@]}"; pause 2.6

echo "[beat 4] tap to pause, tap to resume"
tap "${SCREEN[@]}"; pause 1.8
tap "${SCREEN[@]}"; pause 2.0

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
A pull "$OUT_DEVICE" "${OUT_LOCAL%.mp4}.vfr.mp4" >/dev/null
SH rm -f "$OUT_DEVICE"
cfr "${OUT_LOCAL%.mp4}.vfr.mp4" "$OUT_LOCAL" 1
rm -f "${OUT_LOCAL%.mp4}.vfr.mp4"
echo "done: $OUT_LOCAL"
