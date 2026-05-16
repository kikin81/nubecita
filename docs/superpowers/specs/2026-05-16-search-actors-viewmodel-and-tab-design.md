# SearchActorsViewModel + People tab UI — design

**Date:** 2026-05-16
**Scope:** `nubecita-vrba.7` — the per-tab ViewModel + tab content composable for the People half of the Search results screen. Consumes `SearchActorsRepository` (shipped in `vrba.4`) and the debounced `currentQuery` from the parent `SearchViewModel` (shipped in `vrba.5`). Sits inside `:feature:search:impl/`. No TabRow / no orchestration — that's `vrba.8`.
**Status:** Draft for user review.

## Why this slice

Mirror of `vrba.6` for actors. `vrba.4` shipped the Actors data layer + promoted `ActorUi` to `:data:models`. This child bridges the data layer and the (still-pending) tab orchestration: a `SearchActorsViewModel` that reacts to query changes, fetches paged results, and a `PeopleTabContent` composable that renders the five lifecycle states. Reuses the `HighlightedText` utility shipped in `vrba.6`.

## Inherited from vrba.6 (no new design work needed)

This spec inherits these decisions verbatim from `2026-05-16-search-posts-viewmodel-and-tab-design.md`; the implementation pattern is a tight substitution:

| Inherited decision | Substitution for vrba.7 |
|---|---|
| D1 — `mapLatest` over `MutableStateFlow<FetchKey>` driven by `setQuery(...)`, query changes cancel prior in-flight fetches | `FetchKey(query, incarnation)` — no `sort` field |
| D2 — Sealed `LoadStatus` sum with five variants | `SearchActorsLoadStatus.{Idle, InitialLoading, Loaded(items, nextCursor, endReached, isAppending), Empty, InitialError(error)}` |
| D3 — Typed errors via VM-side extension | `SearchActorsError.{Network, RateLimited, Unknown(cause)}` + `Throwable.toSearchActorsError()` |
| D4 — `LoadMore` single-flight + idempotent past `endReached`, no state flip on append failure | Same guard order, mirrored |
| D6 — `HighlightedText` utility | Reuse the existing `:designsystem/component/HighlightedText.kt` shipped in vrba.6 — no new work |
| D8 — Stateless `TabContent` body + stateful `Screen` entry that hoists `hiltViewModel` | `PeopleTabContent` (stateless) + `SearchActorsScreen` (stateful) |
| D9 — Nav effect routed via `LocalMainShellNavState.current.add(...)` | `NavigateToProfile(handle)` → `Profile(handle = actor.handle)` via `:feature:profile:api` |

The deltas below cover decisions where People materially differs from Posts.

## Decisions

### A1. No sort — `searchActors` doesn't accept a sort param

The Bluesky lexicon for `app.bsky.actor.searchActors` returns actors in server-determined relevance order; no `sort` parameter exists. vrba.7 has no sort chip row, no `SearchActorsSort` enum, no `SortClicked` event. `FetchKey` carries only `(query, incarnation)`.

If a future tagged/filtered actor search ships, sort lands then.

### A2. Build a fresh `ActorRow` in `:feature:search:impl/ui/` — do NOT extract composer's

`:feature:composer:impl/internal/ComposerSuggestionList.kt` has a `private fun ComposerSuggestionRow(actor: ActorUi, onClick: (ActorUi) -> Unit)`. The vrba ticket description suggests reuse, but extracting it has three downsides:

1. The composer row is `private` inside a feature-internal file. Promotion to `:designsystem/component/ActorRow.kt` requires the new row to live in :designsystem (with NubecitaAvatar already there — fine), but composer's existing tests and previews would need updating, and the row's `OutlinedCard` styling is specifically suited to the composer's inline dropdown.
2. The search People tab row needs match-highlighting on both `displayName` and `handle` (via `HighlightedText` / `withMatchHighlight`). The composer row doesn't — composer's typeahead is non-highlighted.
3. The composer row reads inside a divider'd `LazyColumn` (the divider is drawn by the parent). The search row sits in a list with no `OutlinedCard` wrapper. Different surrounding chrome.

Writing a fresh `ActorRow` in `:feature:search:impl/ui/` is ~50 lines (avatar + name + handle + click handler). Promotion can come as a follow-up when a third consumer (Discover, profile lists) needs the same row.

