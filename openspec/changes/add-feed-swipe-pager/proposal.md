## Why

Users have strongly requested the ability to swipe horizontally between feeds on the home screen rather than only tapping top filter chips. In the initial feed switching implementation, a `HorizontalPager` was avoided because of performance concerns (fear of multi-feed layout thrashing, 120Hz scrolling jank, and video playback coordinator conflicts).

Our technical spike demonstrated that `HorizontalPager` with `beyondViewportPageCount = 0` composes only a single active feed when idle, achieving zero overhead during vertical scrolling while delivering fluid horizontal feed swiping. Furthermore, our prototype established clear architectural patterns to protect tablet multi-pane layouts (`ListDetailSceneStrategy`), eliminate video player conflicts, and provide intuitive "exit-through-home" back navigation.

## What Changes

- **Adaptive Horizontal Pager**: Replace the single active feed pane in `FeedHost` with `HorizontalPager(state = pagerState, beyondViewportPageCount = 0)`.
- **Adaptive Swiping Strategy**:
  - On **Compact widths (<600dp, phones)**, `userScrollEnabled = true` enables fluid horizontal feed swiping.
  - On **Medium / Expanded widths (≥600dp, tablets and foldables)**, `userScrollEnabled = false` disables horizontal swiping inside the narrow 412dp/440dp list pane, eliminating touch-slop collisions with `VerticalDragHandle` (`paneExpansionDraggable`) and avoiding multi-pane gesture confusion. Tablet users switch feeds via chip clicks.
- **Hoisted FeedChipRow**: Hoist `FeedChipRow` to `FeedHost` above the pager. Sync pager settlement with `FeedHostViewModel.selectedFeedUri` using `snapshotFlow { pagerState.settledPage }` to eliminate racing and back-navigation bouncing loops.
- **Back Handler ("Exit Through Home")**: Wire `BackHandler(enabled = pagerState.settledPage != 0)` to scroll back to Page 0 ("Following") before exiting the app. Support Android 14+ `PredictiveBackHandler` for interactive back peeking.
- **Video Player Coordinator Gating**: Pass `isPageActive = pagerState.settledPage == page && !pagerState.isScrollInProgress` down to `PostFeedList`. Inactive feeds immediately unbind from `SharedVideoPlayer`.
- **Video Player Engine Hardening**: Update `SharedVideoPlayer` to re-prepare and reset seek position when re-binding the same URL after being idle/ended, and configure emulator-safe MediaCodec fallback to prevent decoder crashes in development environments.

## Non-goals

- Infinite feed swiping or cyclical wrapping between first and last feeds.
- Independent, separate top chip bars per feed pane (chip bar is pinned and shared at `FeedHost`).
- Enabling horizontal swipe gestures inside two-pane tablet layouts.
- Converting pinned lists into permanent, always-visible pager tabs (pinned lists remain accessible via `[ Lists ⌄ ]` disclosure bottom sheet).

## Architecture Baseline Compliance

No deviations from MVI, Compose, Hilt, Room, or Coil baseline. `FeedHostViewModel` maintains MVI state; each feed continues to retain its own `FeedViewModel` keyed by `feedUri` through Hilt.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `feature-feed`: Feed switching in `FeedHost` transitions from single active `SaveableStateHolder` pane to adaptive `HorizontalPager` with `beyondViewportPageCount = 0`, adaptive `userScrollEnabled`, and "exit-through-home" `BackHandler`.
- `feature-feed-video`: `FeedVideoPlayerCoordinator` binds to `SharedVideoPlayer` only when `isPageActive && !listState.isScrollInProgress`, immediately releasing player resources during pager transitions.
- `video-playback-engine`: `SharedVideoPlayer` safely handles same-URL re-binding after completion or error, and configures emulator-safe MediaCodec selectors.

## Impact

- `feature:feed:impl`: `FeedHost.kt`, `FeedScreen.kt`, `PostFeedList.kt`, `FeedVideoPlayerCoordinator.kt`, and feed unit tests.
- `core:video`: `SharedVideoPlayer.kt`.
- No database migrations, network protocol changes, or breaking API changes to other modules.
