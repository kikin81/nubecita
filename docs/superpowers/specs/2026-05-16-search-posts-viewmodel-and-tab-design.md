# SearchPostsViewModel + Posts tab UI — design

**Date:** 2026-05-16
**Scope:** `nubecita-vrba.6` — the per-tab ViewModel + tab content composable for the Posts half of the Search results screen. Consumes `SearchPostsRepository` (shipped in `vrba.3`) and the debounced `currentQuery` from the parent `SearchViewModel` (shipped in `vrba.5`). Sits inside `:feature:search:impl/`. No TabRow / no orchestration — that's `vrba.8`.
**Status:** Draft for user review.

## Why this slice

`vrba.5` shipped the input + recent-search chips and exposes `state.currentQuery: String` (debounced). `vrba.3` shipped the Posts data layer. This child bridges them: a `SearchPostsViewModel` that reacts to query changes, fetches paged results, and a `PostsTabContent` composable that renders the five lifecycle states. The Claude Design handoff (`search-feature` bundle, 2026-05-16) gave us the visual treatment.

## Decisions

### D1. Reactive query in via `mapLatest` over a `MutableSharedFlow<String>`

Per the spec for `vrba.3`'s D7, callers (this VM) own cancellation. The VM exposes a `setQuery(query: String)` function — `vrba.8`'s screen Composable will call this from a `LaunchedEffect(parentState.currentQuery)`. Internally the VM holds a `MutableSharedFlow<String>` whose pipeline is `distinctUntilChanged().mapLatest { runFirstPage(it) }.launchIn(viewModelScope)`. `mapLatest` cancels the prior in-flight fetch on a new query — the canonical pattern from `:feature:composer:impl`'s `ComposerViewModel` typeahead path.

