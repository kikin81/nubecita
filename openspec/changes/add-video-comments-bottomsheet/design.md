## Context

Nubecita supports video consumption in two distinct surfaces:
1. `:feature:videos:impl` (`VideoFeedScreen`): Vertical snapping feed on `VerticalPager` with a persistent single `PlayerSurface` translated across reel items via `graphicsLayer`.
2. `:feature:videoplayer:impl` (`VideoPlayerScreen`): Dedicated media player with M3 Expressive controls, wavy seek bar, and PiP support.

In both surfaces, tapping the comment action currently dispatches `ComposerRoute(replyToUri = videoUri)` directly. This causes total context loss and disrupts video consumption. We need an adaptive comments sheet supporting continuous 120Hz video playback, nested gesture arbitration, adaptive side-panel presentation on tablets/foldables, and a phased comment composer.

## Goals / Non-Goals

**Goals:**
- Provide a non-disruptive, continuous-playback comments viewing experience over active video.
- Resolve vertical scroll gesture conflicts between the comments sheet and `VerticalPager`.
- Prevent race conditions during pager page transitions and PiP entry snapshots.
- Deliver adaptive layouts: bottom sheet for `WindowWidthSizeClass.Compact`, and zero-scrim side sheet / two-pane split for `WindowWidthSizeClass.Medium` & `Expanded` (constrained width, scoped IME).
- Implement Phase 1 docked `"Add a comment..."` pill launching `ComposerRoute` with clean state restoration on return and real-time comment count sync.
- Coordinate with playback lifecycle, PiP auto-collapse, and predictive back gestures.

**Non-Goals:**
- Inline text composition and attachment pickers directly within the sheet in Phase 1 (deferred to Phase 2 headless extraction).
- Deeply nested recursive comment replies inside the bottom sheet (replies deeper than 1 level navigate to `PostDetailRoute`).
- PiP playback showing comments (PiP is strictly video-only).

## Decisions

### D1 — Gesture arbitration via settled-state pager gating and custom `NestedScrollConnection`
- **Choice**:
  1. **Settled-State Pager Gating**: Gate `VerticalPager(userScrollEnabled = isSheetSettledHidden)` where `isSheetSettledHidden = sheetState.currentValue == SheetValue.Hidden && !sheetState.isAnimationRunning`. `userScrollEnabled` MUST remain `false` during any opening, dragging, or closing animation to prevent mid-transition flings from triggering pager swipes.
  2. **Page-Settling Invariant**: Tapping the comment CTA is a strict no-op if `pagerState.isScrollInProgress` or `pagerState.settledPage != page`. This guarantees the sheet only binds to a fully settled video item and prevents layout tearing.
  3. **Nested Scroll Connection**: Within the sheet, wire a custom `NestedScrollConnection` that prioritizes expanding the sheet from `PartiallyExpanded` to `Expanded` on upward drag, delegates to the comments `LazyColumn` for content scroll, and only collapses the sheet downward when the `LazyColumn` is at scroll position 0 (`firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0`).
- **Rationale**: Completely isolates reel paging from sheet manipulation and eliminates touch-event contention.
- **Alternatives Considered**:
  - *Gating on `targetValue == Hidden`*: Rejected; leaves a ~200-300ms window during the exit spring where late multi-touch flings the parent pager.

### D2 — Adaptive layout branching on `WindowWidthSizeClass` with scoped IME and hinge awareness
- **Choice**:
  - `WindowWidthSizeClass.Compact` (< 600dp): Bottom sheet overlaying the lower half of the screen (`PartiallyExpanded` ~50%, `Expanded` ~90%).
  - `WindowWidthSizeClass.Medium` / `Expanded` (>= 600dp / 840dp, or Landscape): Two-pane split layout (using M3 `SupportingPaneScaffold` or adaptive two-pane row). Left pane renders unscaled aspect-fit video; right pane renders comments constrained between `360dp` and `440dp` (default `400dp`).
  - **Scoped IME Handling**: In two-pane mode, `WindowInsets.ime` padding is applied strictly to the right pane container. The left video pane is never re-measured or squeezed when the soft keyboard appears.
  - **Zero Scrim**: Side sheet mode has no dimming scrim. Video controls remain 100% interactive.