```kotlin
@Composable
internal fun ActorRow(
    actor: ActorUi,
    query: String,                      // for match highlight overlay
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

The row renders:
- Leading avatar (`NubecitaAvatar` from `:designsystem`, 48dp).
- Title: `HighlightedText(text = actor.displayName ?: actor.handle, match = query, style = titleMedium)`.
- Subtitle: `HighlightedText(text = "@${actor.handle}", match = query, style = bodySmall, color = onSurfaceVariant)`. Show only when `displayName` is non-null (otherwise the title already shows the handle).

Clickable surface wraps the whole row; the click emits `ActorTapped(actor.handle)`.

### A3. Tap-through navigates to `Profile(handle = actor.handle)`

`feature/profile/api/Profile.kt` declares `data class Profile(val handle: String? = null) : NavKey`. The non-null branch is "another user's profile, pushed onto the active tab's stack" per the project's existing convention (Feed already uses this pattern).

The screen-level effect collector calls `LocalMainShellNavState.current.add(Profile(handle = effect.handle))`. `:feature:search:impl/build.gradle.kts` adds `implementation(project(":feature:profile:api"))` — `:api` only, never `:impl` (which doesn't exist yet anyway; `:app` registers a placeholder for the Profile NavKey until the profile feature epic lands).

### A4. Empty-state body — simpler than Posts (no sort toggle)

Mirror `PostsEmptyBody`'s shape but drop the sort-toggle button. The People empty body has one CTA: "Clear search" (filled tonal). The body copy nudges towards a different query rather than a different sort.

```kotlin
@Composable
internal fun PeopleEmptyBody(
    currentQuery: String,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
)
```

Icon: `NubecitaIconName.PersonOff` (or fallback to a small-letter generic — implementer verifies the icon-font subset has one of these by grepping `NubecitaIconName.kt`; if neither exists, fall back to `Inbox` like Posts did — documented inline).

Heading: `stringResource(R.string.search_people_empty_title, currentQuery)` → e.g. "No people match \"velazquez\"".

Body copy: "Try a different handle or display name."

### A5. Initial-error body — same shape as Posts, different copy

`PeopleInitialErrorBody(error, onRetry, modifier, contentPadding)` — identical structure to `PostsInitialErrorBody`. Maps `SearchActorsError` → string-pair via a `when`. Title/body strings are people-specific:

| Variant | Title | Body |
|---|---|---|
| `Network` | "No connection" | "Check your connection and try again." |
| `RateLimited` | "Slow down" | "You've been searching a lot. Wait a moment and try again." |
| `Unknown` | "Something went wrong" | "We couldn't load your search. Try again." |

(Same copy as Posts because the errors are search-system-wide, not Posts-specific. Could share strings if a `:core:search-strings` module existed; YAGNI for now — duplicate the entries under `search_people_error_*` keys.)

### A6. Loading body — three skeleton actor rows

Mirror Posts's `PostsLoadingBody` shape but render three skeleton actor-row placeholders instead of three `PostCardShimmer`s. Hand-write a small `ActorRowShimmer()` private composable in `PeopleLoadingBody.kt` — a `Row` with a circle placeholder for the avatar + two-line text shimmer. Reuse `:designsystem/component/Shimmer.kt`'s underlying brush primitive if it's reusable; otherwise hand-roll.

## MVI contract

```kotlin
// :feature:search:impl/SearchActorsContract.kt

@Immutable
internal data class SearchActorsState(
    val currentQuery: String = "",
    val loadStatus: SearchActorsLoadStatus = SearchActorsLoadStatus.Idle,
) : UiState

internal sealed interface SearchActorsLoadStatus {
    @Immutable
    data object Idle : SearchActorsLoadStatus

    @Immutable
    data object InitialLoading : SearchActorsLoadStatus

    @Immutable
    data class Loaded(
        val items: ImmutableList<ActorUi>,
        val nextCursor: String?,
        val endReached: Boolean,
        val isAppending: Boolean = false,
    ) : SearchActorsLoadStatus

    @Immutable
    data object Empty : SearchActorsLoadStatus

    @Immutable
    data class InitialError(val error: SearchActorsError) : SearchActorsLoadStatus
}

internal sealed interface SearchActorsEvent : UiEvent {
    data object LoadMore : SearchActorsEvent
    data object Retry : SearchActorsEvent
    data object ClearQueryClicked : SearchActorsEvent
    data class ActorTapped(val handle: String) : SearchActorsEvent
}

