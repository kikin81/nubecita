## 1. Video Player Hardening & Codec Safety

- [ ] 1.1 Update `SharedVideoPlayer.bind()` to reset seek position to 0 and re-prepare when called with the same playlist URL while in idle, ended, or error states. (Unit test: `SharedVideoPlayerTest`)
- [ ] 1.2 Configure `DefaultRenderersFactory` in `createSharedVideoPlayer` with `forceDisableMediaCodecAsynchronousQueueing()`, fallback enablement, and emulator-safe software decoder selector. (Unit test: `SharedVideoPlayerTest`)
- [ ] 1.3 Add `seekTo(0)` in `FeedVideoPlayerCoordinator.bindInternal()` when re-binding an already bound URL. (Unit test: `FeedVideoPlayerCoordinatorTest`)

## 2. Adaptive Horizontal Pager & FeedHost Refactoring

- [ ] 2.1 Hoist `FeedChipRow` from `FeedScreen.kt` up into `FeedHost.kt` above the pager, wiring nested scroll connection for top bar collapse. (Unit test: `FeedHostPagerFeedsTest`)
- [ ] 2.2 Replace single active `FeedPane` in `FeedHost.kt` with `HorizontalPager(beyondViewportPageCount = 0)`. (Unit test: `FeedHostPagerFeedsTest`)
- [ ] 2.3 Set `userScrollEnabled = isCompact` based on `currentWindowAdaptiveInfoV2()` to disable swipe gestures on tablet list panes and eliminate drag handle collisions. (Unit test: `FeedHostPagerFeedsTest`)
- [ ] 2.4 Synchronize `pagerState.settledPage` bidirectionally with `FeedHostViewModel.selectedFeedUri` using `snapshotFlow` and `distinctUntilChanged()`. (Unit test: `FeedHostViewModelTest`)

## 3. Back Navigation & Video Coordinator Gating

- [ ] 3.1 Implement `BackHandler(enabled = pagerState.settledPage != 0)` in `FeedHost.kt` to animate back to Page 0 before exiting app. (Unit test: `FeedHostPagerFeedsTest`)
- [ ] 3.2 Wire `PredictiveBackHandler` on Android 14+ / API 34+ for smooth animated back gesture progress. (Unit test: `FeedHostPagerFeedsTest`)
- [ ] 3.3 Pass `isPageActive = pagerState.settledPage == page && !pagerState.isScrollInProgress` into `FeedPane`, `FeedScreen`, and `PostFeedList`. (Unit test: `FeedVideoPlayerCoordinatorTest`)
- [ ] 3.4 Unbind `SharedVideoPlayer` immediately when `!isPageActive` to prevent dual-feed audio/video conflicts. (Unit test: `FeedVideoPlayerCoordinatorTest`)

## 4. Verification & Testing

- [ ] 4.1 Run unit tests across `:feature:feed:impl`, `:core:video`, and `:core:common`.
- [ ] 4.2 Add Roborazzi screenshot test for `FeedHost` on Compact (Phone) and Expanded (Tablet) window sizes.
- [ ] 4.3 Validate end-to-end swipe, autoplay, and back navigation on emulator devices (`Pixel_10_Pro` and `Pixel_Tablet`).
