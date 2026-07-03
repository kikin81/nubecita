#!/usr/bin/env bash
# Drives the bench app through the promo journey (feed scroll → tab tour →
# post interactions) on a connected emulator, while screenrecord captures.
# Screen assumed 1280x2856. All fake/apolitical bench data.
set -euo pipefail

SERIAL="${1:-emulator-5554}"
PKG="net.kikin.nubecita"
OUT_DEVICE="/sdcard/promo.mp4"
OUT_LOCAL="${2:-promo_raw.mp4}"
A() { adb -s "$SERIAL" "$@"; }
SH() { A shell "$@"; }
tap() { SH input tap "$1" "$2"; }
fling() { SH input swipe "$1" "$2" "$3" "$4" "${5:-220}"; }
pause() { sleep "$1"; }

# Bottom-nav tab x-centers (5 tabs across 1280) at y≈2713
Y_NAV=2713
X_FEED=128; X_SEARCH=384; X_NOTIF=640; X_CHATS=896; X_PROFILE=1152

echo "[demo] clean status bar"
SH settings put global sysui_demo_allowed 1 || true
SH am broadcast -a com.android.systemui.demo -e command enter >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e level 4 >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null || true

echo "[app] relaunch to a clean feed top"
SH am force-stop "$PKG"; pause 0.5
SH am start -n "$PKG/.MainActivity" >/dev/null; pause 5

echo "[record] start"
SH screenrecord --size 640x1428 --bit-rate 16000000 --time-limit 60 "$OUT_DEVICE" &
REC_PID=$!
pause 1.2   # let the recorder warm up + show the resting feed

# Order: interaction FIRST (fresh launch = top post at a known position, so the
# like heart is deterministic), then scroll, then the tab tour.

echo "[beat 1] like animation on the top post"
pause 1.3                                # rest on the feed
tap 690 810; pause 2.3                   # like → heart fills 88→89 (verified); hold

echo "[beat 2] 120hz feed scroll"
for i in 1 2 3 4; do fling 640 2150 640 620 190; pause 0.85; done
pause 0.5
fling 640 800 640 2000 300; pause 1.0    # gentle scroll back (no pull-to-refresh)

echo "[beat 3] tab tour"
tap $X_SEARCH  $Y_NAV; pause 1.7
tap $X_CHATS   $Y_NAV; pause 1.7
tap $X_PROFILE $Y_NAV; pause 1.9
tap $X_FEED    $Y_NAV; pause 1.4

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true
pause 1.0

echo "[demo] restore"
SH am broadcast -a com.android.systemui.demo -e command exit >/dev/null || true

echo "[pull] $OUT_LOCAL"
A pull "$OUT_DEVICE" "$OUT_LOCAL" >/dev/null
echo "done: $OUT_LOCAL"
