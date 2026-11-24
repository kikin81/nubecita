#!/usr/bin/env bash
# Journey: hero. Like the top post → 120hz feed scroll → tab tour
# (Search · Chats · You · Feed). Nubecita_Bench_Phone AVD (1280x2856) running
# the phone bench build; all fake/apolitical bench data. Taps find their
# targets by accessibility label (lib.sh), so layout changes don't break them.
#
#   promo/capture/record-journey.sh <bench-phone-serial> [out.mp4]
set -euo pipefail
# shellcheck disable=SC2034  # used by lib.sh
SERIAL="${1:?usage: record-journey.sh <bench-phone-serial> [out.mp4]}"
OUT_DEVICE="/sdcard/promo.mp4"; OUT_LOCAL="${2:-promo_raw.mp4}"
# shellcheck source=lib.sh
source "$(dirname "$0")/lib.sh"

# On any exit (success, error, Ctrl-C): stop a dangling screenrecord + leave demo mode.
cleanup(){ SH pkill -INT screenrecord >/dev/null 2>&1 || true; demo_off; }
trap cleanup EXIT

echo "[app] demo status bar, fresh process on the feed top"
demo_on; relaunch 6

# Resolve the targets before recording: each lookup is a ~2 s UI dump.
LIKE="$(node content-desc Like 1)"
read -r X_SEARCH Y_NAV <<<"$(node content-desc Search)"
read -r X_CHATS _ <<<"$(node content-desc Chats)"
read -r X_YOU _ <<<"$(node content-desc You)"
read -r X_FEED _ <<<"$(node content-desc Feed)"
[[ -n "$LIKE" && -n "$Y_NAV" && -n "$X_CHATS" && -n "$X_YOU" && -n "$X_FEED" ]] \
  || { echo "feed targets not found" >&2; exit 1; }

echo "[record] start"
SH screenrecord --size 640x1428 --bit-rate 16000000 --time-limit 60 "$OUT_DEVICE" &
REC_PID=$!
pause 1.2   # let the recorder warm up + show the resting feed

# Order: interaction FIRST (fresh launch = top post at a known position), then
# scroll, then the tab tour.
echo "[beat 1] like the top post"
pause 1.3
# shellcheck disable=SC2086
tap $LIKE; pause 2.3                     # heart fills, count ticks up; hold

echo "[beat 2] 120hz feed scroll"
for _ in 1 2 3 4; do fling 640 2150 640 620 190; pause 0.85; done
pause 0.5
fling 640 800 640 2000 300; pause 1.0    # gentle scroll back (no pull-to-refresh)

echo "[beat 3] tab tour"
tap "$X_SEARCH" "$Y_NAV"; pause 1.7
tap "$X_CHATS"  "$Y_NAV"; pause 1.7
tap "$X_YOU"    "$Y_NAV"; pause 1.9
tap "$X_FEED"   "$Y_NAV"; pause 1.4

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
A pull "$OUT_DEVICE" "${OUT_LOCAL%.mp4}.vfr.mp4" >/dev/null
SH rm -f "$OUT_DEVICE"
cfr "${OUT_LOCAL%.mp4}.vfr.mp4" "$OUT_LOCAL" 1
rm -f "${OUT_LOCAL%.mp4}.vfr.mp4"
echo "done: $OUT_LOCAL"
