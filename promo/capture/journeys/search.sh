#!/usr/bin/env bash
# Journey: Search & discovery. Feed → Search tab (Trending, suggested accounts
# and feeds) → type "coffee" (typeahead people) → submit → Posts results →
# Media filter → Feeds tab → Packs tab (starter packs). Nubecita_Bench_Phone
# (1280x2856); targets come from accessibility labels (../lib.sh), resolved in
# a dry pass so the take has no lookup pauses. All fake data.
#
#   promo/capture/journeys/search.sh <bench-phone-serial> [out.mp4]
set -euo pipefail
# shellcheck disable=SC2034  # used by lib.sh
SERIAL="${1:?usage: search.sh <bench-phone-serial> [out.mp4]}"
OUT_DEVICE="/sdcard/search.mp4"; OUT_LOCAL="${2:-search_raw.mp4}"
# shellcheck source=../lib.sh
source "$(dirname "$0")/../lib.sh"

cleanup(){ SH pkill -INT screenrecord >/dev/null 2>&1 || true; demo_off; }
trap cleanup EXIT
need(){ [[ -n "$2" ]] || { echo "not found: $1" >&2; exit 1; }; }

echo "[app] demo status bar; resolve the targets in a dry pass"
demo_on; relaunch 6
TAB="$(node content-desc Search 1)"; need "Search tab" "$TAB"   # the only "Search" on the feed
# shellcheck disable=SC2086
tap $TAB; pause 2.5
FIELD="$(node content-desc Search 1)"; need "search field" "$FIELD"
# shellcheck disable=SC2086
tap $FIELD; pause 1.0; SH input text coffee; pause 1.5; SH input keyevent 66; pause 3.0
MEDIA="$(node text Media)"; need "Media filter" "$MEDIA"
FEEDS="$(node text Feeds)"; need "Feeds tab" "$FEEDS"
PACKS="$(node text Packs)"; need "Packs tab" "$PACKS"
relaunch 6

echo "[record] start"
SH screenrecord --size 640x1428 --bit-rate 16000000 --time-limit 60 "$OUT_DEVICE" &
REC_PID=$!
pause 1.0

echo "[beat 1] search landing — Trending, suggested accounts and feeds"
# shellcheck disable=SC2086
tap $TAB; pause 2.2
drag 640 1900 640 1300 600; pause 1.6         # nudge to reveal the suggested feeds

echo "[beat 2] type — instant typeahead"
# shellcheck disable=SC2086
tap $FIELD; pause 1.0
SH input text coffee; pause 2.0               # typeahead: matching people

echo "[beat 3] results — posts, a filter, feeds, starter packs"
SH input keyevent 66; pause 2.4               # submit → Posts results
# shellcheck disable=SC2086
tap $MEDIA; pause 2.0                          # Media filter
# shellcheck disable=SC2086
tap $FEEDS; pause 2.0
# shellcheck disable=SC2086
tap $PACKS; pause 2.4

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
A pull "$OUT_DEVICE" "${OUT_LOCAL%.mp4}.vfr.mp4" >/dev/null
SH rm -f "$OUT_DEVICE"
cfr "${OUT_LOCAL%.mp4}.vfr.mp4" "$OUT_LOCAL" 1
rm -f "${OUT_LOCAL%.mp4}.vfr.mp4"
echo "done: $OUT_LOCAL"
