#!/bin/zsh
# Drives app/src/journeysTest/video_player_tabletop.journey.xml on a bench debug build and
# asserts the fullscreen player's posture layout from the live UI tree (nubecita-z5ok).
#
# Usage: ANDROID_SERIAL=<serial> scripts/tabletop-video-journey.sh
#        APK=app/build/outputs/apk/bench/debug/app-bench-debug.apk ... to install first.
#
# Two ways to change posture, picked from the serial:
#   emulator  the script folds and unfolds through the emulator console
#             (`adb emu posture`), forces landscape and sets DND. Fully unattended.
#   hardware  a person folds the device. The script prints `>>> ACTION:` lines and
#             waits up to WAIT seconds (default 180) for the device state to change.
#             Start with the device open and turned so the hinge is horizontal. It
#             changes no settings on the device, and keeps the 15s clip alive with
#             Skip back taps while it waits.
#
# What it proves, in order:
#   flat      controller overlays the player (same grid cell), video is playing
#   tabletop  player is entirely above the fold, controller starts exactly on it and
#             stays visible past the auto-hide, playback kept going (position did not
#             reset), same SurfaceView layer, and the controls respond to a tap
#   flat      overlay is back, playback still going, same SurfaceView layer
#   overall   the activity was never relaunched or destroyed
# On the emulator "playback kept going" is the position never going backwards across
# the posture change. On hardware the keep-alive rewinds make that meaningless, so it
# is the position advancing between two reads taken right after each change.
#
# The fold is taken to be the horizontal midline of the app window. That holds for a
# fullscreen window on a book-style foldable held in landscape, which is the only place
# tabletop posture occurs; it is the same assumption the 50/50 grid rows make.
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}; D=--device=$ANDROID_SERIAL
PKG=net.kikin.nubecita
TOL=${TOL:-2} # px of rounding slack on the fold line
WAIT=${WAIT:-180} # hardware only: seconds to wait for a person to fold or unfold
TMP=$(mktemp -d)
[[ $ANDROID_SERIAL == emulator-* ]] && CONSOLE=1 || CONSOLE=0

# Hardware is somebody's device: nothing was changed on it, so nothing to restore.
cleanup(){
  (( CONSOLE )) || return 0
  adb emu posture 3 >/dev/null 2>&1
  adb shell settings put system user_rotation 0
  adb shell settings put system accelerometer_rotation 1
  adb shell cmd notification set_dnd off
}
# On failure keep what the screen and the UI tree looked like, before cleanup unfolds
# and rotates the device and destroys the evidence.
fail(){
  echo "FAIL: $*"
  local display=$(adb shell dumpsys SurfaceFlinger --display-id | sed -nE 's/^Display ([0-9]+).*/\1/p' | head -1)
  adb exec-out screencap -p -d $display > $TMP/fail.png 2>/dev/null
  adb logcat -d --pid=$(adb shell pidof $PKG) > $TMP/fail-logcat.txt 2>/dev/null
  echo "   evidence: $TMP/fail.png $TMP/layout.json $TMP/fail-logcat.txt"
  cleanup; exit 1
}
T(){ android input tap $D --match=first --wait=8000 "$1" >/dev/null || fail "tap $1"; }
# Tap a chrome control. The chrome auto-hides after 3s in flat posture and each CLI
# round trip costs about a second, so reveal it and retry when the control is gone.
CT(){
  android input tap $D --match=first --wait=1200 "$1" >/dev/null 2>&1 && return 0
  reveal
  android input tap $D --match=first --wait=2000 "$1" >/dev/null || fail "tap $1"
}
state(){ adb shell cmd device_state state | sed -nE "s/.*name='([A-Z_]+)'.*/\1/p" | head -1; }
# The SurfaceView's SurfaceFlinger layers. Layer names carry a unique #id, so an
# unchanged set across a posture change means the surface was never destroyed.
surface(){ adb shell dumpsys SurfaceFlinger --list | grep -E "SurfaceView\[$PKG" | sort | tr '\n' ' '; }

