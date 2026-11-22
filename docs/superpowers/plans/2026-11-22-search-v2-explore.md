# Search v2, Explore and Second Pane Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Search real post filters on `searchPostsV2`, an Explore landing (trends,
feeds, people, starter packs), and a right pane on wide windows that shows Explore, then
a search Summary, then the opened item; and fix the fold and phone bugs found on
2026-11-22.

**Architecture:** A singleton `SubmittedSearchStore` holds only what was searched (query,
filters, selected tab, sheet flag). Tab ViewModels and a shell-scoped
`SearchPaneViewModel` read it and fetch first pages through one `SearchFirstPages`
facade that shares in-flight requests. Explore content lives in a singleton
`ExploreRepository` projected by two `DiscoverViewModel` instances (one in the Search
entry, one in the pane). The pane renders in the existing `detailPlaceholder` slot.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 Expressive), Navigation 3 with
`ListDetailSceneStrategy` (adaptive-navigation3 1.3.0), Hilt, MVI on `MviViewModel`,
atproto-kotlin 10.0.0, JUnit 5 + MockK + Turbine + Ktor `MockEngine`, AGP screenshot
tests.

**Spec:** `docs/superpowers/specs/2026-11-22-search-v2-explore-design.md`

**Landing:** one bd epic, one `gh stack`. Each task below is one bd child, one branch
`<type>/<bd-id>-<slug>` and one PR, in this order. The openspec docs PR goes to `main`
on its own first.

| Task | bd child | Task | bd child |
|---|---|---|---|
| 1 | `nubecita-7d66.1` | 8 | `nubecita-7d66.7` |
| 2 | `nubecita-7d66.2` | 9 | `nubecita-7d66.8` |
| 3 | `nubecita-uapw` | 10 | `nubecita-vrba.14` |
| 4 | `nubecita-7d66.3` | 11 | `nubecita-7d66.9` |
| 5 | `nubecita-7d66.4` | 12 | `nubecita-7d66.10` |
| 6 | `nubecita-7d66.5` | 13 | `nubecita-7d66.11` |
| 7 | `nubecita-7d66.6` | 14 | `nubecita-7d66.12` |

Epic: `nubecita-7d66`. `nubecita-uapw` and `nubecita-vrba.14` existed before and were
moved under it.

## Global Constraints

- `:feature:search:impl`, `:feature:feed:impl` and `:core:auth` are flavored. Use
  `testProductionDebugUnitTest`, `lintProductionDebug`, `compileBenchDebugKotlin`,
  `compileProductionDebugScreenshotTestKotlin`. Never the unflavored names.
- Per-task gate before `gh stack submit`: the touched modules' unit tests and lint, plus
  `:feature:search:impl:compileBenchDebugKotlin` whenever a repository interface or an
  enum changes. Full gate (assemble, all lint, compose-expert review of
  `git diff origin/main...HEAD`) only on the top branch.
- Commits: Conventional Commits, `Refs: <bd-id>` footer, no attribution lines, never
  `--no-verify`. Check `git log -1` after each commit: a hook that rewrites files fails
  the commit silently in a chain.
- Search page size stays `25` for every tab and for the Summary, so first pages can be
  shared.
- Posts requests always send `allTime = true` unless the live check in Task 5 says a
  bounded `since` must not.
- Endpoint NSIDs, exact: `app.bsky.feed.searchPostsV2`,
  `app.bsky.graph.searchStarterPacksV2`, `app.bsky.unspecced.getTrends`,
  `app.bsky.unspecced.getSuggestedFeeds`,
  `app.bsky.unspecced.getSuggestedUsersForExplore`,
  `app.bsky.unspecced.getSuggestedStarterPacks`.
- No event bus, no one-shot channel between the Search screen and the pane. Shared
  things are `StateFlow` state.
- The pane never emits `search_perform`.
- Every new string lands with its `values-b+es+419` and `values-pt-rBR` translations in
  the same commit.
- State lists are `ImmutableList`; no `!!`; every `Scaffold` sets
  `containerColor = MaterialTheme.colorScheme.surface`; depth uses the surface-role
  tokens in CLAUDE.md.
- Lazy lists: stable `key`, `contentType`, fixed image sizes, no nested scroll in the
  same direction. Two-column grids are rows of two weighted cells inside the one lazy
  column.
- Screenshot baselines: never commit a local regeneration. Add the `update-baselines`
  label and `gh stack sync` before the next push.
- Device checks use the bench build installed by serial
  (`adb -s <serial> install -r app/build/outputs/apk/bench/debug/app-bench-debug.apk`)
  on the unfolded fold emulator or the real foldable, and a phone.

## Review Focus

1. **A new search is submitted while the previous first page is still in flight.** The
   old result must never render under the new query, in a tab or in the Summary.
   (Test in Task 5.)
2. **A trend link that is not a clean path:** empty, an absolute `https://bsky.app/…`
   URL, a trailing slash, or extra query parameters. The tap must still go somewhere.
   (Test in Task 9.)
3. **Every Explore endpoint fails or the device is offline.** The pane shows one retry
   state, not a blank pane, and a later success recovers. (Test in Task 9 and Task 11.)
4. **A custom date range that is reversed, in the future, or one day long.** The request
   is still valid: bounds are ordered, and `until` is the end of the chosen day in the
   device time zone. (Test in Task 5.)
5. **The account changes while Search is open** (switch account, or logout and login as
   someone else). No suggestion, trend or cached first page from the previous account is
   shown. (Test in Task 5 and Task 9.)

