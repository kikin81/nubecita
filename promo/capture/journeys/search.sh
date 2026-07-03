#!/usr/bin/env bash
# Journey: Search & discovery. Feed → Search tab (Suggested accounts + Discover
# feeds) → type a query (instant typeahead / Top match) → submit → Posts results
# → Feeds tab (discover feeds). 1280x2856 bench data. All fake data.
set -euo pipefail
SERIAL="${1:-emulator-5554}"; PKG="net.kikin.nubecita"
OUT_DEVICE="/sdcard/search.mp4"; OUT_LOCAL="${2:-search_raw.mp4}"
A(){ adb -s "$SERIAL" "$@"; }; SH(){ A shell "$@"; }
tap(){ SH input tap "$1" "$2"; }
drag(){ SH input swipe "$1" "$2" "$3" "$4" "${5:-600}"; }
pause(){ sleep "$1"; }

echo "[demo] clean status bar"
SH settings put global sysui_demo_allowed 1 || true
SH am broadcast -a com.android.systemui.demo -e command enter >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null || true

echo "[app] relaunch to a clean feed top"
SH am force-stop "$PKG"; pause 0.5
SH am start -n "$PKG/.MainActivity" >/dev/null; pause 5

echo "[record] start"
SH screenrecord --size 640x1428 --bit-rate 16000000 --time-limit 60 "$OUT_DEVICE" &
REC_PID=$!
pause 1.0

echo "[beat 1] search landing — suggested accounts + discover feeds"
tap 379 2713; pause 2.0                       # Search tab
drag 640 1900 640 1300 600; pause 1.6         # nudge to reveal Discover feeds

echo "[beat 2] type — instant typeahead"
tap 588 264; pause 1.0
SH input text "design"; pause 2.0             # typeahead: "Top match"

echo "[beat 3] results — posts then feeds"
tap 386 455; pause 2.2                         # "Search for design" → Posts results
drag 640 1900 640 1150 600; pause 1.4         # scroll the post results a touch
tap 1065 443; pause 1.9                        # Feeds tab → discover feeds
drag 640 1900 640 1200 600; pause 1.4         # scroll the feeds list

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
SH am broadcast -a com.android.systemui.demo -e command exit >/dev/null || true
A pull "$OUT_DEVICE" "$OUT_LOCAL" >/dev/null
echo "done: $OUT_LOCAL"
