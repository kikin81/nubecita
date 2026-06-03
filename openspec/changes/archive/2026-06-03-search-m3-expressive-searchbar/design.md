## Context

The Search tab (`:feature:search:impl`) currently renders its input as a single-line `OutlinedTextField` in `ui/SearchInputRow.kt`, hosted by `SearchScreenContent`, which switches a `Column` body across a three-state `SearchPhase` (`Discover | Typeahead | Results`). `SearchViewModel` owns the editor `TextFieldState` (the sanctioned MVI exception), debounces it (250 ms) into `currentQuery` / `isQueryBlank` / `phase`, observes recent searches, and tracks a private `submittedQuery` to compute the phase.

Material 3 `1.5.0-alpha20` (already on the classpath) ships the Expressive `SearchBar` family. Verified against the artifact:

- `rememberSearchBarState(initialValue = SearchBarValue.Collapsed)` → `SearchBarState` with suspend `animateToExpanded()` / `animateToCollapsed()`, and `currentValue` / `targetValue` of `SearchBarValue.{Collapsed,Expanded}`.
- `SearchBarDefaults.InputField(textFieldState, searchBarState, onSearch, modifier, …, leadingIcon, trailingIcon, placeholder, keyboardOptions, lineLimits)` — an overload taking `KeyboardOptions` + `TextFieldLineLimits` directly, so `ImeAction.Search` + `SingleLine` carry over. `onSearch: (String) -> Unit`.
- `SearchBar(state, inputField, …)` (collapsed pill).
- `ExpandedFullScreenSearchBar(state, inputField, …) { /* ColumnScope */ }` (full-window overlay; content composes inside a `Column`).
- `ExpandedDockedSearchBar(...)` and `ExpandedFullScreenContainedSearchBar(...)` + `rememberContainedSearchBarState(...)` also exist — reserved for PR2 (`nubecita-h5zd.2`).

This change is PR1 of epic `nubecita-h5zd` (bd `nubecita-h5zd.1`): swap the input affordance, single-pane only.

## Goals / Non-Goals

**Goals:**
- Replace the bespoke input with the standard M3 Expressive `SearchBar` + full-screen expanded overlay.
- Preserve the existing query lifecycle, recent searches, typeahead, result tabs, and result-row navigation.
- Establish a single swappable expanded-container call site so PR2 only adds a Medium/Expanded branch, not a restructure.
- Keep the MVI boundary clean: `SearchBarState` in the Composable, not the VM.

**Non-Goals:**
- Tablet two-pane / list-detail (PR2).
- Any change to per-tab result ViewModels, repositories, paging, or navigation targets.
- Screenshot coverage of the expanded overlay (a popup window layoutlib cannot capture).
- Advanced-search filter chips (Gmail-style); not part of this app's model.

## Decisions

### D1 — Collapse-to-results interaction model (canonical M3)
The expanded overlay hosts only query-assist (recents / typeahead); submitting collapses the bar and shows the result tabs in the body. Chosen over keeping the bar expanded with results inside the overlay because it is the platform-canonical model, reuses the existing Results-tab body almost verbatim, and maps cleanly onto PR2's list-detail (results = list pane). Alternative (results in overlay) was rejected: larger restructure, less idiomatic.

### D2 — Overlay content from live text; body content from VM phase
Overlay content is selected from the live debounced query (`blank → recents`, `non-blank → SearchTypeaheadScreen`); body content from `SearchPhase` (`Discover → chips`, `Results → tabs`). Decoupling avoids the "re-open a submitted query to edit it" inconsistency that arises if the overlay keys off `phase` (where `text == submittedQuery` reads as `Results` even while editing). The overlay's visibility is governed by the screen-owned `SearchBarState`.

