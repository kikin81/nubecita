---
name: prepare-release
description: Prepare a weekly nubecita release before promoting it — verify the critical flows still work on a real device, refresh the Play release notes, work out whether the store listing needs new phone or Wear screenshots, and regenerate the shippable baseline profile with fresh startup numbers. Use this whenever the user talks about cutting, preparing, or getting ready for a release, doing the weekly release, "what do I need before I promote", refreshing release notes or Play screenshots, or updating baselines before shipping — even if they don't name this skill. Stops short of promoting: `promote-to-production` does that.
license: MIT
compatibility: Requires a signed-in phone on USB (plus a Wear device/emulator for the watch check), the authenticated `gh` CLI, `bd`, and fastlane with Play credentials.
metadata:
  author: nubecita
  version: "1.0"
---

Get a release ready to promote. Four things, in this order, each stopping the run if it fails:

1. the critical flows still work on a real device
2. the Play release notes describe what's shipping
3. the store listing has screenshots for anything new a user will see
4. the shipped baseline profile matches the code that's shipping

**Announce at start:** "Using the prepare-release skill — journeys first, then notes, screenshots and baselines."

**What this does NOT do.** It never promotes and it never publishes screenshots. Both belong to `promote-to-production`, where the build and its listing are reviewed together. The reason is in step 3 and it matters.

## Why the order

The hand-checked flows run first because they are the only thing that can call the release off in minutes, and because steps 3 and 4 cost roughly an hour of device time. Finding a dead Notifications tab after a 40-minute profile regen wastes the hour.

The seven automated journeys are the exception: they are a by-product of the generator in step 4, so their verdict necessarily arrives at the end. That is the cost of not running the generator twice, and it is why step 4 refuses to accept a profile whose coverage line shows a `SKIPPED` journey.

## Preflight

```bash
adb devices -l                     # exactly one phone, or pin a serial everywhere
git rev-parse --abbrev-ref HEAD    # main, up to date
bd dolt pull                       # bd writes come later; pull first, always
```

Two traps worth knowing before you start:

