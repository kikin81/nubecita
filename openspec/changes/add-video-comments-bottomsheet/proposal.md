## Why

When viewing videos in Nubecita — whether in the full-bleed Vertical Reel Feed (`:feature:videos`) or the Feed Detail Player (`:feature:videoplayer`) — tapping the Comment button currently navigates immediately to the full-screen `ComposerRoute`. This forces an abrupt stop to video playback, discards playback context, and prevents viewers from reading the existing conversation or author comments before participating.

Modern social video experiences (TikTok, Instagram Reels, YouTube Shorts) keep the video playing ambiently while viewers read and write comments. We need an adaptive, non-disruptive comments experience that holds 120Hz scrolling, prevents vertical gesture conflicts with `VerticalPager`, adapts cleanly between phones and foldables/tablets, and introduces a docked comment action that prepares for inline commenting without initial scope creep.

## What Changes

- **Adaptive Comments Container**:
  - **Compact Window (Phones)**: Renders as an anchored Material 3 bottom sheet (`PartiallyExpanded` ~50% detent, `Expanded` ~90% detent, `Hidden`).
  - **Medium / Expanded Windows (Tablets / Foldables / Landscape)**: Transitions to a zero-scrim **Side Sheet / Two-Pane layout** (unscaled video on the left, 400dp comments panel on the right) with concurrent video controls.
- **Nested Scroll & Gesture Arbitration**:
  - `VerticalPager` disables `userScrollEnabled` whenever the comments sheet is open or animating.
  - A custom `NestedScrollConnection` ensures vertical list dragging does not bleed into the reel pager and only collapses the sheet once the comments list is scrolled to position 0.
- **Continuous Playback & Lifecycle Coordination**:
  - Video and audio continue uninterrupted during sheet open, drag, and dismiss.
  - Entering Picture-in-Picture (PiP) automatically collapses the comments sheet to maintain a clean video-only PiP surface.
  - Predictive Back (Android 14+) and system back dismiss the sheet before popping the video screen.
- **Phased Composer Integration**:
  - **Phase 1 (This change)**: A docked `"Add a comment..."` pill at the bottom of the comments sheet that launches `ComposerRoute(replyToUri = videoUri)` and seamlessly restores the open sheet on return without losing list scroll position or video state.
  - **Phase 2 (Fast Follow)**: Extraction of a headless composer engine (`:core:composer-engine`) to support typing directly within the sheet.

## Non-Goals

- Inline text composition and attachment pickers directly within the sheet in Phase 1 (deferred to Phase 2 to prevent composer scope creep).
- Nested reply-tree indentation deeper than 1 level inside the bottom sheet (tapping deeply nested sub-threads navigates to the full Post Detail thread view).
- Custom video playback speed manipulation from inside the comments sheet.

## Capabilities

### New Capabilities
- `video-comments-sheet`: Adaptive video comments bottom sheet and side sheet container, gesture arbitration against vertical pagers, thread resolution via `PostThreadRepository`, and docked comment composer launching.

### Modified Capabilities
- `feature-feed-video`: Comment button on the reel action rail opens the comments sheet instead of immediately pushing `ComposerRoute`.
- `media-overlay-controls`: Comment button in video player chrome opens the comments sheet instead of navigating out of the player.

## Impact

- **Affected Code**:
  - `:designsystem` (new adaptive `VideoCommentsSheetScaffold`)
  - `:feature:videos:impl` (`VideoFeedScreen`, `VideoPageChrome`, `VideoFeedViewModel`)
  - `:feature:videoplayer:impl` (`VideoPlayerScreen`, `VideoPlayerChrome`, `VideoPlayerViewModel`)
  - `:core:posts` (reused `PostThreadRepository` for thread fetching)
  - `:core:post-interactions` (`LikeRepostRepository` for comment likes)
- **Dependencies**: No new external dependencies; leverages Material 3, Compose Foundation, and Media3 already present in the version catalog.
- **Architecture Deviations**: The comments container uses an MVI state machine with an explicit `SheetDetent` sum state (`Hidden`, `PartiallyExpanded`, `Expanded`) to arbitrate gestures across layout phases cleanly.
