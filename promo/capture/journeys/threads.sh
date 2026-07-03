#!/usr/bin/env bash
# Journey: Threads + media viewer. In bench, tapping any post opens one canned
# thread — Jessica Elena's 4-image gallery + replies — so the top post's header
# is a deterministic entry point (no scroll hunting). Open thread → open media
# viewer (zoom + swipe the gallery) → scroll replies. 1280x2856. All fake data.
set -euo pipefail
SERIAL="${1:-emulator-5554}"; PKG="net.kikin.nubecita"
OUT_DEVICE="/sdcard/threads.mp4"; OUT_LOCAL="${2:-threads_raw.mp4}"
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
pause 1.5                                    # rest on the feed top

echo "[beat 1] open the thread"
tap 1000 420; pause 2.4                       # top post header → canned gallery thread

echo "[beat 2] media viewer — open, zoom, swipe"
tap 405 1087; pause 1.6                       # first gallery image → viewer (1/4)
tap 640 1400; tap 640 1400; pause 1.3         # double-tap zoom in
tap 640 1400; tap 640 1400; pause 1.0         # double-tap zoom out
SH input swipe 980 1400 260 1400 200; pause 1.2   # → 2/4
SH input swipe 980 1400 260 1400 200; pause 1.2   # → 3/4
tap 94 250; pause 1.2                          # close viewer (X)

echo "[beat 3] scroll the replies"
drag 640 2050 640 1050 600; pause 1.5
drag 640 2050 640 1250 600; pause 1.4

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
SH am broadcast -a com.android.systemui.demo -e command exit >/dev/null || true
A pull "$OUT_DEVICE" "$OUT_LOCAL" >/dev/null
echo "done: $OUT_LOCAL"
