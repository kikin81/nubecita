#!/usr/bin/env bash
# Journey: Tablet adaptive two-pane. Resize the display to 2560x1600 → the
# list-detail layout appears (rail + feed list + detail pane). Fill the detail
# pane (tap a post) → scroll the list independently → open the gallery lightbox.
# Captured at 1280x800 (landscape). Restores the display on exit. All fake data.
set -euo pipefail
SERIAL="${1:-emulator-5554}"; PKG="net.kikin.nubecita"
OUT_DEVICE="/sdcard/tablet.mp4"; OUT_LOCAL="${2:-tablet_raw.mp4}"
A(){ adb -s "$SERIAL" "$@"; }; SH(){ A shell "$@"; }
tap(){ SH input tap "$1" "$2"; }
drag(){ SH input swipe "$1" "$2" "$3" "$4" "${5:-600}"; }
pause(){ sleep "$1"; }

cleanup(){ SH wm size reset >/dev/null 2>&1 || true; SH wm density reset >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "[tablet] resize display to 2560x1600"
SH wm size 2560x1600; SH wm density 240; pause 1

echo "[demo] clean status bar"
SH settings put global sysui_demo_allowed 1 || true
SH am broadcast -a com.android.systemui.demo -e command enter >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null || true

echo "[app] relaunch"
SH am force-stop "$PKG"; pause 0.5
SH am start -n "$PKG/.MainActivity" >/dev/null; pause 6

echo "[record] start (landscape 1280x800)"
SH screenrecord --size 1280x800 --bit-rate 16000000 --time-limit 60 "$OUT_DEVICE" &
REC_PID=$!
pause 1.4                                    # rest on two-pane (empty detail)

echo "[beat 1] fill the detail pane"
tap 640 500; pause 2.4                        # tap a post → detail pane fills (thread)

echo "[beat 2] scroll the list independently (detail stays)"
drag 350 1300 350 520 650; pause 1.3
drag 350 1300 350 700 650; pause 1.3

echo "[beat 3] open the gallery lightbox"
tap 1107 474; pause 1.6                        # tap a detail-pane gallery image → lightbox
SH input swipe 1800 800 800 800 200; pause 1.2 # next image
SH input swipe 1800 800 800 800 200; pause 1.2 # next image
SH input keyevent KEYCODE_BACK; pause 1.2      # close lightbox

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
SH am broadcast -a com.android.systemui.demo -e command exit >/dev/null || true
A pull "$OUT_DEVICE" "$OUT_LOCAL" >/dev/null
echo "done: $OUT_LOCAL"
