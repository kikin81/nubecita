# Play Store marketing screenshots

The Play listing's phone + tablet screenshots are generated from the **`bench`
flavor** (signed-in, deterministic fake data, zero network) by the
`MarketingScreenshotJourney` UiAutomator journey, then framed (device frame +
brand backdrop + localized headline) by fastlane.

- Journey: `app/src/androidTest/.../screenshots/MarketingScreenshotJourney.kt`
- Lanes: `fastlane/Fastfile` — `screenshots` (phone), `screenshots_tablet` (tablet), `frame_marketing_screenshots`, `localize_marketing_screenshots` (es-419 / pt-BR title bands), `upload_screenshots`
- Headlines: `fastlane/metadata/android/<locale>/images/phoneScreenshots/title.strings` (per locale; shared by phone **and** tablet framing). Locales listed in `MARKETING_LOCALES` — see [Localization](#localization).
- Committed assets: `…/images/phoneScreenshots/NN_name_framed.jpg` and `…/images/tenInchScreenshots/NN_name_framed.jpg`
  (the raw `*.png` captures and intermediate `*_framed.png` are gitignored)
- Upload: the `upload_screenshots` lane, run by `.github/workflows/screenshots-upload.yaml` only when committed images change on `main` (never on every release).

## Prerequisites

```bash
# Ruby (pinned by .ruby-version) — the system ruby is too old for fastlane.
rbenv install -s    # installs 3.4.9 if missing
eval "$(rbenv init - bash)"   # or add to your shell profile; CI/non-interactive
                              # shells: export PATH="$HOME/.rbenv/shims:$PATH"
bundle install                # installs fastlane (Gemfile)

brew install imagemagick      # `magick` — used for framing + tablet compositing
```

You also need a **phone** emulator/device and a **tablet** one running (the
lanes take `device:<adb serial>`; list with `adb devices`).

## Regenerate everything

```bash
# Phone — frameit frames with a Pixel-5 device bezel, so the capture display
# MUST be Pixel-5-class or frameit aborts with "Unsupported screen size".
adb -s <phone-serial> shell wm size 1080x2340
adb -s <phone-serial> shell wm density 432
bundle exec fastlane screenshots device:<phone-serial>
adb -s <phone-serial> shell wm size reset && adb -s <phone-serial> shell wm density reset

# Tablet — no frameit (it has no modern Android-tablet frame); a backdrop+title
# composite via fastlane/frameit_assets/compose_tablet.sh. Any tablet resolution.
bundle exec fastlane screenshots_tablet device:<tablet-serial>
```

The phone lane **wipes** `phoneScreenshots` (`clear_previous_screenshots: true`)
then recaptures all shots; the tablet lane keeps the bucket and relocates the
freshly-captured shots into `tenInchScreenshots`. Both end by framing.

> Tablet `06_search_posts` / `07_search_feeds` can fail to capture — the tablet
> search uses a pane-scoped docked search bar whose field tag the journey
> doesn't target yet. Those shots keep their previous capture (the lane runs
> `exit_on_test_failure: false`). Tracked as a follow-up.

## Add a new screenshot

1. Add a `@Test` to `MarketingScreenshotJourney` named `aNN<Screen>` (the `NN`
   prefix orders the listing). Navigate to the screen, then `awaitTag(<tag>)`
   on a stable Compose `testTag` (surfaced as a bare resource-id via
   `testTagsAsResourceId`), then the framework captures via `capture("NN_name")`.
2. Add a headline to `phoneScreenshots/title.strings`:
   `"NN_name" = "Three to five words.";` — **required**, or framing skips the shot
   ("no title in title.strings").
3. Regenerate (above). Commit the new `*_framed.jpg` for both buckets.

### Deep-link captures (e.g. post-detail)

To drive a screen that isn't reachable by tapping (or to avoid a fragile
tap-target), launch via a deep link with `captureDeepLink(name, uri) { awaitTag(...) }`.
`MainActivity` is **`singleTask`**, which trips two traps:

- `ActivityScenario.launch(MainActivity::class, viewIntent)` hangs at
  `PRE_ON_CREATE` (it can't monitor a singleTask launch). So `captureDeepLink`
  boots the app the normal class-based way, waits for the feed, then delivers
  the deep link as a **`NEW_TASK` `ACTION_VIEW`** intent — singleTask routes it
  to `onNewIntent → MainActivity.handleIntent →` the deep-link matchers, exactly
  like `adb shell am start -a android.intent.action.VIEW -d <uri>`.
- That `NEW_TASK` reuse detaches the instance from `ActivityScenario`, so its
  `close()` can't reach `DESTROYED` — the helper swallows that benign teardown
  failure (the capture already happened).

Deep-link URLs must satisfy the matchers: a post URL is
`https://bsky.app/profile/<handle>/post/<rkey>` where `<rkey>` is a valid 13-char
TID (`[2-7a-z]{13}`) and `<handle>` a valid handle — see `ActorValidation`. The
bench post fixture (`benchGalleryPost`) uses `ridgewalk2357`.

## Surgical re-capture of a single shot

To refresh just one shot without re-running (and re-encoding) the whole set:

```bash
# Phone (Pixel-5 res as above), capture only that test:
adb -s <serial> shell am instrument -w \
  -e class 'net.kikin.nubecita.screenshots.MarketingScreenshotJourney#aNN<Screen>' \
  net.kikin.nubecita.test/net.kikin.nubecita.core.testing.android.HiltTestRunner
# Pull the PNG (app-private dir → run-as), drop it in the bucket as NN_name.png,
# move the other raw PNGs (and ALL tenInch PNGs) aside so frameit frames only it:
bundle exec fastlane frame_marketing_screenshots
# …then move the stashed PNGs back.
```

(frameit frames every `*.png` it finds under `fastlane/metadata/android`, and
aborts on the tablet bucket's non-Pixel-5 resolution — hence moving the tenInch
PNGs aside when framing a single phone shot.)

## Localization

The listing ships localized screenshot buckets — currently `en-US`, `es-419`
(Spanish, Latin America), `pt-BR` (Brazilian Portuguese) — listed in the
Fastfile's `MARKETING_LOCALES`.

**Today the localization is title-only.** The app UI has no localized string
resources yet, so the captured app screenshots are English in every bucket; only
the **framed title band** is translated (it's baked into the JPG, so the Play
Console can't translate it post-hoc). So the localized buckets *reuse the en-US
captures* — no need to re-run the journey per locale:

```bash
# 1. Make sure the en-US raw captures exist (regenerate if a fresh checkout —
#    the *.png are gitignored, only the framed JPGs are committed):
bundle exec fastlane screenshots device:<phone-serial>
bundle exec fastlane screenshots_tablet device:<tablet-serial>

# 2. Generate every localized bucket (copies en-US captures, re-frames with each
#    locale's title.strings):
bundle exec fastlane localize_marketing_screenshots
```

Each locale needs a headline file at
`fastlane/metadata/android/<locale>/images/phoneScreenshots/title.strings`
(same keys as en-US; read by both phone and tablet framing). To change the copy,
edit that file and re-run `localize_marketing_screenshots`.

**Add a locale:** add its code to `MARKETING_LOCALIZED` in the Fastfile, create
its `title.strings`, and run the lane. Use the Play Store locale codes (`es-419`,
`pt-BR`, `es-ES`, `pt-PT`, …).

**When the app itself is localized** (ships `values-es`, `values-pt-BR`, …),
switch from reuse to per-locale capture: run the journey with
`locales: [...]` (or once per locale) so each bucket holds its own translated app
UI, then frame as usual. The bucket layout and `title.strings` don't change.

## Upload

Don't upload by hand. Merge the committed `*_framed.jpg` changes to `main`;
`.github/workflows/screenshots-upload.yaml` runs the `upload_screenshots` lane,
which anchors a listing edit to the live internal-track version code and pushes
the images to the single global Play listing (text/icon/feature-graphic stay
untouched).