internal sealed interface SearchActorsEffect : UiEffect {
    data class NavigateToProfile(val handle: String) : SearchActorsEffect
    data class ShowAppendError(val error: SearchActorsError) : SearchActorsEffect
    data object NavigateToClearQuery : SearchActorsEffect
}
```

Note the contract is smaller than vrba.6's — no `SortClicked`, no `NavigateToChangeSort`.

## ViewModel structure

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchActorsViewModel @Inject constructor(
    private val repository: SearchActorsRepository,
) : MviViewModel<SearchActorsState, SearchActorsEvent, SearchActorsEffect>(SearchActorsState()) {

    private data class FetchKey(val query: String, val incarnation: Int)
    private val fetchKey = MutableStateFlow(FetchKey(query = "", incarnation = 0))

    init {
        fetchKey
            .onEach { key -> setState { copy(currentQuery = key.query) } }
            .filter { it.query.isNotBlank() }
            .mapLatest { key -> runFirstPage(key) }
            .launchIn(viewModelScope)
    }

    fun setQuery(query: String) { fetchKey.update { it.copy(query = query) } }

    override fun handleEvent(event: SearchActorsEvent) {
        when (event) {
            SearchActorsEvent.LoadMore -> loadMore()
            SearchActorsEvent.Retry -> fetchKey.update { it.copy(incarnation = it.incarnation + 1) }
            SearchActorsEvent.ClearQueryClicked -> sendEffect(SearchActorsEffect.NavigateToClearQuery)
            is SearchActorsEvent.ActorTapped -> sendEffect(SearchActorsEffect.NavigateToProfile(event.handle))
        }
    }
    // runFirstPage(key) + loadMore() — both inherit the stale-completion guard
    // pattern from vrba.6: capture FetchKey at start, verify on completion.
}
```

**Stale-completion guard inherited from vrba.6.** The code-quality review of vrba.6 caught a `loadMore` race: a stale completion landing after a `SortClicked` reset could splice old-sort items onto the new-sort list. vrba.7 doesn't have sort, but the same race applies to `Retry` + rapid query changes: capture `fetchKey.value` at the start of `loadMore`, compare in both `onSuccess`/`onFailure`, return early if it drifted. Spec D4 + vrba.6 lesson #1 baked in from day one.

## UI components

- **`SearchActorsScreen.kt`** — stateful entry. Hoists `hiltViewModel<SearchActorsViewModel>()`, takes `currentQuery: String` from vrba.8's glue, wires `setQuery` via a `LaunchedEffect`, collects effects, routes `NavigateToProfile` to `LocalMainShellNavState.current.add(Profile(handle = effect.handle))`. Forwards `NavigateToClearQuery` + `ShowAppendError` as callback params for vrba.8.
- **`PeopleTabContent.kt`** — stateless body. Renders the five `SearchActorsLoadStatus` variants. For `Loaded`, a `LazyColumn` of `ActorRow` items, divider between rows, with the `query` propagated for highlight. Pagination via `LaunchedEffect` keyed on `(listState, items.size, endReached)` (NOT `isAppending` — the code-quality lesson from vrba.6).
- **`PeopleLoadingBody.kt`** — three skeleton actor rows + content-description for TalkBack.
- **`PeopleEmptyBody.kt`** — A4's icon + heading + body + single "Clear search" button.
- **`PeopleInitialErrorBody.kt`** — full-screen retry layout, mirroring `PostsInitialErrorBody`.
- **`ui/ActorRow.kt`** — A2's row.

All composables `internal`. None hoist `hiltViewModel` themselves except the entry `SearchActorsScreen`.

## Files

### New (under `:feature:search:impl/src/main/kotlin/.../`)

- `SearchActorsContract.kt`
- `SearchActorsError.kt` + `Throwable.toSearchActorsError()`
- `SearchActorsViewModel.kt`
- `SearchActorsScreen.kt` (stateful)
- `ui/PeopleTabContent.kt` (stateless)
- `ui/PeopleLoadingBody.kt`
- `ui/PeopleEmptyBody.kt`
- `ui/PeopleInitialErrorBody.kt`
- `ui/ActorRow.kt`

### Modified

