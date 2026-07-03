#!/usr/bin/env bash
# Captures the still screenshots that feed the Remotion image-asset stills
# (Assets.tsx / ASSET_SPECS). Phone shots at native 1280x2856, tablet shots at
# 2560x1600 (temporarily resized). All fake/apolitical bench data.
#
# Output PNGs land in ../remotion/public/ so `remotion still` can read them:
#   feed.png search.png profile.png post.png tablet_feed.png tablet_detail.png
set -euo pipefail

SERIAL="${1:-emulator-5554}"
PKG="net.kikin.nubecita"
DEST="$(cd "$(dirname "$0")/../remotion/public" && pwd)"
A() { adb -s "$SERIAL" "$@"; }
SH() { A shell "$@"; }
tap() { SH input tap "$1" "$2"; }
grab() { A exec-out screencap -p > "$DEST/$1"; echo "  → $1"; }
relaunch() { SH am force-stop "$PKG"; sleep 0.5; SH am start -n "$PKG/.MainActivity" >/dev/null; sleep 5; }

Y_NAV=2713; X_FEED=128; X_SEARCH=384; X_PROFILE=1152

echo "[demo] clean status bar"
SH settings put global sysui_demo_allowed 1 || true
SH am broadcast -a com.android.systemui.demo -e command enter >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null || true
SH am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null || true

echo "[phone] native 1280x2856"
relaunch
grab feed.png
tap $X_SEARCH  $Y_NAV; sleep 1.5; grab search.png
tap $X_PROFILE $Y_NAV; sleep 1.5; grab profile.png
tap $X_FEED    $Y_NAV; sleep 1.2; tap 640 900; sleep 2.0; grab post.png   # open a thread

echo "[tablet] resize to 2560x1600"
SH wm size 2560x1600; SH wm density 240; sleep 1
SH am broadcast -a com.android.systemui.demo -e command enter >/dev/null || true
relaunch
grab tablet_feed.png
tap 640 900; sleep 2.0; grab tablet_detail.png     # list-detail: opens the right pane

echo "[tablet] restore"
SH wm size reset; SH wm density reset
SH am broadcast -a com.android.systemui.demo -e command exit >/dev/null || true
echo "done → $DEST"
