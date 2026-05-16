# SearchScreen orchestration — design

**Date:** 2026-05-16
**Scope:** `nubecita-vrba.8` — wire the per-tab stateful entries shipped by `vrba.6` (`SearchPostsScreen`) and `vrba.7` (`SearchActorsScreen`) into the parent `SearchScreen` (shipped by `vrba.5`) via a `SecondaryTabRow` + `HorizontalPager`, hoist a `SnackbarHostState` for tab-level append errors, and route the per-tab `onClearQuery` callback back to the parent's `TextFieldState`. No new business logic, no new VM, no new contract types — pure composition + a small handful of strings.
**Status:** Draft for user review.

## Why this slice

After `vrba.5/.6/.7`, the three VMs and their entry composables exist but live alone — opening the Search tab in `MainShell` lands on the placeholder Idle screen with the input row + recent-search chip strip (`vrba.5`'s output), and the user has no way to reach the Posts or People tab content despite both being fully built. `vrba.8` is the last orchestration step before the Search tab is reachable end-to-end. It also unblocks `vrba.11` (Feeds tab) which needs to drop in as a 3rd `HorizontalPager` page.

## Inherited from vrba.5 / .6 / .7 (no new design work)

| Inherited | Substitution / consumption |
|---|---|
| `SearchViewModel.textFieldState: TextFieldState` (vrba.5 D3) | `SearchScreen` calls `viewModel.textFieldState.clearText()` directly when a per-tab `onClearQuery` callback fires. No new SearchEvent. |
| `SearchScreenViewState.currentQuery: String` (vrba.5 D4) | Passed unchanged into each `Search{Posts,Actors}Screen`'s `currentQuery` param. The per-tab `LaunchedEffect(currentQuery)` already shipped in vrba.6/.7 handles propagation to the per-tab VM. |
| `SearchScreenViewState.isQueryBlank: Boolean` (vrba.5 D6) | Drives the mutually-exclusive "chip strip vs. tab content" switch — see O5. |
| `SearchPostsScreen(currentQuery, onClearQuery, onShowAppendError, ...)` (vrba.6 D8) | Mounted on Pager page 0. `onClearQuery` → `textFieldState.clearText()`. `onShowAppendError` → Snackbar. |
| `SearchActorsScreen(currentQuery, onClearQuery, onShowAppendError, ...)` (vrba.7 D8) | Mounted on Pager page 1. Same callback wiring. |
| Per-tab `NavigateToPost` / `NavigateToProfile` effects (vrba.6 D9 + vrba.7 A3) | Already routed inside each per-tab Screen to `LocalMainShellNavState.current.add(...)`. `vrba.8` does NOT collect these at the parent — vrba.9's "tap-through nav" ticket is effectively folded into vrba.6/.7. |

The deltas below are vrba.8-specific orchestration decisions.

## Decisions

### O1. `SecondaryTabRow` with two tabs ("Posts", "People")

Material 3 Expressive ships both `PrimaryTabRow` (high-emphasis, used at the top of a Scaffold-level surface) and `SecondaryTabRow` (lower-emphasis, used for sub-content navigation inside a screen). The Search tabs sit *inside* `MainShell`'s primary `NavigationBar`-driven tab — using `PrimaryTabRow` here would visually compete with the bottom bar's selection indicator. `SecondaryTabRow` is the right hierarchy.

Two tabs in vrba.8: **Posts** (page 0) and **People** (page 1). Order matches the design handoff. `vrba.11` will add **Feeds** as page 2 — additive, no reorder.

### O2. `HorizontalPager` for swipe-between-tabs

Material 3 idiom for tabbed content. `rememberPagerState { 2 }` (or `{ 3 }` once Feeds lands) drives both the `SecondaryTabRow`'s selected index AND the rendered page. Tapping a tab calls `pagerState.animateScrollToPage(...)` from a `rememberCoroutineScope`. Swiping the body animates the tab indicator in step.

**Key parameter:** `beyondViewportPageCount = 1` so the inactive tab's `Search{Posts,Actors}Screen` stays composed (and its VM stays alive) while the user is on the other tab. Without this, switching tabs would unmount + remount + re-fetch, defeating the "switching tabs preserves results" acceptance criterion in the bd ticket. The trade-off is one extra `hiltViewModel<>()` instance + Idle-state composition cost — negligible relative to the UX win.

### O3. Tab state lives in `rememberPagerState`, NOT `SearchScreenViewState`

The selected tab index is pure Compose UI state — no side effects, doesn't influence which data is loaded. `rememberPagerState`'s built-in `Saver` survives configuration changes. Adding `selectedTab: Int` to `SearchScreenViewState` would force the parent VM to care about UI-only state and introduce churn for every swipe.

### O4. TabRow + Pager are mutually exclusive with the recent-search chip strip

Per vrba.5 D6 the chip strip is hidden when `recentSearches.isEmpty()`. Per the design handoff, when the user has an active query, the chip strip should also be hidden (visual noise to keep "recent searches" visible alongside results from a different query).

Concrete rule (lives in `SearchScreenContent`):

```kotlin
if (isQueryBlank) {
    // pre-search shell
    if (recentSearches.isNotEmpty()) RecentSearchChipStrip(...)
} else {
    // search-in-progress shell
    SecondaryTabRow(selectedTabIndex = pagerState.currentPage) { ... }
    HorizontalPager(state = pagerState, beyondViewportPageCount = 1) { page ->
        when (page) {
            0 -> SearchPostsScreen(currentQuery = currentQuery, ...)
            1 -> SearchActorsScreen(currentQuery = currentQuery, ...)
        }
    }
}
```

When the user clears the field (via the input's clear button, via the per-tab "Clear search" empty-state CTA, or via backspace-to-empty), `isQueryBlank` flips to true after the 250ms debounce → the TabRow + Pager unmount, the chip strip re-renders (if any recents exist). Symmetric and clean.

**Side benefit:** clearing the query unmounts both per-tab Screens — their VMs are released by Hilt + the `viewModelStoreOwner`. Re-typing a query remounts → fresh VMs → fresh fetch. No stale state to manage. (This is also why `Search{Posts,Actors}ViewModel.loadStatus` already resets to `Idle` on blank in vrba.6 fix + vrba.12 backport, in case the Pager is held in composition for any reason — defense in depth.)

### O5. `Scaffold(snackbarHost = ...)` wraps the existing Column

Today `SearchScreen` renders a plain `Column { ... }`. Promote it to a `Scaffold` so a `SnackbarHost(snackbarHostState)` slot is available. The Scaffold's inner content is the same Column shape (input row + conditional chips/tabs), padded by Scaffold's `innerPadding` for the status bar inset.

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
) { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
        // input row, then conditional content per O4
    }
}
```

### O6. `onShowAppendError` callback resolves to a Snackbar via a `rememberCoroutineScope`

Each per-tab Screen exposes `onShowAppendError: (SearchPostsError) -> Unit` / `(SearchActorsError) -> Unit`. `SearchScreen` wires:

```kotlin
val snackScope = rememberCoroutineScope()
val onPostsAppendError: (SearchPostsError) -> Unit = remember(snackbarHostState) {
    { error ->
        snackScope.launch { snackbarHostState.showSnackbar(message = error.toAppendSnackbarMessage(context)) }
    }
}
// same for onActorsAppendError
```

Two new tiny VM-side mappers (each in its own file or co-located with the existing error file):

```kotlin
// :feature:search:impl/SearchPostsError.kt (extend)
@Composable
internal fun SearchPostsError.appendSnackbarMessage(): String =
    when (this) {
        Network -> stringResource(R.string.search_posts_append_error_network)
        RateLimited -> stringResource(R.string.search_posts_append_error_rate_limited)
        is Unknown -> stringResource(R.string.search_posts_append_error_unknown)
    }
