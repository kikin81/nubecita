## Why

When viewing videos in Nubecita — whether in the full-bleed Vertical Reel Feed (`:feature:videos`) or the Feed Detail Player (`:feature:videoplayer`) — tapping the Comment button currently navigates immediately to the full-screen `ComposerRoute`. This forces an abrupt stop to video playback, discards playback context, and prevents viewers from reading the existing conversation or author comments before participating.

Modern social video experiences (TikTok, Instagram Reels, YouTube Shorts) keep the video playing ambiently while viewers read and write comments. We need an adaptive, non-disruptive comments experience that holds 120Hz scrolling, prevents vertical gesture conflicts with `VerticalPager`, adapts cleanly between phones, foldables, and tablets, and introduces a docked comment action that prepares for inline commenting without initial scope creep.

## What Changes

- **Adaptive Comments Container**:
  - **Compact Window (Phones)**: Renders as an anchored Material 3 bottom sheet (`PartiallyExpanded` ~50% detent, `Expanded` ~90% detent, `Hidden`).
  - **Medium & Expanded Windows (Tablets / Foldables / Landscape)**: Transitions to a zero-scrim **Side Sheet / Two-Pane layout** using M3 `SupportingPaneScaffold` (unscaled video on the left, 360–440dp comments panel on the right) with concurrent video controls and scoped `WindowInsets.ime`.
- **Nested Scroll & Gesture Arbitration**:
  - `VerticalPager` disables `userScrollEnabled` whenever the comments sheet is open or animating (strictly gated on `isSheetSettledHidden`).
  - Comment CTA tap is gated on `pagerState.settledPage == page && !pagerState.isScrollInProgress` using the active filtered list.
  - A custom `NestedScrollConnection` ensures vertical list dragging does not bleed into the reel pager and only collapses the sheet once the comments list is scrolled to position 0.
- **Continuous Playback & Lifecycle Coordination**:
  - Video and audio continue uninterrupted during sheet open, drag, and dismiss. Video loops continuously; feed auto-advance is suspended while comments are open.
  - Entering Picture-in-Picture (PiP) instantly drops the comments sheet state to `Hidden` (no animation delay) to prevent capture in the PiP window snapshot.
  - Predictive Back via `PredictiveBackHandler` animates sheet translation smoothly before dismissing.
  - Video player chrome auto-fade timer is suspended while the initial comments thread loading indicator is active.
- **Cross-Shell Composer Navigation & Hydration**:
  - Registers `ComposerRoute` in `@OuterShell` in addition to `@MainShell`, enabling both reel and detail players to push the composer.
  - On submit, immediately increments parent post's `replyCount` badge on the action rail and triggers instant targeted hydration via `PostThreadRepository.getPostThread(uri)`.
- **Supersedes Active Change**:
  - Supersedes decisions D5 & D7 and tasks 8–9 of the active `revamp-fullscreen-video-player` change, unifying the comments architecture across both video surfaces.

## Non-Goals

- Inline text composition and attachment pickers directly within the sheet in Phase 1 (deferred to Phase 2 headless extraction).
- Deeply nested recursive comment replies inside the bottom sheet (replies deeper than 1 level navigate to `PostDetailRoute`).
- PiP playback showing comments (PiP is strictly video-only).

## Capabilities

### New Capabilities
- `video-comments-sheet`: Adaptive video comments bottom sheet and side sheet container, gesture arbitration against vertical pagers, thread resolution via `PostThreadRepository`, and docked comment composer launching.

### Modified Capabilities
- `feature-feed-video`: Comment button on the reel action rail opens the comments sheet instead of immediately pushing `ComposerRoute`.
- `media-overlay-controls`: Comment button in video player chrome opens the comments sheet instead of navigating out of the player.

## Impact

- **Affected Code & Shared Ownership**:
  - `:designsystem` (hosts adaptive container `VideoCommentsSheetScaffold`)
  - `:core:post-interactions-ui` (hosts `VideoCommentsSheetContent`, `VideoCommentsState`, and presenter logic consumed by both video surfaces)
  - `:feature:composer:impl` (`ComposerNavigationModule`: exposes `ComposerRoute` to `@OuterShell` for detail player navigation)
  - `:feature:videos:impl` (`VideoFeedScreen`, `VideoPageChrome`, `VideoFeedViewModel`)
  - `:feature:videoplayer:impl` (`VideoPlayerScreen`, `VideoPlayerChrome`, `VideoPlayerViewModel`)
  - `:core:posts` (reused `PostThreadRepository` for thread fetching)
  - `:core:post-interactions` (`LikeRepostRepository` for comment likes)
- **Superseded Specifications**: Supersedes decisions D5 & D7 and tasks 8–9 of `revamp-fullscreen-video-player`.
- **Dependencies**: No new external dependencies; leverages Material 3, Compose Foundation, and Media3 already present in the version catalog.