# Prints shell assignments for the player state read from the UI tree:
# P_*/C_* = player / controller bounds, FOLD = window midline, CHROME (controls
# showing), PLAYING, POS (seconds). PLAYING and POS are only readable while CHROME=1.
probe(){
  android layout $D --flat --full --no-idle -o $TMP/layout.json >/dev/null 2>&1 || return 1
  python3 - $TMP/layout.json <<'PY'
import json, re, sys
windows = json.load(open(sys.argv[1]))
# More than one APPLICATION window can be up (a transient system surface during a
# posture change, for one). The player is in the one that actually has content.
app = max((w for w in windows if w.get("type") == "APPLICATION"), key=lambda w: len(w.get("content", [])), default=None)
if app is None:
    sys.exit(0)  # no app window in this dump (mid-transition); print nothing, PROBE_OK stays 0
nodes = app.get("content", [])
print("WINDOWS=" + repr(" ".join(f"{w.get('type')}:{w.get('window-title')}:{len(w.get('content', []))}" for w in windows)))
rect = lambda b: [int(v) for v in re.findall(r"-?\d+", b)]
def by_id(rid):
    return next((rect(n["bounds"]) for n in nodes if n.get("resource-id") == rid), None)
def emit(prefix, r):
    print(f"{prefix}_PRESENT={1 if r else 0}")
    for key, value in zip(("L", "T", "R", "B"), r or (0, 0, 0, 0)):
        print(f"{prefix}_{key}={value}")
emit("P", by_id("video_player"))
emit("C", by_id("video_player_controller"))
left, top, right, bottom = rect(app["bounds"])
print(f"FOLD={(top + bottom) // 2}")
print(f"WIN_W={right - left}")
print(f"WIN_H={bottom - top}")
descs = [n.get("content-desc", "") for n in nodes]
print(f"PLAYING={1 if 'Pause' in descs else 0}")
# The controller's layout node (an AnimatedVisibility) stays in the tree while its
# content is hidden, so "is the chrome showing" has to look at a control inside it.
print(f"CHROME={1 if 'Pause' in descs or 'Play' in descs else 0}")
times = [n["text"] for n in nodes if re.fullmatch(r"\d+:\d\d", n.get("text", ""))]
m, s = (times[0].split(":") if times else ("-1", "0"))
print(f"POS={int(m) * 60 + int(s) if times else -1}")
print(f"TIMES={'/'.join(times) or 'none'}")
print("PROBE_OK=1")
PY
}
# Run probe and load its variables. False when the dump failed or had no app window,
# which `eval "$(probe)" || ...` cannot tell: eval of empty output still succeeds.
do_probe(){ PROBE_OK=0; eval "$(probe)"; (( PROBE_OK )); }
# Probe with the chrome showing. In flat posture it auto-hides after 3s and a tap on
# the video brings it back. The tap is a raw coordinate in the video's upper-left
# quarter, clear of every control, so a mistimed one can only toggle the chrome and
# never lands on play/pause the way a tap on the node's center would.
reveal(){ adb shell input tap $(( P_L + (P_R - P_L) / 4 )) $(( P_T + (P_B - P_T) / 4 )); sleep 0.5; }
probe_with_chrome(){
  settled_probe
  if (( ! CHROME )); then reveal; settled_probe; fi
}
# A dump taken while the window is still animating a posture change can come back
# without the player. Retry briefly before calling it missing.
settled_probe(){
  P_PRESENT=0
  for attempt in 1 2 3 4; do
    do_probe && (( P_PRESENT )) && return 0
    sleep 1
  done
  echo "   no video_player node; windows: ${WINDOWS:-none}"
}
# Change posture: through the console on an emulator, by asking a person on hardware.
# $1 = console posture code, $2 = device state to reach, $3 = what the person does.
to_posture(){
  if (( CONSOLE )); then adb emu posture $1 >/dev/null; return 0; fi
  echo ">>> ACTION: $3 (waiting up to ${WAIT}s)"
  local started=$SECONDS last_tap=$SECONDS
  until [[ $(state) == $2 ]]; do
    (( SECONDS - started > WAIT )) && fail "timed out waiting for the device to reach $2"
    # The bench clip is 15s and does not loop. Rewind it every few seconds so it is
    # still playing whenever the person gets to the fold.
    if (( SECONDS - last_tap >= 5 )); then CT "desc=Skip back 10 seconds"; last_tap=$SECONDS; fi
    sleep 1
  done
  echo "    device is $2"
}
# Two reads a few seconds apart: the position must move forward.
assert_advancing(){
  settled_probe; local before=$POS
  sleep 2.5
  if [[ $1 == flat* ]]; then probe_with_chrome; else settled_probe; fi
  (( PLAYING && POS > before )) || fail "$1: playback is not advancing (${before}s -> ${POS}s, playing=$PLAYING)"
  echo "   advancing ${before}s -> ${POS}s"
}
report(){ echo "   player=[$P_L,$P_T][$P_R,$P_B] controller=[$C_L,$C_T][$C_R,$C_B] fold=$FOLD playing=$PLAYING pos=${POS}s ($TIMES)"; }