---

### Task 1: Bench sign-out flips the session

**Files:**
- Modify: `core/auth/src/bench/kotlin/net/kikin/nubecita/core/auth/FakeSessionStateProvider.kt`
- Modify: `core/auth/src/bench/kotlin/net/kikin/nubecita/core/auth/FakeAuthRepository.kt`

**Interfaces:**
- Produces: `FakeSessionStateProvider.setSignedIn(signedIn: Boolean)` (bench only).
  `FakeAuthRepository.signOut()` calls `setSignedIn(false)`; `completeLogin` calls
  `setSignedIn(true)`. The signed-in value is the existing bench handle and DID.

- [ ] **Step 1:** Add `setSignedIn` and inject `FakeSessionStateProvider` into
  `FakeAuthRepository` (both are `@Singleton`; inject the concrete class).
- [ ] **Step 2:** `./gradlew :core:auth:compileBenchDebugKotlin :app:assembleBenchDebug`
  → BUILD SUCCESSFUL.
- [ ] **Step 3: Device check.** Bench build → You → Settings → Sign out → confirm.
  Expected: the dialog closes and the Login screen shows within 2 s. Sign in again with
  any handle; expected: the Feed tab shows.
- [ ] **Step 4:** Commit `fix(auth): complete sign-out in the bench build`.

### Task 2: Search bar width on phones and docked dropdown width

**Files:**
- Modify: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchBarSection.kt` (the `SearchBar(...)` call at line 167 and the `ExpandedDockedSearchBar` call at line 200)

- [ ] **Step 1:** Give the collapsed `SearchBar` `Modifier.fillMaxWidth()` before the
  existing padding, so the pill has equal 16dp margins at every width.
- [ ] **Step 2:** Make the docked dropdown as wide as the collapsed pill so no list
  content shows beside it. Read `ExpandedDockedSearchBar`'s parameters in material3
  1.5.0 before choosing how; do not switch to the contained full-screen variant (it
  covers the whole window inside a list-detail pane).
- [ ] **Step 3: Device check.** Phone: pill left and right margins equal. Fold: open the
  dropdown over the landing; no carousel or list content visible at its right edge.
- [ ] **Step 4:** `./gradlew :feature:search:impl:lintProductionDebug` → no new issues.
  `SearchScreenScreenshotTest` baselines change; refresh them with the `update-baselines`
  label after the PR opens.
- [ ] **Step 5:** Commit `fix(search): size the search bar and its dropdown to the pane`.

### Task 3: Overlay must not reopen after a configuration change (`nubecita-uapw`)

**Files:**
- Modify: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreen.kt` and/or `SearchBarSection.kt` (wherever the root cause is)
- Test: `app/src/journeysTest/search.journey.xml` (existing dark/light steps become the regression check)

Use superpowers:systematic-debugging. Known facts: on Medium/Expanded only, after
`cmd uimode night yes|no` or an unfold, the docked bar is expanded with the keyboard up,
although before the change the bar was collapsed, the field unfocused and
`mInputShown=false`. Compact is unaffected. Two things to rule out first: the
`LaunchedEffect(tabReTapSignal, searchBarState)` at `SearchScreen.kt:192` replaying a
re-tap, and focus being restored to the input field on recreation (a focused field
expands the bar).

- [ ] **Step 1: Reproduce** on the unfolded fold: submit a search, confirm the bar is
  collapsed, run `adb shell cmd uimode night yes`. Expected today: overlay and keyboard
  appear.
- [ ] **Step 2:** Find the root cause; write it into the bd issue with
  `bd update nubecita-uapw --append-notes`.
- [ ] **Step 3:** Fix at the cause. A collapsed bar stays collapsed across recreation; a
  bar the user had expanded stays expanded.
- [ ] **Step 4: Verify** four times each: theme change, fold then unfold, rotation.
  Expected each time: results visible, `dumpsys input_method | grep mInputShown` is
  `false`. Then expand the bar by hand, change theme: it stays expanded.
- [ ] **Step 5:** Commit `fix(search): keep the search bar collapsed across configuration changes`.

### Task 4: `FeedView` takes the detail-pane role

**Files:**
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/di/FeedNavigationModule.kt:137`

- [ ] **Step 1:** `entry<FeedView>(metadata = ListDetailSceneStrategy.detailPane()) { … }`,
  with a comment mirroring the `Hashtag` entry's in `SearchNavigationModule.kt`.
- [ ] **Step 2:** `git grep -n -w 'FeedView(' -- '*.kt'` and list every push site in the
  PR body. On 2026-11-22 there are two, both in Search.
- [ ] **Step 3: Device check, fold.** Search → Feeds tab → tap a feed: it opens beside
  the list; a post tapped inside it stacks in the same pane; back returns to the feed,
  then to the placeholder. Phone: unchanged, full-screen.
- [ ] **Step 4:** While there, watch the feed's loading indicator on the production
  debug build: it must disappear once posts render. If it stays, fix it in this task and
  say so in the PR; if it does not reproduce, note that in the PR.
- [ ] **Step 5:** `./gradlew :feature:feed:impl:lintProductionDebug`; commit
  `fix(feed): open a feed in the detail pane on wide windows`.

### Task 5: Posts on search v2, filter model, shared first pages

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/PostSearchFilters.kt`
- Create: `…/data/FirstPageCache.kt`, `…/data/SearchFirstPages.kt`
- Modify: `…/data/SearchPostsRepository.kt`, `…/data/DefaultSearchPostsRepository.kt`, `feature/search/impl/src/bench/kotlin/…/data/BenchFakeSearchPostsRepository.kt`
- Modify: `…/SearchPostsViewModel.kt`, `…/SearchActorsViewModel.kt`, `…/SearchFeedsViewModel.kt` (first page through `SearchFirstPages`)
- Test: `feature/search/impl/src/test/kotlin/…/data/FirstPageCacheTest.kt`, `PostSearchFiltersTest.kt`, `SearchFirstPagesTest.kt`; update `DefaultSearchPostsRepositoryTest.kt`, `FakeSearchPostsRepository.kt`, the three ViewModel tests

