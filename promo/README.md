# Nubecita promo pipeline

Code-driven marketing assets for Google App campaigns (App Install ads) and
social: three promo **videos** (9:16 / 1:1 / 16:9) and seven store-listing /
campaign **image assets** (phone 4:5 & 1:1, tablet 1.91:1).

Everything here is **regenerable from source** — nothing hand-edited in a video
tool. The generated `.mp4`s, captured screenshots, and `node_modules/` are
git-ignored (see `.gitignore`); this directory holds only the inputs.

## Why the bench build (not the real app)

The footage is captured from the **bench flavor** (`:app:assembleBenchDebug`),
which skips OAuth and serves entirely **fake, apolitical data** (see
`BenchFake*Repository`). A manual recording of the real signed-in feed was
rejected by Google Ads for political content (the account follows public
officials). The bench build has no such content and is fully offline/deterministic,
which also makes the capture reproducible. **Always capture from bench.**

## Layout

```
promo/
  capture/
    record-journey.sh    # adb: drives the bench app + screenrecord → promo_raw.mp4 (the video)
    capture-screens.sh   # adb: still screenshots → remotion/public/*.png (the image assets)
  capture/journeys/      # one script per feature-story video (see below)
  remotion/
    src/Root.tsx         # JOURNEYS registry (5 videos × aspect ratios) + 7 image stills
    src/Promo.tsx        # journey-driven video comp (device frame + captions + CTA outro + Play badge)
    src/Assets.tsx       # the image-asset comp + ASSET_SPECS matrix
    public/gp_badge.png  # official Google Play badge (committed; used unmodified)
    public/*.png,*.mp4   # captured inputs (git-ignored — regenerate via capture/)
```

## Journeys (one video each)

`Root.tsx` renders a `JOURNEYS` array; each entry pairs a captured clip
(`public/<id>.mp4`) with beat-synced captions, and is registered at each aspect
ratio as `<Id>-<tag>` (e.g. `Composer-9x16`, `Tablet-16x9`). Add a video =
capture a clip + add a `JOURNEYS` entry — no new component.

| Journey | Capture script | Clip | Hook |
|---|---|---|---|
| hero | `capture/record-journey.sh <serial>` | `promo.mp4` | like → 120hz scroll → tab tour |
| composer | `capture/journeys/composer.sh <serial>` | `composer.mp4` | "Say it with a GIF" |
| threads | `capture/journeys/threads.sh <serial>` | `threads.mp4` | "Tap any photo to zoom" |
| search | `capture/journeys/search.sh <serial>` | `search.mp4` | "Posts, feeds, and starter packs" |
| translation | `capture/journeys/translation.sh <serial>` | `translation.mp4` | "Read posts in any language" |
| tablet | `capture/journeys/tablet.sh <serial>` | `tablet.mp4` | "Drag to resize, it snaps" |
| watch | `capture/journeys/watch.sh <serial>` | `watch.mp4` | "Bluesky, on your wrist" |
| foldableV2 | `capture/journeys/foldable-v2.sh <serial> remotion/public` | `fold_open.mp4` + `fold_tabletop.mp4` | "Fold it into a stand" |

The journey scripts drive the **bench** build with `adb` and depend on two
bench traits: tapping any post opens one **canned thread** (Jessica Elena's
4-image gallery — used by `threads` and `tablet`), and the feed/search fakes are
deterministic. `tablet.sh` temporarily resizes the display to 2560×1600 @ 320 dpi
(1280×800 dp, where the list-detail divider has four snap stops) and restores
it on exit. The divider handle has no accessibility label, so `lib.sh`'s
`handle_x` finds it in a screenshot. The drags use `input motionevent` with a
pause before release: `input swipe` lets go mid-motion and flings the divider
past the nearest stop.

### Foldable v2 journey

`src/FoldableV2.tsx` draws the nearly square inner display of a book-style
foldable. It plays the open clip, turns a quarter, then folds its bottom half
back into tabletop. `capture/journeys/foldable-v2.sh <serial> remotion/public`
records both clips on the `Nubecita_Bench_Fold` AVD (inner display 2076×2152)
running the phone **bench** build (`:app:assembleBenchDebug`, installed by
serial). It refuses any other device, since the phone emulators here hold real
sessions.