assert_flat(){
  [[ $(state) == OPENED ]] || fail "$1: device state is $(state), want OPENED"
  (( P_PRESENT && C_PRESENT && CHROME )) || fail "$1: player, controller or its controls missing"
  (( C_L <= P_L && C_T <= P_T && C_R >= P_R && C_B >= P_B )) || fail "$1: controller does not overlay the player"
  (( P_T < FOLD && P_B > FOLD )) || fail "$1: player does not straddle the midline, so this is not the flat layout"
  (( PLAYING )) || fail "$1: not playing"
}
assert_tabletop(){
  [[ $(state) == HALF_OPENED ]] || fail "$1: device state is $(state), want HALF_OPENED"
  (( P_PRESENT && C_PRESENT )) || fail "$1: player or controller node missing"
  (( CHROME )) || fail "$1: controls are hidden; tabletop must pin them visible"
  (( P_B <= FOLD + TOL )) || fail "$1: player bottom $P_B is below the fold $FOLD"
  (( C_T >= FOLD - TOL && C_T <= FOLD + TOL )) || fail "$1: controller top $C_T is not on the fold $FOLD"
  (( C_B > FOLD )) || fail "$1: controller has no height below the fold"
  (( PLAYING )) || fail "$1: not playing"
}

[[ -n $APK ]] && { adb install -r $APK >/dev/null || fail "install $APK"; echo installed; }
adb shell cmd device_state print-states | grep -q HALF_OPENED || fail "$ANDROID_SERIAL has no HALF_OPENED device state (not a foldable)"

if (( CONSOLE )); then
  echo "== setup: landscape, DND, cold launch"
  adb emu posture 3 >/dev/null || fail "emulator console did not answer"
  adb shell cmd notification set_dnd priority
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1
else
  echo "== setup (hardware, no settings changed): cold launch"
  if [[ $(state) != OPENED ]]; then
    echo ">>> ACTION: open the device flat, hinge horizontal (it is $(state); waiting up to ${WAIT}s)"
    local_started=$SECONDS
    until [[ $(state) == OPENED ]]; do
      (( SECONDS - local_started > WAIT )) && fail "timed out waiting for the device to be opened flat"
      sleep 1
    done
    sleep 2
  fi
fi
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity 2>&1 | grep -q "Status: ok" || fail "launch"
sleep 4
adb logcat -c; adb logcat -b events -c