**Interfaces (Produces):**

```kotlin
internal data class PostSearchFilters(
    val sort: SearchPostsSort = SearchPostsSort.TOP,
    val followingOnly: Boolean = false,
    val media: MediaFilter = MediaFilter.ANY,          // ANY, HAS_MEDIA, VIDEO_ONLY
    val replies: ReplyFilter = ReplyFilter.INCLUDE,    // INCLUDE, HIDE, ONLY
    val language: String? = null,                      // BCP-47, null = any
    val date: DateFilter = DateFilter.AllTime,
) { val activeCount: Int }                             // non-default fields, sort excluded
internal sealed interface DateFilter {
    data object AllTime; data object PastDay; data object PastWeek; data object PastMonth
    data class Custom(val from: LocalDate, val to: LocalDate)
}
internal fun PostSearchFilters.toRequest(
    query: String, cursor: String?, limit: Int, now: Instant, zone: ZoneId,
): SearchPostsV2Request

// SearchPostsRepository.searchPosts becomes:
suspend fun searchPosts(query: String, filters: PostSearchFilters, cursor: String?,
    limit: Int = SEARCH_POSTS_PAGE_LIMIT): Result<SearchPostsPage>
// SearchPostsPage gains: val hitsTotal: Long?

internal class FirstPageCache<K : Any, V>(scope: CoroutineScope) {
    suspend fun get(key: K, fetch: suspend () -> Result<V>): Result<V>
    suspend fun refresh(key: K, fetch: suspend () -> Result<V>): Result<V>
    fun clear()
}

@Singleton internal class SearchFirstPages {            // @ApplicationScope scope, SessionStateProvider
    val version: StateFlow<Int>                        // bumps on every refresh and clear
    suspend fun posts(query: String, filters: PostSearchFilters, refresh: Boolean = false): Result<SearchPostsPage>
    suspend fun people(query: String, refresh: Boolean = false): Result<ActorSearchPage>
    suspend fun feeds(query: String, refresh: Boolean = false): Result<SearchFeedsPage>
}
```

Request mapping, exact: `sort` TOP→`"top"`, LATEST→`"recent"`; `followingOnly`→
`following = true`; HAS_MEDIA→`hasMedia = true`; VIDEO_ONLY→`hasVideo = true`; HIDE→
`excludeReplies = true`; ONLY→`repliesOnly = true`; `language`→`languages = [code]`;
PastDay/Week/Month→`since = now − 1/7/30 days` as an ISO datetime; Custom→`since` = start
of `min(from, to)` and `until` = start of the day after `max(from, to)`, both in `zone`,
`until` capped at `now`; `allTime = true` always. Unset filters send nothing.

`FirstPageCache` holds one entry: the key plus the `Deferred` of its request, started in
the injected scope so one caller's cancellation does not cancel the other's. `get` with
the same key awaits that `Deferred`; a different key replaces the entry; a failed result
removes the entry. `SearchFirstPages` clears all caches when
`SessionStateProvider.state` leaves `SignedIn` or its `did` changes.

- [ ] **Step 1: Live check** (production debug build, signed in; record the answers in
  the PR body). Compare v1 and v2 results for five plain queries; time `allTime=true`
  against no `allTime`; check whether `since` + `allTime=true` together is accepted and
  whether `since` alone reaches past the recent window; note how often `hitsTotal` is
  present; try `following=true`; send a malformed query and record the error name.
  **Stop and report to the user if v2 relevance is clearly worse on plain queries.**
- [ ] **Step 2: Failing tests.** `FirstPageCacheTest`:
  `two concurrent gets with one key run fetch once`, `a failed fetch is not cached`,
  `a different key replaces the entry`, `refresh runs fetch again and returns the new value`,
  `a get that started before a key change never returns under the new key` (Review Focus 1:
  start `get(A)`, suspend its fetch, `get(B)`, complete A; assert B's caller gets B's
  value and a following `get(B)` does not refetch).
  `PostSearchFiltersTest`: one test per mapping row above, plus
  `reversed custom range is ordered`, `custom range ending today caps until at now`,
  `one day custom range spans that whole day` (Review Focus 4), and
  `activeCount ignores sort`.
  `SearchFirstPagesTest`: `signing out clears every cache and bumps version`,
  `a did change clears every cache` (Review Focus 5).
- [ ] **Step 3:** `./gradlew :feature:search:impl:testProductionDebugUnitTest --tests '*FirstPageCacheTest' --tests '*PostSearchFiltersTest' --tests '*SearchFirstPagesTest'` → FAIL (unresolved).
- [ ] **Step 4:** Implement the types above; move `DefaultSearchPostsRepository.searchPosts`
  to `SearchPostsV2Request` via `toRequest`; map `hitsTotal`; map the `BadQueryString`
  error name to a new `SearchPostsError.BadQuery`. `searchHashtag` is unchanged.