### D3 — `SearchBarState` owned by the Composable, not the VM
`rememberSearchBarState()` lives in `SearchScreenContent`. It is animation/runtime state, like scroll position and `LocalTabReTapSignal`, which CLAUDE.md keeps out of ViewModels. The VM keeps only the `TextFieldState` (editor exception) and derived projections. Submit/clear flow: on IME Search the screen calls `onEvent(SubmitClicked)` then `scope.launch { searchBarState.animateToCollapsed() }`.

### D4 — Simplify `SearchPhase` to `Discover | Results`
The `Typeahead` variant is removed because typeahead now renders only in the overlay (driven by live text), never in the body. `SearchViewModel.persistCurrent()` and `phaseFor()` drop the `Typeahead` computation; `Results(query)` retains its payload. This is a net VM simplification with no new responsibilities.

### D5 — Single width-keyed expanded-container seam
The expanded container is one call site:
```kotlin
when {
    widthClass.isAtLeastMedium -> { /* PR2: ExpandedDocked / ExpandedFullScreenContained */ }
    else -> ExpandedFullScreenSearchBar(state, inputField) { OverlayContent(...) }
}
```
PR1 fills only the `else` branch; the Medium/Expanded branch falls through to full-screen until PR2. Everything else (collapsed pill, `inputField`, `SearchBarState`, `OverlayContent`, Results body) is width-agnostic and shared.

### D6 — Recents rendered as a list inside the overlay
The overlay's blank state reuses the recent-search data and `RecentChipTapped` / `RecentChipRemoved` / `ClearAllRecents` events, presented as a vertical list (the `ColumnScope` content slot). The resting body continues to use `RecentSearchChipStrip` unchanged. No new VM events.

### D7 — Icons and IME
`leadingIcon` switches on `searchBarState.currentValue`: search icon when collapsed, back affordance (`animateToCollapsed()`) when expanded. `trailingIcon` shows the clear (X) when text is non-blank, calling `textFieldState.clearText()`. `InputField` keeps `KeyboardOptions(imeAction = ImeAction.Search)` and `TextFieldLineLimits.SingleLine`; `onSearch` dispatches `SubmitClicked` + collapse.

## Risks / Trade-offs

- **[Alpha API churn]** `material3 1.5.0-alpha20` SearchBar API may shift in later alphas → Pinned via the version catalog; all signatures verified against the resolved artifact; the swap is localized to one screen + one composable.
- **[No screenshot coverage of the overlay]** the expanded overlay renders in a popup window layoutlib cannot capture → Cover the input states + result tabs with collapsed-`SearchBar` baselines and the existing typeahead component tests; assert the lifecycle (phase transitions, submit, tab-retap) in VM unit tests.
- **[Collapse-without-submit text revert]** reverting the field to the last submitted query on back is custom glue not provided by the component → Implement as an explicit step in the screen's collapse handler; cover with a VM/screen unit test.
- **[IME inset behavior]** the full-screen overlay manages its own insets while the collapsed pill sits in the existing `Scaffold(containerColor = surface)` → No `adjustResize` changes needed (search is a top-level tab, not a bottom-pinned input); verify the pill clears the status bar.

## Migration Plan

1. Add the search-bar composable (collapsed pill + expanded overlay + `OverlayContent`) and wire `SearchBarState` in `SearchScreenContent`.
2. Simplify `SearchPhase` and `SearchViewModel`; update VM unit tests.
3. Delete `ui/SearchInputRow.kt` and `SearchInputRowScreenshotTest`; add collapsed-`SearchBar` baselines (regen on Mac, matching CI convention).
4. Run `spotless`, `lint`, `testDebugUnitTest`, and `validateDebugScreenshotTest`; open the PR with `Closes: nubecita-h5zd.1`.

Rollback: revert the PR; no data, schema, or dependency changes.

## Open Questions

- **PR2-deferred:** docked (`ExpandedDockedSearchBar`) vs contained-full-screen (`ExpandedFullScreenContainedSearchBar` + `rememberContainedSearchBarState`) for the tablet list-pane-scoped overlay. Decided in `nubecita-h5zd.2`; out of scope here.
