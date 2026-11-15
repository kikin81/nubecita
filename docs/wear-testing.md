# Testing the Wear OS app

> The watch app has no phone in the loop when it is tested: the `bench` flavor
> replaces every repository with fictional, in-memory data. Everything below
> runs against a Wear emulator with that build.

This is the standard device pass for any change under `wear/`, the commands the
journeys in `wear/app/src/journeysTest/` refer to, and the definition of done
for a Wear pull request.

## Devices

| AVD | Image | Screen | Use it for |
|---|---|---|---|
| `Wear_OS_Large_Round` | Wear OS 7 (API 37) | 454 × 454 px, 227 dp | The default. Every journey runs here. |
| `Wear_OS_Small_Round` | Wear OS 7 (API 37) | 384 × 384 px, 192 dp | Clipping and edge-padding changes. |
| `Wear_OS_6_Large_Round` | Wear OS 6 (API 36) | 454 × 454 px | The tile fallback of the launcher widget, and anything that depends on the system clock or status bar. |

Create the ones you are missing (`ANDROID_HOME` is the SDK path; `android info`
prints it):

```bash
android sdk install system-images/android-37.0/android-wear-signed/arm64-v8a system-images/android-36/android-wear-signed/arm64-v8a
```

```bash
echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n Wear_OS_Large_Round -k "system-images;android-37.0;android-wear-signed;arm64-v8a" -d wearos_large_round
```

```bash
echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n Wear_OS_Small_Round -k "system-images;android-37.0;android-wear-signed;arm64-v8a" -d wearos_small_round
```

```bash
echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n Wear_OS_6_Large_Round -k "system-images;android-36;android-wear-signed;arm64-v8a" -d wearos_large_round
```

Start one and find its serial:

```bash
android emulator start Wear_OS_Large_Round
```

```bash
adb devices
```

**Every command targets the emulator by serial.** A phone is usually plugged in
too, and the watch app shares the phone app's package name
(`net.kikin.nubecita`). The examples use `emulator-5554`; substitute yours.

## Build and install

```bash
./gradlew :wear:app:assembleBenchDebug
```

```bash
adb -s emulator-5554 install -r wear/app/build/outputs/apk/bench/debug/app-bench-debug.apk
```

Never use a Gradle `install…` task: it installs on every attached device.

A failed install leaves the old APK in place and still looks like a launch that
worked, so confirm the install time moved:

```bash
adb -s emulator-5554 shell dumpsys package net.kikin.nubecita | grep -m1 lastUpdateTime
```

## Launching

Stop the app, then start it the way the launcher does. The task must begin with
a launcher intent, or "open again from the launcher" later starts a second copy
of the activity instead of returning to the first:

```bash
adb -s emulator-5554 shell am force-stop net.kikin.nubecita
```

```bash
adb -s emulator-5554 shell am start -n net.kikin.nubecita/net.kikin.nubecita.wear.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --es scenario default
```

### Bench scenarios

`--es scenario <name>` picks what the bench build pretends is true. A scenario
is read only when the activity is first created and then stays for the life of
the process, so force-stop before changing it. An unknown name crashes on
purpose rather than quietly showing the happy path. The list lives in
`wear/app/src/bench/kotlin/.../bench/BenchScenario.kt`.

| Scenario | What the watch sees |
|---|---|
| `default` | Signed in, two feeds, three conversations (two unread), everything available. |
| `signed_out` | The phone app is installed but nobody is signed in. |
| `phone_app_missing` | An Android phone without Nubecita. |
| `iphone` | The watch is paired with an iPhone. |
| `update_required` | The phone speaks a newer protocol than this watch build. |
| `no_feeds` | Signed in, nothing synced yet. |
| `dms_not_synced` | Signed in, messages never synced. |
| `no_conversations` | Messages synced, and there are none. |
| `no_actions` | The phone cannot run like, repost, bookmark or message actions. |
| `no_posting` | The phone cannot create posts. |
| `post_fails` | A post, and every retry, ends as "Not posted". |
| `dm_fails` | A reply or reaction, and every retry, ends as "Not sent" after 2 seconds. |
| `slow_actions` | Likes, reposts, bookmarks, replies and reactions take 15 seconds. |

In every scenario a new post shows "Posting…" for 10 seconds before it settles.

### Launch actions

The complication and the launcher widget open the app with an action, on a new
and cleared task. To do the same by hand, replace the `-a … -c …` pair above:

```bash
adb -s emulator-5554 shell am start -n net.kikin.nubecita/net.kikin.nubecita.wear.MainActivity -a net.kikin.nubecita.wear.action.OPEN_FEED -f 0x10008000 --es scenario default
```

The actions are `OPEN_FEED`, `OPEN_MESSAGES` and `NEW_POST`, all under
`net.kikin.nubecita.wear.action.`.

## Driving the watch

Read the screen as text first; take a screenshot when the question is visual
(clipping, overlap, colour):

```bash
android layout --device=emulator-5554
```

```bash
android screen capture --device=emulator-5554 -o /tmp/wear.png
```

Tap an element at the `center` the layout reports for it:

```bash
adb -s emulator-5554 shell input tap 129 254
```

