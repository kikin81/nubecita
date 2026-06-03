# Tasks — search-m3-expressive-searchbar (bd nubecita-h5zd.1)

## 1. ViewModel — simplify the phase model

- [ ] 1.1 In `SearchContract.kt`, reduce `SearchPhase` to `Discover | Results(query)` (remove the `Typeahead` variant).
- [ ] 1.2 In `SearchViewModel.kt`, drop the `Typeahead` computation from `phaseFor()` and the `persistCurrent()` path; keep `currentQuery` / `isQueryBlank` debounced projections and the recent-searches flow.
- [ ] 1.3 Update `SearchViewModelTest` for the two-state phase: blank → `Discover`, submit → `Results`, edit-after-submit no longer produces a body `Typeahead` (overlay-only). Run `:feature:search:impl:testDebugUnitTest`.

## 2. Search bar composable

- [ ] 2.1 Add a `rememberSearchBarState(initialValue = Collapsed)` to `SearchScreenContent` and a remembered `CoroutineScope` for the suspend `animateTo*` calls.
- [ ] 2.2 Build the shared `inputField` via `SearchBarDefaults.InputField(textFieldState, searchBarState, onSearch = { onEvent(SubmitClicked); scope.launch { animateToCollapsed() } }, placeholder, leadingIcon, trailingIcon, keyboardOptions = ImeAction.Search, lineLimits = SingleLine)`.
- [ ] 2.3 Implement `leadingIcon` (search when collapsed; back→`animateToCollapsed()` when expanded) and `trailingIcon` (clear X when `!isQueryBlank` → `textFieldState.clearText()`), gated on `searchBarState.currentValue`.
- [ ] 2.4 Render the collapsed `SearchBar(state, inputField)` at the top of the Scaffold body; verify the pill clears the status bar within `Scaffold(containerColor = surface)`.

## 3. Expanded overlay + content seam

- [ ] 3.1 Add the width-keyed expanded-container call site (D5): `else → ExpandedFullScreenSearchBar(state, inputField) { OverlayContent(...) }`; leave a `// PR2: nubecita-h5zd.2` placeholder for the Medium/Expanded branch (falls through to full-screen for now).
- [ ] 3.2 Implement `OverlayContent` (ColumnScope): blank query → recent-search **list** (reuse recents data + `RecentChipTapped`/`RecentChipRemoved`/`ClearAllRecents`); non-blank → existing `SearchTypeaheadScreen(currentQuery)`.
- [ ] 3.3 Implement collapse-without-submit text revert: on back-affordance collapse, revert `textFieldState` to the last submitted query (or blank). Add a unit/screen test asserting the revert.

## 4. Wire body + tab-retap

- [ ] 4.1 Replace the `when (phase)` body with the two-branch form: `Discover → RecentSearchChipStrip` (unchanged), `Results → SearchResultsTabBar + HorizontalPager` (unchanged Posts/People/Feeds wiring).
- [ ] 4.2 Change the `LocalTabReTapSignal` collector to `animateToExpanded()` + focus the input field (replacing focus-field + `keyboardController.show()`).

## 5. Remove old input + tests

- [ ] 5.1 Delete `ui/SearchInputRow.kt` and `screenshotTest/.../ui/SearchInputRowScreenshotTest.kt`; remove now-dead imports/strings.
- [ ] 5.2 Add collapsed-`SearchBar` screenshot baselines (blank + with-query, light/dark); regenerate on Mac via `:feature:search:impl:updateDebugScreenshotTest`. Keep tabbar / typeahead / results component baselines.

## 6. Verify

- [ ] 6.1 `./gradlew spotlessCheck lint :app:checkSortDependencies` and `./gradlew :feature:search:impl:testDebugUnitTest`.
- [ ] 6.2 `./gradlew :feature:search:impl:validateDebugScreenshotTest` (after regen) and a root `./gradlew testDebugUnitTest` to catch cross-module fakes.
- [ ] 6.3 Manual smoke on a phone/emulator: tap → expand → recents → type → typeahead → submit → collapse → tabs; clear; back-revert; tab re-tap. Open PR with `Closes: nubecita-h5zd.1`.