```

Or — cleaner — resolve the message inside the `SearchScreen`'s `onShowAppendError` lambda using `LocalContext.current.getString(...)`. The strings already exist (vrba.6 added `search_posts_append_error_*`, vrba.7 added `search_people_append_error_*`). Implementer picks the cleanest form.

**Snackbars from the inactive tab still show.** With `beyondViewportPageCount = 1`, the inactive tab's append fetch can fail while the user is on the other tab. The snackbar message includes "posts" or "people" in the copy already (vrba.6/.7 strings are tab-contextual) so the user understands which tab failed. Not surprising; matches Bluesky native UX.

### O7. `onClearQuery` calls `viewModel.textFieldState.clearText()` directly

The parent `SearchViewModel`'s editor-VM exception (vrba.5 D3) makes `textFieldState` public. The screen Composable mutates it directly from the per-tab `onClearQuery` callback:

```kotlin
val onClearQuery: () -> Unit = remember(viewModel) {
    { viewModel.textFieldState.clearText() }
}
```

After `clearText()`, the snapshotFlow collector inside `SearchViewModel` (`init { snapshotFlow { textFieldState.text.toString() }.debounce(250.ms) ... }`) updates `state.currentQuery = ""` after the debounce. That triggers the per-tab Screen's `LaunchedEffect(currentQuery)` → `vm.setQuery("")` → blank-query branch → `loadStatus = Idle`. The `isQueryBlank` flip also collapses the TabRow per O4.

No new `SearchEvent` needed.

### O8. Input-row clear button calls the same path

The existing `SearchInputRow` (vrba.5) already has a trailing clear button. Verify it calls `textFieldState.clearText()` directly (it should — that's the canonical pattern). If it currently dispatches a `SearchEvent.Clear`-style event, leave it — both paths converge on the same mutation. No change needed in vrba.8 unless the implementer finds inconsistency.

### O9. SearchViewModel changes: none

No new events, no new state fields, no new effects. vrba.8 is pure composition.

(If a future ticket adds Snackbar surfacing for VM-level errors — e.g., recent-search persistence write failure — that's when `SearchEffect` grows. Out of scope.)

### O10. Tab labels are simple `stringResource`

```xml
<string name="search_tab_posts">Posts</string>
<string name="search_tab_people">People</string>
```

### O11. No NestedScrollConnection / collapsing input row

The design handoff hints at a collapsing search input + tab bar that retreats as the user scrolls through results. **Out of scope for vrba.8.** Add as a follow-up (`vrba.13` or similar) once the basic orchestration ships. Reasoning: collapsing-toolbar interactions are a notoriously fiddly area (we wrestled with this in the Profile epic), and shipping the simpler always-visible version unblocks vrba.11 + the end-to-end Search feature without the polish risk.

### O12. Vrba.11 (Feeds tab) extension path

When `vrba.11` lands, the changes to `SearchScreen` are mechanical:
1. Add `<string name="search_tab_feeds">Feeds</string>`.
2. Bump `rememberPagerState { 3 }`.
3. Add the 3rd `SecondaryTabRow` `Tab(...)` entry.
4. Add `2 -> SearchFeedsScreen(currentQuery = currentQuery, ...)` to the Pager's `when`.

No structural refactor. The spec for `vrba.11` will reference this section.

## MVI contract

No changes.

## UI components

### Modified

- **`SearchScreen.kt`** (stateful) — unchanged stateful entry. Hoists VM, passes `state.recentSearches` / `state.isQueryBlank` / `state.currentQuery` / `viewModel.textFieldState` / `viewModel::handleEvent` plus the new `viewModel` reference (for direct `textFieldState.clearText()` access) down to `SearchScreenContent`.
- **`SearchScreenContent.kt`** (stateless) — gains:
  - A new param `onClearQueryRequest: () -> Unit` (or pass the `viewModel` for direct mutation if the stateless Composable can accept it cleanly — preview-friendliness suggests the callback form is cleaner since previews can pass `onClearQueryRequest = {}` instead of a fake VM).
  - The TabRow + Pager block, gated on `!isQueryBlank`.
  - A Scaffold wrapper with a `SnackbarHost` slot.

### New

None. All composables are inline modifications to `SearchScreenContent`, possibly extracted into a small `ui/SearchResultsTabPager.kt` helper if the implementer judges the inline form is too large (no specific size threshold — judge by readability).

### Reused unchanged

- `SearchPostsScreen` (vrba.6) — consumed as a Pager page.
- `SearchActorsScreen` (vrba.7) — consumed as a Pager page.
- `SearchInputRow` (vrba.5).
- `RecentSearchChipStrip` (vrba.5).

## Files

### Modified

- `feature/search/impl/src/main/kotlin/.../SearchScreen.kt` — Scaffold wrap, conditional chip/tab content per O4, callback wiring per O6 + O7.
- `feature/search/impl/src/main/res/values/strings.xml` — `search_tab_posts`, `search_tab_people`.

### New screenshot tests

- `feature/search/impl/src/screenshotTest/.../SearchScreenScreenshotTest.kt` — extend the existing file with new variants: blank-query + empty recents (input only), blank-query + non-empty recents (input + chips), non-blank query + Posts tab selected, non-blank query + People tab selected. Light + dark × 4 variants = 8 baselines. The existing vrba.5-era `SearchScreen` screenshot test stays as a "blank-query + non-empty recents" baseline (regenerate if the Scaffold padding shifts pixels).

## Test buckets

Per the UI-task convention: **unit + previews + screenshot**.

### Unit (plain JVM)

No new VM unit tests — vrba.8 doesn't add reducer logic. The existing `SearchViewModelTest` (vrba.5) stays as-is.

If the implementer extracts any non-trivial composition logic into a helper (e.g., a `derivedStateOf` over query + pager state), unit-test that helper. Don't manufacture tests for the composition itself.

### Compose previews

Inside `SearchScreen.kt` or `ui/SearchResultsTabPager.kt`:

- `SearchScreenContentPreview_blankQuery_noRecents`
- `SearchScreenContentPreview_blankQuery_withRecents`
- `SearchScreenContentPreview_postsTabSelected_loaded`
- `SearchScreenContentPreview_peopleTabSelected_loaded`

The previews pass canned `recentSearches` lists + a `TextFieldState` seeded with the right text + a stubbed `Search{Posts,Actors}Screen`. Since `Search{Posts,Actors}Screen` hoist `hiltViewModel()`, previews can't render them directly without a Hilt graph — use `@Preview(showBackground = true) Composable { … }` and wrap the previewed `SearchScreenContent` with a no-Hilt path (i.e., the preview omits the tab content area, OR passes `tabsSlot = { Box(Modifier.fillMaxSize().background(...)) { Text("Stub Posts/People content") } }`).

**Trade-off:** if the implementer wants the preview to show real tab content, they'd need to introduce a slot-based variant of `SearchScreenContent` that accepts the tab body as a `@Composable` slot. That's a small extra hop. If it complicates the file, fall back to the "stub" preview approach. The screenshot tests below are what actually exercises the visual integration.

### Screenshot tests

Mirror the preview list with `@PreviewTest` × light/dark. ~8 baselines. The existing `SearchScreenScreenshotTest.kt` may need its baselines regenerated due to the Scaffold padding wrap.

### Instrumented tests (out of scope, documented follow-up)

A `connectedDebugAndroidTest` for "swipe between tabs preserves each tab's scroll position + VM state" would be the cleanest validation of the `beyondViewportPageCount = 1` setting. Add as a follow-up ticket so the `run-instrumented` label isn't needed on this PR.

## Risk + rollback

- **Risk: `SecondaryTabRow` not present in the project's Compose Material 3 version.** Implementer verifies with `grep -r "SecondaryTabRow" /Users/velazquez/code/nubecita/`; if absent, falls back to `TabRow` (Primary) — visual style slightly heavier but functionally identical. Document inline.
- **Risk: `beyondViewportPageCount` parameter name / default has changed across Compose Foundation versions.** Implementer verifies via the IDE / docs; if the parameter doesn't exist, use the older `beyondBoundsPageCount` name. Either way, the goal is "keep both pages composed".
- **Risk: `viewModelStoreOwner` scoping bug — both per-tab Screens hoist `hiltViewModel()` from the same `NavBackStackEntry`.** If two `@HiltViewModel`-annotated classes share a `viewModelStoreOwner`, Hilt resolves each by type — different types, different instances. Should work cleanly. If not, the fallback is to pass each Screen an explicit `viewModelStoreOwner` parameter (e.g., wrap each page in `CompositionLocalProvider(LocalViewModelStoreOwner provides ...)`). Verify in `:app:assembleDebug` + a quick on-device sanity check.
- **Risk: Chip strip + TabRow visibility transition is jarring during the 250ms debounce.** When the user types a character, `isQueryBlank` flips from true to false after debounce — that's a discrete 250ms gap. During the gap, the chip strip is still visible AND no tab content yet. Acceptable for V1; could add an `AnimatedVisibility` crossfade as polish in a follow-up.
- **Rollback:** The change is contained to `SearchScreen.kt` + a few strings + screenshot baselines. Reverting restores the placeholder Idle screen — the per-tab Screens are still in the codebase, just not mounted. No data-layer or VM rollback needed.

## Out of scope

- **Vrba.9 (tap-through nav).** Already effectively folded into vrba.6/.7's Screen-level effect collection. After vrba.8 lands and on-device verification confirms taps work end-to-end, close vrba.9 as "implemented as part of vrba.6/.7".
- **Vrba.11 (Feeds tab).** This spec calls out the 4-step extension path (O12). vrba.11 ships its own data layer + VM + tab content.
- **Vrba.10 (typeahead grouped suggestions screen).** Separate child issue.
- **Collapsing input row / NestedScrollConnection** — O11.
- **Pull-to-refresh on tab content** — search has no pull-to-refresh per vrba.6 spec.
- **Tab badges / hit counts** — would need `hitsTotal` on the per-tab `Loaded` (deliberately dropped from vrba.6/.7 per their spec deviations).
- **Instrumented tests** for swipe + tab state preservation — follow-up ticket; not gating MVP.

## Bd structure

Single bd child (`nubecita-vrba.8`), single PR. All three prerequisite VMs (`vrba.5/.6/.7`) merged.

After merge: close `nubecita-vrba.9` as "implemented as part of vrba.6/.7" (no separate PR needed).

## Open questions

None outstanding. Ready for review.
