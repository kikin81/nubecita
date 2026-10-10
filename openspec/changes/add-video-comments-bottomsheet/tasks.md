## 1. Data Models & MVI State Machine (`nubecita-76q8.1`)

- [ ] 1.1 Define `VideoCommentsState`, `CommentItemUi`, and MVI contracts (`OpenComments`, `DismissComments`, `SetDetent`, `LikeComment`, `RetryLoad`), modeling Loaded, Empty ("No comments yet"), and Error states. Unit test: `VideoCommentsStateTest` asserting initial state and detent transitions.
- [ ] 1.2 Wire `PostThreadRepository.getPostThread(uri)` to project focus replies into `ImmutableList<ThreadItem.Reply>`. Unit test: `VideoCommentsViewModelTest` verifying thread resolution, SWR background revalidation, and error handling.

## 2. Adaptive Layout Shell (`nubecita-76q8.2`)

- [ ] 2.1 Implement `VideoCommentsSheetScaffold` supporting `WindowWidthSizeClass.Compact` (bottom sheet with drag handle) and `WindowWidthSizeClass.Expanded` (two-pane split layout using M3 `SupportingPaneScaffold` with hinge awareness, side sheet width constrained between 360dp and 440dp, and scoped `WindowInsets.ime` padding). Screenshot test: `VideoCommentsSheetScaffoldScreenshotTest` for Compact and Expanded.

## 3. Gesture Arbitration & Nested Scroll Isolation (`nubecita-76q8.3`)

- [ ] 3.1 Implement custom `NestedScrollConnection` that prioritizes expanding from `PartiallyExpanded` to `Expanded` on upward drag, delegates to `LazyColumn` for content scroll, and collapses on downward drag only at scroll offset 0. Unit test: `VideoCommentsNestedScrollConnectionTest`.
- [ ] 3.2 Wire `VerticalPager(userScrollEnabled = isSheetSettledHidden)` in `VideoFeedScreen`, ensuring pager scrolling stays disabled until the sheet completely finishes its exit transition. Compose UI test: verify dragging comments does not scroll parent `VerticalPager`.

## 4. Comments Thread List UI & Optimistic Likes (`nubecita-76q8.4`)

- [ ] 4.1 Render `LazyColumn` of comments with author avatar, name, handle, timestamp, body text, empty state banner, and retryable error state. Screenshot test: `VideoCommentsListScreenshotTest` in loaded, empty, and error states.
- [ ] 4.2 Wire comment like button to `LikeRepostRepository` + `PostInteractionsCache` with haptic feedback (`PostHaptics.likeOn/likeOff`). Unit test: `VideoCommentsViewModelTest` verifying optimistic like updates and undo.

## 5. Phase 1 Docked Comment Pill & Composer Navigation (`nubecita-76q8.5`)

- [ ] 5.1 Implement docked comment pill `[Avatar] "Add a comment..." [Send]` pinned to bottom with `.navigationBarsPadding()`. Screenshot test: `DockedCommentPillScreenshotTest`.
- [ ] 5.2 Wire tap action to navigate to `ComposerRoute(replyToUri = videoUri)`. On submit, optimistically append the new comment, immediately increment the parent video's comment count badge on the action rail, and trigger silent background revalidation; on cancel/back, restore exact sheet state and scroll offset without mutation. Unit test: navigation effect and return state handling.

## 6. Reel Player Integration (`nubecita-76q8.6`)

- [ ] 6.1 Integrate `VideoCommentsSheetScaffold` into `VideoFeedScreen`. Wire `VideoPageChrome` comment CTA with settled-page guard (`pagerState.settledPage == page && !pagerState.isScrollInProgress`), and suspend feed auto-advance while the sheet is open. Unit test: `VideoFeedViewModelTest` verifies comment trigger and auto-advance suspension.

## 7. Feed Detail Player Integration (`nubecita-76q8.7`)

- [ ] 7.1 Integrate `VideoCommentsSheetScaffold` into `VideoPlayerScreen` and wire social action group comment CTA to toggle sheet. Screenshot test: detail player with open comments sheet in portrait and landscape.

## 8. Playback Lifecycle, PiP & Back Navigation (`nubecita-76q8.8`)

- [ ] 8.1 Wire `BackHandler(enabled = isCommentsSheetOpen)` so back dismisses the sheet before exiting the screen (with predictive back animation on Android 14+). Unit test: back press consumes sheet dismissal.
- [ ] 8.2 Integrate with `LocalPipController` / `rememberIsInPipMode()` to instantly snap the comments sheet to `Hidden` (no animation) upon PiP entry to prevent sheet capture in the PiP window snapshot. Unit test: PiP transition triggers instant sheet drop.

## 9. Phase 2 Headless Composer Extraction Spike (`nubecita-76q8.9`)

- [ ] 9.1 Produce architectural blueprint and spike report for extracting headless composer state (`:core:composer-engine`) to enable inline text commenting directly in the sheet.

## 10. Verification, Screenshot Baselines & Macrobenchmark (`nubecita-76q8.10`)

- [ ] 10.1 Run full unit test suite and generate checked-in screenshot baselines across all configurations.
- [ ] 10.2 Add Macrobenchmark measuring frame timing during sheet drag over active video playback to confirm 120Hz frame pacing.