The VM does NOT expose the parent `SearchViewModel` as a dependency. Hilt-injecting ViewModels into each other is a smell; the screen Composable is the orchestration seam (per CLAUDE.md's MVI conventions).

### D2. Lifecycle is a sealed `SearchPostsLoadStatus` sum

```kotlin
sealed interface SearchPostsLoadStatus {
    data object Idle : SearchPostsLoadStatus                            // query is blank; render nothing
    data object InitialLoading : SearchPostsLoadStatus                  // first-page fetch for current query
    data class Loaded(
        val items: ImmutableList<FeedItemUi.Single>,
        val hitsTotal: Long?,                                           // surfaced for tab badge in vrba.8
        val nextCursor: String?,
        val endReached: Boolean,
        val isAppending: Boolean = false,                               // pagination-in-flight indicator
    ) : SearchPostsLoadStatus
    data object Empty : SearchPostsLoadStatus                           // query returned zero results
    data class InitialError(val error: SearchPostsError) : SearchPostsLoadStatus
}
```

Mirrors `FeedLoadStatus` from `:feature:feed:impl/FeedContract.kt` (dropping `Refreshing` — search has no pull-to-refresh; query change IS the refresh signal). Per CLAUDE.md's MVI carve-out for mutually-exclusive view modes, this stays a sealed sum.

### D3. Typed errors via `SearchPostsError`

```kotlin
sealed interface SearchPostsError {
    data object Network : SearchPostsError
    data object RateLimited : SearchPostsError
    data class Unknown(val cause: String?) : SearchPostsError
}

internal fun Throwable.toSearchPostsError(): SearchPostsError = when (this) {
    is IOException -> SearchPostsError.Network
    // 429 / atproto-kotlin rate-limit exception — actual type pending verification against the SDK
    else -> SearchPostsError.Unknown(cause = message)
}
```

Lives in `:feature:search:impl/`. The repository surface stays untouched (it returns `Result<Throwable>`); error mapping happens VM-side per `vrba.3` D2. RateLimited is best-effort — the implementer verifies how atproto-kotlin surfaces 429s and adjusts the `when` branch accordingly.

### D4. Pagination single-flight + idempotent end-of-results

`handleEvent(SearchPostsEvent.LoadMore)`:

1. If `status !is Loaded` → no-op.
2. If `status.endReached` → no-op (idempotent).
3. If `status.isAppending` → no-op (single-flight).
4. Else: set `isAppending = true`, fetch with current cursor, append on success / emit `ShowAppendError` effect on failure (no state flip — keep existing results visible).

Mirrors `FeedViewModel.loadMore()`'s guard order exactly.

### D5. Sort row: Top / Latest (extends `SearchPostsRepository`)

The design's results screen includes a sort chip row (`Top` selected, `Latest`, `Filters`). Bluesky's `searchPosts` lexicon supports `sort=top|latest` natively. Including the sort affordance brings parity with the Bluesky-app's existing UX and is small in scope.

**Data-layer extension** (small follow-on to `vrba.3`'s already-merged surface):

```kotlin
// :feature:search:impl/data/SearchPostsRepository.kt — add sort param + enum
internal enum class SearchPostsSort { TOP, LATEST }

internal interface SearchPostsRepository {
    suspend fun searchPosts(
        query: String,
        cursor: String?,
        limit: Int = SEARCH_POSTS_PAGE_LIMIT,
        sort: SearchPostsSort = SearchPostsSort.TOP,         // new
    ): Result<SearchPostsPage>
}
```

`DefaultSearchPostsRepository` passes `sort.name.lowercase()` (or a `when` mapping) to `SearchPostsRequest.sort`. One new unit test verifies the param passthrough.

**VM contract:** `state.sort: SearchPostsSort` (defaults `TOP`); `SearchPostsEvent.SortClicked(sort: SearchPostsSort)` resets pagination + triggers a fresh first-page fetch (same path as a query change — `setQuery(currentQuery)` retriggers via `distinctUntilChanged()` … actually no, `distinctUntilChanged` would skip it. Use a `Triple<Query, Sort, IncarnationToken>` keyed flow or explicitly trigger via a separate mechanism). **Implementer picks the cleanest single-flow shape — preference is a `data class FetchKey(query: String, sort: SearchPostsSort)` flowed through `distinctUntilChanged().mapLatest`.**

The `Filters` chip from the design is **out of scope** (deferred to a follow-up; epic non-goal already documented).

### D6. Match highlighting on post body via a Compose `Highlighted` util

The design highlights query substrings in the rendered post body (`<Highlighted text={post.body} match={query}/>`). Implement as a small `:designsystem` Compose util:

```kotlin
// :designsystem/src/main/kotlin/.../typography/HighlightedText.kt
@Composable
fun HighlightedText(
    text: String,
    match: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    highlightStyle: SpanStyle = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
)
```

Splits `text` into `AnnotatedString` segments. Case-insensitive substring match. If `match.isNullOrBlank()`, renders `Text(text)` with no styling. Three previews (no match, single match, multi-match). One screenshot test.

`PostsTabContent` calls `HighlightedText(post.text, match = state.currentQuery)`. Reuses for vrba.7 (People rows) too — promote-from-day-one rather than duplicate per-feature.

### D7. Reuse the existing feed PostCard composable (do NOT fork)

`:feature:feed:impl` has a `PostCard` (or equivalent) that renders `FeedItemUi.Single`. The design's `SearchPostCard` is structurally identical (avatar + name + handle + timestamp + body + action row) with one addition: match-highlighting in the body.

**Implementation choice:** the existing `PostCard` accepts a `FeedItemUi.Single` and renders the body as `Text(post.text)`. We extend it to accept an optional `bodyMatch: String? = null` parameter; when non-null, body renders via `HighlightedText` instead. Single source of truth; no fork. The change to `:feature:feed:impl`'s `PostCard` is additive (default null) — no feed-screen regressions.

Alternative considered (rejected): write a `SearchPostCard` in `:feature:search:impl/ui/`. Forks rendering, duplicates 100+ lines of layout, future per-post tweaks (like-button, embeds, etc.) drift between the two. Not worth it.

### D8. Tab-content composable signature

```kotlin
@Composable
internal fun PostsTabContent(
    state: PostsTabState,
    onEvent: (SearchPostsEvent) -> Unit,
    modifier: Modifier = Modifier,
)
```

`PostsTabState` is a thin projection of `SearchPostsLoadStatus` + `currentQuery` (passed in by `vrba.8`'s screen Composable for highlighting). `onEvent` dispatches `LoadMore`, `Retry`, `PostTapped`, `SortClicked` upward.

The composable does NOT hoist its own `hiltViewModel<SearchPostsViewModel>()` — `vrba.8` does that. This keeps `PostsTabContent` driveable from previews + screenshot tests without a Hilt graph.

There's also a thin **`PostsTab(modifier)`** entry that `vrba.8` calls: it hoists `hiltViewModel<SearchPostsViewModel>()`, collects state, derives `currentQuery` from the parent VM (passed in by `vrba.8`), and delegates to `PostsTabContent`. Same stateful/stateless split as `vrba.5`'s `SearchScreen` / `SearchScreenContent`.

### D9. Tap-through via `SearchPostsEffect.NavigateToPost(uri: String)`

VM emits the effect; `vrba.8`'s screen Composable collects it and calls `LocalMainShellNavState.current.add(PostDetailRoute(uri))`. Mirrors `ChatsEffect.NavigateToChat` exactly. `:feature:search:impl/build.gradle.kts` adds `implementation(project(":feature:postdetail:api"))` — depends only on the `:api` module per the project's cross-feature rule.

Append-time errors emit a sibling `SearchPostsEffect.ShowAppendError(error: SearchPostsError)` effect that `vrba.8` surfaces as a Snackbar. Initial errors do NOT emit an effect — they flip the state to `InitialError(...)` and render full-screen.

### D10. Rich empty-state layout

The design's zero-results screen uses a cloud illustration + display-font heading + buttons. Adopting for the `Empty` state:

- Material Symbols `NubecitaIconName.SearchOff` (or `CloudOff` — implementer picks; both exist in the subset font) at 96dp, color = `MaterialTheme.colorScheme.surfaceContainer`.
- Heading: `Text("Nothing matches \"{query}\"", style = NubecitaTypography.displaySmall)` with the SOFT font variation if `Fraunces` supports it locally (the design specs `fontVariationSettings: "SOFT" 60`).
- Body: secondary-color text "Try a different spelling, or remove filters to broaden the search."
- Buttons: a tonal "Clear search" (emits `ClearQueryClicked` event → parent VM clears the field) and an outlined "Change sort" (emits `SortClicked(LATEST)` if currently `TOP`, or vice versa) — replaces the design's "Remove filters" since filters aren't in scope.

Skipping the design's `assets/cloud-illustration.svg` — custom illustrations are a `:designsystem` concern and out of scope here. The Material Symbol fallback is consistent with the rest of the app's art direction.

## MVI contract

```kotlin
// :feature:search:impl/SearchPostsContract.kt
@Immutable
data class SearchPostsState(
    val currentQuery: String = "",         // mirror from parent SearchViewModel; needed for empty-state copy + body match-highlighting
    val sort: SearchPostsSort = SearchPostsSort.TOP,
    val loadStatus: SearchPostsLoadStatus = SearchPostsLoadStatus.Idle,
) : UiState

sealed interface SearchPostsEvent : UiEvent {
    data object LoadMore : SearchPostsEvent
    data object Retry : SearchPostsEvent                    // InitialError → re-fetch first page
    data object ClearQueryClicked : SearchPostsEvent         // empty-state button; parent VM resets the field
    data class SortClicked(val sort: SearchPostsSort) : SearchPostsEvent
    data class PostTapped(val uri: String) : SearchPostsEvent
}

sealed interface SearchPostsEffect : UiEffect {
    data class NavigateToPost(val uri: String) : SearchPostsEffect
    data class ShowAppendError(val error: SearchPostsError) : SearchPostsEffect
    data object NavigateToClearQuery : SearchPostsEffect    // vrba.8 collects → parent VM dispatch
    data class NavigateToChangeSort(val sort: SearchPostsSort) : SearchPostsEffect
}
```

Internal-facing — only `:feature:search:impl` modules see these types.

## ViewModel structure

```kotlin
@HiltViewModel
internal class SearchPostsViewModel @Inject constructor(
    private val repository: SearchPostsRepository,
) : MviViewModel<SearchPostsState, SearchPostsEvent, SearchPostsEffect>(SearchPostsState()) {

    private data class FetchKey(val query: String, val sort: SearchPostsSort)
    private val fetchKey = MutableStateFlow(FetchKey(query = "", sort = SearchPostsSort.TOP))

    init {
        fetchKey
            .distinctUntilChanged()
            .onEach { key -> setState { copy(currentQuery = key.query, sort = key.sort) } }
            .filter { it.query.isNotBlank() }
            .mapLatest { key -> runFirstPage(key) }
            .launchIn(viewModelScope)
    }

    fun setQuery(query: String) { fetchKey.update { it.copy(query = query) } }

    override fun handleEvent(event: SearchPostsEvent) {
        when (event) {
            SearchPostsEvent.LoadMore -> loadMore()
            SearchPostsEvent.Retry -> fetchKey.update { it }   // retriggers mapLatest via no-op update? Use an incarnation token instead.
            SearchPostsEvent.ClearQueryClicked -> sendEffect(SearchPostsEffect.NavigateToClearQuery)
            is SearchPostsEvent.SortClicked -> fetchKey.update { it.copy(sort = event.sort) }
            is SearchPostsEvent.PostTapped -> sendEffect(SearchPostsEffect.NavigateToPost(event.uri))
        }
    }
    // ...
}
```

**Retry detail:** `fetchKey.update { it }` won't fire because `distinctUntilChanged` filters equal values. Add an `incarnation: Int` field to `FetchKey` that bumps on Retry. The implementer codes the precise shape.

`runFirstPage(key)` does the `Idle → InitialLoading → (Loaded / Empty / InitialError)` transition. `loadMore()` does the single-flight append.

## UI components

- **`SearchPostsScreen.kt`** — internal Composable that hoists `hiltViewModel<SearchPostsViewModel>()`, takes `currentQuery: String` and `sort: SearchPostsSort` (from `vrba.8`'s glue), wires `setQuery` and `setSort` via `LaunchedEffect`s, delegates rendering to `PostsTabContent`.
- **`PostsTabContent.kt`** — stateless body. Renders the five `SearchPostsLoadStatus` variants. For `Loaded`, uses a `LazyColumn` of feed `PostCard` calls (with `bodyMatch = state.currentQuery`). Pagination via `LaunchedEffect` on derived state of "last visible item near end of items".
- **`PostsLoadingBody.kt`** — circular progress + skeleton rows (3 rows of avatar + 2-line shimmer). Mirrors feed's loading body.
- **`PostsEmptyBody.kt`** — D10 layout. Internal Composable.
- **`PostsInitialErrorBody.kt`** — full-screen retry layout (mirror `:feature:chats:impl`'s ErrorBody). Maps `SearchPostsError` → stringResource via a small `when`.
- **`PostsSortRow.kt`** — chip strip ("Top" / "Latest"; "Filters" is a disabled or absent chip — implementer picks). Visible only when `loadStatus is Loaded` or query is non-blank.

All composables are `internal`. None hoist `hiltViewModel` themselves except the entry `SearchPostsScreen`.

## Files

### New (under `:feature:search:impl/src/main/kotlin/.../`)

- `SearchPostsContract.kt`
- `SearchPostsError.kt` + the `Throwable.toSearchPostsError()` extension
- `SearchPostsViewModel.kt`
- `SearchPostsScreen.kt` (stateful)
- `ui/PostsTabContent.kt` (stateless)
- `ui/PostsLoadingBody.kt`
- `ui/PostsEmptyBody.kt`
- `ui/PostsInitialErrorBody.kt`
- `ui/PostsSortRow.kt`

### Modified

- `:feature:search:impl/data/SearchPostsRepository.kt` — add `SearchPostsSort` enum + `sort` param to `searchPosts(...)` interface (default `TOP`).
- `:feature:search:impl/data/DefaultSearchPostsRepository.kt` — pass `sort` through to `SearchPostsRequest.sort`. New unit test `searchPosts_passesSortThrough`.
- `:feature:search:impl/build.gradle.kts` — add `implementation(project(":feature:postdetail:api"))` and `implementation(project(":designsystem"))` (already there via the feature convention plugin, verify).
- `:feature:search:impl/src/main/res/values/strings.xml` — add empty-state heading template, body, button labels, error-state strings, sort labels.

### New under `:designsystem/`

- `:designsystem/src/main/kotlin/.../typography/HighlightedText.kt` — the `HighlightedText` Composable + previews.

### Modified under `:feature:feed:impl/`

- `:feature:feed:impl/.../ui/PostCard.kt` (or wherever the post body Text is) — add an optional `bodyMatch: String? = null` parameter; when non-null, body renders via `HighlightedText`. Default null behavior is byte-identical to today's rendering.

## Test buckets

Per the UI-task convention: **unit + previews + screenshot**.

### Unit (plain JVM, `:feature:search:impl/src/test/.../`)

`SearchPostsViewModelTest`:

- `setQuery_blank_emitsIdle`
- `setQuery_nonBlank_fetchesFirstPage_emitsLoaded`
- `setQuery_emptyResponse_emitsEmpty`
- `setQuery_failure_emitsInitialError_withMappedError`
- `setQuery_rapidChange_cancelsPrior_mapLatestSemantic`
- `loadMore_loaded_appendsNextPage_emitsIsAppendingTransient`
- `loadMore_endReached_isNoOp`
- `loadMore_alreadyAppending_isNoOp` (single-flight)
- `loadMore_failure_emitsShowAppendError_keepsExistingItems`
- `sortClicked_resetsPaginationAndFetches`
- `retry_initialError_retriggersFirstPage`
- `postTapped_emitsNavigateToPost`
- `clearQueryClicked_emitsNavigateToClearQuery`

Hand-written `FakeSearchPostsRepository` (`MutableStateFlow<List<FeedItemUi.Single>>` + cursor counter + injectable throwable). Plus `Throwable.toSearchPostsError()` mapping test.

Extend `DefaultSearchPostsRepositoryTest` with `searchPosts_passesSortThrough` exercising the new `sort` URL param.

### Compose previews

In the same files as the composables (per the project convention):

- `PostsTabContentPreview_initialLoading` / `_empty` / `_loaded` / `_loadedAppending` / `_initialError_network` / `_initialError_rateLimited`
- `PostsSortRowPreview_top` / `_latest`
- `PostsEmptyBodyPreview`
- `HighlightedTextPreview_noMatch` / `_singleMatch` / `_multiMatch`

### Screenshot tests (`:feature:search:impl/src/screenshotTest/`)

Each preview × light/dark. Mirror `:feature:chats:impl`'s `screenshotTest/` layout. Roughly 12 baselines.

## Risk + rollback

- **Risk: `:feature:feed:impl`'s `PostCard` body Text isn't single-source.** If body rendering is split across multiple composables (one for plain text, one for facets, one for hashtags), match-highlighting needs to thread through all of them. Implementer reports `DONE_WITH_CONCERNS` if the path turns out to be 50+ lines and we evaluate whether to ship without highlighting and follow up.
- **Risk: atproto-kotlin's 429 / RateLimited exception shape unknown.** `Throwable.toSearchPostsError()`'s `RateLimited` branch is best-effort; defaults to `Unknown` until the SDK surface is verified.
- **Risk: `Fraunces` SOFT variation may not render with the project's vendored subset font.** If so, the empty-state heading falls back to standard `displaySmall` — no visual regression.
- **Rollback:** the PR is independently revertible. The `PostCard` extension is additive (default `null`); reverting search doesn't break feed.

## Out of scope

- **Typeahead screen** — landed as a new bd child `nubecita-vrba.10` (filed alongside this spec).
- **Feeds tab** — landed as `nubecita-vrba.11`.
- **Adaptive tablet/foldable layouts** — captured in the design but explicit follow-up epic.
- **`Filters` chip** beyond Top/Latest — design shows a "Filters" chip but no filter UI; deferred until tagged search / date range / language filters land.
- **Custom cloud illustration** — Material Symbol fallback for V1; `:designsystem` follow-up if branding wants the custom asset.
- **`Refreshing` lifecycle state** — search has no pull-to-refresh; query change is the implicit refresh.
- **Tap-through to PostDetail's actual rendering** — `vrba.9` wires the navigation effect; `vrba.6` only emits it.

## Bd structure

Single bd child (`nubecita-vrba.6`), single PR. Blocked by `vrba.3` (Posts data layer ✓ merged) and `vrba.5` (parent SearchViewModel ✓ merged) — both prerequisites are in.

## Open questions

None outstanding. Ready for review.
