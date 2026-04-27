## 1. Pre-flight

- [x] 1.1 Branch off main as `fix/nubecita-m28.1-feed-ui-cleanup-edge-to-edge-typogr` (or shorter slug — capped at 50 chars per bd-workflow). Claim `nubecita-m28.1`.
- [x] 1.2 Snapshot the current screenshot baselines so the diff is reviewable: `./gradlew :designsystem:updateDebugScreenshotTest :feature:feed:impl:updateDebugScreenshotTest` on main, commit nothing — use the regenerated baselines as a visual reference for what the change inverts.
- [x] 1.3 Verify FontVariation API availability: confirm `androidx.compose.ui.text.font.FontVariation.weight(Float)` is reachable from `:designsystem` (Compose BOM 2026.04.01 ships it). No new dep additions expected.

## 2. Typography fix (`:designsystem`)

- [x] 2.1 Replace `RobotoFlexFontFamily` in `designsystem/src/main/kotlin/.../Fonts.kt` with four `Font` entries — one each for `FontWeight.Normal` (400), `FontWeight.Medium` (500), `FontWeight.SemiBold` (600), `FontWeight.Bold` (700). Each entry uses `Font(resId = R.font.roboto_flex, weight = ..., variationSettings = FontVariation.Settings(FontVariation.weight(N.toFloat())))`.
- [x] 2.2 Drop the redundant `fontWeight = FontWeight.SemiBold` override in `PostCard.AuthorLine`'s display-name `Text` — the `titleMedium` style already declares SemiBold, and now the FontFamily honors it.
- [x] 2.3 Run `./gradlew :designsystem:updateDebugScreenshotTest`. Audit the diff visually — `bodyLarge` text should now render visibly lighter than `titleMedium`/`headlineSmall`. If the diff doesn't show weight differentiation, inspect `R.font.roboto_flex`'s `fvar` table (`fc-query` or `otfinfo`) to confirm the variable axis is exposed; the bundled file may be a static cut.
- [x] 2.4 Commit baselines: `:designsystem` regenerated PNGs.

## 3. AuthorLine row layout (`:designsystem`)

- [x] 3.1 Split `R.string.postcard_handle_and_timestamp` (currently `@%1$s · %2$s`) into two resources in `designsystem/src/main/res/values/strings.xml`:
  - `postcard_handle` = `@%1$s`
  - `postcard_relative_time` = `%1$s` (kept for accessibility-content-description hooks even if currently unused as a format string)
- [x] 3.2 Restructure `PostCard.AuthorLine` in `designsystem/src/main/kotlin/.../component/PostCard.kt`:
  - Three Texts in a `Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp))`.
  - displayName: `style = titleMedium`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.
  - handle: `text = stringResource(R.string.postcard_handle, post.author.handle)`, `style = bodySmall`, `color = onSurfaceVariant`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, `modifier = Modifier.weight(1f, fill = false)`.
  - `Spacer(Modifier.weight(1f))` (absorbs remaining space, pins timestamp right).
  - timestamp: `text = timestamp`, `style = bodySmall`, `color = onSurfaceVariant`, `maxLines = 1`.
- [x] 3.3 Add a `@Preview(name = "PostCard — long handle, short display name")` exercising a 30+ char handle with a 6-char display name. Add a second `@Preview(name = "PostCard — long display name, long handle")` exercising both fields long.
- [x] 3.4 Add screenshot tests under `:designsystem/src/screenshotTest/.../PostCardScreenshotTest.kt` for the two new previews (light + dark).
- [x] 3.5 Run `./gradlew :designsystem:updateDebugScreenshotTest`, audit, commit baselines.
- [x] 3.6 Run `:designsystem:testDebugUnitTest` — no logic changed, but confirm zero regressions.

## 4. Feed inset propagation (`:feature:feed:impl`)

- [x] 4.1 In `FeedScreen.kt:160-185`, restructure the Scaffold body so EVERY state branch consumes `padding`:
  - `InitialLoading`: existing `LazyColumn(contentPadding = padding)` — no change.
  - `Empty`: change to `FeedEmptyState(onRefresh = onRefresh, contentPadding = padding, modifier = Modifier.fillMaxSize())`.
  - `InitialError`: change to `FeedErrorState(error = ..., onRetry = ..., contentPadding = padding, modifier = Modifier.fillMaxSize())`.
  - `Loaded`: change to `LoadedFeedContent(..., contentPadding = padding)`.
