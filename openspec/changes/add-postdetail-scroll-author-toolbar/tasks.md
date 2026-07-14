## 1. Pure scroll math + its tests (TDD — no UI yet)

- [ ] 1.1 Create `feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/ui/PostDetailTopBar.kt` containing **only** the pure `internal fun shouldShowAuthorInBar(focusIndex: Int, firstVisibleItemIndex: Int, focusItemTopPx: Int?, enterThresholdPx: Int, exitThresholdPx: Int, currentlyShown: Boolean): Boolean`, plus the two threshold constants (`AUTHOR_BAR_ENTER_THRESHOLD = 56.dp`, `AUTHOR_BAR_EXIT_THRESHOLD = 40.dp`) and the slide constant (`AUTHOR_BAR_SLIDE_DISTANCE = 24.dp`). Resolution order per `design.md` Decision 4.
- [ ] 1.2 Add `feature/postdetail/impl/src/test/kotlin/.../ui/PostDetailTopBarTest.kt` (JUnit Jupiter). Table-driven over: `focusIndex == -1` → false regardless of other args; focus absent from visible items and `focusIndex < firstVisibleItemIndex` → true; focus absent and `focusIndex >= firstVisibleItemIndex` → false; focus visible and tucked below the enter threshold → false; tucked at/past the enter threshold → true; **both** hysteresis directions inside the 40–56dp band (`currentlyShown = true` → stays true, `currentlyShown = false` → stays false); focus visible with a positive `focusItemTopPx` (below the bar) → false.
- [ ] 1.3 Run `./gradlew :feature:postdetail:impl:testDebugUnitTest` — the new test class passes, every pre-existing test still passes untouched.

## 2. The stateless bar