- **Gradle installs to every attached device.** `:app:install…` fans out, so a second device (a Wear emulator, a watch, the user's own phone on wifi) fails the whole task on a signature mismatch. Build once, then `adb -s <serial> install -r <apk>` to the device you mean.
- **A foldable needs an explicit display for screenshots.** `adb exec-out screencap -p` writes a `[Warning] Multiple displays...` line into the PNG and corrupts it. Get the physical id from `dumpsys SurfaceFlinger --display-id` and pass `-d <id>`.

## Step 1 — critical flows

Most of these are already automated. `:benchmark`'s `BaselineProfileGenerator` drives seven journeys and logs a coverage line:

```
baseline journey coverage: feed_scroll=ok, post_detail=ok, trending_videos=SKIPPED, profile=ok, search=ok, chats=ok, composer=ok
```

That line is the verdict for those seven, and it is read in **step 4**, where the generator actually runs — there is no reason to spend forty minutes running it twice.

So this step is the cheap gate: the flows the generator does **not** cover, checked by hand on the device now, before any of the expensive work. A failure here ends the run in minutes instead of an hour.

| Flow | Why it's a show-stopper |
|---|---|
| Signed-in cold start | A release that silently signs people out is the worst outcome on this list, and a flow that signs in fresh every time cannot see it |
| Feed switching (chip row) | Bugs have hidden here that only appear on a non-default feed |
| Notifications tab | A whole tab; crashes are fatal |
| Like a post | The most-used write in the app |
| Tap an image | Media viewer is a separate route from video |
| Settings | Reachable from Profile; a crash strands the user |
| Paywall view | Opening it only — never attempt a real purchase |
| Wear app launches | Ships in the same release, and the shrinker has stripped its capabilities before |

### Cold-start network check

Run this on the same signed-in device, after the hand-checked flows. It catches failures that only show up when a fresh process sends many authenticated requests at once, which no single hand-checked flow does. The atproto 9.15.0 DPoP nonce race (fixed in 9.15.1) failed feeds, profiles and chats on cold start for weeks without a crash.

```bash
S=<serial>; P=net.kikin.nubecita
for c in $(seq 1 10); do
  adb -s $S logcat -c
  adb -s $S shell am force-stop $P; sleep 2
  adb -s $S shell am start -n $P/.MainActivity >/dev/null
  sleep 8
  adb -s $S shell uiautomator dump /data/local/tmp/u.xml >/dev/null
  snack=$(adb -s $S exec-out cat /data/local/tmp/u.xml | grep -c "load your feeds")
  errors=$(adb -s $S logcat -d | grep -cE "use_dpop_nonce|XrpcError")
  echo "cycle $c snackbar=$snack errors=$errors"
done
```

Pass: no snackbar and no errors in all 10, still signed in at the end. The snackbar check works on any build. The logcat check needs a debug build, because release builds strip the app's logging.

Use `am start -n …/.MainActivity`, not `monkey`: debug builds also ship LeakCanary's launcher activity, which `monkey` can pick. Keep it to about 10 cycles: each force-stop that lands mid-refresh is a small logout risk on the signed-in account.

### When one fails

- **A crash never retries.** If logcat shows `FATAL` for `net.kikin.nubecita`, stop. No judgement call.
- **Everything else gets exactly one retry.** A retry-pass continues, but report it as **flaky**, not green. An intermittent pass is a finding; treating it as success is how a real bug ships.
- **Separate "broken" from "couldn't run."** Not signed in, no network, device asleep, wrong build installed — those are preconditions, and they read differently from "tapped Search and it died."
- **Capture evidence at the moment of failure**, into the scratch directory: a screenshot, `uiautomator dump`, and a logcat tail. Re-running to investigate loses the state that explains it.
- Offer to file a bd issue. Don't file one unasked.

## Step 2 — release notes

There is exactly **one** notes file per locale, reused for every release:

```
fastlane/metadata/android/{en-US,es-419,pt-BR}/changelogs/default.txt
```

The `promote` lane reads these at promote time and **fails if any locale is missing or empty**, so all three move together. Whatever is in them ships for every later promotion until someone changes them again — they are not per-version, and that is deliberate.

Draft from the commits since the last promotion, in the user's voice, not the commit subjects. Commit as `docs:`.

**`docs:` does not cut a release, and does not need to.** The notes are not baked into the AAB; promote reads them from the repo. This is the answer to "but a docs commit won't reach Play" — it doesn't have to.

If the repo has a `translate-strings` skill and the English copy changed meaningfully, the other two locales are a translation job, not a copy-paste.

## Step 3 — store screenshots

**Committing an image to `main` publishes it immediately.** `.github/workflows/screenshots-upload.yaml` fires on any push to `main` touching `fastlane/metadata/android/**/images/**` and uploads to the Play listing. That listing is **global** — the Play API keys store images by language and image type, with no track — so an image committed while the build is still on `internal` is visible to everyone on the store page.

That is why this step prepares but does not commit:

1. List every `feat:` merged since the last **production** promotion — not since the last release, because the listing is behind by however long promotion lags.
2. Ask the user which of those change what a new user sees on the phone or Wear listing. Don't try to infer it; nothing in the repo links a feature to the listing, and guessing both misses things and cries wolf.
3. For the ones they name, capture with the existing lanes — `screenshots`, `screenshots_tablet`, `wear_screenshots`, then `frame_marketing_screenshots` and `localize_marketing_screenshots`.
4. Leave the images **uncommitted**, or on a branch that is not merged. Hand them to `promote-to-production`, where they go live with the feature.

## Step 4 — baseline profile and startup

Regenerate every release. The generator's own KDoc says "not on every release", but a weekly cadence makes that advice moot, and a stale profile silently under-serves the startup path.

Use the `run-startup-bench` skill for the mechanics; it already carries the device hygiene and the correct flavored task names. The parts that matter here:

```bash
./gradlew :app:generateProductionReleaseBaselineProfile -PbaselineProfileEnvironment=production \
  -Pandroid.testInstrumentationRunnerArguments.class=net.kikin.nubecita.benchmark.BaselineProfileGenerator
```

- `-PbaselineProfileEnvironment=production` is what makes it shippable. The default targets the `bench` flavor with fake repositories and produces a profile missing the real network, crypto and serialization rules.
- The `class=` filter is load-bearing. Without it the task runs every test in `:benchmark`, and those reinstall the app and wipe its data — destroying the sign-in the generator depends on, mid-run.
- It needs a signed-in `productionNonMinifiedRelease` on the device, and the run uninstalls the app at the end, so every regen needs a fresh sign-in.

**Then check the profile before trusting it**, because the generator's summary lies by omission:

- **Read the coverage line.** This is where the seven automated journeys from step 1 get their verdict. **A `SKIPPED` journey is a failure, not a gap** — the trending-videos leg reported `SKIPPED` on every iteration for weeks and read as silence, while the screens it covers were simply missing from the shipped profile. Anything that did not run did not pass, and the profile is not shippable until it does.
- The summary reports "0 Removed rules" against its own previous output, **not** against the committed file. Diff them properly as a set:

```bash
comm -23 <(sort -u <committed>) <(sort -u <new>) | wc -l   # rules dropped
comm -13 <(sort -u <committed>) <(sort -u <new>) | wc -l   # rules gained
```

A regen on 2026-11-04 reported "0 Removed" while actually dropping 577 rules from the journey profile, 238 of them media3/ExoPlayer, because one journey was skipped. Surface dropped rules by area so the user can judge whether the trade is acceptable.

Open the PR as **`perf:`** — unlike `docs:`, that does cut a release, which is what a new profile needs since it ships inside the AAB.

Finally, post the `StartupBenchmark` numbers to the `nubecita-crmi` epic thread (`bd comments nubecita-crmi`). Same-cell comparison against the last entry is the only way slow drift across releases ever gets noticed.

## Report

Finish with a short written summary the user can act on:

```
Release prep — <date>

Journeys     <n> ok, <n> flaky, <n> failed      <names of anything not clean>
Notes        updated / unchanged                 <commit sha>
Screenshots  <n> captured, uncommitted           <features they cover>
Baselines    +<n> / -<n> rules                   <PR link>  <TTID median, CoV>

Ready to promote: yes / no — <what's blocking>
```

Say plainly what was not verified. A step that was skipped, a journey that passed only on retry, a profile whose dropped rules were not explained — each of those is something the user would want to know before promoting, and none of them is visible in a green summary.