| Gesture | Command |
|---|---|
| Rotary crown, one notch down | `adb -s emulator-5554 shell input rotaryencoder scroll --axis SCROLL,-1` |
| Rotary crown, one notch up | `adb -s emulator-5554 shell input rotaryencoder scroll --axis SCROLL,1` |
| Swipe back (dismiss) | `adb -s emulator-5554 shell input swipe 5 227 400 227 300` |
| Screen off | `adb -s emulator-5554 shell input keyevent KEYCODE_SLEEP` |
| Screen on | `adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP` |
| Watch face | `adb -s emulator-5554 shell input keyevent KEYCODE_HOME` (a second press opens the app list) |
| Open from the launcher | `adb -s emulator-5554 shell monkey -p net.kikin.nubecita -c android.intent.category.LAUNCHER 1` |
| Kill the background process | `adb -s emulator-5554 shell am kill net.kikin.nubecita` (only works once the app is off screen) |

The swipe coordinates are for a 454 px screen: start at the left edge, at half
the height.

### Typing

`adb shell input text` does not work on a watch. The Wear keyboard shows the
injected text but never hands it back to the app, so the review screen comes up
empty. Tap the keys instead. With the keyboard open on its letters:

```bash
scripts/wear-type-keys.py emulator-5554 "hello watch"
```

The script reads the keys from the layout, so it works at any screen size. It
types letters and spaces only, and the keyboard capitalises the first letter.
To reach the keyboard from the app: tap **New post** (or **Reply** → **Speak or
type**), then the keyboard choice on the system input screen. The keyboard's
own **Send** key returns the text to the app.

### Locale and font size

```bash
adb -s emulator-5554 shell cmd locale set-app-locales net.kikin.nubecita --locales es-419
```

```bash
adb -s emulator-5554 shell cmd locale set-app-locales net.kikin.nubecita --locales ""
```

```bash
adb -s emulator-5554 shell settings put system font_scale 1.3
```

```bash
adb -s emulator-5554 shell settings put system font_scale 1.0
```

The app ships `es-419` and `pt-BR`. Put the font scale back to `1.0` when you
are done; it is a device setting, not an app one.

### The launcher widget

Add it once per emulator. It becomes the first surface to the left of the
watch face:

```bash
adb -s emulator-5554 shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile --ecn component net.kikin.nubecita/net.kikin.nubecita.wear.widget.LauncherWidgetService
```

To show it, go to the watch face and swipe left:

```bash
adb -s emulator-5554 shell input swipe 400 227 60 227 200
```

`android layout` reads the widget's targets like any other screen. Do not use
`android run --type=WEAR_WIDGET` between journey steps: it reinstalls the app,
which restarts the process and drops the bench scenario.

## Journeys

`wear/app/src/journeysTest/` holds the regression journeys, in the same format
as `app/src/journeysTest/`. Each is a list of plain-language steps that an agent
(or a person) performs on the emulator with the commands above.

| Journey | Covers |
|---|---|
| `wear_launch_landing` | Landing screen and its three entry points. |
| `wear_feed_browse` | Reading the feed with the crown, every card type, switching feeds. |
| `wear_post_actions` | Like, repost, bookmark, Open on phone; slow and unavailable actions. |
| `wear_messages_quick_reply` | Conversation list, thread, quick reply, typed reply, reaction, failed send. |
| `wear_new_post_keyboard` | Typing, reviewing, changing and posting; a failed post; posting unavailable. |
| `wear_launcher_widget_deeplinks` | The widget's three targets and the back stack each one starts. |
| `wear_swipe_dismiss_back_stack` | What back returns to from every screen. |
| `wear_state_retention_screen_off` | Screen off, watch face, background process kill, widget then launcher. |
| `wear_gate_states` | Every "nothing to show yet" state. |

Rules for running one:

- "Launch Nubecita on the watch with the bench scenario X" means force-stop,
  then the launcher-shaped start above with `--es scenario X`.
- Perform each step exactly as written, and judge a "Verify" step from the
  screen as it is, without scrolling or tapping to make it true.
- The journey is the specification. When the app disagrees with a step, the
  step fails: file a bd bug that names the journey and the step, and leave the
  step as it is. Edit a step only when the step itself described the design
  wrongly.
- Write the result of each run as a markdown file (one heading per step, pass
  or fail, and what was on screen) and attach it to the pull request.

## Definition of done for a Wear pull request

A change under `wear/`, `core/wear-contract/` or `core/wear-sync/` is done when:

1. **It was seen working on the emulator.** Bench build, installed by serial,
   install time confirmed. Say in the pull request which AVD.
2. **The journeys that cover it pass**, or fail only on steps that already
   have a bd bug. Navigation, launch-intent and back-stack changes run
   `wear_swipe_dismiss_back_stack`, `wear_state_retention_screen_off` and
   `wear_launcher_widget_deeplinks`. A new screen or flow adds or extends a
   journey in the same pull request.
3. **Layout changes were checked at the edges**: on `Wear_OS_Small_Round`, at
   font scale 1.3, and in `es-419` or `pt-BR`, whichever has the longest
   strings for the screen. Nothing is cut by the round edge or sits under the
   clock.
4. **UI changes have a `@Preview` and a screenshot test**, with baselines from
   the `update-baselines` bot, never from a Mac.
5. **Both flavors compile and their tests pass**:
   `./gradlew :wear:app:testProductionDebugUnitTest :wear:app:testBenchDebugUnitTest :wear:app:lintProductionDebug`,
   plus `:wear:data:testDebugUnitTest` when `wear/data` changed.
6. **A new state the watch can be in has a bench scenario**, so a journey can
   reach it without a phone.
7. **Battery was considered.** No new periodic work, no wake-ups the change
   does not need, and no retry that keeps the radio up. Battery beats
   reliability here.
8. **The emulator was left as it was found**: font scale 1.0, app locale
   cleared, app stopped.
