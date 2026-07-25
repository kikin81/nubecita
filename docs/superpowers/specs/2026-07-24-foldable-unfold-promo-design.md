# Foldable unfold promo video — design

**Date:** 2026-07-24
**bd:** nubecita-i6zk
**Status:** design (approved in brainstorm; awaiting spec review → implementation plan)

## Goal

A slick promo clip built on the existing `promo/` Remotion pipeline: a Pixel
Fold scrolls the Nubecita timeline on its **cover display**, then physically
**unfolds** (3D CSS hinge) to reveal the **list-detail two-pane** on its inner
display. The unfold is the hero moment — the device's hardware gesture maps
directly onto Nubecita's adaptive list-detail layout, so "open the phone" and
"open the post" become the same motion.

This is a marketing asset, not app code. It lives entirely under `promo/` and
changes no shipped module.

## Locked decisions (from brainstorm)

| Decision | Choice | Rationale |
|---|---|---|
| Venue | **Design-once, export two cuts** | One master comp exports a constrained Ads cut *and* a freeform hero cut; venue can be decided after a preview render. |
| Unfold style | **3D CSS hinge unfold** | Physically convincing; pure CSS 3D transforms in Remotion, no external device asset, stays regenerable-from-source. |
| Footage source | **Real bench footage on the Pixel Fold (both displays)** | Authentic per-display aspect ratios and real content continuity. Bench flavor is mandatory anyway (Google Ads rejects the real signed-in feed as political content — see `promo/README.md`). |
| Continuity | **Tight** — settle on post N on the cover, unfold reveals post N's thread in the detail pane | Highest storytelling payoff. Bench determinism makes the cross-display match reliable. |

## Non-goals

- No photoreal device render / 3D model / per-frame screen tracking (rejected in
  brainstorm as asset-dependent overkill).
- No changes to any shipped app module. The only app touchpoint is *capturing*
  the bench build.
- No new heavy dependencies — everything is Remotion + CSS already in
  `promo/remotion`.
- No automated video test suite (validation is visual review + a clean render).

## Architecture

Mirrors the current `promo/` structure (`capture/*.sh` produces clips →
`remotion/src/*` renders them). New and changed files:

```
promo/
  capture/journeys/
    foldable.sh          # NEW — drives bench on the Fold, produces the two clips
  remotion/
    src/
      shared.tsx         # NEW (small refactor) — palette, fonts, CtaOutro
      Foldable.tsx       # NEW — 3D hinge device + unfold timeline + captions
      Promo.tsx          # CHANGED — consume shared.tsx (de-dup palette/fonts/outro)
      Root.tsx           # CHANGED — register Foldable-* compositions
    public/
      fold_cover.mp4     # NEW capture (git-ignored) — outer display, feed
      fold_inner.mp4     # NEW capture (git-ignored) — inner display, two-pane
```

### `shared.tsx` (refactor, in-scope)

`Promo.tsx` currently inlines the brand palette (`NAVY`/`NAVY_2`/`ACCENT`/
`WHITE`), the Fraunces + Inter font loads, and the CTA outro block (Nubecita
wordmark + tagline + Google Play badge). `Foldable.tsx` needs all three. Extract
them into `shared.tsx` and have both comps import them, so there is exactly one
copy of the palette and one CTA outro to maintain. This is a targeted cleanup of
code we're already touching — not a broader refactor.

- `export const PALETTE = { NAVY, NAVY_2, ACCENT, WHITE }`
- `export const FRAUNCES`, `export const INTER` (loaded once)
- `export const CtaOutro: React.FC<{ layout: Layout; startSec: number }>` —
  the fade-in outro, driven by the caller's frame/outro interpolation.

### `Foldable.tsx` (new composition)

A dedicated component — **not** the flat `DeviceFrame` from `Promo.tsx`. Props:
`{ layout: Layout }` plus a small `FoldableJourney` describing the two clips,
the settle/unfold/open beat times, and captions. Structure:

- An `AbsoluteFill` brand-gradient background (same as `Promo`).
- A `perspective` container holding the **device**.
- The device is two half-panels sharing a **center spine** (`transformOrigin`
  at the hinge edge). Each inner half renders half of `fold_inner.mp4`
  (left = feed list pane, right = detail pane). A separate **cover panel**
  renders `fold_cover.mp4` in the folded silhouette.
- Captions reuse the `Sequence` + spring-in pattern from `Promo.tsx`.
- `CtaOutro` from `shared.tsx` at the end.

### `Root.tsx` registration

Register the aspect variants, following the existing `Foo-9x16` naming:

- `Foldable-9x16` (1080×1920) — primary social / Ads.
- `Foldable-16x9` (1920×1080) — web / site hero.
- `Foldable-1x1` (1080×1080) — **optional**, only if the full Ads trifecta is
  wanted (deferred unless requested).

Aspect handling reuses the `geometry()` idea from `Promo.tsx`: the device is
centered and scaled to fit the frame in both its folded (tall) and open
(near-square) footprints.

## Capture choreography (`foldable.sh`)

