## Context

Nubecita's home screen provides feed switching across pinned feeds (Following, Discover, custom algorithmic feeds, and pinned lists). In the initial implementation (`add-feed-switching`), `HorizontalPager` was avoided because of performance and multi-pane scene strategy concerns; instead, a single active `FeedPane` was composed in `SaveableStateHolder`.

A prototype spike (`spike/feed-swipe-pager`) confirmed that a modern `HorizontalPager` with `beyondViewportPageCount = 0` provides fluid 120Hz swiping on phones with zero idle composition overhead. However, it surfaced two critical invariants:
1. In two-pane tablet layouts (`ListDetailSceneStrategy`), horizontal dragging on the 412dp list pane conflicts directly with `VerticalDragHandle` (`paneExpansionDraggable`).
2. Two feed pages composed during a swipe must not contend for the single shared ExoPlayer instance (`FeedVideoPlayerCoordinator` / `SharedVideoPlayer`).
3. Back navigation from secondary feeds must exit through home rather than popping the Nav3 backstack or terminating the app.

## Goals / Non-Goals

**Goals:**
- Provide smooth, fluid horizontal swiping between feeds on compact screens (phones).
- Prevent touch-slop conflicts with the tablet pane expansion drag handle (`VerticalDragHandle`) on medium/expanded screens.
- Guarantee that video autoplay is active only on the settled, active feed page, and that adjacent off-screen pages release playback resources.
- Guarantee intuitive back navigation ("exit through home") from secondary feeds to Page 0 (Following).
- Eliminate race conditions between pager gestures and ViewModel selection state via `snapshotFlow { pagerState.settledPage }`.

**Non-Goals:**
- Allowing horizontal gestures to resize panes and swipe feeds simultaneously on tablets.
- Supporting continuous loop/infinite wrapping across feed boundaries.
- Hoisting individual scroll positions into a single shared list state.

## Decisions

### Decision 1: Adaptive Horizontal Pager Swiping
- **Choice**: Enable `userScrollEnabled = true` only on compact widths (`<600dp`). Set `userScrollEnabled = false` on medium/expanded widths (`≥600dp`).
- **Rationale**: In two-pane tablet mode, `FeedHost` occupies a narrow list pane (412dp/440dp) immediately adjacent to `VerticalDragHandle`. Allowing horizontal swipe inside this pane causes severe gesture ambiguity and accidental pane resizing. On tablets, users switch feeds cleanly via chip clicks with animated pager scrolling.
- **Alternatives Considered**:
  - *Universal Pager with Edge Padding*: Fails because the entire 412dp pane is within thumb reach of the divider; user intent remains ambiguous.
  - *No Pager on Tablet (divergent UI trees)*: Increases maintenance burden; keeping `HorizontalPager` with `userScrollEnabled = false` shares identical state and animation mechanics across all form factors.

### Decision 2: State Synchronization via `settledPage`
- **Choice**: Use `snapshotFlow { pagerState.settledPage }.distinctUntilChanged()` to synchronize pager navigation to `FeedHostViewModel.handleEvent(FeedHostEvent.SelectFeed)`.
- **Rationale**: `pagerState.currentPage` updates mid-gesture (at the 50% scroll boundary). If a user crosses 50% while dragging, an effect reading `currentPage` with an `isScrollInProgress` guard drops the event. When navigating to detail and returning, the desync causes an animated snap back to the old page. `settledPage` emits strictly after the swipe motion has finished.
- **Alternatives Considered**:
  - *Immediate sync on `currentPage`*: Causes rapid ViewModel updates during drags and breaks when gestures are cancelled.

### Decision 3: "Exit Through Home" Back Navigation
- **Choice**: Wire `BackHandler(enabled = pagerState.settledPage != 0)` to execute `pagerState.animateScrollToPage(0)`.
- **Rationale**: Bluesky feeds are secondary views under the Home tab. When a user swipes to Discover or a custom feed, pressing Back or swiping the screen edge should return to their primary Following timeline before exiting the app or popping tab navigation.

### Decision 4: Single Video Player Coordinator Gating
- **Choice**: Pass `isPageActive = pagerState.settledPage == page && !pagerState.isScrollInProgress` to `PostFeedList`.
- **Rationale**: Nubecita enforces a single-player invariant (`FeedVideoPlayerCoordinator` -> `SharedVideoPlayer`). If an off-screen or animating page attempts to bind the most visible video, it disrupts audio and can hijack the player surface. Gating on `isPageActive` ensures unbinding occurs the moment a swipe begins.

## Risks / Trade-offs

- **[Risk] Memory usage when multiple feeds are visited** → Each visited feed retains its `FeedViewModel` and scroll position. `beyondViewportPageCount = 0` ensures off-screen pages are not kept composed in the layout hierarchy when idle.
- **[Risk] Gesture collision between horizontal swipe and post media carousels** → Nested horizontal scrollables in Compose automatically prioritize the inner carousel when dragging horizontally over multi-image posts, and bubble to the parent pager when the carousel reaches its edge.
- **[Risk] Emulator video decoding crashes during swipe** → `SharedVideoPlayer` enforces `forceDisableMediaCodecAsynchronousQueueing()` and selects Google software decoders on emulator environments.

## Open Questions

None. All architectural decisions were validated against live phone and tablet emulators during the spike prototype.