- [ ] **Step 5:** The three tab ViewModels request `cursor == null` pages through
  `SearchFirstPages` (pull-to-refresh passes `refresh = true`) and later pages through
  their repositories. `SearchPostsViewModel` keeps its `sort` for now by building
  `PostSearchFilters(sort = sort)`.
- [ ] **Step 6:** `BenchFakeSearchPostsRepository` honours `media` (HAS_MEDIA and
  VIDEO_ONLY return only fixture posts with media / video) and returns
  `hitsTotal = items.size`.
- [ ] **Step 7:** `./gradlew :feature:search:impl:testProductionDebugUnitTest :feature:search:impl:compileBenchDebugKotlin :feature:search:impl:lintProductionDebug` → PASS.
- [ ] **Step 8:** Commit `refactor(search): move post search to v2 and share first pages`.

### Task 6: `SubmittedSearchStore`, tabs read it, back clears the search

**Files:**
- Create: `…/impl/data/SubmittedSearchStore.kt`
- Modify: `…/impl/SearchViewModel.kt`, `SearchContract.kt`, `SearchScreen.kt`, `SearchPostsViewModel.kt`, `SearchPostsScreen.kt`
- Test: `…/test/…/data/SubmittedSearchStoreTest.kt`, `SearchBackTest.kt`; update `SearchViewModelTest.kt`, `SearchPostsViewModelTest.kt`

**Interfaces:**
- Consumes: `PostSearchFilters`, `SearchFirstPages` (Task 5).
- Produces:

```kotlin
internal enum class SearchTab { POSTS, PEOPLE, FEEDS }          // PACKS added in Task 13
internal data class SubmittedSearch(
    val query: String, val fromRecent: Boolean,
    val filters: PostSearchFilters = PostSearchFilters(),
    val selectedTab: SearchTab = SearchTab.POSTS,
    val isFilterSheetOpen: Boolean = false,
)
@Singleton internal class SubmittedSearchStore @Inject constructor() {
    val submitted: StateFlow<SubmittedSearch?>
    fun submit(query: String, fromRecent: Boolean)   // keeps filters and tab of a current search
    fun clear()                                      // null; filters reset
    fun updateFilters(transform: (PostSearchFilters) -> PostSearchFilters)
    fun selectTab(tab: SearchTab)
    fun setFilterSheetOpen(open: Boolean)
}
internal fun shouldClearSearchOnBack(isTopOfTabStack: Boolean, phase: SearchPhase, isBarCollapsed: Boolean): Boolean
// SearchEvent gains: data object ClearSearch
```

- [ ] **Step 1: Failing tests.** `SubmittedSearchStoreTest`: `submit publishes query with default filters`,
  `a second submit keeps filters and tab`, `clear resets to null and the next submit has default filters`,
  `updateFilters, selectTab and setFilterSheetOpen are no-ops when nothing is submitted`.
  `SearchBackTest`: the truth table; true only for `(true, Results, true)`.
  `SearchViewModelTest`: `submit writes the store`, `blanking the field clears the store`,
  `a new ViewModel clears a stale store`, `onCleared clears the store`,
  `ClearSearch blanks the field and returns to Discover`.
  `SearchPostsViewModelTest`: `a filter change refetches`, `an unchanged search does not refetch`,
  `SortClicked updates the store's filters`.
- [ ] **Step 2:** Run them → FAIL.
- [ ] **Step 3:** Implement the store. `SearchViewModel` injects it: submit paths call
  `submit`, the blank-field path and `onCleared` call `clear`, `init` calls `clear`.
  `SearchPostsViewModel` derives its fetch key from `store.submitted` (query, fromRecent,
  filters) and drops `setQuery` and its own `sort`; `SearchPostsState.sort` is read from
  the filters. People and Feeds keep `setQuery`.
- [ ] **Step 4:** In `SearchScreenContent`, the pager follows `selectedTab`
  (`LaunchedEffect(selectedTab)` → `animateScrollToPage`) and reports settles
  (`snapshotFlow { pagerState.settledPage }` → `selectTab`).
- [ ] **Step 5:** In the stateful `SearchScreen`, add a back handler enabled by
  `shouldClearSearchOnBack(navState.backStack.lastOrNull() == Search, state.phase, searchBarState.currentValue == SearchBarValue.Collapsed)`
  that sends `SearchEvent.ClearSearch`. No screen in the repo registers a back handler
  today; use `androidx.activity.compose.BackHandler`, and confirm on device that it wins
  over `NavDisplay`'s handler only while enabled.
- [ ] **Step 6: Device check, fold and phone.** Results → back → landing → back → Feed
  tab. Results → open a post → back → results (the search is still there) → back →
  landing. Repeat after folding mid-search and after unfolding mid-search.
- [ ] **Step 7:** Module tests, bench compile and lint → PASS; commit
  `feat(search): hold the submitted search in a shared store`.

### Task 7: Filter chip row, filter sheet, result count

**Files:**
- Create: `…/impl/ui/PostsFilterRow.kt` (replaces `ui/PostsSortRow.kt`, delete it), `…/impl/ui/PostFiltersSheet.kt`, `…/impl/ui/FilterLabels.kt`
- Modify: `SearchPostsContract.kt` (`Loaded` gains `hitsTotal: Long?`), `SearchPostsScreen.kt`, `SearchScreen.kt` (hosts the sheet), `res/values*/strings.xml`
- Test: `…/test/…/ui/FilterLabelsTest.kt`; screenshot `PostsFilterRowScreenshotTest.kt` (replaces `PostsSortRowScreenshotTest.kt`), `PostFiltersSheetScreenshotTest.kt`

