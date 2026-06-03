# Tasks — search-adaptive-list-detail (bd nubecita-h5zd.2)

## 1. Search as a list pane

- [x] 1.1 Add `implementation(libs.androidx.compose.material3.adaptive.navigation3)` to `feature/search/impl/build.gradle.kts` (keep deps sorted; run `:app:checkSortDependencies`).
- [x] 1.2 Reuse the shared `:designsystem` string `R.string.nubecita_detail_pane_select_post` ("Select a post to read") that Feed/Profile already use — no new string.
- [x] 1.3 In `di/SearchNavigationModule.kt`, tag `entry<Search>` with `ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPaneEmptyState(icon = NubecitaIconName.Article, message = stringResource(...)) })`; add `@OptIn(ExperimentalMaterial3AdaptiveApi::class)`.

## 2. Width-gated expanded container

- [x] 2.1 In `SearchScreenContent`, read the window width class (same source as `MainShell`, e.g. `currentWindowAdaptiveInfoV2().windowSizeClass`) and pass an `isAtLeastMedium: Boolean` (or the size class) into `SearchBarSection`.
- [x] 2.2 In `SearchBarSection`, replace the single `ExpandedFullScreenSearchBar` call with the width branch: Medium/Expanded → `ExpandedDockedSearchBar(state, inputField) { SearchOverlayContent(...) }`; else → `ExpandedFullScreenSearchBar(...)`. Add `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. Update the KDoc (drop the `// PR2` placeholder note).
- [x] 2.3 Verify the collapsed pill, `inputField`, `SearchBarState` (plain `rememberSearchBarState()`), and overlay content are shared/unchanged across both branches.

## 3. Detail selection semantics

- [x] 3.1 Change the post-tap navigation in `SearchPostsScreen` (and any other post-push site) from `navState.add(PostDetailRoute(...))` to `navState.replaceTop(PostDetailRoute(...))`. Leave actor `navState.add(Profile(...))` unchanged.
- [ ] 3.2 (Optional, D4) Derive the open post URI from the back stack and highlight the selected result row on Medium/Expanded (mirror `selectedConvoDid`). Defer if it complicates the list.

## 4. Tests

- [x] 4.1 Added a `@PreviewNubecitaScreenPreviews` device sweep (Phone / Foldable / Tablet × Light/Dark) for the Search list-pane home, matching the Feed convention; regenerated baselines. Existing Compact baselines unchanged (collapsed render is width-independent).
- [x] 4.2 Confirm existing VM / typeahead / tab component tests still pass (no VM changes expected).

## 5. Verify

- [x] 5.1 `spotlessApply` + `:feature:search:impl:lintProductionDebug` + `:feature:search:impl:testProductionDebugUnitTest` + `:app:assembleProductionDebug` (+ `:app:checkSortDependencies` for the new dep).
- [x] 5.2 `:feature:search:impl:validateProductionDebugScreenshotTest` (post-regen) + root `testDebugUnitTest`.
- [x] 5.3 Tablet smoke test on the bench build (`:app:assembleBenchDebug`, `emulator-5556`): Search two-pane home → submit → results in list pane → tap post fills detail pane → tap another swaps → Back returns to results → expand search shows the docked popup with detail pane still visible. Open PR with `Closes: nubecita-h5zd.2`.
