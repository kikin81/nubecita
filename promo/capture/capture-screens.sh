#!/usr/bin/env bash
# Captures the still screenshots that feed the Remotion image-asset stills
# (Assets.tsx / ASSET_SPECS). Phone shots at native 1280x2856; tablet shots at
# 2560x1600 @ 320 dpi (1280x800 dp, the geometry tablet.sh records), restored on
# exit. Nubecita_Bench_Phone only; tabs are found by accessibility label
# (lib.sh). All fake bench data.
#
# Output PNGs land in ../remotion/public/ so `remotion still` can read them:
#   feed.png search.png profile.png post.png tablet_feed.png tablet_detail.png
#
# These are the promo/ads stills, not the Play listing: Play screenshots come
# from the fastlane lanes in the prepare-release skill.
#
#   promo/capture/capture-screens.sh <bench-phone-serial>
set -euo pipefail
# shellcheck disable=SC2034  # used by lib.sh
SERIAL="${1:?usage: capture-screens.sh <bench-phone-serial>}"
# shellcheck source=lib.sh
source "$(dirname "$0")/lib.sh"
DEST="$(cd "$(dirname "$0")/../remotion/public" && pwd)"

cleanup(){
  SH wm size reset >/dev/null 2>&1 || true
  SH wm density reset >/dev/null 2>&1 || true
  demo_off
}
trap cleanup EXIT

grab(){ A exec-out screencap -p > "$DEST/$1"; echo "  → $1"; }
# The feed opens on the bench's foreign-language posts (the translation
# journey's), so the stills start further down. Find the post by its text and
# drag it to <y> standing still: a fling would land somewhere else each run.
scroll_to(){ # scroll_to <x> <post text> <y>
  local pos y _
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    pos="$(node text "$2")"; [[ -n "$pos" ]] && break
    SH input swipe "$1" 1400 "$1" 500 1500; pause 1   # fits the phone and the tablet
  done
  [[ -n "$pos" ]] || { echo "not on screen: $2" >&2; exit 1; }
  y="${pos#* }"
  SH "input motionevent DOWN $1 $y; input motionevent MOVE $1 $(( (y + $3) / 2 )); input motionevent MOVE $1 $3; sleep 0.4; input motionevent UP $1 $3"
  pause 1.2
}

echo "[phone] native 1280x2856"
demo_on; relaunch 6
tap 700 600; pause 2.5; grab post.png                          # the top post's body → its thread
relaunch 6
tap_node content-desc Search 1; pause 2.5; grab search.png    # Trending + suggestions
tap_node content-desc You;      pause 2.5; grab profile.png
tap_node content-desc Feed;     pause 1.5
scroll_to 640 "one shot from this morning" 420; grab feed.png  # photo posts

echo "[tablet] resize to 2560x1600 @ 320 dpi"
SH wm size 2560x1600; SH wm density 320; pause 1
relaunch 6
scroll_to 500 "one shot from this morning" 300
tap 500 300; pause 2.5; grab tablet_detail.png                 # that post → the detail pane
tap_node content-desc Search 1; pause 3; grab tablet_feed.png  # Search + the Explore pane

echo "done → $DEST"   # display + demo mode restored by the EXIT trap
