# Video Feed Playback Progress Bar (D8) — Design

**bd:** nubecita-zdv8.14 (child of epic nubecita-zdv8 — Vertical video feed)
**Supersedes/implements:** decision D8 in `docs/superpowers/specs/2026-07-19-video-feed-chrome-design.md`, deferred out of Slice 3b PR3.

## Goal

A thin, read-only playback progress line at the foot of the vertical video feed,
reflecting the active clip's position. Read-only in this slice — no scrubbing.

## Placement & appearance

- One **screen-level** instance over the pager (not per-page), aligned
  `BottomCenter` inside the feed content `Box`.
- **Inset + rounded:** ~3dp tall, ~16dp horizontal inset margin, fully rounded
  caps. An ~8dp lift off the content bottom, below the caption. (No
  `navigationBarsPadding()`: the `Scaffold` already insets the feed content by
  `innerPadding`, which includes the nav-bar inset — adding it again double-counts
  and shoves the bar up into the author/caption block. Verified on device.)
- On the always-black feed canvas: a **translucent-white track** with a
  **solid-white fill** that grows left→right to the current fraction.
- Visible while paused too — it simply stops advancing (the driver loop only
  runs while playing; the last drawn value stays).

## Architecture

Three units, mirroring the established LikeBurst / poster-crossfade split so the
visual layer is stateless and screenshot-testable while the frame driver is
isolated:

### 1. `progressFraction(positionMs: Long, durationMs: Long): Float` (pure)

Lives in `feature/videos/impl/.../ui/VideoFeedPresentation.kt` beside the other
pure presentation helpers.

- Returns `0f` when `durationMs <= 0` — covers an unprepared player
  (`Player.duration == TIME_UNSET == -1`) and a zero duration (no divide-by-zero,
  no `NaN`/`Infinity` reaching the draw).
- Otherwise `(positionMs.toFloat() / durationMs).coerceIn(0f, 1f)` — the coerce
  guards a `currentPosition` transiently past `duration` at a loop boundary.

Unit-tested directly.

### 2. `VideoProgressBarContent(progress: () -> Float, modifier: Modifier)` (stateless)

- Renders the track + fill via a single `Modifier.drawBehind { … }` block that
  reads `progress()`.
- The read is **deferred** (a `() -> Float` invoked inside the draw lambda), so
  an advancing bar re-runs only the **draw phase** — never composition or layout.
  This is the same deferred-read discipline as `VideoFeedPage`'s poster alpha and
  `LikeBurst`.
- Draws a rounded track across the full inset width, then a rounded fill from the
  start to `progress()` of the width. Fill width `0f` draws nothing; `1f` fills
  the track.
- Carries the `VideoFeedTestTags.PROGRESS_BAR` test tag.

### 3. `VideoProgressBar(player: Player?, isPlaying: Boolean, modifier: Modifier)` (driver)

- Owns `val progress = remember { mutableFloatStateOf(0f) }` (a `MutableFloatState`).
- A `LaunchedEffect(player, isPlaying)` runs the frame loop **only when**
  `player != null && isPlaying`:

  ```kotlin
  LaunchedEffect(player, isPlaying) {
      if (player != null && isPlaying) {
          while (true) {
              withFrameNanos { }                       // suspend to the next frame
              progress.floatValue = progressFraction(
                  player.currentPosition,
                  player.duration,
              )
          }
      }
  }
  ```

- `withFrameNanos` naturally pauses when the app is backgrounded (no frames are
  produced), so the loop costs nothing off-screen.
- When `isPlaying` flips false or `player` becomes null, the effect cancels and
  the last `progress.floatValue` remains drawn (a static bar under pause).
- Calls `VideoProgressBarContent(progress = { progress.floatValue }, …)`.

## Data flow / wiring

In `VideoFeedScreen`, inside the `VideoFeedStatus.Content` branch's feed content
`Box` (the same `Box` that holds the surface and the `VerticalPager`), add
`VideoProgressBar` as the last child so it draws above the pager:

```kotlin
VideoProgressBar(
    player = activePlayer,
    isPlaying = !state.isPaused,
    modifier = Modifier.align(Alignment.BottomCenter),
)
```

- `activePlayer` and `state.isPaused` already exist in the screen; no new VM
  state, no new effect, no new event.
- **Position/duration come from the player, never `EmbedUi.Video.durationSeconds`.**
  The bench fixture declares `durationSeconds = 8` while the bundled clips run
  14–15s; metadata-driven progress would fill to 100% at ~55% of the clip and then
  sit pinned. Real posts can carry equally wrong metadata.
- **Loop reset is automatic.** Clips use `REPEAT_MODE_ONE`; on a loop the player
  wraps `currentPosition` back to ~0, and because the bar recomputes the fraction
  from the live position each frame (never accumulating), the next frame reflects
  the reset. There is nothing to clear or special-case.

## Testing

- **Unit** (`VideoFeedPresentationTest`): `progressFraction` for unprepared
  (`durationMs = -1` / `TIME_UNSET` → `0f`), zero duration (`0` → `0f`),
  mid-clip (e.g. `5000 / 10000` → `0.5f`), full (`10000 / 10000` → `1f`), and an
  over-run past duration (`11000 / 10000` → `1f`).
- **Screenshot**: a stateless `VideoProgressBarContent` fixture at fractions
  `0f`, `0.4f`, and `1f`, on the black canvas. layoutlib cannot run
  `withFrameNanos`, so only the stateless visual is captured — the driver is not
  exercised in screenshots. Baselines must render three visibly distinct fills
  (verify by hashing, per the "baselines must discriminate" lesson).
- **Device (Pixel Fold, required):** enter the immersive feed via the Discover
  tab's Trending Videos carousel; capture two frames a known interval apart and
  confirm the fill's x-extent has grown (a moving bar cannot be verified by a
  single eyeball capture). Confirm the bar resets rather than sticking at full
  when a short clip loops.

## Non-goals (this slice)

- No scrubbing / seek (drag-to-seek is a separate future slice).
- No buffered-range (secondary progress) indicator.
- No time labels.
- No VM state or events — the bar is a pure projection of the existing
  `activePlayer` + `isPaused`.

## Files

- Modify: `feature/videos/impl/.../ui/VideoFeedPresentation.kt` (add `progressFraction`).
- Create: `feature/videos/impl/.../ui/VideoProgressBar.kt` (`VideoProgressBar` + `VideoProgressBarContent`).
- Modify: `feature/videos/impl/.../VideoFeedScreen.kt` (wire the bar into the content `Box`).
- Modify: `feature/videos/impl/.../VideoFeedTestTags.kt` (add `PROGRESS_BAR`).
- Modify: `feature/videos/impl/.../ui/VideoFeedPresentationTest.kt` (add `progressFraction` cases).
- Create: screenshot test for `VideoProgressBarContent`.
