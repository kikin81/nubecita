#!/usr/bin/env bash
# Journey: Composer + GIF picker. Feed → compose FAB → type → GIF chip → KLIPY
# picker (animated grid) → pick a GIF → composer shows the GIF card. 1280x2856.
#
# NOTE: the bench KLIPY fake is temporarily pointed at bundled public-domain
# GIFs (app/src/bench/assets/promo_gifs + BenchFakeKlipyRepository.PROMO_GIFS)
# so the grid shows real animated content offline. Revert both after capture.
set -euo pipefail
SERIAL="${1:-emulator-5554}"; PKG="net.kikin.nubecita"
OUT_DEVICE="/sdcard/composer.mp4"; OUT_LOCAL="${2:-composer_raw.mp4}"
A(){ adb -s "$SERIAL" "$@"; }; SH(){ A shell "$@"; }
tap(){ SH input tap "$1" "$2"; }
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

echo "[beat 1] compose + type"
tap 1148 2460; pause 2.0                       # compose FAB → composer
SH input text "good%smorning"; pause 1.9       # type a message

echo "[beat 2] GIF picker — animated grid"
tap 865 660; pause 3.2                          # GIF chip → KLIPY picker (grid animates)

echo "[beat 3] pick a GIF → composer shows the card"
tap 336 1959; pause 3.0                          # tap first grid tile → attach
pause 0.8

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
SH am broadcast -a com.android.systemui.demo -e command exit >/dev/null || true
A pull "$OUT_DEVICE" "$OUT_LOCAL" >/dev/null
echo "done: $OUT_LOCAL"
