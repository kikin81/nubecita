#!/usr/bin/env bash
# Journey: On-device translation. The bench Following feed opens on posts in
# Japanese and German. Translate the top (Japanese) post in place and accept the
# "Translate Japanese automatically?" offer → scroll: the next Japanese post is
# translated too → flip it to the original and back → open its thread, where
# the Japanese replies arrive translated.
# Nubecita_Bench_Phone (1280x2856); targets come from accessibility labels
# (../lib.sh), resolved in a dry pass that runs the same beats, so the take has
# no lookup pauses.
#
# ML Kit downloads each language pack once (Wi-Fi), so the first run on a fresh
# emulator spends a few seconds on "Downloading language…": run the script
# twice and keep the second take. Every run resets the translation preferences
# and the Room translation cache, so each take starts untranslated.
#
#   promo/capture/journeys/translation.sh <bench-phone-serial> [out.mp4]
set -euo pipefail
# shellcheck disable=SC2034  # used by lib.sh
SERIAL="${1:?usage: translation.sh <bench-phone-serial> [out.mp4]}"
OUT_DEVICE="/sdcard/translation.mp4"; OUT_LOCAL="${2:-translation_raw.mp4}"
# shellcheck source=../lib.sh
source "$(dirname "$0")/../lib.sh"

cleanup(){ SH pkill -INT screenrecord >/dev/null 2>&1 || true; demo_off; }
trap cleanup EXIT
need(){ [[ -n "$2" ]] || { echo "not found: $1" >&2; exit 1; }; }
# node, retried: a translation lands a moment after the scroll that shows it.
wait_node(){ # wait_node <attr> <value> [n]
  local pos _
  for _ in 1 2 3 4; do pos="$(node "$@")"; [[ -n "$pos" ]] && break; pause 1; done
  echo "$pos"
}

# Forget every translation: the auto-translate preferences (the only keys in
# user_preferences) and the post_translation cache. The image ships no sqlite3,
# so the database round-trips through the host. The app must be stopped.
reset_translations(){
  local tmp; tmp="$(mktemp -d)"
  SH am force-stop "$PKG"
  SH run-as "$PKG" rm -f files/datastore/user_preferences.preferences_pb
  for f in nubecita.db nubecita.db-wal nubecita.db-shm; do
    A exec-out run-as "$PKG" cat "databases/$f" > "$tmp/$f" 2>/dev/null || rm -f "$tmp/$f"
  done
  sqlite3 "$tmp/nubecita.db" "DELETE FROM post_translation; PRAGMA wal_checkpoint(TRUNCATE);" >/dev/null
  A exec-in run-as "$PKG" sh -c 'cat > databases/nubecita.db' < "$tmp/nubecita.db"
  SH run-as "$PKG" rm -f databases/nubecita.db-wal databases/nubecita.db-shm
  rm -rf "$tmp"
}

SCROLL=(640 2100 640 1200 700)

# Accepting the offer sometimes leaves a post that was already on screen
# offered instead of translated (an app race, nubecita-ty8r), so the dry
# pass retries and the take is checked at the end.
dry_pass(){
  reset_translations; relaunch 6
  JA="$(node text "Translate from Japanese" 1)"; need "Japanese chip" "$JA"
  # shellcheck disable=SC2086
  tap $JA; pause 1.5
  AUTO="$(node text "Translate automatically")"; need "auto-translate offer" "$AUTO"
  # shellcheck disable=SC2086
  tap $AUTO; pause 2.0
  drag "${SCROLL[@]}"; pause 2.4
  ORIG="$(wait_node text "Show original" '$')"   # the post scrolled in
  BODY="$(node text "Today's Tokyo")"
  [[ -n "$ORIG" && -n "$BODY" ]] || return 1
  # shellcheck disable=SC2086
  tap $ORIG; pause 1.5
  SHOWTR="$(wait_node text "Show translation")"; need "Show translation" "$SHOWTR"
}

echo "[app] demo status bar; resolve the targets in a dry pass"
demo_on
for try in 1 2 3; do
  dry_pass && break
  [[ $try == 3 ]] && { echo "the scrolled-in post never translated; rerun" >&2; exit 1; }
  echo "  dry pass $try: the scrolled-in post stayed untranslated, retrying"
done
reset_translations; relaunch 6

echo "[record] start"
SH screenrecord --size 640x1428 --bit-rate 16000000 --time-limit 60 "$OUT_DEVICE" &
REC_PID=$!
pause 1.5                                     # rest on the untranslated feed

echo "[beat 1] translate a Japanese post in place → translate Japanese automatically"
# shellcheck disable=SC2086
tap $JA; pause 2.0                             # translated, and the offer dialog
# shellcheck disable=SC2086
tap $AUTO; pause 1.8

echo "[beat 2] scroll: the next Japanese post arrives translated"
drag "${SCROLL[@]}"; pause 1.8

echo "[beat 3] flip it to the original and back"
# shellcheck disable=SC2086
tap $ORIG; pause 1.8
# shellcheck disable=SC2086
tap $SHOWTR; pause 1.8

echo "[beat 4] open its thread: the replies arrive translated too"
# shellcheck disable=SC2086
tap $BODY; pause 4.0

echo "[record] stop"
SH pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" 2>/dev/null || true; pause 1.0
[[ -n "$(node text "Today's Tokyo")" ]] || { echo "take missed the translation (app race); rerun" >&2; exit 1; }
A pull "$OUT_DEVICE" "${OUT_LOCAL%.mp4}.vfr.mp4" >/dev/null
SH rm -f "$OUT_DEVICE"
cfr "${OUT_LOCAL%.mp4}.vfr.mp4" "$OUT_LOCAL" 3   # hold the thread
rm -f "${OUT_LOCAL%.mp4}.vfr.mp4"
echo "done: $OUT_LOCAL"