- **open** (flat, portrait): open a post into the second pane → Search, with
  Explore in the second pane → search "coffee" → the search summary takes
  over the second pane.
- **tabletop** (landscape): Luna Martinez's video post fullscreen, played flat,
  then `adb emu sensor set hinge-angle0 90`. The emulator reports a real
  `HALF_FOLDED` posture (some physical foldables don't), and the player puts
  the video on the top half and the controls on the bottom.

The script turns the emulator with `adb emu rotate` and puts the hinge and
rotation back on exit. It finds Luna's post through `uiautomator` text instead
of fixed scrolls, and skips the player back to the start (the inline player
autoplays while the feed scrolls). Both clips are resampled to a constant
60 fps like the watch clip.

`openSegments` in `Root.tsx` are source seconds tied to the script's pauses.
`tabletopSwitchSec` is the tabletop clip's first split frame. The drawn bend
ends on it, so retime it after a recapture by finding the first frame whose top
band turns bright. List-detail column snapping is not in this cut: on a ~850dp
fold the anchors are ~26dp apart, so it belongs in the tablet journey. `npm run
render:foldable` renders `FoldableV2-9x16`, `-1x1` and `-16x9`.

### Watch journey

`src/Watch.tsx` draws a round watch case (metal ring, bezel, crown, strap) and
clips the capture to a circle. `capture/journeys/watch.sh <serial>` records it on
the `Wear_OS_Large_Round` AVD (454×454) running the Wear **bench** build
(`:wear:app:assembleBenchDebug`, installed by serial — see
`docs/wear-testing.md`); it refuses any device that isn't a round Wear AVD,
since the phone emulators here hold real sessions. The Nubecita launcher widget
must already sit on the widget carousel, to the right of the watch face.

The beats: watch face → widget → Home → feed → post → Repost (confirm) +
Bookmark → widget → Messages → quick reply → widget → New post → type → Post →
"Posting…" → posted. Typing uses `scripts/wear-type-keys.py`, because
`adb shell input text` never reaches the app on a watch.

`screenrecord` writes a frame only when the screen changes, so the script
resamples the raw clip to a constant 60 fps and holds its last frame (the
posted card) for 3 s. The raw take runs ~57 s; `WATCH_SEGMENTS` in `Root.tsx`
cuts it to ~26 s by speeding up the slow parts (typing, navigation) and
skipping the app's launch splash and the bench's 10 s "Posting…". The segment
times are source-clip seconds tied to the script's pauses: change a pause and
retime the segments (a 1–2 fps `ffmpeg … tile` contact sheet of
`public/watch.mp4` makes the beats easy to read). Registered as `Watch-9x16`,
`Watch-1x1` and `Watch-16x9`; `npm run render:watch` renders all three.

### Translation journey

`translation.sh` uses the real on-device ML Kit translator, so the bench phone
needs network once to download the Japanese pack: the first run on a fresh
emulator spends a few seconds on "Downloading language…", so run it twice and
keep the second take. Each run starts untranslated: it deletes the translation
preferences and empties the Room `post_translation` cache (the emulator ships
no `sqlite3`, so the database round-trips through the host's). Accepting the
auto-translate offer sometimes leaves a post that is already on screen offered
instead of translated, an app race (`nubecita-ty8r`); the script retries its dry pass and exits
non-zero when the take missed it, so rerun on that.

### Composer journey needs real GIFs

The bench KLIPY fake serves *synthetic* URLs (`static.klipy.com/…/<slug>.webp`),
so the picker grid is empty offline. To capture `composer.sh`, temporarily point
the fake at real, loadable GIFs, then revert:

1. Put a few GIFs in `app/src/bench/assets/promo_gifs/`. The last capture cut
   six 2 s loops (320×240, 12 fps, `loop1.gif`…`loop6.gif`) from the bench's
   own video clips (`app/src/bench/assets/video/clip-1.mp4` and `clip-3.mp4`)
   with ffmpeg `palettegen`/`paletteuse`.
2. In `core/klipy/src/bench/…/BenchFakeKlipyRepository.kt`, return a list of
   `KlipyMediaUi` whose `previewUrl`/`embedUrl` are
   `file:///android_asset/promo_gifs/<file>.gif` (Coil loads asset URIs offline).
3. `./gradlew :app:assembleBenchDebug`, install it on the bench phone by
   serial, run `composer.sh`, then **revert** the fake, delete the assets, and
   reinstall a clean bench build (this override is intentionally not committed).

## Prerequisites

- **Node ≥ 20**, **ffmpeg** (`brew install ffmpeg`).
- The **bench** build (`./gradlew :app:assembleBenchDebug`) installed **by
  serial** on a dedicated emulator: `adb -s <serial> install -r
  app/build/outputs/apk/bench/debug/app-bench-debug.apk`. Never use a Gradle
  `install…` task, which installs on every connected device.
- The phone scripts (`record-journey.sh`, `capture-screens.sh` and the
  `composer`, `threads`, `search` and `tablet` journeys) need the
  **`Nubecita_Bench_Phone`** AVD (Pixel 10 Pro profile, 1280×2856) and refuse
  any other device, because the other phone emulators here hold real signed-in
  sessions. Create it once with:

  ```bash
  avdmanager create avd -n Nubecita_Bench_Phone -d pixel_10_pro \
    -k "system-images;android-37.0;google_apis_playstore;arm64-v8a"
  ```

  `tablet.sh` resizes that emulator to a Pixel Tablet (2560×1600 @ 320 dpi)
  for the take and restores it on exit.
- The scripts share `capture/lib.sh`. It finds tap targets by accessibility
  label through `uiautomator` instead of fixed coordinates, so a layout change
  doesn't silently tap the wrong thing. A missing label stops the script. Each
  lookup is a ~2 s UI dump, so the scripts resolve targets in a dry pass before
  recording. Clips are resampled to a constant 60 fps (`cfr`).

## Regenerate — full flow

```bash
# 1. Capture the video journey (feed like → 120hz scroll → tab tour)
promo/capture/record-journey.sh <bench-phone-serial>   # → promo_raw.mp4 (640×1428, 60fps)
cp -f promo_raw.mp4 promo/remotion/public/promo.mp4

# 2. Capture the still screenshots for the image assets
promo/capture/capture-screens.sh <bench-phone-serial>  # → remotion/public/{feed,search,profile,post,tablet_*}.png

# 3. Render
cd promo/remotion
npm install
npm run render:all                              # hero (3 aspect ratios) + the 4 feature journeys
# …or a single composition by id: npx remotion render Composer-9x16 out/composer-9x16.mp4

# image assets (one still per ASSET_SPECS entry):
npx remotion still phone-feed-45   out/phone-feed-45.png
# …or loop over the ids printed by `npx remotion compositions`
```

Preview interactively with `npm start` (Remotion Studio).

### Known gotcha — Remotion can't find Chrome

On some Node versions Remotion downloads its headless Chrome zip but fails to
extract it (`No browser found` / `should have downloaded browser`). Unzip it
manually and pass the binary explicitly:

```bash
BIN=node_modules/.remotion/chrome-headless-shell/mac-arm64/chrome-headless-shell-mac-arm64/chrome-headless-shell
chmod +x "$BIN"
npx remotion render Hero-9x16 out/hero-9x16.mp4 --browser-executable="$BIN"
```

## Google Play badge

`remotion/public/gp_badge.png` is the **official** "Get it on Google Play" badge
(from Google's badge CDN). Per the [badge guidelines](https://partnermarketinghub.withgoogle.com/brands/google-play/visual-identity/badge-guidelines/)
it is used **unmodified** — uniform scale only, no recolor/crop/distortion, and
its built-in clear-space is preserved. To swap in a localized variant, replace
the file (keep it transparent PNG/SVG) and re-render.

## Google Ads asset specs (reference)

- **Images:** 4:5 → 1200×1500, 1:1 → 1200×1200, 1.91:1 → 1200×628. 8-bit sRGB,
  RGB (no alpha). App campaigns append their own install CTA, so the in-asset
  badge/CTA is optional.
- **Videos:** must be **hosted on YouTube** to be used by an App campaign. Upload
  the rendered `.mp4`s, then reference the YouTube URLs. Providing a portrait
  (9:16) video improves reach/ad-strength.
