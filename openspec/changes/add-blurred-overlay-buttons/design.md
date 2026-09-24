## Context

Three surfaces render video, all sharing `:core:video` for playback but each owning its chrome:

| Surface | Module | Chrome | Media3 surface type |
|---|---|---|---|
| Trending video feed | `:feature:videos:impl` | `VideoPageChrome` + `VideoRailAction` | `SURFACE_TYPE_TEXTURE_VIEW` |
| Fullscreen player | `:feature:videoplayer:impl` | `VideoPlayerChrome` | `SURFACE_TYPE_SURFACE_VIEW` |
| Inline timeline autoplay | `:feature:feed:impl` | none (poster + badge) | n/a |

Relevant project constraints:

- **120 Hz is a hard requirement** → an **8.33 ms** frame budget, not 16.67 ms.
- **`minSdk = 28`.**
- **Battery is the top project priority.** The trending feed's `TextureView` is already documented
  in-tree as a deliberate battery cost, "isolated here so a battery pass can flip it back to
  `SURFACE_TYPE_SURFACE_VIEW` as a one-line change."

This design originally specified background blur via Haze. That was reconsidered — D1 records why,
and the surface-type and cost findings that survived the reconsideration are kept in D6 and D7
because they govern the follow-up change.

## Goals / Non-Goals

**Goals:**

- Overlay controls legible against arbitrary video, including a white frame.
- One `:designsystem` component set, so the "control over media" pattern stops diverging.
- A component API that can gain a blur treatment later with **zero call-site churn**.
- A benchmark baseline banked while an un-effected build still exists to measure.
- No new dependency, no per-frame GPU work, no regression to battery posture.

**Non-Goals:**

- Background blur. Deferred to its own change (D1).
- Merging the three video players. Playback is already shared; the chromes differ for real reasons.
- Unifying `VideoRailAction` with `:designsystem`'s `PostStat` (worth doing; churns PostCard
  baselines; separable).
- Restyling MediaViewer's `ChromeBar` or `PostCardVideoEmbed`.

## Decisions

### D1. Ship the scrim; defer blur to its own change

The legibility defect is fully fixed by a scrim. Blur is an aesthetic upgrade on top, and every
route to it today carries a cost this change should not absorb:

| Route | Status | Problem |
|---|---|---|
| Haze `2.0.0-rc01` | prerelease | 2.x API moved through alpha→beta (`hazeEffect`→`hazeBlur`, `blurEnabled` relocated). rc freezes it, but the whole 2.x line is still unreleased. |
| Cloudy | latest stable `0.7.1`; 1.0.0 is alpha01 | Less mature by version semantics. Release notes include "Fix RenderThread crash when capturing a blurred backdrop" and "capture at scale to fix legacy CPU blur scroll ANR". |
| Hand-rolled `rememberGraphicsLayer()` + `BlurEffect` | available on our Compose BOM | ~150–250 lines we own forever, and those Cloudy release notes describe precisely the bug class we would inherit. |
| First-party Compose API | **does not exist** | `Modifier.blur()` blurs a composable's *own* content, not the backdrop. Everyone doing backdrop blur captures a layer and applies `RenderEffect`. |

Deferring costs nothing because of D2: the component API hides the treatment, so the blur change is
additive rather than a refactor. The follow-up should be gated on Haze 2.0 reaching stable, at
which point its cost is one dependency and one file.

*Alternative considered:* adopt Haze rc01 now, isolated behind D2. Genuinely viable — the exposure
really is one file — and rejected only because the scrim delivers the actual requirement and the
blur can arrive later at strictly lower risk. This is a sequencing decision, not a judgement that
Haze is unfit.

*Also considered:* blur a **snapshot** captured when chrome appears rather than every frame. Much
cheaper and visually fine for the fullscreen chrome, which auto-hides after 3 s — but it goes stale
on the trending-feed rail, which is permanently visible over moving video. Worth revisiting in the
blur change as a per-surface option; not a reason to keep blur in this one.

### D2. The public API MUST NOT expose the backing treatment

This is the load-bearing decision and the reason deferring blur is free.

`:designsystem` exposes overlay controls whose public API carries no treatment concept — no scrim
color, no alpha, no blur parameter, no backdrop-source handle. Feature code asks for "a control
over media"; the component decides how to back it.

Consequences, all deliberate:

- The blur change adds a treatment inside one file. No feature module recompiles against a changed
  signature, and no call site is edited.
- A treatment can be varied per API level, or per surface, without leaking that variation outward.
- If a blur backend is adopted and later regresses, reverting is a one-file change.

### D3. One component set, two layouts

