#!/usr/bin/env bash
# Journey: Wear OS. Watch face → Nubecita launcher widget (2 new messages) →
# Home → feed → open a post → Repost (confirm) + Bookmark → widget → Messages → quick
# reply "On my way" → widget → New post → type → Post → "Posting…" → posted.
#
# Wear_OS_Large_Round AVD (454x454) running the Wear bench build with the
# `default` scenario (docs/wear-testing.md). The launcher widget must already
# be on the widget carousel, to the right of the watch face. All fake data.
#
#   promo/capture/journeys/watch.sh <wear-emulator-serial> [out.mp4]
set -euo pipefail
SERIAL="${1:?usage: watch.sh <wear-emulator-serial> [out.mp4]}"; PKG="net.kikin.nubecita"
OUT_DEVICE="/sdcard/watch.mp4"; OUT_LOCAL="${2:-watch_raw.mp4}"
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
A(){ adb -s "$SERIAL" "$@"; }; SH(){ A shell "$@"; }
tap(){ SH input tap "$1" "$2"; }
drag(){ SH input swipe "$1" "$2" "$3" "$4" "${5:-500}"; }
pause(){ sleep "$1"; }
cleanup(){ SH pkill -INT screenrecord >/dev/null 2>&1 || true; }
trap cleanup EXIT

# The phone emulators on this machine hold real signed-in sessions; refuse
# anything that isn't a round Wear AVD.
AVD="$(A emu avd name 2>/dev/null | head -1 | tr -d '\r')"
[[ "$AVD" == Wear_OS_*Round ]] || { echo "refusing: $SERIAL is '$AVD', not a round Wear AVD" >&2; exit 1; }

widget(){ SH input keyevent 3; pause 1.0; drag 400 227 50 227 300; pause 1.6; }

echo "[app] fresh process with the default scenario, then back to the watch face"
SH am force-stop "$PKG"; pause 0.5
SH am start -n "$PKG/$PKG.wear.MainActivity" -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER --es scenario default >/dev/null; pause 3
SH input keyevent 3; pause 1.5

echo "[record] start"
SH screenrecord --bit-rate 12000000 --time-limit 90 "$OUT_DEVICE" &
REC_PID=$!
pause 1.2

echo "[beat 1] watch face → launcher widget"
drag 400 227 50 227 300; pause 2.2

echo "[beat 2] Home → feed → post → repost + bookmark"
tap 327 191; pause 2.0                         # widget: Home
drag 227 400 227 250 400; pause 1.0           # peek the next post
drag 227 250 227 400 400; pause 0.8
tap 227 278; pause 1.8                         # open Ana's post
drag 227 400 227 100 400; pause 0.8
drag 227 380 227 160 400; pause 1.2           # actions row
tap 227 255; pause 1.2                         # Repost → "Repost this?"
tap 299 347; pause 1.2                         # Confirm
tap 331 255; pause 1.6                         # Bookmark

echo "[beat 3] widget → Messages → quick reply"
widget
tap 157 248; pause 1.8                         # widget: Messages
tap 227 237; pause 1.8                         # Ana Ruiz
drag 227 400 227 100 400; pause 1.0
tap 226 390; pause 1.4                         # Reply
tap 227 324; pause 2.4                         # "On my way" → Sent

echo "[beat 4] widget → New post → type → Post"
widget
tap 327 305; pause 1.8                         # widget: New post
tap 368 221; pause 1.8                         # keyboard input
"$ROOT/scripts/wear-type-keys.py" "$SERIAL" "hello from my wrist"; pause 0.8
tap 376 132; pause 1.8                         # keyboard: Send
tap 226 390; pause 4.0                         # Post → "Posting…"
pause 14.0                                     # "Posting…" (bench: 10 s), then the posted card

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
A pull "$OUT_DEVICE" "${OUT_LOCAL%.mp4}.vfr.mp4" >/dev/null

# screenrecord writes a frame only when the screen changes, so the clip is
# variable-rate and the posted card is its very last frame, shown for an
# instant. Resample to a constant 60 fps (Remotion seeks deterministically)
# and hold that last frame for 3 s.
ffmpeg -loglevel error -y -i "${OUT_LOCAL%.mp4}.vfr.mp4" \
  -vf "fps=60,tpad=stop_mode=clone:stop_duration=3" \
  -c:v libx264 -crf 12 -preset slow -pix_fmt yuv420p -an "$OUT_LOCAL"
rm -f "${OUT_LOCAL%.mp4}.vfr.mp4"
echo "done: $OUT_LOCAL"
