#!/bin/zsh
# Drives app/src/journeysTest/memory_leak_check.journey.xml on a bench debug build, then
# prints LeakCanary results. Usage: APK=app/build/outputs/apk/bench/debug/app-bench-debug.apk ANDROID_SERIAL=<serial> scripts/leak-journey.sh
# Taps go through accessibility, so expect the known semantics-cache report (see the journey file).
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}; D=--device=$ANDROID_SERIAL
T(){ android input tap $D --match=first --wait=6000 "$1" >/dev/null || { echo "FAIL tap $1"; return 1; }; sleep 1.5; }
B(){ adb shell input keyevent BACK; sleep 1.5; }
run(){
for i in 1 2 3; do
  echo "== iteration $i"
  T desc=Feed || return 1
  T id=post_card_body || return 1; B
  T "desc=Alice Chen,clickable=true" || return 1; B
  T desc=Search && T desc=Notifications && T desc=Chats && T desc=You || return 1
  T desc=Settings || return 1; B
  adb shell cmd uimode night yes >/dev/null; sleep 4
  T desc=You || return 1
  adb shell cmd uimode night no >/dev/null; sleep 4
  T desc=Feed || return 1
done; }
adb install -r ${APK:?} >/dev/null && echo installed
adb shell am force-stop net.kikin.nubecita; adb shell am start -n net.kikin.nubecita/.MainActivity >/dev/null; sleep 5
adb logcat -c
run && echo JOURNEY_OK
adb shell input keyevent HOME; sleep 25
echo "--- LeakCanary (non-watch lines)"; adb logcat -d | grep -E "LeakCanary|FATAL" | grep -v "Watching instance" | cut -c1-220 | tail -40
echo "--- watched"; adb logcat -d | grep 'Watching instance' | sed -E 's/.*Watching instance of ([^ ]+) .*/\1/' | sort | uniq -c
adb shell dumpsys meminfo net.kikin.nubecita | grep -E "Activities"
