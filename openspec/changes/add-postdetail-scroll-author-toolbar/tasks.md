> **Spike note (2026-07-14).** A working prototype validated the behavior on the
> bench flavor. Corrections are folded into `design.md` and the tasks below
> ([spike] markers). The prototype instrumentation (a `Log.d("TopBarSpike", …)` in
> the stateful bar) MUST be removed before landing — see task 3.4. One design
> question is left OPEN (focus vs. thread-root anchor; `design.md` Decision 1);
> it does not block any task here.

## 0. Bench fixture — make the thread scrollable (spike-landed, keep)

- [x] 0.1 In `core/posts/src/bench/.../BenchPosts.kt`, add `benchThreadFillerReplies` (a handful of text replies) so the bench thread is tall enough to scroll the focus card under the bar. The original `[Focus, reply, reply]` fixture was too short — the swap was unreachable on device.
- [x] 0.2 In `BenchFakePostThreadRepository`, append the filler replies AFTER the existing focus + replies. Keep the focus at **index 0** (no ancestors) so `a09PostDetail`'s marketing screenshot still opens on the gallery card. Register the filler posts in `benchPostsByUri`.
- [ ] 0.3 Confirm `MarketingScreenshotJourney.a09PostDetail` still captures the gallery focus card leading the frame (the filler replies sit below the fold at scroll 0).

## 1. Pure scroll math + its tests (TDD — no UI yet)

- [x] 1.1 Create `feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/ui/PostDetailTopBar.kt` containing **only** the pure `internal fun shouldShowAuthorInBar(focusIndex: Int, firstVisibleItemIndex: Int, focusItemTopPx: Int?, enterThresholdPx: Int, exitThresholdPx: Int, currentlyShown: Boolean): Boolean`, plus the two threshold constants (`AUTHOR_BAR_ENTER_THRESHOLD = 56.dp`, `AUTHOR_BAR_EXIT_THRESHOLD = 40.dp`) and the slide constant (`AUTHOR_BAR_SLIDE_DISTANCE = 24.dp`). Resolution order per `design.md` Decision 4.
- [x] 1.2 Add `feature/postdetail/impl/src/test/kotlin/.../ui/PostDetailTopBarTest.kt` (JUnit Jupiter). Table-driven over: `focusIndex == -1` → false regardless of other args; focus absent from visible items and `focusIndex < firstVisibleItemIndex` → true; focus absent and `focusIndex >= firstVisibleItemIndex` → false; focus visible and tucked below the enter threshold → false; tucked at/past the enter threshold → true; **both** hysteresis directions inside the 40–56dp band (`currentlyShown = true` → stays true, `currentlyShown = false` → stays false); focus visible with a positive `focusItemTopPx` (below the bar) → false.
- [x] 1.3 Run `./gradlew :feature:postdetail:impl:testDebugUnitTest` — the new test class passes, every pre-existing test still passes untouched.

## 2. The stateless bar

