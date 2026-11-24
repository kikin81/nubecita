#!/usr/bin/env bash
# Journey: Tablet adaptive two-pane. The bench phone's display resized to a
# Pixel Tablet (2560x1600 @ 320 dpi = 1280x800 dp), where the list-detail
# divider has four snap stops: a 360dp list, a 440dp list, an even split and a
# 360dp detail. Fill the detail pane (tap a post) → scroll the list on its own →
# drag the divider to an even split, then to a wide detail, then a wide list.
# Captured at 1280x800 (landscape). Restores the display on exit. All fake data.
#
#   promo/capture/journeys/tablet.sh <bench-phone-serial> [out.mp4]
set -euo pipefail
# shellcheck disable=SC2034  # used by lib.sh
SERIAL="${1:?usage: tablet.sh <bench-phone-serial> [out.mp4]}"
OUT_DEVICE="/sdcard/tablet.mp4"; OUT_LOCAL="${2:-tablet_raw.mp4}"
# shellcheck source=../lib.sh
source "$(dirname "$0")/../lib.sh"

cleanup(){
  SH pkill -INT screenrecord >/dev/null 2>&1 || true
  SH wm size reset >/dev/null 2>&1 || true
  SH wm density reset >/dev/null 2>&1 || true
  demo_off
}
trap cleanup EXIT

Y_MID=800   # the divider handle sits at the window's vertical center
# Drag the divider handle by <dx> px and let go standing still. `input swipe`
# releases mid-motion, so the pane flings past the nearest stop; separate
# DOWN/MOVE/UP events with a pause before UP settle on the stop nearest the
# release point.
divider(){ # divider <dx>
  local x i; x="$(handle_x $Y_MID)"
  [[ -n "$x" ]] || { echo "divider handle not found" >&2; exit 1; }
  SH input motionevent DOWN "$x" $Y_MID
  for i in 1 2 3 4 5 6 7 8; do SH input motionevent MOVE $(( x + $1 * i / 8 )) $Y_MID; done
  pause 0.4
  SH input motionevent UP $(( x + $1 )) $Y_MID
}

echo "[tablet] resize display to 2560x1600 @ 320 dpi (1280x800 dp)"
SH wm size 2560x1600; SH wm density 320; pause 1
demo_on; relaunch 6

echo "[record] start (landscape 1280x800)"
SH screenrecord --size 1280x800 --bit-rate 16000000 --time-limit 60 "$OUT_DEVICE" &
REC_PID=$!
pause 1.4                                    # rest on two-pane (empty detail)

echo "[beat 1] fill the detail pane"
tap 640 410; pause 2.4                        # the top post's body text → its thread in the detail pane

echo "[beat 2] scroll the list independently (detail stays)"
drag 500 1300 500 600 650; pause 1.3
drag 500 1300 500 800 650; pause 1.3

echo "[beat 3] resize the panes: the divider snaps"
divider 320;   pause 1.6                      # 440dp list → even split
divider 480;   pause 1.6                      # → a wide list (360dp detail)
divider -1400; pause 1.8                      # → a wide detail (360dp list)

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
A pull "$OUT_DEVICE" "${OUT_LOCAL%.mp4}.vfr.mp4" >/dev/null
SH rm -f "$OUT_DEVICE"
cfr "${OUT_LOCAL%.mp4}.vfr.mp4" "$OUT_LOCAL" 1
rm -f "${OUT_LOCAL%.mp4}.vfr.mp4"
echo "done: $OUT_LOCAL"