**Interfaces:**
- Consumes: `SubmittedSearchStore`, `PostSearchFilters`.
- Produces: `PostsFilterRow(filters, onFiltersChange, onOpenSheet, modifier)`,
  `PostFiltersSheet(filters, hitsTotal, onFiltersChange, onReset, onDismiss)`,
  `filterChipState(filters): FilterChipState` (pure: per chip, selected flag and the value
  label to show in place).

Chip order, exact: `☰ n`, Sort menu, Following, Media, Video, Language, Date. Chips never
reorder. Media and Video chips drive the one `media` field (Video on ⇒ VIDEO_ONLY; Media
on ⇒ HAS_MEDIA; turning Video off returns to ANY). Language and Date show their value as
the label when set. Sheet rows and choices are the table in spec section 4, Date default
"All time", custom range through the M3 `DateRangePicker`. Count line under the row:
`About %1$s posts` with `formatCompactCount` (`core/common/.../text/CompactCount.kt`);
omitted when `hitsTotal` is null. Sheet button: `Show about %1$s posts`, or
`Show results` when null. The sheet is a `ModalBottomSheet` composed in
`SearchScreenContent` while `isFilterSheetOpen`, so it opens on any results tab.

- [ ] **Step 1: Failing test** `FilterLabelsTest`: `no filters selects nothing and shows default labels`,
  `language set shows its display name and is selected`, `date preset shows its label`,
  `video only selects Video and Media`, `count is activeCount`.
- [ ] **Step 2:** Implement `FilterLabels.kt`, then the row and the sheet, wired to the
  store (`updateFilters`, `setFilterSheetOpen`).
- [ ] **Step 3: Screenshot fixtures:** row with no filters; with Following + a language;
  the same in `es-419`; sheet default; sheet with a custom range; count line present and
  absent. `./gradlew :feature:search:impl:compileProductionDebugScreenshotTestKotlin` → OK.
- [ ] **Step 4: Bench device check** (must visibly toggle): search, tap Media; the list
  shrinks to media posts and the count changes; screenshot before and after. Open the
  sheet from the People tab via the `☰` chip after switching back; reset clears chips.
- [ ] **Step 5:** Tests, bench compile, lint (catches missing translations) → PASS;
  commit `feat(search): add post filters and a result count`.

### Task 8: Result-row polish

**Files:**
- Modify: the People row and Feeds row composables under `…/impl/ui/` (`ActorRow`, feed row) and their ViewModels' follow / pin handling; `SearchTypeaheadScreen.kt` highlight
- Test: update `ActorRowScreenshotTest.kt`, `FeedsTabContentScreenshotTest.kt`; `…/test/…/TypeaheadHighlightTest.kt`

- [ ] **Step 1: Failing test** `TypeaheadHighlightTest`:
  `query al does not highlight inside bsky.social`, `query ali highlights the name part of alice.bsky.social`,
  `a custom-domain handle is highlighted in full`.
- [ ] **Step 2:** People rows gain a one-line bio (`maxLines = 1`, ellipsis) and a Follow
  button; Feed rows show `formatCompactCount(likes)` and a Pin button. Follow and pin use
  the same primitives `DiscoverViewModel` uses today (`OnFollowTapped`, `OnPinTapped`):
  optimistic, rolled back with a snackbar on failure. Add `SearchActorsViewModelTest` and
  `SearchFeedsViewModelTest` cases for success and rollback.
- [ ] **Step 3:** Restrict the typeahead highlight to the handle's first label when the
  handle ends in `.bsky.social`.
- [ ] **Step 4:** Tests and lint → PASS; commit `feat(search): show bio, follow and pin in result rows`.

### Task 9: Explore data

**Files:**
- Create: `…/impl/data/ExploreRepository.kt`, `DefaultExploreRepository.kt`, `TrendUi.kt`, `TrendLink.kt`; `src/bench/…/data/BenchFakeExploreRepository.kt`; `src/production/…/di/ExploreRepositoryModule.kt`, `src/bench/…/di/ExploreRepositoryModule.kt`
- Delete: `SuggestionsRepository.kt`, `DefaultSuggestionsRepository.kt`, `BenchFakeSuggestionsRepository.kt`, both `SuggestionsRepositoryModule.kt`, `DefaultSuggestionsRepositoryTest.kt`
- Modify: `DiscoverViewModel.kt`, `DiscoverContract.kt`
- Test: `…/test/…/data/TrendLinkTest.kt`, `DefaultExploreRepositoryTest.kt`; update `DiscoverViewModelTest.kt`

**Interfaces (Produces):**

```kotlin
internal data class TrendUi(val topic: String, val displayName: String, val link: String,
    val category: String?, val postCount: Long, val isHot: Boolean)
internal sealed interface TrendLink {
    data class Feed(val actor: String, val rkey: String) : TrendLink     // actor: DID or handle
    data class StarterPack(val actor: String, val rkey: String) : TrendLink
    data class Search(val query: String) : TrendLink
    data object Unknown : TrendLink
}
internal fun parseTrendLink(link: String): TrendLink

internal data class ExploreContent(
    val trends: ImmutableList<TrendUi>, val trendsStatus: DiscoverSectionStatus,
    val feeds: ImmutableList<DiscoverFeedUi>, val feedsStatus: DiscoverSectionStatus,
    val accounts: ImmutableList<SuggestedAccountUi>, val accountsStatus: DiscoverSectionStatus,
)
internal interface ExploreRepository {
    val content: StateFlow<ExploreContent>
    suspend fun load(force: Boolean = false)     // shares in-flight; no-op once loaded
    suspend fun loadFeedPreview(feedUri: String)
    fun setFollowing(did: String, following: Boolean)
    fun dismissAccount(did: String)
    fun setPinned(feedUri: String, pinned: Boolean)
    suspend fun resolveDid(actor: String): Result<String>   // DID passes through; handle → resolveHandle
}
```

