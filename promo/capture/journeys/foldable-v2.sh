#!/usr/bin/env bash
# Journey: Foldable v2. Two clips from the Nubecita_Bench_Fold AVD (inner display
# 2076x2152, hinge vertical down the middle), bench build with its fixed fixtures:
#
#   1) OPEN      flat, portrait. Feed two-pane → open a post in the detail pane →
#                Search: Explore in the second pane → search "coffee" → the
#                search summary takes over the second pane.
#   2) TABLETOP  Luna's video post (clip-1) fullscreen, device turned so the hinge
#                is horizontal, played flat, then the hinge closes to 90° and the
#                player splits on the fold: video up top, controls on the bottom half.
#
# The emulator reports a real HALF_FOLDED posture (`adb emu sensor set
# hinge-angle0 90`), which some physical foldables don't. Taps are in the inner
# display's device pixels. Outputs <out-dir>/fold_open.mp4 and fold_tabletop.mp4,
# constant 60 fps with a 1.5 s hold on the last frame.
#
#   promo/capture/journeys/foldable-v2.sh <bench-fold-emulator-serial> [out-dir]
set -euo pipefail
SERIAL="${1:?usage: foldable-v2.sh <bench-fold-emulator-serial> [out-dir]}"; PKG="net.kikin.nubecita"
OUT_DIR="${2:-.}"
A(){ adb -s "$SERIAL" "$@"; }; SH(){ A shell "$@"; }
tap(){ SH input tap "$1" "$2"; }
drag(){ SH input swipe "$1" "$2" "$3" "$4" "${5:-700}"; }
pause(){ sleep "$1"; }

# The phone emulators on this machine hold real signed-in sessions; refuse
# anything that isn't the bench fold AVD, before touching the device.
AVD="$(SH getprop ro.boot.qemu.avd_name | tr -d '\r')"   # empty on a real device
[[ "$AVD" == Nubecita_Bench_Fold ]] || { echo "refusing: $SERIAL is '$AVD', not Nubecita_Bench_Fold" >&2; exit 1; }
SIZE="$(SH wm size | awk '/Physical size/{print $3}' | tr -d '\r')"
[[ "$SIZE" == 2076x2152 ]] || { echo "refusing: inner display is $SIZE; the journey's coordinates need 2076x2152" >&2; exit 1; }
# Two displays (inner + cover): screencap and screenrecord need the inner one's id.
DISPLAY_ID="$(SH dumpsys SurfaceFlinger --display-id | awk '/EMU_display_0/{print $2}' | tr -d '\r')"
[[ -n "$DISPLAY_ID" ]] || { echo "no inner display id" >&2; exit 1; }

ROTATIONS=0   # `adb emu rotate` turns 90° per call; cleanup turns it back.
cleanup(){
  SH pkill -INT screenrecord >/dev/null 2>&1 || true
  A emu sensor set hinge-angle0 180 >/dev/null 2>&1 || true
  while (( ROTATIONS % 4 != 0 )); do A emu rotate >/dev/null 2>&1 || true; ROTATIONS=$((ROTATIONS + 1)); done
  SH settings put global heads_up_notifications_enabled 1 >/dev/null 2>&1 || true
  SH am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
}
trap cleanup EXIT

demo(){
  SH settings put global sysui_demo_allowed 1 || true
  SH am broadcast -a com.android.systemui.demo -e command enter >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile hide >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null || true
}

# Center "x y" of the first node whose <attr> starts with <value>, or nothing.
find_node(){ # find_node <text|content-desc> <value>
  SH uiautomator dump /sdcard/fold_ui.xml >/dev/null 2>&1 || return 0
  SH cat /sdcard/fold_ui.xml | tr '>' '\n' | grep -F " $1=\"$2" | head -1 \
    | sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' \
    | awk '{print int(($1+$3)/2), int(($2+$4)/2)}' || true
}
find_text(){ find_node text "$1"; }