Two composables, matching what already exists: an **icon-only** control (fullscreen player's back /
skip / play-pause / mute / PiP) and an **icon-with-count** control stacked vertically (the trending
feed's rail cell).

`VideoRailAction`'s accessibility contract is carried over verbatim rather than redesigned:
`toggleable` + `Role.Switch` with the label as `contentDescription` for **like, repost, bookmark
and mute**; `clickable` + `Role.Button` + `onClickLabel` with a decorative icon for **reply, share
and overflow**; labels always plain nouns, never inverse verbs. That is all **seven** rail cells —
bookmark and overflow are easy to overlook. The contract is already correct and already tested;
this change moves it, it does not revisit it.

### D4. The scrim must independently meet a contrast floor

The scrim is the only rendering that ships here, so it carries the whole legibility requirement:
**4.5:1** between control foreground and treated background, measured against a worst-case white
frame. Tuned and measured, with the ratio recorded in a code comment so a later visual tweak cannot
quietly drop below it.

Colors come from `MaterialTheme` per the design-system spec — no `Color(0xFF…)` literals.

### D5. Benchmark now, even though the scrim is cheap

A scrim adds one draw per control: no capture, no sampling, no per-frame GPU work. The benchmark is
not expected to move, and that is not its purpose. Its purpose is to **bank the baseline while an
un-effected build still exists**. Once blur lands there is no way to reconstruct a before-number,
and an after-number alone cannot attribute a regression.

It also guards the scrim itself against an unexpected regression, which is a cheap bonus rather
than the motivation.

### D6. (Carried forward for the blur change) Surface type decides where blur is even possible

Recorded here because it was discovered during this design and will otherwise be rediscovered
expensively.

**Backdrop blur composites within the Compose/View render tree. A `SurfaceView` is positioned and
drawn by the window compositor in a separate hardware layer, so a backdrop capture samples nothing
from it.**

- **Trending feed** renders into a **`TextureView`** → blur is possible.
- **Fullscreen player** renders into a **`SurfaceView`** → blur is **not** possible. It would blur
  an empty layer.

So even when blur arrives, the fullscreen player keeps the scrim — and that is correct, not a
concession: `SurfaceView` is the right choice there (hardware overlay, no per-frame GPU composite,
better battery, no drag-tracking requirement). D2 means both surfaces use the same component
regardless.

### D7. (Carried forward) The blur change inherits a 120 Hz gate and a battery question

Two constraints the follow-up must answer, recorded now:

- **Frame budget.** Haze's published measurements put `Quality` mode at **1.7 ms** of P90 headroom
  on a Pixel 8a at 60 Hz, and `Balanced` at ~5 ms. Our budget is 8.33 ms. Blur over continuously
  moving video while the user swipes is the documented worst case, and it is exactly the trending
  feed. Start at the cheapest quality mode and treat the benchmark as a veto.
- **Battery coupling.** Blur on the trending feed depends on its `TextureView`, which is documented
  as a reversible battery cost. Adopting blur there quietly spends that reversibility. D2 softens
  it — the component degrades to scrim if the surface flips — but the trade is real and needs an
  explicit call in the blur change.

## Risks / Trade-offs

**The scrim reads as heavier than blur.** → Accepted; it is the point of the trade. D4 fixes a
measurable floor rather than a taste target, and D2 means the upgrade path costs no call-site churn.

**A "temporary" scrim becomes permanent.** → Honest risk. Mitigation is that this is a legitimate
terminal state: the scrim meets the requirement, and is what API-level and surface-type constraints
would force on some devices and surfaces even after blur lands (D6).

**Two adopted surfaces, one abstraction — the component may be under-generalized.** → Two consumers
with genuinely different layouts is a reasonable basis. MediaViewer is deliberately excluded rather
than bent to fit.

**Accessibility regression while moving `VideoRailAction`'s semantics.** → The contract is carried
verbatim (D3) and `VideoFeedTestTagsTest` plus the existing rail tests must pass unchanged. Any
change in TalkBack output is a defect, not an improvement.

## Migration Plan

Additive; no data or API migration. Sequence matters:

1. Benchmark, captured on the **pre-change** build → the baseline number.
2. `:designsystem` component set with the scrim treatment.
3. Adopt in `VideoPlayerChrome`.
4. Adopt in `VideoRailAction`.
5. Re-run the benchmark; confirm the scrim did not move it.

Rollback is per-step: steps 3–4 are call-site swaps, independently revertable.

The follow-up blur change then adds a treatment inside the component from step 2, gated on a stable
implementation, D6's surface-type limit, and D7's budget.

## Open Questions

1. Which device is the benchmark's reference — the plugged-in foldable, or a lower-tier device that
   better represents the floor?
2. Does the scrim want a hairline border or shadow for shape definition against mid-tone content,
   or is fill alone enough? A visual call, to settle against the previews in D4.
3. For the blur change: revisit the snapshot-blur option in D1, which may suit the auto-hiding
   fullscreen chrome even where live blur is unaffordable.
