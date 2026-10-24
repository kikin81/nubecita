## ADDED Requirements

### Requirement: Feed video player binding is gated on active pager page

The system SHALL gate `FeedVideoPlayerCoordinator.bindMostVisibleVideo` on `isPageActive: Boolean` and `!listState.isScrollInProgress`. `isPageActive` MUST evaluate to true only when `pagerState.settledPage == pageIndex && !pagerState.isScrollInProgress`.

#### Scenario: Swiping away from a playing video
- **WHEN** a video is playing on Feed 0 and the user initiates a horizontal swipe
- **THEN** Feed 0 immediately unbinds from `SharedVideoPlayer`, pausing playback and releasing video surface resources

#### Scenario: Settling on a feed containing a video
- **WHEN** the user settles on a feed page containing an in-viewport video post
- **THEN** the active feed binds the video to `SharedVideoPlayer` and begins autoplay if autoplay is enabled
