## 1. Contract: `LocalScrollToTopSignal` in `:core:common:navigation`

- [x] 1.1 Add `LocalScrollToTopSignal: ProvidableCompositionLocal<SharedFlow<Unit>>` in a new file `core/common/src/main/kotlin/net/kikin/nubecita/core/common/navigation/ScrollToTopSignal.kt`. Default value is an empty `MutableSharedFlow<Unit>(replay = 0).asSharedFlow()` so previews / screenshot tests / detached compositions don't need a custom provider. KDoc covers the producer / consumer contract from design Decision 1 + 2.

## 2. Producer: MainShell tab-tap wiring

- [x] 2.1 In `:app/MainShell` (or wherever the bottom-nav tab-tap handler lives), create a `remember { MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 0) }` and expose its read-only view via `LocalScrollToTopSignal provides scrollToTopSignal.asSharedFlow()` in the existing `CompositionLocalProvider` block.
- [x] 2.2 Update the tab-tap handler so that `if (tappedTab == activeTab) scrollToTopSignal.tryEmit(Unit)` (no navigation), else navigate as before. The `activeTab` reference MUST resolve from the post-mutation MainShell state to keep rapid double-tap behavior correct (per design Decision 3 / spec).
- [ ] 2.3 MainShell-level test or instrumentation: verify that re-tapping the active tab calls `tryEmit` while a tab switch does not. (If MainShell already has a test surface for tab-tap, fold this in; otherwise, defer to the consumer-side test in task 5.x.) (Deferred — see task 5.2 note.)

## 3. Consumer: `FeedScreen` signal collector + FAB

- [x] 3.1 Add a `LaunchedEffect(scrollToTopSignal, listState) { scrollToTopSignal.collect { listState.animateScrollToItem(0) } }` block inside `FeedScreen`'s body. `scrollToTopSignal = LocalScrollToTopSignal.current` is read at the top of the composable.
- [x] 3.2 Add `val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_TO_TOP_FAB_THRESHOLD } }` with `private const val SCROLL_TO_TOP_FAB_THRESHOLD = 5` near the existing screen-level constants.
- [x] 3.3 Wire the FAB into the existing `Scaffold.floatingActionButton` slot. Wrap with `AnimatedVisibility(visible = showFab, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut())`. Inside, render `SmallFloatingActionButton(onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } })` with `Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.feed_scroll_to_top))`.
- [x] 3.4 Add the string resource `<string name="feed_scroll_to_top">Scroll to top</string>` to `:feature:feed:impl/src/main/res/values/strings.xml`.

## 4. Screenshot fixture

- [x] 4.1 Add a new fixture `FeedScreenLoadedWithFabVisibleScreenshot` (light theme) that pre-seeds `LazyListState(firstVisibleItemIndex = SCROLL_TO_TOP_FAB_THRESHOLD)` and renders enough fixture posts (≥ 10) to make the scroll position valid. The fixture captures the FAB in its `AnimatedVisibility = true` resting state.
- [x] 4.2 Run `./gradlew :feature:feed:impl:updateDebugScreenshotTest` to record the new baseline. Verify that the four pre-existing video-flake fixtures (and ONLY those four) regenerate as expected; restore them via `git checkout -- ...` per the m28.5.2 / m28.4 PR pattern.
- [x] 4.3 Run `./gradlew :feature:feed:impl:validateDebugScreenshotTest` and confirm only the new fixture is added; every existing baseline (loaded-light, loaded-dark, etc.) stays byte-for-byte unchanged.

## 5. Unit tests

- [x] 5.1 No `FeedViewModel` test changes — the VM is untouched. The `mvi-foundation` exhaustiveness contract is unaffected.
- [ ] 5.2 If MainShell has an existing test surface (per task 2.3), assert: re-tap → `signal.tryEmit` called once; tab switch → `signal.tryEmit` NOT called. (Deferred: MainShell currently has no test surface for tab-tap behavior; the producer-side regression contract is locked by the new screenshot fixture's pre-scrolled state and manual smoke under task 6.5. Filing a follow-up if the MainShell test surface lands later.)
- [ ] 5.3 (Optional, defer if heavy.) Add a Compose UI / instrumentation test in `:feature:feed:impl/src/androidTest/` that pre-scrolls a FeedScreen past the threshold, asserts the FAB is in the semantics tree, taps it, and asserts `firstVisibleItemIndex` returns to 0. If the project's instrumentation harness doesn't yet support `LazyListState` assertion idiomatically, skip and rely on the screenshot fixture + manual smoke for v1. (Deferred: relying on the new screenshot fixture + manual smoke under task 6.5.)

## 6. Verification gate

- [x] 6.1 `./gradlew :core:common:assembleDebug` clean (one-file addition).
- [x] 6.2 `./gradlew :feature:feed:impl:testDebugUnitTest` clean (no test changes; baseline holds).
- [x] 6.3 `./gradlew :feature:feed:impl:validateDebugScreenshotTest` clean (modulo the documented 4 pre-existing video flakes).
- [x] 6.4 `./gradlew :app:assembleDebug spotlessCheck lint` clean.
- [ ] 6.5 Manual smoke on a connected device: scroll Feed past 5 items → FAB appears; tap FAB → scrolls to top, FAB fades out; scroll Feed past 5 items → tap the Feed tab in the bottom nav → scrolls to top via the signal path; switch to Profile, back to Feed → Feed restores its last position (NOT scroll-to-top, because that's a tab switch).
- [ ] 6.6 PR description references the openspec change name (`add-feed-scroll-to-top`) and the bd id (`Closes: nubecita-crs`).