record(){ # record <device-file> <WxH> — runs in the background, sets REC_PID
  SH screenrecord --display-id "$DISPLAY_ID" --size "$2" --bit-rate 16000000 --time-limit 60 "$1" &
  REC_PID=$!; pause 1.2
}
stop_record(){ # stop_record <device-file> <local-name>
  SH pkill -INT screenrecord >/dev/null 2>&1 || true
  wait "$REC_PID" 2>/dev/null || true; pause 1.0
  A pull "$1" "$OUT_DIR/$2.vfr.mp4" >/dev/null
  SH rm -f "$1"
  # screenrecord writes a frame only when the screen changes: resample to a
  # constant 60 fps (Remotion seeks deterministically) and hold the last frame.
  ffmpeg -loglevel error -y -i "$OUT_DIR/$2.vfr.mp4" \
    -vf "fps=60,tpad=stop_mode=clone:stop_duration=1.5" \
    -c:v libx264 -crf 14 -preset slow -pix_fmt yuv420p -an "$OUT_DIR/$2.mp4"
  rm -f "$OUT_DIR/$2.vfr.mp4"
}

echo "[setup] flat, portrait, no heads-up, demo status bar, fresh process"
A emu sensor set hinge-angle0 180 >/dev/null
SH settings put global heads_up_notifications_enabled 0 || true
SH am force-stop "$PKG"; pause 0.5
SH am start -n "$PKG/.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER >/dev/null
pause 6; demo; pause 1

# ---------- Clip 1: OPEN (two panes, Search second pane) ----------
echo "[open] record"
record /sdcard/fold_open.mp4 1038x1076
pause 1.5
tap 711 560; pause 3.0                        # the first post's body text → the thread fills the detail pane
drag 1500 1700 1500 1000 900; pause 1.6       # read down the thread a little
tap 97 392; pause 3.0                         # Search: Trending + Explore in the second pane
drag 1650 1700 1650 1100 900; pause 1.8       # glance down Explore
tap 680 224; pause 1.2                        # the search field
SH input text coffee; pause 1.0
SH input keyevent 66; pause 4.0               # submit → results + the search summary pane
stop_record /sdcard/fold_open.mp4 fold_open

# ---------- Clip 2: TABLETOP (video player on the fold) ----------
echo "[tabletop] find Luna's video post in the feed"
tap 97 245; pause 2.0                          # Feed tab
pos=""
for _ in $(seq 1 30); do
  pos="$(find_text "slow process shot")"
  [[ -n "$pos" ]] && break
  drag 600 1800 600 900 600; pause 0.6
done
[[ -n "$pos" ]] || { echo "Luna's video post not found" >&2; exit 1; }
read -r _ ty <<<"$pos"
drag 600 "$ty" 600 400 1500; pause 1.5         # slow drag (no fling): the post near the top
pos="$(find_text "slow process shot")"; read -r _ ty <<<"$pos"
tap 711 $((ty + 320)); pause 2.0               # its video, just below the post text
echo "[tabletop] turn the device so the hinge is horizontal"
A emu rotate >/dev/null; ROTATIONS=$((ROTATIONS + 1)); pause 3.0
# The inline player autoplayed while the feed scrolled, and fullscreen picks up
# where it was: skip back to the start so the 15 s clip outlasts the fold.
pos="$(find_node content-desc "Skip back 10 seconds")"
[[ -n "$pos" ]] || { tap 1076 700; pause 0.8; pos="$(find_node content-desc "Skip back 10 seconds")"; }
[[ -n "$pos" ]] || { echo "player controls not found" >&2; exit 1; }
read -r bx by <<<"$pos"; tap "$bx" "$by"; pause 0.4; tap "$bx" "$by"; pause 1.0
echo "[tabletop] record"
record /sdcard/fold_tabletop.mp4 1076x1038
pause 2.5                                      # playing flat
A emu sensor set hinge-angle0 120 >/dev/null; pause 0.4
A emu sensor set hinge-angle0 90 >/dev/null; pause 6.0   # HALF_FOLDED → split on the fold
stop_record /sdcard/fold_tabletop.mp4 fold_tabletop

echo "done: $OUT_DIR/fold_open.mp4 $OUT_DIR/fold_tabletop.mp4"
