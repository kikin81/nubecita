# Vertical video feed: chrome, poster & presentation polish

**bd:** nubecita-zdv8.9 (Slice 3b of epic nubecita-zdv8)
**Date:** 2026-07-19
**Status:** approved

## Context

`:feature:videos:impl` is functionally complete as of Slices 1–5: the data source,
the `VerticalVideoPlaylistPlayer` pool in `:core:video` (pool-of-2, decoder-budget
degrade, analytics, data-saver prewarm gate), `VideoFeedScreen`, the Discover
"Trending Videos" carousel entry, and a perf/analytics pass have all landed.

The screen is deliberately minimal. A single persistent `PlayerSurface` sits behind
a transparent `VerticalPager` that owns the swipe gesture; the surface re-binds to
whichever pooled player is active (`VideoFeedViewModel.activePlayer`) rather than
being recreated per page. That single-surface design fixed a black-first-frame bug
and must not be reverted to per-page surfaces.

This slice is the presentation layer. Three deficiencies it closes:

1. A cold page shows black until the first frame decodes.
2. The video cross-cuts on settle instead of sliding with the swipe gesture.
3. There is no chrome at all — no author, caption, interactions, or mute control.

Blurred letterbox fill (the Reels-style treatment for landscape bars) is explicitly
**out of scope** and gets its own bd issue; it carries a per-frame GPU cost that
needs measuring on its own.

## Decisions

### D1 — TextureView, not SurfaceView

The surface switches from `SurfaceView` to `TextureView`.

`SurfaceView` renders through a hardware overlay, which is the cheaper path for
full-screen video and is why Slice 3a chose it. But a `SurfaceView`'s position is
managed by the window compositor rather than the view hierarchy, so translating it
during a drag can visibly lag the app frame. `TextureView` composites like a normal
view: it translates and alpha-blends exactly in step with everything else.

Accepted cost: full-screen video is composited through the GPU every frame instead
of an overlay, a real battery cost on the surface where users linger longest. This
was chosen with that tradeoff stated. To keep it reversible, the surface type lives
in a **single named constant** in `VideoFeedScreen.kt`, so a later battery pass can
flip back to `SurfaceView` (accepting option A below) as a one-line change.

Alternatives rejected:

- **A — persistent `SurfaceView` offset by pager scroll.** Keeps the overlay path.
  Rejected because SurfaceView position updates can lag the app frame on some
  devices, and the risk was judged not worth carrying.
- **C — cover the video with its own poster for the duration of the drag.** Only
  posters slide, so it is trivially smooth. Rejected: the poster is the *first*
  frame, so swiping mid-playback snaps the image backwards before it slides — a
  visible artifact on every single swipe.

### D2 — The poster renders *over* the video, not under it

The obvious framing is "poster underlay". That is wrong for this hierarchy.

The pager sits **above** the surface, and posters live inside pager pages. So each
page's poster naturally renders *over* the video and fades **out** to reveal it. The
visual result is identical to an underlay, but nothing has to reach around the pager
and the surface never moves in the hierarchy.

Alpha rule per page `i`:

- `1f` by default.
- Animates to `0f` (~150ms tween) when `i` is the settled page **and**
  `rememberPresentationState(player).coverSurface` has gone false.

Consequences: a cold page is never black, it is the poster; and the handoff between
outgoing video → poster → incoming video is covered at every instant, because the
settled page's poster is fully opaque and full-screen at the moment the pool
promotes the next player.

### D3 — Displacement is measured against `settledPage`

The surface translates via `Modifier.graphicsLayer { translationY = … }`, computed
from the pager's current scroll position **relative to `settledPage`** — the page
whose player is actually loaded.

`currentPage` must not be used: it flips at the halfway point of a drag, which would
snap the video sideways mid-gesture.

The translation is read inside the `graphicsLayer` lambda (a deferred read), so a
swipe re-runs only the layer block — never composition or layout. This is what keeps
the gesture at 120hz.

### D4 — Poster and video must share identical bounds

The poster is sized from `EmbedUi.Video.aspectRatio`, which is known before any
decode happens, and is placed inside the same aspect-ratio-locked, centered box as
the video surface.

If the poster and the first frame land on even slightly different bounds, the
crossfade reads as a jump rather than a dissolve. This alignment is the single
correctness requirement of the poster layer.

Missing `posterUrl` falls back to a flat black fill (not the play-badge
`VideoPosterEmbed`, which carries affordances this surface does not want).

### D5 — Interaction delegation, per the sanctioned exception

`VideoFeedViewModel` implements `PostInteractionHandler by handler` with an injected
`DefaultPostInteractionHandler`, and calls `handler.bind(PostSurface.Videos, viewModelScope)`
in `init` — the pattern documented in CLAUDE.md and implemented by `FeedViewModel`.

Two points where this surface differs from Feed:

- It consumes `interactions.tapMarkers` **directly** from `rememberPostInteractions`
  and does **not** mirror them into `UiState`. That mirror is a Feed-only migration
  artifact kept to preserve committed screenshot baselines; CLAUDE.md directs new
  surfaces to skip it.
- The VM **keeps** its own `postInteractionsCache.state → items` read-merge and seed,
  so like/repost counts stay live. The handler owns writes and tap markers only.
  Dropping this merge is a known regression shape from an earlier slice.