echo "== open a video post in the fullscreen player"
T text=Discover; sleep 1.5
# Bring the clip into view with TOUCH swipes, then let the feed start autoplaying it
# before the tap. Do not use `android input scroll --until` here: that scrolls through
# accessibility, and opening the player after it gets the shared ExoPlayer
# idle-released 30s later, mid-playback (nubecita-khe8). That is a feed to fullscreen
# handoff bug on main, not a posture one, so this journey stays off that path.
for i in 1 2 3 4 5 6 7 8; do
  do_probe || { sleep 1; continue; }
  grep -q feed_video_embed $TMP/layout.json && break
  # Swipe inside the feed pane (left quarter of a two-pane window), bottom to top.
  adb shell input swipe $(( WIN_W * 28 / 100 )) $(( WIN_H * 72 / 100 )) $(( WIN_W * 28 / 100 )) $(( WIN_H * 24 / 100 )) 300; sleep 1.2
done
grep -q feed_video_embed $TMP/layout.json || fail "no video post in the Discover feed"
sleep 1.5
T id=feed_video_embed; sleep 2

echo "== flat: rewind so the 15s bench clip outlasts the journey"
probe_with_chrome
CT "desc=Skip back 10 seconds"; CT "desc=Skip back 10 seconds"; sleep 1
probe_with_chrome; report
assert_flat "flat (before)"
POS_FLAT=$POS; SURFACE_FLAT=$(surface)
[[ -n $SURFACE_FLAT ]] || fail "no SurfaceView layer for $PKG"

echo "== fold to tabletop mid-playback"; adb shell log -t JOURNEY fold
# Wait past the 3s auto-hide on purpose: in flat posture the controls would be gone
# by now, so finding them proves tabletop pins them rather than catching a leftover.
to_posture 2 HALF_OPENED "fold the device halfway with the hinge horizontal, like a laptop, and leave it"; sleep 4
settled_probe; report
assert_tabletop "tabletop"
(( ! CONSOLE )) || (( POS >= POS_FLAT )) || fail "tabletop: position went back from ${POS_FLAT}s to ${POS}s (player reset)"
[[ $(surface) == $SURFACE_FLAT ]] || fail "tabletop: SurfaceView layer changed, surface was recreated"
POS_TABLETOP=$POS

echo "== tabletop: controls on the flat half respond (skip back), which also re-arms the short clip"; adb shell log -t JOURNEY skipback
T "desc=Skip back 10 seconds"; sleep 1
settled_probe; report
(( PLAYING )) || fail "tabletop (after skip back): not playing"
(( POS < POS_TABLETOP || POS_TABLETOP <= 2 )) || fail "tabletop: skip back did not move the position (${POS_TABLETOP}s -> ${POS}s)"
POS_REWOUND=$POS
(( CONSOLE )) || assert_advancing "tabletop"

echo "== unfold back to flat"; adb shell log -t JOURNEY unfold
to_posture 3 OPENED "open the device flat again, keeping the same orientation"; sleep 2.5
probe_with_chrome; report
assert_flat "flat (after)"
if (( CONSOLE )); then
  (( POS > POS_REWOUND )) || fail "flat (after): position did not advance past ${POS_REWOUND}s (player reset or stalled)"
else
  CT "desc=Skip back 10 seconds"; assert_advancing "flat (after)"
fi
[[ $(surface) == $SURFACE_FLAT ]] || fail "flat (after): SurfaceView layer changed, surface was recreated"

adb logcat -d -t 400 | grep -E "FATAL EXCEPTION|AndroidRuntime: .*$PKG" && fail "crash in logcat"
# A posture change must be a relayout only. A relaunch or destroy of the activity
# would mean the fold went through a configuration change after all.
RELAUNCHES=$(adb logcat -b events -d | grep -cE "wm_relaunch_activity|wm_relaunch_resume_activity|wm_on_destroy_called")
(( RELAUNCHES == 0 )) || fail "the activity was relaunched or destroyed $RELAUNCHES time(s) during the journey"
cleanup
rm -rf "$TMP" # the dump, screenshot and logcat are only worth keeping when a run fails
echo "JOURNEY_OK position flat ${POS_FLAT}s -> tabletop ${POS_TABLETOP}s -> skip back ${POS_REWOUND}s -> flat ${POS}s, no activity relaunch"
echo "           surface: $SURFACE_FLAT"