Request limits: trends `10`, feeds `8`, people `12`. `isHot` is `status == "hot"`.
Requests and responses are hand-written `@Serializable` classes in
`DefaultExploreRepository.kt`, reusing the generated `TrendView`, `GeneratorView` and
`ProfileView`; required response fields have no defaults, as in
`DefaultSearchFeedsRepository.kt`. One endpoint failing marks only its section `Error`.
`content` is reset when `SessionStateProvider.state` leaves `SignedIn` or its `did`
changes. `DiscoverViewModel` becomes a projection of `content`: `DiscoverState` gains
`trends` and `trendsStatus`; follow, pin, dismiss and preview go through the repository;
it gains `DiscoverEvent.OnTrendTapped(trend)` and `DiscoverEffect.SubmitSearch(query)`.

- [ ] **Step 1: Failing tests.** `TrendLinkTest`: `/profile/did:plc:abc/feed/xyz` → `Feed`;
  `/profile/bsky.app/feed/xyz` → `Feed`; `/starter-pack/alice.test/3k` → `StarterPack`;
  `/search?q=lunar%20eclipse` → `Search("lunar eclipse")`; and Review Focus 2:
  `""`, `"/"`, `/profile/did:plc:abc`, `/profile/did:plc:abc/feed/` → `Unknown`;
  `https://bsky.app/profile/did:plc:abc/feed/xyz`, a trailing slash and `?ref=x` on a
  feed link all → `Feed`.
  `DefaultExploreRepositoryTest` (Ktor `MockEngine`): each endpoint decodes;
  `a missing required field fails only that section`;
  `two concurrent loads hit each endpoint once`;
  `every endpoint failing leaves all sections Error and a forced load recovers` (Review Focus 3);
  `a did change resets content` (Review Focus 5); `resolveDid passes a DID through without a call`.
  `DiscoverViewModelTest`: `a feed trend navigates to at://<did>/app.bsky.feed.generator/<rkey>`,
  `a handle trend resolves first`, `a failed resolve submits the display name`,
  `an unknown link submits the display name`, `a search link submits its query`,
  `a starter-pack link submits the display name` (Task 13 changes this one).
- [ ] **Step 2:** Run → FAIL. Implement. Bench fake returns six trends covering each link
  shape, plus the existing bench accounts and feeds.
- [ ] **Step 3:** Tests, bench compile, lint → PASS; commit
  `feat(search): load trends and suggestions for Explore`.

### Task 10: Landing UI on phone and in the list pane (`nubecita-vrba.14`)

