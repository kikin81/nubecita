## Why

The Search tab's input is a bespoke single-line `OutlinedTextField` (`SearchInputRow`) wrapped in a hand-rolled three-phase body. Material 3 Expressive now ships a first-class `SearchBar` (full-corner collapsed pill that expands to a full-screen typing surface), which is the platform-canonical search affordance, animates the collapse/expand transition for free, and — via its docked/contained expanded variants — is the foundation the upcoming tablet list-detail layout needs. Adopting it now replaces custom code with the standard component and unblocks the adaptive-panes follow-up (PR2, `nubecita-h5zd.2`).

This is PR1 of the "Search panes" epic (`nubecita-h5zd`), tracked as `nubecita-h5zd.1`. It is **single-pane only**; the tablet two-pane list-detail is PR2.

## What Changes

- Replace `SearchInputRow`'s `OutlinedTextField` with the M3 Expressive `SearchBar` (collapsed full-corner pill) at the top of the Search tab body.
- Add an `ExpandedFullScreenSearchBar` overlay as the active typing surface. A single shared `SearchBarDefaults.InputField(textFieldState, searchBarState, …)` lambda backs both the collapsed pill and the expanded overlay.
- Adopt the **collapse-to-results** interaction model: expanded overlay shows recents (blank) or typeahead (typing); submitting collapses the bar and renders the existing Posts/People/Feeds result tabs in the body below the collapsed pill.
- Drive overlay content from **live query text** (blank → recents, non-blank → existing `SearchTypeaheadScreen`) and body content from **VM phase** (Discover → recent chips, Results → tabs), decoupling the screen-owned expand/collapse state from VM state.
- Own `SearchBarState` in the screen Composable (`rememberSearchBarState()`), not the ViewModel — it is an animation/runtime concern, consistent with how scroll state and the tab-retap signal are handled.
- Simplify `SearchPhase` from `Discover | Typeahead | Results` to `Discover | Results`; the `Typeahead` body branch is removed (typeahead now lives only in the overlay).
- Route the expanded container through a single width-class-keyed call site, with the Medium/Expanded branch left as a `// PR2` placeholder that falls through to full-screen for now.
- Update the tab-retap behavior to `animateToExpanded()` + focus (replacing the focus-field + show-keyboard call).
- Delete `SearchInputRow.kt` and its screenshot test; add collapsed-`SearchBar` screenshot baselines.
- Opt into `@ExperimentalMaterial3Api` (user-approved). No dependency or version bump — `material3 1.5.0-alpha20` already ships the API.

## Capabilities

### New Capabilities
- `feature-search`: The Search tab's input affordance and query lifecycle — collapsed/expanded search bar states, the collapse-to-results model, resting/overlay/results content rules, recent-search surfacing, and submit/clear/tab-retap behavior. (This change establishes the search-input requirements; PR2 will extend the same capability with list-detail pane requirements.)

### Modified Capabilities
<!-- None. No existing capability's requirements change; the search feature had no prior spec. -->

## Impact

- **Code:** `:feature:search:impl` — `SearchScreen.kt` (`SearchScreenContent`), new search-bar composable, deletion of `ui/SearchInputRow.kt`; `SearchViewModel.kt` + `SearchContract.kt` (`SearchPhase` simplification); recents rendered as a list inside the overlay (reusing the recents data + events).
- **Tests:** Remove `SearchInputRowScreenshotTest`; add collapsed-`SearchBar` baselines; keep tabbar/typeahead/results component tests; VM unit tests updated for the `SearchPhase` change. The full-screen overlay is a popup window layoutlib cannot capture, so it stays covered by VM unit tests + existing typeahead component tests, not a screen-level screenshot.
- **APIs/deps:** No new dependency. New `@OptIn(ExperimentalMaterial3Api::class)` surface.
- **No deviation** from MVI / Compose / Hilt baseline — the editor `TextFieldState` exception (already in use here) continues; `SearchBarState` follows the established "Compose-runtime state lives in the Composable, not the VM" rule.
- **Downstream:** Establishes the swappable expanded-container seam that PR2 (`nubecita-h5zd.2`) fills with the docked/contained pane-scoped variant.