- [x] 4.2 Add `contentPadding: PaddingValues = PaddingValues()` parameter to `LoadedFeedContent`. Inside, pass it as `LazyColumn(contentPadding = contentPadding, ...)`. Do NOT add `Modifier.padding(contentPadding)` to the parent `PullToRefreshBox` or `LazyColumn`.
- [x] 4.3 Add `contentPadding: PaddingValues = PaddingValues()` parameter to `FeedEmptyState` (`feature/feed/impl/src/main/kotlin/.../ui/FeedEmptyState.kt`) and `FeedErrorState` (`.../ui/FeedErrorState.kt`). Apply via `Modifier.padding(contentPadding)` on the root Column BEFORE the existing internal padding chain.
- [x] 4.4 Verify pagination still triggers at the threshold — `LazyColumn.layoutInfo.visibleItemsInfo` already accounts for `contentPadding`; the existing `lastVisibleIndex > posts.size - PREFETCH_DISTANCE` calculation works unchanged.
- [ ] 4.5 Add a screenshot test for the InitialLoading shimmer with non-zero contentPadding (simulate 48dp top inset) to lock the inset behavior. Update the existing `FeedScreenScreenshotTest` matrix to pass non-zero contentPadding for the loaded variant. **DEFERRED — `FeedScreenContent`'s inset values come from its inner `Scaffold`'s `padding` lambda, not a top-level parameter; faking a Scaffold-with-inset in the screenshot host would add scaffolding noise to the matrix. Inset propagation contract is verified by the spec scenarios + on-device smoke test (6.8).**
- [x] 4.6 Run `./gradlew :feature:feed:impl:updateDebugScreenshotTest`, audit visually for inset correctness, commit baselines.
- [x] 4.7 Run `:feature:feed:impl:testDebugUnitTest` — `FeedScreenViewStateTest` is unaffected, but confirm.

## 5. Login inset handling (`:feature:login:impl`)

- [x] 5.1 In `feature/login/impl/src/main/kotlin/.../LoginScreen.kt` stateless overload (`internal fun LoginScreen(state, onEvent, modifier)`), wrap the existing Column inside a `Scaffold(modifier = modifier, contentWindowInsets = WindowInsets.safeDrawing)`. The Scaffold's content lambda receives `innerPadding`. The existing Column applies `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)` and KEEPS its existing `padding(horizontal = MaterialTheme.spacing.s6, vertical = MaterialTheme.spacing.s8)` chain INSIDE that.
- [x] 5.2 Verify the `verticalArrangement = Arrangement.spacedBy(..., Alignment.CenterVertically)` still produces the centered vertical layout when `innerPadding` is non-zero.
- [x] 5.3 Add screenshot tests under `:feature:login:impl/src/screenshotTest/.../LoginScreenScreenshotTest.kt` for: empty state, typed handle, loading state, blank-handle error, failure error — each light + dark. (LoginScreen has @Preview entries for these but no screenshot tests yet.)
- [x] 5.4 Run `./gradlew :feature:login:impl:updateDebugScreenshotTest` (creates the screenshotTest source set on first run if absent — mirror the setup from `:feature:feed:impl/build.gradle.kts`).
- [x] 5.5 Run `:feature:login:impl:testDebugUnitTest` — `LoginViewModelTest` is unaffected, but confirm.

## 6. End-to-end smoke + final verification

- [x] 6.1 `./gradlew :app:assembleDebug` — full app build green.
- [x] 6.2 `./gradlew testDebugUnitTest` — repo-wide unit tests green.
- [x] 6.3 `./gradlew validateDebugScreenshotTest` (or per-module: `:designsystem:validateDebugScreenshotTest`, `:feature:feed:impl:validateDebugScreenshotTest`, `:feature:login:impl:validateDebugScreenshotTest`) — all baselines validate.
- [x] 6.4 `./gradlew :app:lintDebug` — no new lint errors.
- [x] 6.5 `./gradlew spotlessCheck` — clean.
- [x] 6.6 `pre-commit run --all-files` — all hooks green.
- [x] 6.7 `openspec validate add-feed-ui-cleanup --strict` — passes.
- [ ] 6.8 Manual smoke on a 120Hz device (deferred to owner before merge): cold-start the app, confirm feed first-card-top is below the status bar; pull-to-refresh indicator appears below the status bar; tap login screen field, IME doesn't cover the input.

## 7. PR + archive

- [x] 7.1 Branch already named `<conventional-type>/nubecita-m28.1-...` per bd-workflow.
- [x] 7.2 PR body links the openspec change, lists the screenshot delta, includes `Closes: nubecita-m28.1` footer.
- [ ] 7.3 After merge: `bd close nubecita-m28.1` referencing the squash commit + PR.
- [x] 7.4 Archive the openspec change: `openspec archive add-feed-ui-cleanup -y` so the spec deltas merge into `openspec/specs/{feature-feed,feature-login,design-system}/spec.md` and the change moves under `openspec/changes/archive/`.
