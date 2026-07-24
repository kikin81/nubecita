#!/usr/bin/env bash
# Journey: Foldable unfold. Two segments captured at the Pixel Fold's two display
# geometries via `wm size` (reproducible — same trick as tablet.sh):
#   1) COVER  1080x2092 @ density 420 -> compact single-pane feed, settle on post N
#   2) INNER  2208x1840 @ density 380 -> expanded list-detail two-pane, tap post N
# Post N = the 3rd feed card. Outputs fold_cover_raw.mp4 + fold_inner_raw.mp4.
# Bench flavor, all fake/apolitical data. Restores display + demo mode on exit.
# NOTE: input tap/swipe coords are in the wm-size DEVICE space, not the record size.
set -euo pipefail
SERIAL="${1:-37201FDHS002UN}"; PKG="net.kikin.nubecita"
A(){ adb -s "$SERIAL" "$@"; }; SH(){ A shell "$@"; }
tap(){ SH input tap "$1" "$2"; }
drag(){ SH input swipe "$1" "$2" "$3" "$4" "${5:-700}"; }
pause(){ sleep "$1"; }

cleanup(){
  SH pkill -INT screenrecord >/dev/null 2>&1 || true
  SH wm size reset >/dev/null 2>&1 || true
  SH wm density reset >/dev/null 2>&1 || true
  SH settings put global heads_up_notifications_enabled 1 >/dev/null 2>&1 || true
  SH am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
}
trap cleanup EXIT

demo(){
  SH settings put global sysui_demo_allowed 1 || true
  SH am broadcast -a com.android.systemui.demo -e command enter >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null || true
}

launch(){ SH am force-stop "$PKG"; pause 0.5; SH am start -n "$PKG/.MainActivity" >/dev/null; pause 6; }

echo "[dnd] suppress heads-up notifications (avoid stolen taps)"
SH settings put global heads_up_notifications_enabled 0 || true

# ---------- Segment 1: COVER (single-pane feed) ----------
echo "[cover] resize 1080x2092 @ 420 -> compact"
SH wm size 1080x2092; SH wm density 420; pause 1
demo; launch
echo "[cover] record (downscaled 540x1046)"
SH screenrecord --size 540x1046 --bit-rate 12000000 --time-limit 20 /sdcard/fold_cover.mp4 &
RID=$!; pause 1.2
# settle so the 3rd card (post N) rests mid-screen; two gentle scrolls
drag 540 1500 540 1040; pause 1.6
drag 540 1400 540 1120; pause 2.4
SH pkill -INT screenrecord >/dev/null 2>&1 || true; wait "$RID" 2>/dev/null || true; pause 1
A pull /sdcard/fold_cover.mp4 fold_cover_raw.mp4 >/dev/null

# ---------- Segment 2: INNER (two-pane list-detail) ----------
echo "[inner] resize 2208x1840 @ 380 -> expanded two-pane"
SH wm size 2208x1840; SH wm density 380; pause 1
demo; launch
echo "[inner] record (downscaled 1104x920)"
SH screenrecord --size 1104x920 --bit-rate 16000000 --time-limit 20 /sdcard/fold_inner.mp4 &
RID=$!; pause 1.2
# scroll the LEFT list pane to post N, then tap it -> right pane shows its thread
# SLOW 2.5s drag releases with ~zero velocity => NO fling. A tap fired while a
# LazyColumn is still flinging is swallowed as a fling-catch, not a click, so the
# no-fling drag is what makes the following tap register reliably.
drag 460 1300 460 950 2500; pause 1.0
# tap the post-card TEXT BAND (between the header=profile and image=lightbox nested
# clickables) so the card's own onClick fires and fills the detail pane. Bounds via
# uiautomator: card [190,390][1170,1402], header [.,388][.,477], image [.,707][.,1277].
tap 700 580; pause 2.8
SH pkill -INT screenrecord >/dev/null 2>&1 || true; wait "$RID" 2>/dev/null || true; pause 1
A pull /sdcard/fold_inner.mp4 fold_inner_raw.mp4 >/dev/null

echo "done: fold_cover_raw.mp4  fold_inner_raw.mp4"
