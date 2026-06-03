# Tasks — search-m3-expressive-searchbar (bd nubecita-h5zd.1)

## 1. ViewModel — simplify the phase model

- [x] 1.1 In `SearchContract.kt`, reduce `SearchPhase` to `Discover | Results(query)` (remove the `Typeahead` variant).
- [x] 1.2 In `SearchViewModel.kt`, drop the `Typeahead` computation from `phaseFor()` and the `persistCurrent()` path; keep `currentQuery` / `isQueryBlank` debounced projections and the recent-searches flow.
- [x] 1.3 Update `SearchViewModelTest` for the two-state phase: blank → `Discover`, submit → `Results`, edit-after-submit no longer produces a body `Typeahead` (overlay-only). Run `:feature:search:impl:testProductionDebugUnitTest`.

## 2. Search bar composable

- [x] 2.1 Add a `rememberSearchBarState(initialValue = Collapsed)` to `SearchScreenContent` and a remembered `CoroutineScope` for the suspend `animateTo*` calls.
- [x] 2.2 Build the shared `inputField` via `SearchBarDefaults.InputField(textFieldState, searchBarState, onSearch = { onEvent(SubmitClicked); scope.launch { animateToCollapsed() } }, placeholder, leadingIcon, trailingIcon, keyboardOptions = ImeAction.Search, lineLimits = SingleLine)`.
- [x] 2.3 Implement `leadingIcon` (search when collapsed; back→`animateToCollapsed()` when expanded) and `trailingIcon` (clear X when `!isQueryBlank` → `textFieldState.clearText()`), gated on `searchBarState.targetValue`.
- [x] 2.4 Render the collapsed `SearchBar(state, inputField)` at the top of the Scaffold body; verify the pill clears the status bar within `Scaffold(containerColor = surface)`.

## 3. Expanded overlay + content seam

- [x] 3.1 Add the width-keyed expanded-container call site (D5): `ExpandedFullScreenSearchBar(state, inputField) { SearchOverlayContent(...) }`; KDoc placeholder for the PR2 (`nubecita-h5zd.2`) Medium/Expanded docked/contained branch.
- [x] 3.2 Implement `SearchOverlayContent` (ColumnScope): blank query → recent-search **list** (`RecentSearchOverlayList`, reusing recents data + `RecentChipTapped`/`RecentChipRemoved`/`ClearAllRecents`); non-blank → existing `SearchTypeaheadScreen(currentQuery)`.
- [x] 3.3 Implement collapse-without-submit text revert: back affordance reverts `textFieldState` to `revertTargetFor(phase)` (last submitted query, or blank). `RevertTargetTest` covers the derivation.

## 4. Wire body + tab-retap

- [x] 4.1 Replace the `when (phase)` body with the two-branch form: `Discover → RecentSearchChipStrip` (unchanged), `Results → SearchResultsTabBar + HorizontalPager` (unchanged Posts/People/Feeds wiring).
- [x] 4.2 Change the `LocalTabReTapSignal` collector to `animateToExpanded()` (which focuses the field + pops the IME), replacing focus-field + `keyboardController.show()`.

## 5. Remove old input + tests

- [x] 5.1 Delete `ui/SearchInputRow.kt` and `screenshotTest/.../ui/SearchInputRowScreenshotTest.kt` (+ orphaned reference PNGs); remove now-dead imports.
- [x] 5.2 Add collapsed-`SearchBar` screenshot baselines (blank + with-query, light/dark) via `:feature:search:impl:updateProductionDebugScreenshotTest`. Kept tabbar / typeahead / results component baselines.

## 6. Verify

- [x] 6.1 `:feature:search:impl:spotlessApply` + `:feature:search:impl:lintProductionDebug` + `:feature:search:impl:testProductionDebugUnitTest` + `:app:assembleProductionDebug` (DI graph links).
- [x] 6.2 `:feature:search:impl:validateProductionDebugScreenshotTest` (post-regen) + root `testDebugUnitTest` to catch cross-module fakes.
- [ ] 6.3 Manual smoke on a phone/emulator: tap → expand → recents → type → typeahead → submit → collapse → tabs; clear; back-revert; tab re-tap. (Deferred — needs a device; tracked for pre-merge verification.)
