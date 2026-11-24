#!/usr/bin/env bash
# Journey: Threads + media viewer. In bench, tapping any post opens one canned
# thread (Jessica Elena's 4-image gallery + replies), so the top post is a
# deterministic entry point. Open thread → open the media viewer (zoom + swipe
# the gallery) → close → scroll the replies. Nubecita_Bench_Phone (1280x2856);
# taps find their targets by accessibility label (../lib.sh). All fake data.
#
#   promo/capture/journeys/threads.sh <bench-phone-serial> [out.mp4]
set -euo pipefail
# shellcheck disable=SC2034  # used by lib.sh
SERIAL="${1:?usage: threads.sh <bench-phone-serial> [out.mp4]}"
OUT_DEVICE="/sdcard/threads.mp4"; OUT_LOCAL="${2:-threads_raw.mp4}"
# shellcheck source=../lib.sh
source "$(dirname "$0")/../lib.sh"

cleanup(){ SH pkill -INT screenrecord >/dev/null 2>&1 || true; demo_off; }
trap cleanup EXIT

echo "[app] demo status bar; find the viewer targets in a dry pass"
demo_on; relaunch 6
# Each label lookup is a ~2 s UI dump: resolve them before recording so the
# take has no dead time. The canned thread and viewer lay out the same each run.
tap 700 600; pause 2.4
IMAGE="$(node content-desc "Sunrise over the ridge crest.")"
[[ -n "$IMAGE" ]] || { echo "gallery image not found" >&2; exit 1; }
# shellcheck disable=SC2086
tap $IMAGE; pause 1.6
CLOSE="$(node content-desc "Close image viewer")"
[[ -n "$CLOSE" ]] || { echo "viewer close button not found" >&2; exit 1; }
relaunch 6

echo "[record] start"
SH screenrecord --size 640x1428 --bit-rate 16000000 --time-limit 60 "$OUT_DEVICE" &
REC_PID=$!
pause 1.5                                    # rest on the feed top

echo "[beat 1] open the thread"
tap 700 600; pause 2.4                       # the top post's body text (no links) → canned thread

echo "[beat 2] media viewer — open, zoom, swipe"
# shellcheck disable=SC2086
tap $IMAGE; pause 1.6                         # first gallery image → viewer
double_tap 640 1400; pause 1.3                # zoom in
double_tap 640 1400; pause 1.0                # zoom out
SH input swipe 980 1400 260 1400 200; pause 1.2   # → 2/4
SH input swipe 980 1400 260 1400 200; pause 1.2   # → 3/4
# shellcheck disable=SC2086
tap $CLOSE; pause 1.2

echo "[beat 3] scroll the replies"
drag 640 2050 640 1050 600; pause 1.5
drag 640 2050 640 1250 600; pause 1.4

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
A pull "$OUT_DEVICE" "${OUT_LOCAL%.mp4}.vfr.mp4" >/dev/null
SH rm -f "$OUT_DEVICE"
cfr "${OUT_LOCAL%.mp4}.vfr.mp4" "$OUT_LOCAL" 1
rm -f "${OUT_LOCAL%.mp4}.vfr.mp4"
echo "done: $OUT_LOCAL"