- **Rationale**: Maximizes screen real estate on tablets/foldables without distorting the video viewport.
- **Alternatives Considered**:
  - *Window-wide IME resize*: Rejected; would compress the video viewport horizontally or vertically while typing.

### D3 — Continuous playback, looping invariant, and layout-phase surface geometry
- **Choice**: ExoPlayer playback is never paused when the comments sheet opens, closes, or resizes. The persistent `PlayerSurface` remains attached.
  - **Looping & Auto-Advance Invariant**: The active video continues to loop seamlessly while comments are open. If auto-advance to next video is enabled in user settings, auto-advance is suspended while `isCommentsSheetOpen == true`.
  - **Layout Phase Sizing**: Sizing adjustments during sheet expansion run purely in the Compose layout/draw phase (`graphicsLayer` + layout offsets).
- **Rationale**: Keeps playback at 120Hz without dropped frames or black surface flashes.

### D4 — Instant PiP snap-to-hidden and hierarchical back handling
- **Choice**:
  1. **Instant PiP Snap**: When entering PiP (`rememberIsInPipMode()`), bypass animated dismissal and perform an immediate state drop (`sheetState.snapTo(Hidden)` or conditional non-composition `if (!isInPip) { CommentsSheet(...) }`).
  2. **Hierarchical Back Handler**: Compose `BackHandler(enabled = isCommentsSheetOpen)` intercepts back presses to dismiss the sheet first before popping the video screen.
- **Rationale**: Animated sheet exit during `onUserLeaveHint` (home gesture) races against OS PiP capture, risking a half-collapsed sheet captured into the PiP window. Instant snapping guarantees a clean video frame.

### D5 — Phased composer rollout with optimistic cache + SWR revalidation
- **Choice**:
  - **Phase 1**: Render a docked pill `[Avatar] "Add a comment..." [Send]` inside the sheet. Tapping launches `ComposerRoute(replyToUri = videoUri)`.
  - **State Restoration**: If user navigates back without posting, restore sheet and scroll position with no mutations. If user posts, optimistically append the new reply via `:core:post-interactions`, immediately increment the parent video's comment count badge on the action rail, and trigger silent background revalidation (Stale-While-Revalidate).
  - **Phase 2**: Extract headless composer logic (`:core:composer-engine`) to allow typing inline directly in the sheet.
- **Rationale**: Keeps v1 delivery focused while establishing an instant, flicker-free feedback loop for user comments.

## Risks / Trade-offs

- **[Risk] Pager touch events race with sheet close animation** → Mitigation: Gate `userScrollEnabled` on `isSheetSettledHidden`, verifying both `currentValue == Hidden` and `!isAnimationRunning`.
- **[Risk] PiP capture snapshot captures collapsing sheet** → Mitigation: Instant `snapTo(Hidden)` / conditional composition bypass on `isInPip`.
- **[Risk] Heavy thread fetching stutters video playback** → Mitigation: Run `PostThreadRepository.getPostThread()` asynchronously on `Dispatchers.IO` and project to immutable UI models before emitting to `UiState`.
- **[Risk] Inset collision with gesture navigation bar** → Mitigation: Apply `.navigationBarsPadding()` directly to the docked comment pill and test on 3-button and gesture navigation modes.

## Migration Plan

1. Scaffold adaptive `VideoCommentsSheetScaffold` in `:designsystem` behind unit tests.
2. Build comments thread UI with empty/error states and Phase 1 docked pill in `:feature:comments`.
3. Wire into Reel Player (`:feature:videos`) and Feed Detail Player (`:feature:videoplayer`), incorporating settled-state guards.
4. Validate 120Hz frame pacing and instant PiP collapse with Macrobenchmarks.
5. Land Phase 2 headless composer spike (`nubecita-76q8.9`) as a follow-up.

## Open Questions

- **Verdict on optimistic cache vs. refresh**: Resolved — adopt **optimistic cache insertion + silent background revalidation** (SWR). The new comment appears instantaneously without jumpy scroll resets, while fresh replies from other users stream in quietly.
