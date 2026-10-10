## Layer 1: Foundations & Gesture Arbitration (`refactor/nubecita-76q8-sheet-foundations`)

- [ ] 1.1 Define `VideoCommentsState`, `CommentItemUi`, and MVI presenter in `:core:post-interactions-ui` (`OpenComments`, `DismissComments`, `SetDetent`, `LikeComment`, `RetryLoad`), modeling Loaded, Empty ("No comments yet"), and Error states (`nubecita-76q8.1`). Unit test: `VideoCommentsStateTest` asserting initial state and detent transitions.
- [ ] 1.2 Wire `PostThreadRepository.getPostThread(uri)` to project focus replies into `ImmutableList<ThreadItem.Reply>` with SWR background revalidation and reconciliation (`nubecita-76q8.1`). Unit test: `VideoCommentsViewModelTest` verifying thread resolution and error handling.
- [ ] 1.3 Implement `VideoCommentsSheetScaffold` in `:designsystem` supporting `WindowWidthSizeClass.Compact`, `WindowWidthSizeClass.Medium`, and `WindowWidthSizeClass.Expanded` using M3 `SupportingPaneScaffold` with hinge awareness, side sheet width constrained between 360dp and 440dp, zeroed sheet window insets, and scoped `WindowInsets.ime` padding (`nubecita-76q8.2`). Screenshot test: `VideoCommentsSheetScaffoldScreenshotTest` for Compact, Medium, and Expanded.
- [ ] 1.4 Implement custom `NestedScrollConnection` that prioritizes expanding from `PartiallyExpanded` to `Expanded` on upward drag, delegates to `LazyColumn` for content scroll, and collapses on downward drag only at scroll offset 0 (`nubecita-76q8.3`). Unit test: `VideoCommentsNestedScrollConnectionTest`.
- [ ] 1.5 Wire `VerticalPager(userScrollEnabled = isSheetSettledHidden)` in `VideoFeedScreen`, ensuring pager scrolling stays disabled until the sheet completely finishes its exit transition (`nubecita-76q8.3`). Compose UI test: verify dragging comments does not scroll parent `VerticalPager`.

## Layer 2: Comments UI & Docked Pill (`refactor/nubecita-76q8-comments-ui`)

- [ ] 2.1 Render `LazyColumn` of comments in `:core:post-interactions-ui` with author avatar, name, handle, timestamp, body text, empty state banner, and retryable error state (`nubecita-76q8.4`). Screenshot test: `VideoCommentsListScreenshotTest` in loaded, empty, and error states.
- [ ] 2.2 Wire comment like button to `LikeRepostRepository` + `PostInteractionsCache` with haptic feedback (`PostHaptics.likeOn/likeOff`) (`nubecita-76q8.4`). Unit test: `VideoCommentsViewModelTest` verifying optimistic like updates and undo.
- [ ] 2.3 Implement docked comment pill `[Avatar] "Add a comment..." [Send]` pinned to bottom with `.navigationBarsPadding()` and accessibility semantics (`Role.Button`, decorative icons) (`nubecita-76q8.5`). Screenshot test: `DockedCommentPillScreenshotTest`.
- [ ] 2.4 Register `ComposerRoute` in `@OuterShell` within `ComposerNavigationModule` to enable cross-shell navigation from detail player. On `ComposerSubmitEvent`, increment parent post's `replyCount` badge on action rail and trigger hydration refetch; on cancel, restore exact sheet state and scroll offset (`nubecita-76q8.5`). Unit test: navigation effect and submit-event hydration.

## Layer 3: Surfaces Integration & Lifecycle (`feat/nubecita-76q8-video-comments-sheet`)

- [ ] 3.1 Integrate `VideoCommentsSheetScaffold` into `VideoFeedScreen`. Wire `VideoPageChrome` comment CTA with settled-page guard resolved against active filtered list (`status.items[page]`), suspend feed auto-advance while sheet is open, and suspend chrome auto-fade during loading (`nubecita-76q8.6`). Unit test: `VideoFeedViewModelTest` verifies comment trigger and auto-advance suspension.
- [ ] 3.2 Integrate `VideoCommentsSheetScaffold` into `VideoPlayerScreen` and wire social action group comment CTA to toggle sheet via outer shell navigator (`nubecita-76q8.7`). Screenshot test: detail player with open comments sheet in Compact, Medium, and Expanded.
- [ ] 3.3 Wire `PredictiveBackHandler(enabled = isCommentsSheetOpen)` with gesture progress collection to translate sheet offset interactively, with `BackHandler` fallback on older APIs (`nubecita-76q8.8`). Unit test: predictive back progress updates sheet offset.
- [ ] 3.4 Integrate with `LocalPipController` / `rememberIsInPipMode()` to instantly snap comments sheet to `Hidden` (no animation) upon PiP entry to prevent sheet capture in the PiP window snapshot (`nubecita-76q8.8`). Unit test: PiP transition triggers instant sheet drop.

## Layer 4: Verification, Benchmarks & Phase 2 Spike (`test/nubecita-76q8-benchmarks-and-spike`)

- [ ] 4.1 Produce architectural blueprint and spike report for extracting headless composer state (`:core:composer-engine`) to enable inline text commenting directly in the sheet (`nubecita-76q8.9`).
- [ ] 4.2 Run full unit test suite and generate checked-in screenshot baselines across Compact, Medium, and Expanded configurations (`nubecita-76q8.10`).
- [ ] 4.3 Add Macrobenchmark measuring frame timing during sheet drag over active video playback to confirm 120Hz frame pacing, pre-resolving all UIAutomator selectors in setup prior to `measureRepeated` (`nubecita-76q8.10`).
