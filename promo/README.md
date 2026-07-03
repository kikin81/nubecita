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
| hero | `capture/record-journey.sh` | `promo.mp4` | like → 120hz scroll → tab tour |
| composer | `capture/journeys/composer.sh` | `composer.mp4` | "Say it with a GIF" |
| threads | `capture/journeys/threads.sh` | `threads.mp4` | "Tap any photo to zoom" |
| search | `capture/journeys/search.sh` | `search.mp4` | "Posts, people, and feeds" |
| tablet | `capture/journeys/tablet.sh` | `tablet.mp4` | "Two panes, more context" |

The journey scripts drive the **bench** build with `adb` and depend on two
bench traits: tapping any post opens one **canned thread** (Jessica Elena's
4-image gallery — used by `threads` and `tablet`), and the feed/search fakes are
deterministic. `tablet.sh` temporarily resizes the display to 2560×1600 and
restores it on exit.

### Composer journey needs real GIFs

The bench KLIPY fake serves *synthetic* URLs (`static.klipy.com/…/<slug>.webp`),
so the picker grid is empty offline. To capture `composer.sh`, temporarily point
the fake at real, loadable GIFs, then revert:

1. Drop a few public-domain GIFs in `app/src/bench/assets/promo_gifs/` (e.g.
   Wikimedia Commons via `https://commons.wikimedia.org/wiki/Special:FilePath/<file>.gif`).
2. In `core/klipy/src/bench/…/BenchFakeKlipyRepository.kt`, return a list of
   `KlipyMediaUi` whose `previewUrl`/`embedUrl` are
   `file:///android_asset/promo_gifs/<file>.gif` (Coil loads asset URIs offline).
3. `./gradlew :app:installBenchDebug`, run `composer.sh`, then **revert** the
   fake + delete the assets (this override is intentionally not committed).

## Prerequisites

- **Node ≥ 20**, **ffmpeg** (`brew install ffmpeg`).
- A **connected emulator/device** with the **bench** build installed:
  `./gradlew :app:installBenchDebug` (see the `bench-build-skips-signin` note).
  The capture scripts assume a phone screen of **1280×2856** — retune the tap
  coordinates in the scripts if your AVD differs.

## Regenerate — full flow

```bash
# 1. Capture the video journey (feed like → 120hz scroll → tab tour)
promo/capture/record-journey.sh                 # → promo_raw.mp4 (640×1428, 60fps)
cp promo_raw.mp4 promo/remotion/public/promo.mp4

# 2. Capture the still screenshots for the image assets
promo/capture/capture-screens.sh                # → remotion/public/{feed,search,profile,post,tablet_*}.png

# 3. Render
cd promo/remotion
npm install
npm run render:all                              # → out/promo-9x16.mp4, promo-1x1.mp4, promo-16x9.mp4

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
npx remotion render PromoVertical out/promo-9x16.mp4 --browser-executable="$BIN"
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
