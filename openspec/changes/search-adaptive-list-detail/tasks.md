# Tasks — search-adaptive-list-detail (bd nubecita-h5zd.2)

## 1. Search as a list pane

- [ ] 1.1 Add `implementation(libs.androidx.compose.material3.adaptive.navigation3)` to `feature/search/impl/build.gradle.kts` (keep deps sorted; run `:app:checkSortDependencies`).
- [ ] 1.2 Add a placeholder string `search_detail_pane_select_post` ("Select a post to read" or similar).
- [ ] 1.3 In `di/SearchNavigationModule.kt`, tag `entry<Search>` with `ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPaneEmptyState(icon = NubecitaIconName.Article, message = stringResource(...)) })`; add `@OptIn(ExperimentalMaterial3AdaptiveApi::class)`.

## 2. Width-gated expanded container

- [ ] 2.1 In `SearchScreenContent`, read the window width class (same source as `MainShell`, e.g. `currentWindowAdaptiveInfo().windowSizeClass`) and pass an `isAtLeastMedium: Boolean` (or the size class) into `SearchBarSection`.
- [ ] 2.2 In `SearchBarSection`, replace the single `ExpandedFullScreenSearchBar` call with the width branch: Medium/Expanded → `ExpandedDockedSearchBar(state, inputField) { SearchOverlayContent(...) }`; else → `ExpandedFullScreenSearchBar(...)`. Add `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. Update the KDoc (drop the `// PR2` placeholder note).
- [ ] 2.3 Verify the collapsed pill, `inputField`, `SearchBarState` (plain `rememberSearchBarState()`), and overlay content are shared/unchanged across both branches.

## 3. Detail selection semantics

- [ ] 3.1 Change the post-tap navigation in `SearchPostsScreen` (and any other post-push site) from `navState.add(PostDetailRoute(...))` to `navState.replaceTop(PostDetailRoute(...))`. Leave actor `navState.add(Profile(...))` unchanged.
- [ ] 3.2 (Optional, D4) Derive the open post URI from the back stack and highlight the selected result row on Medium/Expanded (mirror `selectedConvoDid`). Defer if it complicates the list.

## 4. Tests

- [ ] 4.1 Screenshot baselines for the Search list-pane home at Compact / Medium / Expanded (360 / 600 / 840 dp), light + dark; regenerate via `:feature:search:impl:updateProductionDebugScreenshotTest`. Confirm Compact baselines match PR1.
- [ ] 4.2 Confirm existing VM / typeahead / tab component tests still pass (no VM changes expected).

## 5. Verify

- [ ] 5.1 `spotlessApply` + `:feature:search:impl:lintProductionDebug` + `:feature:search:impl:testProductionDebugUnitTest` + `:app:assembleProductionDebug` (+ `:app:checkSortDependencies` for the new dep).
- [ ] 5.2 `:feature:search:impl:validateProductionDebugScreenshotTest` (post-regen) + root `testDebugUnitTest`.
- [ ] 5.3 Tablet smoke test on the bench build (`:app:assembleBenchDebug`, `emulator-5556`): Search two-pane home → submit → results in list pane → tap post fills detail pane → tap another swaps → Back returns to results → expand search shows the docked popup with detail pane still visible. Open PR with `Closes: nubecita-h5zd.2`.
