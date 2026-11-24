# shellcheck shell=bash
# Shared helpers for the phone journey scripts. Source it after setting SERIAL:
#
#   SERIAL="${1:?usage: …}"; source "$(dirname "$0")/lib.sh"   (or ../lib.sh)
#
# It refuses any device that isn't the Nubecita_Bench_Phone AVD before touching
# it: the other phone emulators on this machine hold real signed-in sessions.
: "${SERIAL:?set SERIAL before sourcing lib.sh}"
PKG="net.kikin.nubecita"
A(){ adb -s "$SERIAL" "$@"; }
SH(){ A shell "$@"; }
tap(){ SH input tap "$1" "$2"; }
drag(){ SH input swipe "$1" "$2" "$3" "$4" "${5:-600}"; }
fling(){ SH input swipe "$1" "$2" "$3" "$4" "${5:-220}"; }
pause(){ sleep "$1"; }
# Two `input tap`s in a row land ~0.3 s apart, past the double-tap timeout;
# start the second one 0.1 s after the first, in the same shell.
double_tap(){ SH "input tap $1 $2 & sleep 0.1; input tap $1 $2; wait"; }

AVD="$(SH getprop ro.boot.qemu.avd_name | tr -d '\r')"   # empty on a real device
[[ "$AVD" == Nubecita_Bench_Phone ]] || { echo "refusing: $SERIAL is '$AVD', not Nubecita_Bench_Phone" >&2; exit 1; }

demo_on(){
  SH settings put global sysui_demo_allowed 1 || true
  SH am broadcast -a com.android.systemui.demo -e command enter >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e level 4 >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null || true
}
demo_off(){ SH am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true; }

# Fresh process on the feed, launched the way the launcher does.
relaunch(){
  SH am force-stop "$PKG"; pause 0.5
  SH am start -n "$PKG/.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER >/dev/null
  pause "${1:-5}"
}

# Center "x y" of the <n>th (default 1st) node whose <attr> starts with <value>,
# or nothing. Labels survive layout changes that break fixed coordinates.
node(){ # node <text|content-desc> <value> [n]
  SH uiautomator dump /sdcard/journey_ui.xml >/dev/null 2>&1 || return 0
  SH cat /sdcard/journey_ui.xml | tr '>' '\n' | grep -F " $1=\"$2" | sed -n "${3:-1}p" \
    | sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' \
    | awk '{print int(($1+$3)/2), int(($2+$4)/2)}' || true
}
# Tap that node, or fail loudly: a missed tap would record the wrong journey.
tap_node(){ # tap_node <text|content-desc> <value> [n]
  local pos; pos="$(node "$@")"
  [[ -n "$pos" ]] || { echo "not on screen: $1=\"$2\"" >&2; exit 1; }
  # shellcheck disable=SC2086
  tap $pos
}

# X of the list-detail divider handle: it has no accessibility label, so find
# it in a screenshot as the first 6–12 px wide column that stays dark across a
# 72 px band at y=<y> (text never does; a photo is wider).
handle_x(){ # handle_x <y>
  A exec-out screencap -p \
    | ffmpeg -loglevel error -f png_pipe -i - -vf "crop=iw:72:0:$(( $1 - 36 )),format=gray" -f rawvideo - \
    | python3 -c '
import sys
b = sys.stdin.buffer.read(); h = 72; w = len(b) // h
dark = [all(b[y * w + x] < 170 for y in range(h)) for x in range(w)]
x = 0
while x < w:
    if dark[x]:
        s = x
        while x < w and dark[x]: x += 1
        if 6 <= x - s <= 12: print((s + x) // 2); break
    x += 1
'
}

# screenrecord writes a frame only when the screen changes: resample a pulled
# clip to a constant 60 fps (Remotion seeks deterministically) and hold its
# last frame for <hold> seconds.
cfr(){ # cfr <in.vfr.mp4> <out.mp4> [hold]
  ffmpeg -loglevel error -y -i "$1" -vf "fps=60,tpad=stop_mode=clone:stop_duration=${3:-1}" \
    -c:v libx264 -crf 14 -preset slow -pix_fmt yuv420p -an "$2"
}