- `feature/search/impl/build.gradle.kts` — add `implementation(project(":feature:profile:api"))`. (`:feature:postdetail:api` is already there from vrba.6, but People doesn't need it; leave it alone.)
- `feature/search/impl/src/main/res/values/strings.xml` — actor-tab strings (empty heading + body + button, error states, loading content description).

### Reused unchanged

- `:designsystem/component/HighlightedText.kt` (vrba.6).
- `:designsystem/component/NubecitaAvatar.kt`.
- `:data:models/ActorUi.kt` (vrba.4).
- `:feature:search:impl/data/SearchActorsRepository.kt` + `DefaultSearchActorsRepository.kt` (vrba.4).

## Test buckets

Per the UI-task convention: **unit + previews + screenshot**.

### Unit (plain JVM, `:feature:search:impl/src/test/.../`)

`SearchActorsViewModelTest` — 13 cases, mirroring vrba.6's `SearchPostsViewModelTest`:

- `setQuery_blank_stateStaysIdle`
- `setQuery_nonBlank_fetchesFirstPage_emitsLoaded`
- `setQuery_emptyResponse_emitsEmpty`
- `setQuery_failure_emitsInitialError_withMappedError`
- `setQuery_rapidChange_cancelsPrior_viaMapLatest`
- `loadMore_loaded_appendsNextPage_andClearsIsAppending`
- `loadMore_endReached_isNoOp`
- `loadMore_alreadyAppending_isNoOp_singleFlight`
- `loadMore_failure_emitsShowAppendError_keepsExistingItems`
- `loadMore_inFlight_whenQueryChanges_doesNotClobberNewQueryItems` ← **stale-completion guard regression test from day one**
- `retry_initialError_retriggersFirstPage_viaIncarnationBump`
- `actorTapped_emitsNavigateToProfileEffect`
- `clearQueryClicked_emitsNavigateToClearQueryEffect`

Hand-written `FakeSearchActorsRepository` mirroring vrba.6's fake shape. Plus `Throwable.toSearchActorsError()` mapping test (`SearchActorsErrorTest` — 3 cases).

### Compose previews

In the same files as the composables:

- `PeopleTabContentPreview_initialLoading / _empty / _loaded / _loadedAppending / _initialError_network / _initialError_rateLimited`
- `PeopleEmptyBodyPreview`
- `ActorRowPreview` — with and without `displayName`, with and without match.

### Screenshot tests (`:feature:search:impl/src/screenshotTest/`)

Each preview × light/dark. Roughly 14 baselines.

## Risk + rollback

- **Risk: `NubecitaIconName.PersonOff` may not exist in the icon-font subset.** Plan implementer verifies via `grep PersonOff designsystem/.../NubecitaIconName.kt`; falls back to `Inbox` (already a tested fallback from vrba.6's empty body) and documents inline. No spec change.
- **Risk: atproto-kotlin's 429 / RateLimited exception shape unknown.** Same as vrba.6 — `toSearchActorsError()`'s `RateLimited` branch defaults to `Unknown` until the SDK exposes a typed exception. File an upstream issue against `kikin81/atproto-kotlin` (per the project's existing convention) when a real RateLimited mapping is needed.
- **Risk: `ProfileView` on `searchActors` response missing the `description` / additional fields ActorUi might want.** Out of scope — `ActorUi` doesn't carry `description`. If the design later wants a bio snippet in the row, extend `ActorUi` and the data layer's `ProfileView.toActorUi()` mapper. vrba.7 ships rows with avatar + displayName + handle only.
- **Rollback:** PR independently revertible. No changes to vrba.6's shipped code (no PostCard edits, no HighlightedText edits, no contract churn). Reverting search doesn't break composer typeahead.

## Out of scope

- **Typeahead screen (vrba.10)** — separate child issue.
- **Feeds tab (vrba.11)** — separate child.
- **Filters / `searchActorsTypeahead` substitution** — `vrba.7` uses `searchActors` (paged, query-results). `searchActorsTypeahead` (composer's typeahead) is a different RPC with no cursor; not relevant here.
- **Inline `Follow` button on actor rows** — design handoff didn't include it for the search results screen. Future epic if/when post-interactions expand to actor-level relationships.
- **`description` (bio) snippet in the row** — out of scope per Risk note.
- **Promotion of `ActorRow` to `:designsystem`** — wait for a third consumer.
- **Adaptive tablet/foldable layouts** — same explicit follow-up epic as vrba.6.

## Bd structure

Single bd child (`nubecita-vrba.7`), single PR. Blocked by `vrba.4` (Actors data layer ✓ merged) and `vrba.5` (parent SearchViewModel ✓ merged) — both prerequisites are in.

## Open questions

None outstanding. Ready for review.
