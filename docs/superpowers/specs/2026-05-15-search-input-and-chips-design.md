# Search input row + recent-search chips — design

**Date:** 2026-05-15
**Scope:** `nubecita-vrba.5` — the parent `SearchViewModel` + search input row + recent-search chip strip in `:feature:search:impl`. User-facing entry point of the Search tab.
**Status:** Draft for user review.

## Why this slice

The Search epic (`nubecita-vrba`) decomposes into nine landing PRs. `vrba.5` is the **input** half — the parent VM that owns the canonical query state, the search bar, and the recent-search chip strip. It consumes the recent-search persistence shipped in `vrba.2`. It does **not** include the result tabs (Posts / People), which land in `vrba.6 / .7`, nor the cross-VM orchestration (`vrba.8`).

## Decisions

### D1. Live-search firing on every keystroke (debounced)

Each keystroke after a 250 ms debounce window updates `state.currentQuery` (the canonical, debounced submitted-query view). Downstream tab VMs (`vrba.6 / .7`) will subscribe to `currentQuery` via the screen Composable and re-issue their search RPCs whenever it changes.

**Why:** matches the Bluesky-app UX (results update as you type). The composer already establishes the snapshotFlow + debounce pattern; the search input mirrors it.

### D2. Persist to recent searches on explicit commit only

