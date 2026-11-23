# Tasks

Each group is one bd issue, one branch and one PR in the `nubecita-7d66` stack, in this order. Step-level detail, signatures and exact test names are in `docs/superpowers/plans/2026-11-22-search-v2-explore.md` under the same task number.

## 1. Bench sign-out completes (bd: nubecita-7d66.1)

- [x] 1.1 Bench `FakeAuthRepository.signOut()` flips `FakeSessionStateProvider` to signed-out and `completeLogin` back to signed-in. No unit or screenshot test (bench-only fakes); verify on device: Settings → Sign out shows the Login screen within 2 s, and signing in again shows the Feed tab.

## 2. Search bar sizing (bd: nubecita-7d66.2)

- [x] 2.1 Collapsed search bar fills the width with equal margins; docked dropdown matches the bar's width. Updates the `SearchScreenScreenshotTest` baselines (via the `update-baselines` label); verify on a phone and on the fold that margins are equal and nothing shows beside the dropdown.

## 3. Search bar state across configuration changes (bd: nubecita-uapw)

- [x] 3.1 Find the root cause of the bar re-expanding after recreation on Medium/Expanded and record it in the bd issue. Verify: the reproduction (submit, `cmd uimode night yes`) is explained by the recorded cause.
- [x] 3.2 Fix at the cause. The dark/light steps of `search.journey.xml` are the regression check; verify four times each for theme change, fold/unfold and rotation that the bar stays collapsed and `mInputShown` is false, and that a hand-expanded bar stays expanded.

## 4. Feed in the detail pane (bd: nubecita-7d66.3)

- [x] 4.1 `FeedView` entry takes the detail-pane role; list every push site in the PR. No new unit test (navigation metadata); verify on the fold that a feed from Search opens beside the list and posts stack in the same pane, and on a phone that it is still full-screen.
- [x] 4.2 Check the feed loading indicator on a production debug build and fix it if it stays over loaded posts. Verify: indicator gone once posts render, result noted in the PR.

## 5. Post search on v2 with shared first pages (bd: nubecita-7d66.4)

- [x] 5.1 Signed-in live check of `searchPostsV2` (relevance against v1, `allTime` latency, `since` with `allTime`, `hitsTotal` presence, `following`, the bad-query error). Verify: answers recorded in the PR; stop and report if plain-query relevance is clearly worse.
- [x] 5.2 Filter model and request mapping. Adds `PostSearchFiltersTest` (one test per mapping row, reversed and one-day custom ranges, `activeCount`).
- [x] 5.3 First-page cache and `SearchFirstPages`. Adds `FirstPageCacheTest` (concurrent callers share one fetch, failures not cached, key change, refresh, stale key) and `SearchFirstPagesTest` (sign-out and DID change clear every cache).
- [x] 5.4 Repository moves to v2 with `hitsTotal`; tab ViewModels take first pages from `SearchFirstPages`; bench fake honours the media filter. Updates `DefaultSearchPostsRepositoryTest`, `SearchPostsViewModelTest`, `SearchActorsViewModelTest`, `SearchFeedsViewModelTest`; verify `:feature:search:impl:compileBenchDebugKotlin` passes.

## 6. Submitted-search store and back handling (bd: nubecita-7d66.5)

- [x] 6.1 `SubmittedSearchStore`. Adds `SubmittedSearchStoreTest`.
- [x] 6.2 `SearchViewModel` writes the store; `SearchPostsViewModel` reads query and filters from it; the pager follows the selected tab. Updates `SearchViewModelTest` and `SearchPostsViewModelTest`.
- [x] 6.3 System back from results clears the search. Adds `SearchBackTest` (truth table); verify on phone and fold, including after folding and unfolding mid-search, that back from a thread keeps the search.

## 7. Filter UI and result count (bd: nubecita-7d66.6)

- [x] 7.1 Chip-state derivation. Adds `FilterLabelsTest`.
- [x] 7.2 Chip row, filter sheet hosted by the Search screen, and the count line, with es-419 and pt-BR strings. Adds `PostsFilterRowScreenshotTest` (replacing `PostsSortRowScreenshotTest`) and `PostFiltersSheetScreenshotTest`; verify in the bench build that tapping Media visibly changes the list and the count.

## 8. Result-row polish (bd: nubecita-7d66.7)

- [x] 8.1 Typeahead highlight skips the `.bsky.social` suffix. Adds `TypeaheadHighlightTest`.
- [x] 8.2 People rows show a bio line and Follow; feed rows show a compact like count and Pin. Updates `ActorRowScreenshotTest` and `FeedsTabContentScreenshotTest`; adds follow / pin success and rollback cases to `SearchActorsViewModelTest` and `SearchFeedsViewModelTest`.

## 9. Explore data (bd: nubecita-7d66.8)