Bench is deterministic and, per `promo/README.md`, **every** post opens the one
canned thread (Jessica Elena's 4-image gallery). That makes tight continuity
easy: whichever post we settle on, the detail pane shows that same canned thread.

Two recordings, stitched by the hinge animation in Remotion (the two physical
displays can't both be live at once on a Pixel Fold, so this is a **semi-manual**
journey — the operator folds/unfolds on cue):

1. **Cover clip → `fold_cover.mp4`** (device folded, inner display off):
   - Launch bench on the **outer** display.
   - Scroll the feed and settle on **post N** (a fixed index, e.g. the 3rd
     bench post — deterministic).
   - Record the outer display via `adb shell screenrecord --display-id <cover>`.
2. **Inner clip → `fold_inner.mp4`** (device unfolded):
   - Same bench feed in the **left list pane**, scrolled to **post N**.
   - Tap post N → right (detail) pane shows its thread.
   - Record the inner display.

The two feeds line up because the Nth bench post is byte-identical on both
displays.

### Capture traps to handle (from the device-capture memory)

- **Per-display targeting.** `screenrecord`/`screencap` on a foldable must target
  the right display id (`--display-id` / `-d <displayId>`); a wrong pick or a
  warning line can corrupt the output. Resolve the cover vs inner display ids at
  runtime (`adb shell dumpsys display` / `SurfaceFlinger`), don't hardcode.
- **Display OFF.** The display you record may be `state=OFF` (folded → inner off,
  and vice versa). Ensure the target display is on before recording.
- **Heads-up notification stealing the tap.** Enable DND for the capture and use
  uiautomator bounds (not eyeballed coords) for the post-N tap, so a merged
  action-row / incoming notification can't misfire the tap (false pass risk).
- Bench flavor only (`:app:assembleBenchDebug` / `installBenchDebug`) — apolitical,
  offline, deterministic; required for Ads compliance.

## The 3D hinge (the "slick", and the main risk)

Pure CSS 3D in Remotion. The inner display is two half-panels around a center
spine; the whole thing sits in a `perspective` container so `rotateY` reads as
depth.

Timeline (times are the v1 default; tunable in Studio):

- **Folded (0–4s).** Render the **cover-feed panel** in a tall portrait
  silhouette (aspect ≈ 0.5, matching the Fold cover ~2092×1080). Hinge shadow
  down one long edge; the folded slab casts the existing deep drop-shadow.
- **Unfold (4–7s).** The right inner half sweeps `rotateY` from folded-back
  (≈ −170°, hidden behind the left half) to 0° (coplanar). A driven catch-light
  gradient sweeps across the glass during the rotation to sell reflectivity.
  Past ~90° open, crossfade the cover-feed panel **out** and the two inner panes
  **in**, so the reveal reads as "the feed opens up into feed + detail". Lands on
  the near-square inner footprint (aspect ≈ 1.2, matching ~2208×1840).
- **Open (7–11s).** Flat inner two-pane. The detail pane does a subtle settle
  (the thread finishing its entrance). Optional slow push-in for life.

**Risk (called out honestly):** making the hinge/perspective read as a real
Pixel Fold rather than "a box unfolding" is the time sink. It is iterative work
in Remotion Studio (live scrub of `rotateY`, perspective distance, spine shadow,
catch-light), not a one-shot. This is where most of the effort goes; the rest of
the comp is straightforward. The implementation plan should budget an explicit
"tune the hinge in Studio" step with a visual checkpoint.

## Beats & captions (~12–15s)

Reuses the Fraunces + accent-word caption style from `Promo.tsx`.

| Time | Beat | Caption (`lead` / *accent*) |
|---|---|---|
| 0–4s | Cover feed scrolls, settles on post N | "Your whole timeline —" / *in your pocket* |
| 4–7s | **The unfold** (hero moment) | (caption clears for the motion) |
| 7–11s | Inner two-pane; the thread reveals | "Two panes," / *more context* |
| 11–15s | CTA outro (`shared.CtaOutro`) | Nubecita · "A fast, native Bluesky client" · Play badge |

## Outputs & validation

- **v1 render targets:** `Foldable-9x16` + `Foldable-16x9`. `1x1` deferred.
- **Ads cut** stays inside spec (bench footage, aspect box, ≤ the campaign length
  cap). **Hero cut** (16:9) may run a touch longer/looser later — same master
  comp.
- **Validation gate (no unit tests — it's video):**
  1. Both captures exist and show the *same* post N on cover and inner-left pane.
  2. Remotion Studio preview scrubs cleanly through folded → unfold → open.
  3. `npx remotion render` completes for both aspects without dropped frames.
  4. Human visual review of the hinge realism + continuity match.
- The generated `.mp4`s and captured `.mp4` inputs stay **git-ignored** (existing
  `promo/.gitignore` convention — only inputs/source are committed).

## Open questions (resolve during planning, not blocking)

- Exact settle index for post N (pick a bench post whose card is visually
  distinctive so the cross-display match is obvious).
- Whether the hero cut wants a longer open-state dwell than the Ads cut.
- Whether to add `Foldable-1x1` for the full Ads trifecta.