Typing alone never writes to the recent-search repo. Persistence fires on:
- IME `Search` action / hardware Enter key.
- A recent-search chip tap (re-recording bumps the chip's recency).

**Why:** debounced typing would pollute the chip strip with intermediate states (`"k"`, `"ko"`, `"kot"`, ...). Explicit commit keeps the chip strip meaningful.

### D3. Sanctioned editor `TextFieldState` exception applies

The VM holds a public `val textFieldState: TextFieldState`. The screen Composable wires `OutlinedTextField(state = vm.textFieldState, ...)` directly. The IME's writes are local to the field and never round-trip through `handleEvent` / `setState`. The VM observes via `snapshotFlow { textFieldState.text.toString() }` collected from `init`.

Rationale lives in `openspec/changes/add-composer-mention-typeahead/design.md`: the `value`/`onValueChange` round-trip is the canonical source of cursor-jump bugs once the reducer does any non-trivial work. Search's debounced live-search satisfies the "non-trivial work" criterion.

### D4. `currentQuery` lives in `UiState`, not a separate `StateFlow`

The debounced query is exposed as `SearchScreenViewState.currentQuery: String`. Downstream tab VMs read it via the screen Composable's `state` and pass the value down to their tab content; their VMs handle a `QueryChanged(query)` event.

**Why:** one StateFlow per VM is the established pattern. Adding a side-channel `StateFlow` for the debounced query would create two sources of truth for the same value and complicate testing.

### D5. Chip strip uses per-chip dismiss (X) + overflow "Clear all"

Each `InputChip` carries a trailing dismiss icon (X). Tapping the chip body fires `RecentChipTapped(query)`; tapping the X fires `RecentChipRemoved(query)`. A trailing overflow `IconButton` opens a menu with a single "Clear all" item firing `ClearAllRecentsClicked`.

**Why:** richer UX matches the user's choice in the brainstorm. Cost is one new `remove(query)` repo method + one new DAO method + their tests. No schema change.

### D6. Hide chip strip entirely when empty

`recentSearches.isEmpty() == true` → the strip composable doesn't render. The screen shows only the input bar above the (future, `vrba.8`) tab content.

**Why:** cleaner first-run; no "Recent searches will appear here" chrome that vanishes forever after one search.

### D7. No `SavedStateHandle` persistence

The input text + the `recentSearches` derived state are lost on process kill. Matches composer policy.

**Why:** matches the editor-exception precedent; the user can re-enter from the chip strip if they had just submitted, and the `recentSearches` flow re-populates on cold start from Room.

### D8. Repo + DAO gain `remove(query)` in this PR

The per-chip dismiss surfaces a need that didn't exist when `vrba.2` shipped. Adding now is cleaner than a tiny follow-up PR against `vrba.2`'s already-merged work.

- `RecentSearchDao.delete(query: String)` — `@Query("DELETE FROM recent_search WHERE \`query\` = :query")`.
- `RecentSearchRepository.remove(query: String)` — trims, ignores blank, delegates.

DAO test gains `delete_removesOnlyMatchingRow`; repo test gains `remove_delegates` + `remove_blankIgnored`. No schema bump (no migration, `2.json` unchanged).

## MVI contract

```kotlin
// :feature:search:impl/src/main/kotlin/.../SearchContract.kt
package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

@Immutable
data class SearchScreenViewState(
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val currentQuery: String = "",
    val isQueryBlank: Boolean = true,
) : UiState

sealed interface SearchEvent : UiEvent {
    /** IME action `Search` / hardware Enter. */
    data object SubmitClicked : SearchEvent

    /** User tapped a chip body. Seeds the field + bumps the chip's recency. */
    data class RecentChipTapped(val query: String) : SearchEvent

    /** User tapped the trailing X on a chip. */
    data class RecentChipRemoved(val query: String) : SearchEvent

    /** Overflow menu "Clear all" item. */
    data object ClearAllRecentsClicked : SearchEvent
}

/**
 * Empty for vrba.5. The downstream cross-VM orchestration (vrba.8) will
 * emit a NavigateTo(target: NavKey) effect when a post / actor row is
 * tapped in the tab content; until then there's nothing for this screen
 * to signal externally.
 */
sealed interface SearchEffect : UiEffect
```

## ViewModel

```kotlin
// :feature:search:impl/src/main/kotlin/.../SearchViewModel.kt
@OptIn(FlowPreview::class)  // for Flow.debounce
@HiltViewModel
internal class SearchViewModel @Inject constructor(
    private val recentSearches: RecentSearchRepository,
) : MviViewModel<SearchScreenViewState, SearchEvent, SearchEffect>(SearchScreenViewState()) {

    /** Sanctioned editor exception — see SearchContract KDoc + composer precedent. */
    val textFieldState: TextFieldState = TextFieldState()

    init {
        // Debounced live-search: each keystroke after 250 ms updates state.currentQuery.
        // Downstream tab VMs (vrba.6 / .7) will read state.currentQuery via the screen
        // Composable and re-issue their RPCs on change.
        snapshotFlow { textFieldState.text.toString() }
            .debounce(DEBOUNCE.inWholeMilliseconds)
            .distinctUntilChanged()
            .onEach { raw ->
                val trimmed = raw.trim()
                setState { copy(currentQuery = trimmed, isQueryBlank = trimmed.isEmpty()) }
            }
            .launchIn(viewModelScope)

        recentSearches.observeRecent()
            .onEach { list -> setState { copy(recentSearches = list.toImmutableList()) } }
            .launchIn(viewModelScope)
    }

    override fun handleEvent(event: SearchEvent) {
        when (event) {
            SearchEvent.SubmitClicked -> persistCurrent()
            is SearchEvent.RecentChipTapped -> {
                textFieldState.setTextAndPlaceCursorAtEnd(event.query)
                persistCurrent()
            }
            is SearchEvent.RecentChipRemoved -> viewModelScope.launch {
                recentSearches.remove(event.query)
            }
            SearchEvent.ClearAllRecentsClicked -> viewModelScope.launch {
                recentSearches.clearAll()
            }
        }
    }

    private fun persistCurrent() {
        val text = textFieldState.text.toString().trim()
        if (text.isEmpty()) return
        viewModelScope.launch { recentSearches.record(text) }
    }

    private companion object {
        val DEBOUNCE = 250.milliseconds
    }
}
```

## UI components

Three new internal Composables:

### `SearchInputRow(textFieldState, isQueryBlank, onSubmit, modifier)`

Material 3 `OutlinedTextField(state = textFieldState, ...)`. Leading: `Icons.Search`. Trailing X (renders only when `!isQueryBlank`) clears the field via `textFieldState.clearText()`. `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)`; `keyboardActions = KeyboardActions(onSearch = { onSubmit() })`. Single line; placeholder `R.string.search_input_hint`.

### `RecentSearchChipStrip(items, onChipTap, onChipRemove, onClearAll, modifier)`

Hidden by the caller when `items.isEmpty()`. `LazyRow` of `InputChip`s — each chip:
- `label = { Text(query) }`
- `onClick = { onChipTap(query) }`
- `selected = false`
- `trailingIcon = { Icon(Icons.Close, contentDescription = ...) { onChipRemove(query) } }`

Trailing item: an `IconButton(Icons.MoreVert)` that opens a `DropdownMenu` containing a single `DropdownMenuItem` labelled `R.string.search_recent_clear_all` firing `onClearAll`. Items separated by `Spacer(8.dp)` (or `contentPadding` on the LazyRow).

### `SearchScreen()` (replaces the existing scaffold)

```kotlin
@Composable
internal fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        SearchInputRow(
            textFieldState = viewModel.textFieldState,
            isQueryBlank = state.isQueryBlank,
            onSubmit = { viewModel.handleEvent(SearchEvent.SubmitClicked) },
        )
        if (state.recentSearches.isNotEmpty()) {
            RecentSearchChipStrip(
                items = state.recentSearches,
                onChipTap = { viewModel.handleEvent(SearchEvent.RecentChipTapped(it)) },
                onChipRemove = { viewModel.handleEvent(SearchEvent.RecentChipRemoved(it)) },
                onClearAll = { viewModel.handleEvent(SearchEvent.ClearAllRecentsClicked) },
            )
        }
        // vrba.8 inserts the TabRow + tab content here.
    }
}
```

## Data flow

```
IME keystroke
  ↓
textFieldState (Compose state)
  ├──→ Renders in OutlinedTextField (zero-latency, no round-trip)
  └──→ snapshotFlow { text.toString() }.debounce(250ms).distinctUntilChanged()
         ↓
       setState { currentQuery = trimmed, isQueryBlank = ... }
         ↓
       SearchScreenViewState (collected by Composable)
         ↓
       vrba.8 reads state.currentQuery and pushes to tab VMs

User taps IME Search / Enter
  ↓
SearchEvent.SubmitClicked
  ↓
persistCurrent()  →  RecentSearchRepository.record(text)
                      ↓
                    Room INSERT OR REPLACE + LRU trim
                      ↓
                    observeAll() emits  →  state.recentSearches updates

User taps chip
  ↓
SearchEvent.RecentChipTapped(query)
  ↓
textFieldState.setTextAndPlaceCursorAtEnd(query)   (triggers debounce + re-emit)
  +
persistCurrent()                                    (bumps recency)

User taps X on chip
  ↓
SearchEvent.RecentChipRemoved(query)
  ↓
RecentSearchRepository.remove(query)  →  dao.delete  →  observeAll() emits

User taps overflow > "Clear all"
  ↓
SearchEvent.ClearAllRecentsClicked
  ↓
RecentSearchRepository.clearAll()  →  observeAll() emits empty list  →  strip hides
```

## Files

### New

- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchContract.kt`
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModel.kt`
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/SearchInputRow.kt`
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/RecentSearchChipStrip.kt`
- `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModelTest.kt`
- `feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/ui/SearchScreenScreenshotTest.kt` — mirror `:feature:chats:impl`'s screenshot-test split (read the existing layout under `feature/chats/impl/src/screenshotTest/` before deciding whether to consolidate or split per composable; default to mirroring exactly).

### Modified

- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreen.kt` — replace the scaffold body with the real screen.
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/RecentSearchRepository.kt` — add `remove(query)`.
- `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/RecentSearchRepositoryTest.kt` — add `remove_delegates` + `remove_blankIgnored`.
- `feature/search/impl/build.gradle.kts` — add screenshot test plumbing (mirror `:feature:chats:impl`).
- `feature/search/impl/src/main/res/values/strings.xml` — add `search_input_hint`, `search_input_clear_content_desc`, `search_recent_overflow_content_desc`, `search_recent_remove_content_desc`, `search_recent_clear_all`.
- `core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/RecentSearchDao.kt` — add `delete(query)`.
- `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/dao/RecentSearchDaoTest.kt` — add `delete_removesOnlyMatchingRow`.

### Deleted

- `feature/search/impl/src/main/res/values/strings.xml`'s `search_screen_scaffold_title` — the centered scaffold title is replaced by the real screen.

## Testing strategy

### Unit (plain JVM, `:feature:search:impl/src/test/`)

`SearchViewModelTest`:

- `init_seedsRecentSearchesFromRepo`
- `textFieldState_typing_updatesCurrentQuery_afterDebounce` — uses `Snapshot.sendApplyNotifications()` + `testScheduler.runCurrent()` + `testScheduler.advanceTimeBy(251)` per the snapshot-in-unit-tests memory.
- `textFieldState_typing_distinctUntilChanged_collapsesEquivalentSnapshots`
- `currentQuery_isTrimmed`
- `submitClicked_persists_currentNonBlankText`
- `submitClicked_blank_isNoOp`
- `recentChipTapped_seedsTextField_andPersists`
- `recentChipRemoved_delegatesToRepo`
- `clearAllRecentsClicked_delegatesToRepo`

`RecentSearchRepositoryTest` (extend):
- `remove_delegates`
- `remove_blankIgnored`

`FakeRecentSearchDao` (extend): implement `delete(query)`.

### Compose previews (in the same files as the composables)

- `SearchScreenPreview_empty` — no recent searches, strip hidden.
- `SearchScreenPreview_withChips` — populated strip.
- `SearchInputRowPreview_blank`
- `SearchInputRowPreview_typed`
- `RecentSearchChipStripPreview`

### Screenshot tests (`:feature:search:impl/src/screenshotTest/`)

Light + dark variants of each preview above. Mirror `:feature:chats:impl`'s screenshot-test layout — `screenshotTestDebug/reference/` PNGs + the `@PreviewLightDark`-driven test classes.

### DAO androidTest (`:core:database/src/androidTest/`)

`RecentSearchDaoTest` (extend):
- `delete_removesOnlyMatchingRow`

### Test infrastructure additions to `:feature:search:impl/build.gradle.kts`

Mirror `:feature:chats:impl`'s screenshot-test deps. The unit-test deps (`turbine`, `kotlinx-coroutines-test`, `:core:testing`) are already present from `vrba.2`.

## Risk + rollback

- **Risk: snapshotFlow doesn't fire in unit tests by default.** Documented in the memory `feedback_compose_snapshot_in_unit_tests.md`; tests use `Snapshot.sendApplyNotifications()` + `testScheduler.runCurrent()` to force progress. Pattern lifted from `:feature:composer:impl`'s `ComposerViewModelTypeaheadTest`.
- **Risk: `OnConflictStrategy.REPLACE` upsert vs. the new `delete()` may race on the foreground vs. observer ordering.** Room serializes per-table writes; the `observeAll` flow uses Room's invalidation tracker which is consistent post-transaction. Existing `vrba.2` tests cover the dedup path; the new delete test will cover the symmetric case.
- **Risk: `InputChip` from M3 1.5.0-alpha19 may have visual regressions.** It's the current pinned Compose Material3 version in `gradle/libs.versions.toml`; we already use it elsewhere (e.g. composer typeahead). Screenshot tests catch any drift.
- **Rollback:** the screen Composable is the only user-visible change; reverting the PR brings back the scaffold "Search" title. No database schema bump, so no migration to undo.

## Out of scope

- **Tab content (Posts / People)** — `vrba.6` / `.7` add the tab VMs + content area; `vrba.8` adds the TabRow + cross-VM orchestration.
- **`OnQueryChanged` / `OnQuerySubmitted` effect emission to outside consumers** — defer until `vrba.8` wires the three VMs together. `state.currentQuery` is the consumption seam for now.
- **SavedStateHandle persistence** — see D7.
- **Voice input** — out of epic scope.
- **Search history limits beyond LRU 10** — `vrba.2`'s cap stands.
- **Curated/trending suggestions when empty** — epic non-goal.

## Bd scope

Single child (`nubecita-vrba.5`), single PR. The repo extension (`remove`/`delete`) is scoped within this PR rather than a follow-up against `vrba.2` because the per-chip-X UI is the only consumer and the change is one method per layer.

## Open questions

None outstanding. Ready for review.