- [x] 2.1 In `PostDetailTopBar.kt`, add the stateless `internal fun PostDetailTopBar(author: AuthorUi?, showAuthor: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier)`. M3 `TopAppBar`; `navigationIcon` is the existing back `IconButton` moved verbatim out of `PostDetailScreenContent` (same `NubecitaIcon(ArrowBack, filled = true, Modifier.mirror())`, same `postdetail_back_content_description`).
- [x] 2.2 Implement the `title` slot as `AnimatedContent(targetState = showAuthor && author != null, transitionSpec = { ... } using SizeTransform(clip = false))`. Asymmetric spec per `design.md` Decision 5: entering author = `slideInHorizontally(spatialSpec) { startOffsetPx } + fadeIn(effectsSpec)`; exiting "Post" = `fadeOut(effectsSpec)` **only, no translation**. Reversed on the way back.
- [x] 2.3 Read the specs from `MaterialTheme.motionScheme.defaultSpatialSpec()` / `.defaultEffectsSpec()`. Do NOT hand-roll a `spring()` or `tween()` — that silently opts the surface out of `NubecitaMotionScheme`'s reduce-motion branch.
- [x] 2.4 Derive the slide offset sign from `LocalLayoutDirection`: `-24.dp.roundToPx()` in LTR, `+24.dp.roundToPx()` in RTL. `slideInHorizontally` takes raw pixels and does not mirror on its own. A fixed dp — NOT `{ -it / n }`, which would make a long display name slide farther and faster than a short one.
- [x] 2.5 Author block content: `Row(verticalAlignment = CenterVertically)` of a 28dp `NubecitaAvatar` (`contentDescription = null` — decorative; the name is adjacent) + `Text(author.displayName.ifBlank { "@${author.handle}" }, style = titleMedium, maxLines = 1, overflow = Ellipsis)` (`displayName` is non-null on `AuthorUi`, so `ifBlank` — not a safe-call). Wrap the Row in `Modifier.semantics(mergeDescendants = true) {}` so TalkBack reads it as one node. **No `clickable`.**
- [x] 2.6 Add `@Preview`s for the stateless bar: `showAuthor = false`; `showAuthor = true`; long display name; avatarless author (exercises `NubecitaAvatar`'s initial fallback). Light + dark.

## 3. The stateful bar

- [x] 3.1 Add the stateful `internal fun PostDetailTopBar(author: AuthorUi?, listState: LazyListState, focusIndex: Int, onBack: () -> Unit, modifier: Modifier = Modifier)` overload — mirrors the two-overload shape `:feature:profile:impl`'s `ProfileTopBar` already uses.
- [x] 3.2 Hold `var shown by remember { mutableStateOf(false) }` and drive it from a `LaunchedEffect(listState, focusIndex, enterPx, exitPx)` that runs `snapshotFlow { …read listState.layoutInfo… }.collect { shown = shouldShowAuthorInBar(…, currentlyShown = shown) }`. **NOT `derivedStateOf`** — the hysteresis makes this a fold over `shown`'s own previous value, so a derivedStateOf that reads and writes `shown` is a backwards write during composition (`design.md` Decision 2 [spike]). `shown` being a plain boolean MutableState means writing the same value is a no-op, so the bar recomposes only on the flip. Delegate to the stateless overload.
- [x] 3.3 Pass `focus.offset` straight through as `focusItemTopPx` — **no normalization needed**. The spike verified on device that `LazyListItemInfo.offset` is already measured from the app-bar bottom (`viewportStartOffset == -beforeContentPadding`, so their sum is 0; `design.md` Decision 4 [spike]). Keep the pure function consuming an already-normalized value so this stays a call-site concern.
- [x] 3.4 **Remove the spike instrumentation** — the `Log.d("TopBarSpike", …)` / `snapshotFlow`-logging left in the prototype's stateful bar. The production collector computes `shown` without logging. Grep for `TopBarSpike` to confirm none remains.

## 4. Wire it into the screen

- [x] 4.1 In `PostDetailScreen.kt`, hoist `rememberLazyListState()` out of the private `LoadedThread` composable up into `PostDetailScreenContent`, and pass it down as a parameter to `LoadedThread` (which hands it to its `LazyColumn`). The `topBar` slot and the list must share one `LazyListState`.
- [x] 4.2 Replace the inline `TopAppBar { Text(stringResource(R.string.postdetail_title)) }` in the `topBar` slot with the stateful `PostDetailTopBar`, fed `author = state.focusPost?.author` (the existing extension on `PostDetailState`) and `focusIndex = remember(state.items) { state.items.indexOfFirst { it is ThreadItem.Focus } }`.
- [x] 4.3 Confirm no edit to `PostDetailViewModel.kt` or `PostDetailContract.kt` is required. If one appears necessary, stop — the design is wrong, not the contract (`design.md` Decision 6).

## 5. Screenshot coverage

- [x] 5.1 Add fixtures to `PostDetailScreenScreenshotTest` (or a new `PostDetailTopBarScreenshotTest`) driving the **stateless** overload: `showAuthor = false`, `showAuthor = true`, long-display-name truncation, avatarless author. Light + dark.
- [x] 5.2 Do NOT add a fixture that drives the **stateful** overload with a pre-scrolled `LazyListState` — it would capture an in-flight spring and be nondeterministic (`design.md` Risks).
- [x] 5.3 Run `./gradlew :feature:postdetail:impl:validateDebugScreenshotTest`. **[spike] 5 pre-existing focus-bearing LIGHT fixtures legitimately move** (`single-post`, `loaded`, `loaded-refreshing`, `container-hierarchy`, `multi-image-carousel`) — the `AnimatedContent` title wrapper sub-pixel-shifts the body and the reply FAB's shadow crosses the pixel threshold (`design.md` Risks [spike]). This was verified as a real, unavoidable consequence of the swap (not a regression) by reproducing it on the WIP commit's clean tree. Regenerate ALL baselines (`updateDebugScreenshotTest`) and commit the 5 updated + 6 new; re-run validate and confirm green. Dark variants don't move.

## 6. Verify + land

- [ ] 6.1 `./gradlew :feature:postdetail:impl:testDebugUnitTest :feature:postdetail:impl:lintProductionDebug spotlessCheck` — note the **module's own** lint, not `:app`'s (`:app:lintProductionDebug` misses `MissingTranslation`; this change adds no strings, but run it anyway to catch anything else).
- [ ] 6.2 On-device smoke on the bench flavor (`./gradlew :app:installBenchDebug`): open the bench post detail (focus at index 0 — tap the first post in the feed), scroll down past the focus card, confirm "Post" fades out in place while the avatar + name slide in from the start and the bar shows the FOCUS author (Jessica Elena) even while replies from other authors are on screen; scroll back to the top and confirm the exact reverse. Screenshot both ends as evidence. (This was exercised by the spike and passed; re-confirm against the final, non-instrumented build.)
- [ ] 6.3 Smoke a thread still loading (`InitialLoading` → bar reads "Post", no crash on `focusIndex == -1`). Note: the bench thread has no ancestors by design (`design.md` Risks [spike]), so the focus-at-non-zero-index path is covered by task 1.2's unit tests, not on device.
- [ ] 6.4 Run the compose-expert skill over the diff (it adds `@Composable` lines, so the project's Compose review gate applies).