**Files:**
- Create: `…/impl/ui/TrendingSection.kt`
- Modify: `…/impl/SearchScreen.kt` (Discover branch), the existing Discover section composables, `res/values*/strings.xml`
- Test: screenshot `TrendingSectionScreenshotTest.kt`; update `DiscoverScreenshotTest.kt`, `SearchScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: `DiscoverState.trends`, `DiscoverEvent.OnTrendTapped`, `DiscoverEffect.SubmitSearch`.
- Produces: `TrendingSection(trends, expanded, onToggleExpanded, onTrendTap, modifier)`
  as `LazyListScope` items; `DiscoverLanding` takes `showCards: Boolean`.

Compact: search bar, recent chips, Trending (5 rows, "Show more" expands in place to
all 10), Suggested feeds carousel, People carousel. Medium/Expanded (`isAtLeastMedium`):
search bar, recent chips, all trends; **no** feed or people sections (`showCards = false`),
those move to the pane in Task 11. A trend row: rank, display name, then
`Hot · {category} · {compact count} posts` with the missing parts left out; the whole row
is one click target of at least 48dp. `SubmitSearch` sets the field text and submits
through the existing submit path. The "expanded" flag lives in `DiscoverState`.

- [ ] **Step 1:** Screenshot fixtures: five trends collapsed, ten expanded, a trend with
  no category, loading, section hidden on error; phone landing; list-pane landing.
- [ ] **Step 2:** Implement; wire effects in `SearchScreen`.
- [ ] **Step 3: Device check.** Phone: order as above, "Show more" works, a trend opens
  its feed, back returns to the landing at the same scroll position. Fold: list shows
  trends only; a trend opens its feed in the right pane.
- [ ] **Step 4:** Screenshot compile, tests, lint → PASS; commit
  `feat(search): show trending on the search landing`.

### Task 11: Explore in the right pane

**Files:**
- Create: `…/impl/pane/SearchPaneContract.kt`, `SearchPaneViewModel.kt`, `SearchPane.kt`, `ExplorePaneContent.kt`
- Modify: `…/impl/di/SearchNavigationModule.kt` (`detailPlaceholder = { SearchPane() }`)
- Test: `…/test/…/pane/SearchPaneViewModelTest.kt`; screenshot `ExplorePaneContentScreenshotTest.kt`

**Interfaces:**
- Consumes: `SubmittedSearchStore`, `ExploreRepository` through a second
  `DiscoverViewModel` instance.
- Produces:

```kotlin
internal data class ScrollPosition(val index: Int = 0, val offset: Int = 0)
internal sealed interface SearchPaneMode {
    data object Explore : SearchPaneMode
    data class Summary(val search: SubmittedSearch) : SearchPaneMode   // content added in Task 12
}
internal data class SearchPaneState(
    val mode: SearchPaneMode = SearchPaneMode.Explore,
    val exploreScroll: ScrollPosition = ScrollPosition(),
    val summaryScroll: ScrollPosition = ScrollPosition(),
) : UiState
internal sealed interface SearchPaneEvent : UiEvent {
    data class ScrollSaved(val position: ScrollPosition) : SearchPaneEvent   // saved for the current mode
}
@Composable internal fun SearchPane()        // stateful: hiltViewModel<SearchPaneViewModel>(), hiltViewModel<DiscoverViewModel>()
```

`SearchPane` is composed in the placeholder slot, so both ViewModels are scoped to the
logged-in shell entry and nothing there may use `rememberSaveable`. Scroll: create the
`LazyListState` with the stored position and send `ScrollSaved` from
`DisposableEffect { onDispose { … } }`. `summaryScroll` resets to zero when the search's
query or filters change. Explore content: Suggested feeds, then People to follow, each a
two-column grid of weighted cells; cards reuse the Discover card composables; "Show
more" expands in place (4 shown, then all). In Task 11 the Summary mode renders the same
Explore content; Task 12 replaces it. Navigation from the pane uses
`LocalMainShellNavState.current.add(…)`; confirm the local is available in the slot.

- [ ] **Step 1: Failing tests** `SearchPaneViewModelTest`: `mode is Explore with no search`,
  `mode is Summary after a submit`, `mode returns to Explore after clear`,
  `ScrollSaved stores under the current mode only`,
  `explore scroll survives a search and its clearing`,
  `summary scroll resets when the query changes`, `summary scroll resets when a filter changes`,
  `summary scroll is kept when only the selected tab changes`.
- [ ] **Step 2:** Implement. Screenshot fixtures at 480dp and 700dp pane widths: loaded,
  loading, all sections failed (one retry state), odd item counts (last row has one cell
  and an empty weighted spacer).
- [ ] **Step 3: Device check, fold.** Landing shows trends left, cards right, nothing
  clipped at the pane edge. Scroll the pane, open a profile, back: same scroll position.
  Switch to Home and back: same position. Fold and unfold: same position. Home tab with
  nothing selected never shows Search's pane. Bench sign-out (Task 1): logcat shows both
  pane ViewModels' `onCleared` (add a temporary `Timber.d`, remove before commit).
- [ ] **Step 4:** Run `tab_state_retention` and `pane_boundary_state_retention` journeys
  on the fold.
- [ ] **Step 5:** Tests, screenshot compile, lint → PASS; commit
  `feat(search): show Explore in the detail pane`.

### Task 12: Summary in the right pane

**Files:**
- Create: `…/impl/pane/SummaryPaneContent.kt`
- Modify: `SearchPaneContract.kt`, `SearchPaneViewModel.kt`, `SearchPane.kt`, `res/values*/strings.xml`
- Test: update `SearchPaneViewModelTest.kt`; screenshot `SummaryPaneContentScreenshotTest.kt`

**Interfaces:**
- Consumes: `SearchFirstPages` (`posts`, `people`, `feeds`, `version`), the row
  composables from Task 8, `PostFiltersSheet` via `setFilterSheetOpen`.
- Produces: `SearchPaneMode.Summary` gains
  `hitsTotal: Long?`, `topPost: FeedItemUi.Single?`, `people: ImmutableList<ActorUi>` (max 3),
  `feeds: ImmutableList<FeedGeneratorUi>` (max 3), and one `SummarySectionStatus`
  (`Loading`, `Loaded`, `Error`) per section. `SearchPaneEvent` gains `FilterRemoved(…)`,
  `EditFiltersClicked`, `SeeAllClicked(tab: SearchTab)`, `RetrySection(…)`.

The ViewModel combines `store.submitted` (distinct by query + filters) with
`firstPages.version` and requests the three first pages concurrently. `SeeAllClicked`
calls `store.selectTab`; `FilterRemoved` calls `store.updateFilters`; `EditFiltersClicked`
calls `store.setFilterSheetOpen(true)`. None of them changes the selected tab except
`SeeAllClicked`. Layout is spec section 6: header (query; `About N posts · Top|Latest`
when the count is known; removable filter chips; "Edit filters"), Top post card, People
rows, Feeds rows. Empty sections are hidden; all empty and loaded → `No results for
"{query}"`. Follow and pin from Summary rows go through the same primitives as Task 8.

- [ ] **Step 1: Failing tests:** `summary loads the three first pages once per search key`,
  `a tab loading the same search does not cause a second request` (fake counts calls),
  `a tab pull-to-refresh updates the summary` (bump `version`),
  `a result for an old key is dropped` (Review Focus 1),
  `one section failing leaves the others loaded and RetrySection reloads only it` (Review Focus 3),
  `null hitsTotal omits the count`, `FilterRemoved updates the store and does not change the tab`,
  `SeeAllClicked selects that tab`, `EditFiltersClicked opens the sheet`.
- [ ] **Step 2:** Implement. Screenshot fixtures: full; no count; only posts; no results;
  sections loading; `es-419` with two filter chips.
- [ ] **Step 3: Device check, fold.** Submit: Summary appears with the top post; tapping
  it opens the thread; back returns to the Summary at the same scroll position; back
  again returns to Explore. "See all" under People moves the left pane to People. From
  the People tab, "Edit filters" opens the sheet. Watch `adb logcat` for request lines:
  one request per endpoint per search.
- [ ] **Step 4:** Tests, screenshot compile, lint → PASS; commit
  `feat(search): summarize the search in the detail pane`.

### Task 13: Starter packs: tab, cards, and a pack screen

**Files:**
- Create: modules `feature/starterpack/api` (`StarterPack(uri: String) : NavKey`) and
  `feature/starterpack/impl` (screen, ViewModel, repository, navigation module,
  `consumer-rules.pro`), following `build-logic/README.md`; add both to
  `settings.gradle.kts` and to `:app`'s dependencies
- Create in search: `…/impl/data/SearchStarterPacksRepository.kt` + default + bench fake + DI modules, `StarterPackUi.kt`, `…/impl/SearchPacksViewModel.kt`, `SearchPacksContract.kt`, `SearchPacksScreen.kt`, `…/impl/ui/StarterPackRow.kt`, `StarterPackCard.kt`
- Modify: `SubmittedSearchStore.kt` (`SearchTab.PACKS`), `SearchScreen.kt` (pager `pageCount = 4`, tab label), `SearchFirstPages.kt` (`packs`), `ExploreRepository` + default + bench (`starterPacks`, limit `6`), `ExplorePaneContent.kt`, the phone landing, `SummaryPaneContent.kt` (max 2), `DiscoverViewModel` (starter-pack trend links now navigate)
- Test: unit tests for the new repository, ViewModels and `StarterPackViewModel`; screenshot tests for row, card and screen

**Interfaces (Produces):**

```kotlin
internal data class StarterPackUi(val uri: String, val name: String, val creatorHandle: String,
    val memberCount: Int?, val avatarUrls: ImmutableList<String>)