- [ ] 2.1 In `PostDetailTopBar.kt`, add the stateless `internal fun PostDetailTopBar(author: AuthorUi?, showAuthor: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier)`. M3 `TopAppBar`; `navigationIcon` is the existing back `IconButton` moved verbatim out of `PostDetailScreenContent` (same `NubecitaIcon(ArrowBack, filled = true, Modifier.mirror())`, same `postdetail_back_content_description`).
- [ ] 2.2 Implement the `title` slot as `AnimatedContent(targetState = showAuthor && author != null, transitionSpec = { ... } using SizeTransform(clip = false))`. Asymmetric spec per `design.md` Decision 5: entering author = `slideInHorizontally(spatialSpec) { startOffsetPx } + fadeIn(effectsSpec)`; exiting "Post" = `fadeOut(effectsSpec)` **only, no translation**. Reversed on the way back.
- [ ] 2.3 Read the specs from `MaterialTheme.motionScheme.defaultSpatialSpec()` / `.defaultEffectsSpec()`. Do NOT hand-roll a `spring()` or `tween()` — that silently opts the surface out of `NubecitaMotionScheme`'s reduce-motion branch.
- [ ] 2.4 Derive the slide offset sign from `LocalLayoutDirection`: `-24.dp.roundToPx()` in LTR, `+24.dp.roundToPx()` in RTL. `slideInHorizontally` takes raw pixels and does not mirror on its own. A fixed dp — NOT `{ -it / n }`, which would make a long display name slide farther and faster than a short one.
- [ ] 2.5 Author block content: `Row(verticalAlignment = CenterVertically)` of a 28dp `NubecitaAvatar` (`contentDescription = null` — decorative; the name is adjacent) + `Text(author.displayName?.takeIf { it.isNotBlank() } ?: "@${author.handle}", style = titleMedium, maxLines = 1, overflow = Ellipsis)`. Wrap the Row in `Modifier.semantics(mergeDescendants = true) {}` so TalkBack reads it as one node. **No `clickable`.**
- [ ] 2.6 Add `@Preview`s for the stateless bar: `showAuthor = false`; `showAuthor = true`; long display name; avatarless author (exercises `NubecitaAvatar`'s initial fallback). Light + dark.

## 3. The stateful bar

- [ ] 3.1 Add the stateful `internal fun PostDetailTopBar(author: AuthorUi?, listState: LazyListState, focusIndex: Int, onBack: () -> Unit, modifier: Modifier = Modifier)` overload — mirrors the two-overload shape `:feature:profile:impl`'s `ProfileTopBar` already uses.
- [ ] 3.2 Inside it, hold `var shown by remember { mutableStateOf(false) }`-style current-state for the hysteresis input, and compute the target in a `derivedStateOf` over `listState.layoutInfo`: look up `visibleItemsInfo.firstOrNull { it.index == focusIndex }`, normalize its `offset` to be **relative to the bottom of the app bar**, and call `shouldShowAuthorInBar(...)` passing the current value as `currentlyShown`. Delegate to the stateless overload.
- [ ] 3.3 **Verify the `LazyListItemInfo.offset` origin on device** (`design.md` Risks) — whether it already excludes the top `contentPadding` or needs `- layoutInfo.beforeContentPadding`. Getting this wrong makes the swap fire ~64dp early or late. Normalize at this call site only; the pure function must keep consuming an already-normalized value.

## 4. Wire it into the screen

- [ ] 4.1 In `PostDetailScreen.kt`, hoist `rememberLazyListState()` out of the private `LoadedThread` composable up into `PostDetailScreenContent`, and pass it down as a parameter to `LoadedThread` (which hands it to its `LazyColumn`). The `topBar` slot and the list must share one `LazyListState`.
- [ ] 4.2 Replace the inline `TopAppBar { Text(stringResource(R.string.postdetail_title)) }` in the `topBar` slot with the stateful `PostDetailTopBar`, fed `author = state.focusPost?.author` (the existing extension on `PostDetailState`) and `focusIndex = remember(state.items) { state.items.indexOfFirst { it is ThreadItem.Focus } }`.
- [ ] 4.3 Confirm no edit to `PostDetailViewModel.kt` or `PostDetailContract.kt` is required. If one appears necessary, stop — the design is wrong, not the contract (`design.md` Decision 6).

## 5. Screenshot coverage

- [ ] 5.1 Add fixtures to `PostDetailScreenScreenshotTest` (or a new `PostDetailTopBarScreenshotTest`) driving the **stateless** overload: `showAuthor = false`, `showAuthor = true`, long-display-name truncation, avatarless author. Light + dark.
- [ ] 5.2 Do NOT add a fixture that drives the **stateful** overload with a pre-scrolled `LazyListState` — it would capture an in-flight spring and be nondeterministic (`design.md` Risks).
- [ ] 5.3 Run `./gradlew :feature:postdetail:impl:validateDebugScreenshotTest`. Every **pre-existing** post-detail fixture must stay byte-for-byte unchanged (they all render at scroll 0, where the bar is the identical "Post" `Text`). If an existing baseline moves, the bar's resting layout changed and that is a bug — investigate before regenerating. Generate baselines for the new fixtures only.

## 6. Verify + land

- [ ] 6.1 `./gradlew :feature:postdetail:impl:testDebugUnitTest :feature:postdetail:impl:lintProductionDebug spotlessCheck` — note the **module's own** lint, not `:app`'s (`:app:lintProductionDebug` misses `MissingTranslation`; this change adds no strings, but run it anyway to catch anything else).
- [ ] 6.2 On-device smoke on the bench flavor (`./gradlew :app:installBenchDebug`): open a post detail **with ancestors**, scroll down past the focus card, confirm "Post" fades out in place while the avatar + name slide in from the start; scroll back to the top and confirm the exact reverse. Confirm the swap fires when the focus card's author row passes the bar — not ~64dp off (that is the `beforeContentPadding` bug from task 3.3). Screenshot both ends as evidence.
- [ ] 6.3 Smoke a thread **without** ancestors (focus is item 0) and a thread still loading (`InitialLoading` → bar reads "Post", no crash on `focusIndex == -1`).
- [ ] 6.4 Run the compose-expert skill over the diff (it adds `@Composable` lines, so the project's Compose review gate applies).