- [x] 9.1 Trend link parser. Adds `TrendLinkTest` (DID and handle feed links, starter-pack and search links, malformed and absolute links).
- [x] 9.2 `ExploreRepository` over the unspecced endpoints with bench fake and DI; replaces `SuggestionsRepository`. Adds `DefaultExploreRepositoryTest` (decoding, one section failing, concurrent loads, all failing then recovering, DID change reset); removes `DefaultSuggestionsRepositoryTest`.
- [x] 9.3 `DiscoverViewModel` projects the repository and handles trend taps. Updates `DiscoverViewModelTest` (feed, handle, failed resolve, unknown, search and starter-pack links).

## 10. Landing with Trending (bd: nubecita-vrba.14)

- [x] 10.1 Trending section and the phone landing order; list-pane landing shows Trending only on wide windows. Adds `TrendingSectionScreenshotTest`; updates `DiscoverScreenshotTest` and `SearchScreenScreenshotTest`; verify on a phone that a trend opens its feed and back returns to the same scroll position.

## 11. Explore in the detail pane (bd: nubecita-7d66.9)

- [x] 11.1 `SearchPaneViewModel` with mode and per-mode scroll position. Adds `SearchPaneViewModelTest`.
- [x] 11.2 `SearchPane` in the Search entry's detail placeholder with two-column Explore grids. Adds `ExplorePaneContentScreenshotTest` (two pane widths, loading, all failed, odd counts); verify on the fold: nothing clipped, scroll kept after opening a profile, a tab switch and a fold/unfold, no Search content on the Home tab, and both pane ViewModels cleared on bench sign-out.
- [x] 11.3 Run the `tab_state_retention` and `pane_boundary_state_retention` journeys on the fold. Verify: both pass.

## 12. Summary in the detail pane (bd: nubecita-7d66.10)

- [x] 12.1 Summary state from the store and shared first pages. Updates `SearchPaneViewModelTest` (one load per search key, no second request when a tab loads, refresh propagation, stale key dropped, per-section retry, filter removal keeps the tab, See all, Edit filters).
- [x] 12.2 Summary layout with es-419 and pt-BR strings. Adds `SummaryPaneContentScreenshotTest` (full, no count, posts only, no results, loading, long-label locale); verify on the fold: top post opens and returns to the same scroll position, "See all" switches the tab, "Edit filters" opens from the People tab, and logcat shows one request per endpoint per search.

## 13. Starter packs (bd: nubecita-7d66.11)

- [x] 13.1 New `:feature:starterpack:{api,impl}` modules with the pack screen (header, paged members with Follow, feeds). Adds the screen's ViewModel and repository unit tests and its screenshot test; verify `:feature:starterpack:impl:assembleDebug` and `:app:checkSortDependencies` pass.
- [x] 13.2 Packs results tab over `searchStarterPacksV2`. Adds `SearchPacksViewModelTest` and the pack row screenshot test.
- [x] 13.3 Packs in Explore (pane and phone) and in the Summary; starter-pack trend links open the pack. Updates `DefaultExploreRepositoryTest`, `DiscoverViewModelTest`, `SearchPaneViewModelTest` and the Explore and Summary screenshot tests.

## 14. Journeys and the full gate (bd: nubecita-7d66.12)

- [x] 14.1 Update `search.journey.xml` and add `search_second_pane.journey.xml`; document the placeholder-slot scoping rule and the new module in `CLAUDE.md`. Verify: both journeys pass on the fold emulator, the search journey passes on a phone, and LeakCanary reports no retained objects after three loops.
- [x] 14.2 Top-of-stack gate: `:app:assembleDebug`, lint for every touched module, `spotlessCheck`, `:app:checkSortDependencies`, `jacocoTestReportAggregated`, the compose-expert review of the cumulative diff, and Compose compiler reports for the new composables. Verify: all pass and review findings are resolved.
- [x] 14.3 Device pass on the real foldable with the bench build. Verify: results list, Explore and Summary scroll at 120 Hz with no visible dropped frames, noted in the PR.

## Deviations recorded at landing

- 1.1: "signing in again shows the Feed" was dropped. The bench login opens a browser tab that cannot complete offline; restarting the app restores the signed-in state.
- 4.2: the loading indicator was checked on the bench build, not a production debug build. The cause was the bench feed fixture, which is fixed.
- 11.2: clearing of the pane ViewModels on sign-out was confirmed by reading the code, not on a device (bd: nubecita-rmqd).
- 11.3 and 14.1: the Search steps of `tab_state_retention` (1-25) and `pane_boundary_state_retention` (1-19) passed on the fold emulator; their non-Search tails were not run.
- 14.2: Compose compiler reports were not generated; the compose-expert review covered the cumulative diff.
- 14.3: the real-foldable pass was a `SearchScrollBenchmark` run on a Galaxy Z Fold (numbers in PR #1251), not a visual 120 Hz check. The results-list run is not like-for-like with the feed (bd: nubecita-nrsb).
- Landed as PRs #1237, #1238 and #1240 to #1251 in one atomic merge (main `453b26cbb`), one Release run.

## Workflow follow-up

- Land with `gh stack merge --squash --yes` and confirm one Release run.
- `bd close` every child and the epic `nubecita-7d66`.
- Archive this change.
