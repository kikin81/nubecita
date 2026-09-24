## Why

Controls drawn over video are currently invisible against bright content. `VideoRailAction`
(the trending video feed's like/repost/reply/share/mute rail) renders a white icon and a white
count with **no background at all**. Over a bright frame — a daylit race track, snow, a white
studio — it disappears entirely.

`VideoPlayerChrome` has the same problem in two grades. Its back, mute and pop-out controls are
bare `IconButton`s tinted `Color.White` with no backing at all. Its skip ±10s buttons *do* carry a
backing — `translucentSkipColors()` = `Color.White @ 0.16 alpha` — but it is too faint to read
against a bright frame, which is exactly what `nubecita-6rdb.12` records. So the skip buttons are
an under-strength treatment rather than a missing one, and this change supersedes that issue by
giving every control in the chrome one properly-measured treatment instead of hand-tuning a single
alpha value.

The same problem has already produced divergence: `VideoRailAction`'s own KDoc records that
`:designsystem`'s `PostStat` "is `internal` and lays out horizontally, so it cannot be reused
here", so the app now carries two unrelated implementations of "a post-action control" and a
third media surface with no shared styling at all. Fixing legibility and removing that
duplication are the same task, and both land in `:designsystem`.

This change ships the **tinted scrim** treatment only. Background blur — the look this work
started from — is deliberately deferred to a follow-up change. The reasoning is in design.md D1,
but in short: the scrim fully solves the stated legibility problem, costs no new dependency and
effectively no frame time, while every available blur implementation today is either prerelease
with a moving API or a non-trivial piece of graphics code to own. The component API is designed so
blur can be added later **without touching a single call site**.

## What Changes

- Add an **overlay control set** to `:designsystem`, covering the two layouts already in the app:
  an icon-only control and an icon-with-count control stacked vertically.
- Back those controls with a **tinted scrim** that holds a legibility floor against any frame,
  including a fully white one.
- **The public API carries no backing-treatment concept.** Callers describe what the control *is*,
  never how it is backed. This is the load-bearing decision: it is what lets a later change swap
  in blur behind the same API.
- Adopt the new components in **`VideoRailAction`** (`:feature:videos:impl`) and
  **`VideoPlayerChrome`** (`:feature:videoplayer:impl`).
- Add a **Macrobenchmark covering the trending video feed with chrome visible**, and capture it on
  the pre-change build. The scrim is not expected to move the number; the point is to bank the
  baseline now, while an un-effected build still exists to measure.

**No new dependency.** Haze was evaluated and is not adopted here — see design.md D1.

Not in scope, deliberately: background blur (its own change, gated on a stable implementation),
MediaViewer's `ChromeBar` (already legible behind a 45 % black scrim), `PostCardVideoEmbed` (no
chrome to restyle), unifying `VideoRailAction` with `PostStat` (worth doing, but it churns
PostCard's screenshot baselines and is separable), and any merge of the three video players
(their playback layer is already shared via `:core:video`; only the chrome differs, and it differs
for good reason).

## Capabilities

### New Capabilities

- `media-overlay-controls`: Controls rendered on top of media. Covers the `:designsystem`
  component contract, the contrast and accessibility floor the controls must hold against
  arbitrary video, and the rule that the backing treatment stays an implementation detail so it
  can change without reaching call sites.

### Modified Capabilities

- `benchmark-macrobenchmark`: adds a requirement for a video-overlay frame-timing benchmark, and
  the rule that a surface adopting a per-frame GPU effect must carry a before/after measurement
  rather than being adopted unmeasured.
- `design-system`: extends "feature code MUST NOT hard-code theme values" to cover overlay
  controls — feature modules must consume the shared overlay components rather than hand-rolling
  a bare `IconButton` tinted `Color.White` over media.

## Impact

**Dependencies.** None added. This was the main change of direction: an earlier draft pinned
`dev.chrisbanes.haze:haze:2.0.0-rc01`, and it is dropped.

**Modules.**

| Module | Change |
|---|---|
| `:designsystem` | new overlay component set, previews, screenshot baselines |
| `:feature:videos:impl` | `VideoRailAction` delegates to the DS component |
| `:feature:videoplayer:impl` | `VideoPlayerChrome` buttons delegate to the DS component |
| `:benchmark` | new video-overlay benchmark |

**Screenshot baselines.** `:designsystem` gains new baselines. `:feature:videos:impl` and
`:feature:videoplayer:impl` baselines change where controls gain a backing. All of these capture
the scrim, which the host renders faithfully — so unlike a blur treatment, the baselines here are
true evidence of the shipped appearance.

**Risk.** Low. The scrim is an extra draw per control with no capture, no sampling and no
per-frame GPU work; the plausible failure is aesthetic (too heavy, too light) rather than
performance. The benchmark exists mainly to bank a baseline for the blur change that follows.

**Not affected.** Playback, caching, PiP and audio focus are untouched — this change does not
reach into `:core:video`.