Cross-module edit required: **`PostSurface.Videos("videos")` must be added** to the
enum in `:core:analytics/AnalyticsEvent.kt`. No such variant exists today.

### D6 — Chrome layout: right rail

Reels/TikTok idiom — a vertical action column on the right edge (avatar, like,
repost, reply, share), author and caption bottom-left, mute toggle and progress on
the bottom edge, back affordance top-left. Chosen over a PostCard-style bottom
action row because it maximizes uncovered video area and matches format convention.

### D7 — Gesture arbitration

`detectTapGestures(onTap = togglePlayPause, onDoubleTap = like)` on the page content.
This does not consume drags, so the pager gesture is unaffected.

Known inherent cost: supplying `onDoubleTap` makes `onTap` wait out the ~300ms
double-tap timeout, so pause registers slightly late. This is unavoidable with a
single tap surface serving both gestures, and matches how Reels behaves.

Double-tap **only ever likes, never unlikes**. A mistimed double tap on an
already-liked post is a no-op rather than a silent undo.

### D8 — Progress bar is draw-phase only

A `withFrameNanos` loop writes player position into a `MutableFloatState` that only a
`drawBehind` block reads. The draw phase re-runs each frame; composition does not.
The loop runs for the active page while playing, and only then.

Read-only — no scrubbing in this slice.

## Architecture

Z-order, bottom to top:

1. Black scaffold canvas.
2. One persistent `PlayerSurface` (`TextureView`), aspect-ratio-locked, centered,
   translated per D3.
3. The transparent `VerticalPager`. Pages now carry content: poster (D2) + chrome (D6).

### Files

| File | Role |
|---|---|
| `VideoFeedScreen.kt` | Stateful host: VM wiring, lifecycle, pager, surface, effect collection |
| `ui/VideoFeedPage.kt` | **Stateless** page — poster + chrome. Takes `player: Player?` |
| `ui/VideoPageChrome.kt` | **Stateless** right rail, author/caption, mute, progress |
| `ui/VideoFeedInteractions.kt` | `rememberVideoFeedInteractions`, mirroring `FeedInteractions.kt` |

`VideoFeedPage` accepting a **nullable** player is load-bearing for testing: layoutlib
cannot construct a player surface, so screenshot tests pass `null` and the composable
skips the surface while still rendering poster and chrome.

### Contract changes

- `VideoFeedEffect.NavigateTo(target: NavKey)` — author profile and post detail. The
  screen collects it and calls `LocalMainShellNavState.current.add(target)`. Per
  CLAUDE.md the VM never injects the nav state holder.
- `VideoFeedEvent.TogglePlayPause`.

## Error handling

Interaction failures route through the handler's own `interactionEffects`, collected
by `rememberPostInteractions` — the VM must **not** forward them onto its own effect
channel. Existing `VideoFeedStatus.Error` and retry behaviour are unchanged.

A missing poster degrades to black fill (D4); a poster that fails to load leaves the
video to present on its own — neither is an error state.

## Testing

- **Screenshot** (`src/screenshotTestDebug/reference/`, `NubecitaCanvasPreviewTheme`,
  pinned `Instant`s for determinism): portrait, landscape letterbox, long caption,
  missing-poster fallback, muted and unmuted — light and dark.
- **Unit**: new events and effects, the cache read-merge, and that `bind` is called
  with `PostSurface.Videos`.
- **Manual**: bench smoke (`./gradlew :app:installBenchDebug`) plus real visual
  capture on the Pixel 10 Pro XL for the slide and the crossfade. No automated test
  can assert either — the emulator's goldfish decoder cannot render these clips.

~17 new string keys × 3 locales (13 of them `InteractionStrings`).

Accessibility labels follow `PostStat`'s split, which depends on whether the cell is
a toggle:

- **One-shot actions** (reply, share, overflow): `Modifier.clickable(role = Role.Button,
  onClickLabel = …)` — the label is the action verb.
- **Toggles** (like, repost, and the mute control): `Modifier.toggleable(value, role =
  Role.Switch, onValueChange = …)` — the label is the static **noun** being toggled
  ("Like", "Mute"), never the inverse verb ("Unlike", "Unmute"), because the on/off
  state is announced by the switch semantics. `onClickLabel` does not exist on the
  toggleable path and would be silently dropped.

Note this means the mute control does **not** copy `VideoPlayerChrome`'s pattern of an
`IconButton` with a `contentDescription` swapped between "Mute"/"Unmute". That surface
predates the toggle convention; this one follows `PostStat`.

## Delivery

Three PRs, each independently shippable and visually verifiable:

1. **Presentation** — TextureView, slide, poster crossfade.
2. **Chrome + interactions** — right rail, author/caption, mute, `PostSurface.Videos`,
   strings, cache merge, nav effects.
3. **Gestures + progress** — tap-to-pause, double-tap-to-like, Choreographer progress.

## Risks

| Risk | Mitigation |
|---|---|
| TextureView battery cost (D1) | Surface type behind one constant; revisit under a battery pass |
| Poster/video bounds mismatch (D4) | Shared aspect-ratio box; verify visually on device |
| Tap-to-pause 300ms latency (D7) | Inherent; matches format convention |
| Dropping the cache read-merge (D5) | Explicit unit test; this has regressed once before |