internal data class SearchStarterPacksPage(val items: ImmutableList<StarterPackUi>, val nextCursor: String?)
internal interface SearchStarterPacksRepository {
    suspend fun searchStarterPacks(query: String, cursor: String?, limit: Int = 25): Result<SearchStarterPacksPage>
}
// SearchFirstPages gains: suspend fun packs(query: String, refresh: Boolean = false): Result<SearchStarterPacksPage>
```

The pack screen (detail-pane role, `ListDetailSceneStrategy.detailPane()`) loads
`app.bsky.graph.getStarterPack` for the header (name, creator, description, feeds) and
pages members with `app.bsky.graph.getList` on the pack's list URI, 50 per page. Each
member row has a Follow button; there is no "Follow all". A pack URI built from a trend
link is `at://{did}/app.bsky.graph.starterpack/{rkey}`, with a handle resolved through
`ExploreRepository.resolveDid`.

- [ ] **Step 1:** Create the two modules; `./gradlew :feature:starterpack:impl:assembleDebug :app:checkSortDependencies` → OK.
- [ ] **Step 2: Failing tests**, then the pack screen: `loads header and first member page`,
  `appends the next page`, `an invalid uri shows the error state`, `follow is optimistic and rolls back`.
- [ ] **Step 3: Failing tests**, then the Packs tab, mirroring `SearchFeedsViewModelTest`
  case for case.
- [ ] **Step 4:** Add packs to Explore (pane and phone), to the Summary, and switch the
  `DiscoverViewModelTest` starter-pack case to
  `a starter-pack trend link navigates to the pack`.
- [ ] **Step 5: Device check.** Packs tab lists and paginates; a pack opens beside the
  list on the fold and full-screen on the phone; Explore and Summary show packs.
- [ ] **Step 6:** All touched modules' tests, bench compile, lint → PASS; commit
  `feat(starterpack): add starter packs to search and a pack screen`.

### Task 14: Journeys and the full gate

**Files:**
- Modify: `app/src/journeysTest/search.journey.xml`
- Create: `app/src/journeysTest/search_second_pane.journey.xml`
- Modify: `CLAUDE.md` (module map: `starterpack`; one paragraph on the Search pane's scoping rule: ViewModels in a `detailPlaceholder` are shell-scoped and must not use `rememberSaveable`)

- [ ] **Step 1:** Update the search journey for the new landing and the Packs tab.
- [ ] **Step 2:** Write `search_second_pane`: landing shows Trending left and cards
  right → submit a search → Summary shows the query → open the top post → back → Summary
  → fold → results list → unfold → Summary → switch to Home and back → Summary at the
  same scroll → back → Explore → back → Feed tab.
- [ ] **Step 3:** Run both journeys on the fold emulator and the search journey on a
  phone; LeakCanary shows no retained objects after three loops.
- [ ] **Step 4: Full gate on the top branch:** `./gradlew :app:assembleDebug`, lint for
  every touched module, `./gradlew spotlessCheck :app:checkSortDependencies`,
  `./gradlew jacocoTestReportAggregated` (the root `testDebugUnitTest` skips flavored
  modules), the compose-expert skill on `git diff origin/main...HEAD`, and Compose
  compiler reports (`-PcomposeReports=true -PdebugSignedRelease=true`) checked for the
  new composables being skippable.
- [ ] **Step 5: Device pass on the real foldable** with the bench build: scroll the
  results list, the Explore pane and the Summary at 120 Hz and note any dropped frames.
- [ ] **Step 6:** Commit `test(search): cover the second pane with a journey`; then land
  with `gh stack merge --squash --yes`, check `gh run list --workflow=release.yaml --limit 5`
  shows one Release run, `bd close` every child and the epic, archive the openspec change.
