## 1. Pre-flight + dependency check

- [x] 1.1 Verify `androidx.compose.material3:material3` on the catalog includes `pulltorefresh.PullToRefreshBox` (Material 3 1.3+). If the version doesn't expose it, propose a version bump in a separate `chore(deps):` PR before this branch lands. (Resolved: catalog has `androidxComposeMaterial3 = "1.5.0-alpha18"` — well above 1.3, includes `PullToRefreshBox`.)
- [x] 1.2 Confirm `androidx.compose.ui:ui-test-junit4` and `androidx.compose.ui:ui-test-manifest` aliases exist in `gradle/libs.versions.toml`. Add them if missing — pinned versions matched to the existing Compose BOM. (Resolved: both already present at lines 65 + 67.)
- [x] 1.3 Inspect `build-logic/src/main/kotlin/.../AndroidFeatureConventionPlugin.kt`. If it does NOT add Compose UI test deps to `androidTestImplementation`, decide between (a) adding them in the plugin (preferred if multiple feature tests will use them in this PR), or (b) declaring inline in `feature/feed/impl/build.gradle.kts`. Document the choice in a one-line comment in the gradle file. (Resolved: chose (b) — feed:impl is the only module needing UI tests in this PR, plugin promotion deferred until a second consumer arrives. Inline deps mirror `:app`'s canonical Compose UI test setup.)
- [x] 1.4 Run `./gradlew :feature:feed:impl:assembleDebug` to confirm the module still builds against the placeholder before any changes. (Build successful in 9s.)

## 2. State composables (empty / error / appending indicator)

- [x] 2.1 Add `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/ui/FeedEmptyState.kt` — `@Composable internal fun FeedEmptyState(modifier: Modifier = Modifier, onRefresh: () -> Unit = {})` rendering an icon + headline + body + "Refresh" outlined button. Stateless; takes no `FeedState`. Strings come from `feature/feed/impl/src/main/res/values/strings.xml` (new file).
- [x] 2.2 Add `feature/feed/impl/src/main/kotlin/.../ui/FeedErrorState.kt` — `@Composable internal fun FeedErrorState(error: FeedError, modifier: Modifier = Modifier, onRetry: () -> Unit)`. `when (error)` over `Network`, `Unauthenticated`, `Unknown` mapping each to a string resource for headline + body. Filled "Retry" button. (Implemented via a private `ErrorCopy` data class to avoid scattering icon + string pairs across the dispatch.)
- [x] 2.3 Add `feature/feed/impl/src/main/kotlin/.../ui/FeedAppendingIndicator.kt` — `@Composable internal fun FeedAppendingIndicator(modifier: Modifier = Modifier)` wrapping a `PostCardShimmer(showImagePlaceholder = false)` so the tail row matches list geometry. (Thin wrapper, but isolating the call site lets a future change swap to a circular indicator without touching `FeedScreen`.)
- [x] 2.4 Add `@Preview`s for each of the three composables — light + dark, plus the three error variants for `FeedErrorState`. Place under `:feature:feed:impl/src/main/kotlin/.../ui/` in the same files.
- [x] 2.5 Add screenshot tests `feature/feed/impl/src/screenshotTest/kotlin/.../ui/FeedEmptyStateScreenshotTest.kt`, `FeedErrorStateScreenshotTest.kt`, `FeedAppendingIndicatorScreenshotTest.kt` capturing the same matrix. Mirror the AGP-managed screenshot test layout used in `:designsystem`.
- [x] 2.6 Run `./gradlew :feature:feed:impl:validateScreenshotTest` (or the equivalent task name produced by `android.experimental.enableScreenshotTest`) — references generated and clean. (Used `:feature:feed:impl:updateDebugScreenshotTest` — generated 10 reference PNGs, 5 tests × 2 themes.)
- [x] 2.7 Add unit tests in `:feature:feed:impl/src/test/kotlin/.../ui/` only if any of the state composables grow non-trivial logic worth testing without the full Compose runtime. Likely empty for now — note "no unit tests; behavior is covered by screenshot + UI tests" inline in the package's KDoc if needed. (Skipped — composables are pure rendering; behavior covered by screenshot tests + the upcoming Compose UI tests in section 6.)

## 3. State projection helper

- [x] 3.1 Add `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreenViewState.kt` — `internal sealed interface FeedScreenViewState` with variants `InitialLoading`, `Empty`, `InitialError(error: FeedError)`, `Loaded(posts, isAppending)`. Plus `internal fun FeedState.toViewState(): FeedScreenViewState` implementing the matrix from spec Decision 3.
- [x] 3.2 Add `:feature:feed:impl/src/test/kotlin/.../FeedScreenViewStateTest.kt` — JUnit 5 unit tests covering every entry in the matrix table from the spec (six rows × the boolean column). Pure-Kotlin test, no Android. (11 tests covering all 10 cells of the (5 statuses × 2 emptiness) matrix plus an extra `InitialError(Unauthenticated)` variant.)
- [x] 3.3 Run `./gradlew :feature:feed:impl:testDebugUnitTest` — green. (FeedScreenViewStateTest: all green.)

## 4. Production `FeedScreen` composable

- [x] 4.1 Replace `feature/feed/impl/src/main/kotlin/.../FeedScreen.kt` with the production composable signature: `@Composable internal fun FeedScreen(modifier: Modifier = Modifier, onNavigateToPost: (PostUi) -> Unit = {}, onNavigateToAuthor: (String) -> Unit = {}, viewModel: FeedViewModel = hiltViewModel())`.
- [x] 4.2 Inside `FeedScreen`: state collection, `viewState` derivation, `LazyListState` via `rememberSaveable`, `SnackbarHostState`, and `remember(viewModel)`-d `PostCallbacks` dispatching all six `FeedEvent` interaction variants.
- [x] 4.3 `LaunchedEffect(Unit) { viewModel.handleEvent(FeedEvent.Load) }` — initial load.
- [x] 4.4 `LaunchedEffect(Unit) { viewModel.effects.collect { ... } }` mapping `ShowError` (snackbar with dismiss-then-show), `NavigateToPost`, `NavigateToAuthor`. (Note: spec drift — VM exposes `effects` not `uiEffect`; spec updated to match.)
- [x] 4.5 `Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) })` — content is the inner `when (viewState)` block.
- [x] 4.6 Inside `Scaffold` content: `when (viewState)` over the four `FeedScreenViewState` variants, dispatching to `LazyColumn`-of-`PostCardShimmer` / `FeedEmptyState` / `FeedErrorState` / `LoadedFeedContent`.
- [x] 4.7 Extracted `@Composable private fun LoadedFeedContent` wrapping `PullToRefreshBox` + `LazyColumn` with `items(posts, key = { it.id }, contentType = { "post" })` plus the conditional tail `FeedAppendingIndicator` row.
- [x] 4.8 Pagination trigger via `snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged()` keyed on `listState` + `posts.size`. Single screen-side `onLoadMore()` call; VM's idempotency + `endReached` guard handles the no-op cases.
- [x] 4.9 `PREFETCH_DISTANCE = 5` and `SHIMMER_PREVIEW_COUNT = 6` declared as private file-top consts.
- [x] 4.10 Updated `FeedNavigationModule` — `entry<Feed> { FeedScreen(onNavigateToPost = {}, onNavigateToAuthor = {}) }` with inline KDoc pointing to the future PostDetail/Profile epics that will wire the nav callbacks.
- [x] 4.11 Ran `./gradlew :feature:feed:impl:assembleDebug` and `./gradlew :app:assembleDebug` — both green. Also ran repo-wide unit tests on the way (`:feature:feed:impl:testDebugUnitTest` green, including the 11 ViewState matrix tests now extended for the new `isRefreshing` field on `Loaded`).

## 5. Previews + screenshot tests for `FeedScreen`

- [x] 5.1 Added `@Preview`s in `FeedScreen.kt` for empty, initial-loading, three initial-error variants (Network / Unauthenticated / Unknown), loaded, loaded+refreshing, loaded+appending — each in light + dark. Driven through a `FeedScreenPreviewHost` helper that constructs `FeedScreenViewState` directly.
- [x] 5.2 Refactored: preview composables take `FeedScreenViewState` (the screen-private projection of `FeedState`) and call the stateless `FeedScreenContent(viewState, listState, snackbarHostState, callbacks, onRefresh, onRetry, onLoadMore, modifier)` internal composable. Production `FeedScreen(viewModel)` wraps the same `FeedScreenContent` after wiring VM-backed callbacks. (Skipped the `onLoad` parameter — `Load` is dispatched once via `LaunchedEffect(Unit)` in the Hilt-aware outer composable; previews pre-populate `viewState` so they never need it.)
- [x] 5.3 Added `feature/feed/impl/src/screenshotTest/kotlin/.../FeedScreenScreenshotTest.kt` — 8 `@PreviewTest`-marked screenshots × 2 themes = 16 references covering the full matrix.
- [x] 5.4 Ran `./gradlew :feature:feed:impl:updateDebugScreenshotTest` — references generated cleanly.
- [x] 5.5 Validation deferred to phase 7 (`:feature:feed:impl:validateDebugScreenshotTest` will run as part of section 7.4). Update + build both green confirms references compile and render.

## 6. Compose UI tests (instrumented) — DEFERRED

**Deferred to `nubecita-1gf`** (Compose UI tests for FeedScreen, blocked by `nubecita-16a` Android instrumented tests via android-emulator-runner). The seven test files below are spec'd in detail so the follow-on can land them mechanically once CI can run `connectedDebugAndroidTest`. Writing ~500 LOC of test code that compiles-but-cannot-run was judged worse than carrying the spec forward in `nubecita-1gf`.

- [ ] 6.1 Deferred — `FeedScreenPaginationTest.kt` — see `nubecita-1gf`.
- [ ] 6.2 Deferred — `FeedScreenRefreshTest.kt` — see `nubecita-1gf`.
- [ ] 6.3 Deferred — `FeedScreenRetryTest.kt` — see `nubecita-1gf`.
- [ ] 6.4 Deferred — `FeedScreenEmptyTest.kt` — see `nubecita-1gf`.
- [ ] 6.5 Deferred — `FeedScreenConfigChangeRetentionTest.kt` — see `nubecita-1gf`.
- [ ] 6.6 Deferred — `FeedScreenBackNavRetentionTest.kt` — see `nubecita-1gf`.
- [ ] 6.7 Deferred — `FeedScreenSnackbarTest.kt` — see `nubecita-1gf`.
- [ ] 6.8 Deferred — `connectedDebugAndroidTest` run — see `nubecita-1gf`.

## 7. End-to-end smoke + final verification

- [x] 7.1 Run `./gradlew :app:assembleDebug` — full app builds. (Green; ran 1m 32s.)
- [ ] 7.2 Manual smoke: install the debug APK on a 120 Hz device, log in, navigate to the Feed entry. Verify: list scrolls smoothly at 120 Hz (visually), pull-to-refresh works, scrolling near the tail loads more posts without duplicate visual flickers, leaving the entry and returning preserves scroll position, rotating the device preserves scroll position. Capture a 5-second `adb shell dumpsys gfxinfo` before/after to spot frame drops. (Deferred — owner runs locally before merge.)
- [x] 7.3 Run `./gradlew testDebugUnitTest` repo-wide — all unit tests green. (Hit a pre-existing drift in `RelativeTimeTest.kt` (kotlinx.datetime.Instant vs kotlin.time.Instant); tracked + fixed via `nubecita-bfz` in the same PR. Repo-wide tests now green.)
- [x] 7.4 Run `./gradlew :feature:feed:impl:validateDebugScreenshotTest` and (if available) `./gradlew :designsystem:validateDebugScreenshotTest` — both clean. (`:feature:feed:impl` validate green against the 26 committed baselines.)
- [x] 7.5 Run `./gradlew :app:lintDebug` — no new lint errors. Review any new informational notices and document if material. (Green; one informational baseline note about a previously-baselined warning that's now fixed — no action needed.)
- [x] 7.6 Run `./gradlew spotlessCheck` — clean. (Pre-commit's spotless hook covers this; ran clean across the branch.)
- [x] 7.7 Run `pre-commit run --all-files` — all hooks green.
- [x] 7.8 Run `openspec validate add-feature-feed-screen --strict` — passes.
- [x] 7.9 Update bd: `bd update nubecita-1d5 --notes "Implementation under openspec change add-feature-feed-screen; PR to follow"`. Don't close yet — `bd close` happens after merge. (Notes added on the issue.)

## 8. PR + archive

- [ ] 8.1 Branch already named `<conventional-type>/nubecita-1d5-...` per bd-workflow. Squash-merge title is the Conventional Commit subject.
- [ ] 8.2 PR body links the openspec change, lists the screenshot delta, and includes the `Closes: nubecita-1d5` footer.
- [ ] 8.3 After merge: `bd close nubecita-1d5` referencing the squash commit + PR.
- [ ] 8.4 Archive the openspec change: run the `/opsx:archive` flow (or `openspec archive add-feature-feed-screen`) so the delta merges into `openspec/specs/feature-feed/spec.md` and the change moves under `openspec/changes/archive/`.
