# Search upgrade: v2 filters, Explore, and a useful second pane

Date: 2026-11-22 · Status: approved 2026-11-22 · One epic, landed as one `gh stack`

## Intent

Search works, but it is thin. The Posts tab offers only Top and Latest, the landing is
two carousels with nothing trending, and on a foldable the right half of the window says
"Select a post to read" while the left half clips those carousels. Bluesky now has a v2
post search with real filters, and a set of discovery endpoints (trends, suggested feeds,
people, starter packs) that the app does not use.

The user wants Search to be good at every width: real filters on posts, a landing worth
opening, and a right pane on foldables and tablets that always shows something on-topic.
The app's standing values apply: Material 3 design, adaptive layouts, and 120 Hz
scrolling.

## Goals

1. **Filters on the Posts tab**, backed by `app.bsky.feed.searchPostsV2`.
2. **An Explore landing** with trends, suggested feeds, people to follow and starter
   packs, on phone and on wide windows.
3. **A right pane that is never a dead end** on Medium and Expanded widths: Explore
   before a search, a Summary of the search after one, and the opened item after a tap.
4. **The fold and phone bugs from the 2026-11-22 journey run are fixed** (section 9).
5. **No extra network calls** from the second pane: it shares requests with the tabs.

## Non-goals

- Filtering by author, mention, or link domain (`authors`, `mentions`, `domains`, `urls`
  and their exclude forms). Each needs a picker. Follow-up work.
- "Search this thread" and "search this profile" entry points (`threadRootUri`,
  `authors`).
- Filter-only searches with an empty query.
- A Summary on phones. On Compact width, matching people, feeds and packs stay in their
  tabs.
- Restoring a submitted search after process death. Today it is not restored; that stays.
- `nubecita-0rj4` (image taps in search results) and `nubecita-f34c` (search analytics).
  They are related and stay standalone. This epic must not make either worse: the second
  pane never emits `search_perform`.
- A `SupportingPaneSceneStrategy` in `MainShell`.

## Anti-goals

- Opening the first result automatically. It costs a thread fetch per search, marks a row
  the user did not pick, and hides the Summary.
- An event bus between the Search screen and the pane.
- A pane that feels sloppy: scroll position that resets after every opened post, content
  from another tab flashing in, or a back gesture that clears the search from under an
  open thread.

## Constraints

- **The pane slot is outside the Search entry.** `ListDetailSceneStrategy` composes
  `detailPlaceholder` at the scene level, not inside the list entry. Verified on the fold
  emulator with a throwaway probe:
  - `hiltViewModel()` there resolves to the logged-in shell entry's store. The instance
    survived tab switches, theme changes (activity recreation), fold and unfold, and
    opening then closing a detail.
  - `rememberSaveable` there is **lost** whenever the placeholder leaves composition (tab
    switch, fold, opening a detail). It survives only activity recreation.
  - Clearing on logout was not verified: bench sign-out never completes (section 9).
- **Cross-tab isolation already exists.** `ActiveTabScopedSceneStrategy` hands the
  list-detail strategy only the active tab's slice of the back stack, so Search's
  placeholder is never composed on another tab. Read from code, not exercised.
- **Search v2 was not exercised with free text.** The public endpoint returns 403 without
  auth. The app already calls v2 authenticated for hashtags. Section 10 lists what to
  check live before the Posts tab switches over.
- **The discovery endpoints are unspecced** and absent from the SDK's generated services.
  The app already calls one such endpoint with hand-written request and response classes
  (`DefaultSearchFeedsRepository`); the new ones follow that pattern. No SDK release is
  needed.
- **There is no starter-pack screen in the app today** (section 7).
- Repo conventions hold: MVI on `MviViewModel`, flat state with a sealed status for
  mutually exclusive modes, `ImmutableList` in state, surface-role tokens, es-419 and
  pt-BR strings in the same commit, bench fakes for every new repository.

## Approaches

For the right pane on wide windows:

1. **Explore and Summary in the existing `detailPlaceholder` slot.** *Chosen.* The slot is
   where the dead-end text renders today; a tapped item still replaces it and back
   returns to it. Nothing changes in `MainShell`.
2. **A `SupportingPaneSceneStrategy` beside list-detail.** It ships in
   adaptive-navigation3 1.3.0, but the supporting pane needs its own back-stack entry and
   a second strategy competing for the Search entry. Rejected.
3. **Search owns both panes itself.** A pushed post could then no longer replace only the
   right half without a custom scene. Rejected.

For sharing search state with the pane:

1. **Mirror store with four writers** (the Search ViewModel and three tab ViewModels each
   push their piece). Trivial pane, but hand-copied state, a reset obligation per writer,
   and "matching feeds" stays empty until the Feeds tab is visited. Rejected.
2. **A store that holds only what was searched, plus shared first-page results in the
   repositories.** *Chosen.* Nothing is mirrored; pane and tabs await the same request.
3. **Lend the pane the Search entry's ViewModel store.** No new state, but it holds a
   navigation-library owner outside its entry. Rejected.

For what the pane shows after a search: keep Explore, auto-open the first result, or show
a Summary. The user chose a sequence: Explore, then Summary with the top post as a card,
then the tapped item. No auto-open.

## 1. The right-pane state machine

| Moment (Medium / Expanded) | Left pane | Right pane |
|---|---|---|
| Landing, nothing searched | Search bar, recents, Trending | **Explore** |
| Search submitted, nothing opened | Tabs and results | **Summary** |
| Post, person, feed, trend or pack opened | unchanged | that item |
| Back from the item | unchanged | Summary (or Explore if no search) |
| Back from Summary | landing | Explore (the search is cleared) |

On Compact width there is one pane: landing, then results, then the opened item. Back
from the item returns to results; back from results clears the search and returns to the
landing.

**Back handling.** The "clear search" back handler lives in the Search screen and is
enabled only when all three hold: Search is the top entry of its tab's stack, results are
showing, and the search bar is collapsed. The check reads the back stack, not the pane
layout, so folded and unfolded behave the same and folding mid-search needs no special
case. Clearing the search uses the existing path (blank the field), which also resets the
store in section 2. Today system back from results jumps straight to the Feed tab; after
this change that happens only from the landing.

## 2. Shared search state

**`SubmittedSearchStore`** (`:feature:search:impl`, `@Singleton`, internal) holds what was
searched and nothing else:

```kotlin
data class SubmittedSearch(
    val query: String,
    val filters: PostSearchFilters,   // includes sort
    val selectedTab: SearchTab,
    val isFilterSheetOpen: Boolean,
)
val submitted: StateFlow<SubmittedSearch?>   // null = no search, pane shows Explore
```

- `SearchViewModel` is the only writer of `query`: it publishes on submit, republishes on
  creation, and resets to `null` when the search is cleared and in `onCleared()`.
- `filters` and `selectedTab` are changed through the store's own methods by whichever
  surface the user touched: the chip row and the filter sheet on the left, the removable
  chips and "See all" in the Summary on the right. *Builder's call, flagged for review:
  this widens "single writer" to "single writer for the query"; it is what lets the
  Summary's chips and "See all" work without an event channel.* The pager follows
  `selectedTab`; the tab ViewModels read `query` and `filters` from the store.
- **The filter sheet is hosted by the Search screen, not by the Posts tab**, and is shown
  while `isFilterSheetOpen` is true. That flag is plain state: either side sets it, and
  dismissing the sheet clears it. So "Edit filters" in the Summary opens the sheet
  whichever results tab is active on the left, with no one-shot event. Neither "Edit
  filters" nor removing a chip in the Summary switches the left pane's tab: the filters
  change in the store, the Posts first page is re-requested through section 3, and the
  Summary's count and top post update. The Posts tab shows the new results when the user
  returns to it.
- The typed text and the search bar's expansion stay where they are (`TextFieldState` in
  `SearchViewModel`, `SearchBarState` in the screen).

**`SearchPaneViewModel`** (created with `hiltViewModel()` inside the placeholder, so it is
scoped to the logged-in shell entry) exposes a read-only `StateFlow<SearchPaneState>`
whose mode is a sealed `Explore` / `Summary(...)`. It derives the mode from the store and
gets Summary content from the same repositories the tabs use. It also holds what
`rememberSaveable` cannot keep in this slot:

- one scroll position (index, offset) for Explore and one for Summary. Summary's resets
  when the submitted search key (query + filters) changes; Explore's is kept across
  searches.
- any expanded or collapsed section toggles.

The pane is only composed on wide windows, so on a phone this ViewModel is never created
and makes no calls.

## 3. Shared first-page results

Each search repository (posts, people, feeds, starter packs) remembers the first page of
the current search:

- **Key:** the full parameter tuple. For posts that is query + every filter + sort; for
  the others it is the query. One entry per repository.
- **In-flight sharing:** the entry holds the running request (a `Deferred` or equivalent),
  not just a finished result, so the Posts tab and the pane arriving in the same frame
  await one call. A failed request is not cached.
- **Refresh:** pull-to-refresh on a tab replaces that repository's entry and emits, so the
  Summary follows the tab list.
- Later pages are not cached.

Cost on a wide window: the Summary asks for the first page of people, feeds and packs when
a search is submitted. People is a call the pager already makes (it preloads the
neighbouring tab). Feeds and packs are two calls that would otherwise wait for a tab
visit; when the user does visit, the tab is served from the entry. The Summary uses the
tabs' page size so the entry can be shared.

## 4. Posts tab: search v2 and filters

The Posts tab moves from `searchPosts` to `searchPostsV2` (already in atproto 10.0.0 and
already used by the hashtag screen), subject to the live check in section 10.

**Filter row (chosen: one scrolling chip row).** In order:

`☰ n` · `Top ▾` · `Following` · `Media` · `Video` · `Language ▾` · `Date ▾`

- `☰ n` opens the full sheet and shows the number of active filters.
- Sort is a menu chip, not a button group, because long es-419 and pt-BR labels break
  button groups in this app.
- Chips keep their position. An active chip takes selected styling and, where it has a
  value, shows it in place: `Language` becomes `English`, `Date` becomes `Past week`.
  *Builder's call between the two options offered: in-place labels rather than moving
  active chips to the front, so chips do not jump under the user's finger.*
- The row appears only on the Posts tab; the other tabs have no filter parameters.

**Filter sheet.** A modal bottom sheet, width-capped on wide windows. First set:

| Filter | Choices | Sent as |
|---|---|---|
| Sort | Top, Latest | `sort=top` / `recent` |
| From | Anyone, People you follow | `following=true` |
| Media | Any, Has media, Video only | `hasMedia=true` / `hasVideo=true` |
| Replies | Include, Hide replies, Only replies | `excludeReplies=true` / `repliesOnly=true` |
| Language | Any, one language | `languages=[code]` |
| Date | **All time (default)**, Past day, Past week, Past month, Custom range | see below |

**Date mapping.** Without `allTime`, v2 searches only a recent-post window, which would
silently hide older posts. So "All time" is the default and sends `allTime=true` with no
bounds. The presets send `since`; a custom range sends `since` and `until` from an M3
date-range picker. The mockup's "Recent" choice is dropped: its window length is not
defined by the lexicon, so the label could not be explained to a user. Whether a preset
must also send `allTime=true` to reach past the recent window is part of the live check.

**Result count.** `hitsTotal` is optional in the lexicon and is an estimate. When present,
"About 1,240 posts" shows under the chip row (compact, localized number formatting). When
absent or null, the line is omitted. The sheet's button reads "Show about N posts" or just
"Show results".

**Lifetime of filters.** Filters persist across successive searches while results are
showing and reset when the search is cleared. *Builder's call.*

**Errors.** `BadQueryString` maps to the Posts tab's existing error state with its own
message.

## 5. Explore

**Content and source** (all read through one `ExploreRepository` that keeps the loaded
sections in memory, shares in-flight requests, and reloads on pull-to-refresh):

| Section | Endpoint | Tap |
|---|---|---|
| Trending | `app.bsky.unspecced.getTrends` | opens the trend's feed |
| Suggested feeds | `app.bsky.unspecced.getSuggestedFeeds` | opens the feed; Pin button |
| People to follow | `app.bsky.unspecced.getSuggestedUsersForExplore` | opens the profile; Follow button |
| Starter packs | `app.bsky.unspecced.getSuggestedStarterPacks` | opens the pack (section 7) |

- A trend row shows rank, display name, category, formatted post count, and a "Hot" mark
  when `status` is `hot`. Unknown status values show no mark.
- A trend's `link` is a path. The parser recognises three shapes and treats everything
  else as unknown:
  - `/profile/{actor}/feed/{rkey}`, where `{actor}` is a DID **or a handle**. It opens the
    existing `FeedView` with `at://{did}/app.bsky.feed.generator/{rkey}`. `FeedView`
    passes its URI to the repository untouched, so a handle is resolved to a DID first
    (one `com.atproto.identity.resolveHandle` call at tap time, only for handle links).
  - `/starter-pack/{actor}/{rkey}` opens the starter-pack screen (section 7).
  - `/search?q=…` submits that query as a search.
  - Anything unrecognised, or a handle that fails to resolve, submits the trend's display
    name as a search. A trend tap always goes somewhere.
  The live sample on 2026-11-22 contained only the DID feed shape; the other shapes are
  handled defensively and unit-tested from fixtures.
- A section that fails or comes back empty is hidden; if everything fails the pane shows
  one retry state. *Builder's call.*
- The current `getSuggestions` / `feed.getSuggestedFeeds` calls behind the Discover
  carousels are replaced by the endpoints above. Today's feed-card post preview is kept
  as it is.
- People, trend actors and packs go through the same moderation mapping as the rest of
  the app before they reach the UI.
- Everything held in memory for Explore and for section 3 is dropped when the session
  changes (logout or account switch), so one account never sees another's suggestions.

**Wide windows (chosen: trends in the list, cards on the right).**

- Left pane: search bar, recent-search chips, Trending as ranked text rows.
- Right pane: Suggested feeds, People to follow, Starter packs, each a **true two-column
  grid sized to the pane** (rows of two weighted cells inside one lazy column, never a
  horizontal carousel, nothing clipped at the pane edge).

**Phone.** One column in the same reading order: search bar, recent chips, Trending (five
rows, "Show more" expands in place), then feeds, people and packs as carousels.

**"See all" in Explore becomes "Show more" in place** up to what was fetched. *Builder's
call, departing from the mockup: a real "See all" needs three new list screens.*

## 6. Summary (wide windows only; chosen: rows with a line of context)

Top to bottom:

1. **Header:** the query, "About N posts · Top" when the count is known, the active
   filters as removable chips, and an "Edit filters" chip that opens the sheet.
2. **Top post:** the first post of the Posts first page as a card. Tap opens post detail.
3. **People:** the first three people, each a full-width row with name, a one-line bio,
   and Follow. "See all" switches the left pane to the People tab.
4. **Feeds:** the first three feeds, each a row with name, description or author, likes,
   and Pin. "See all" switches to the Feeds tab.
5. **Starter packs:** the first two packs. "See all" switches to the Packs tab.

Sections with no results are hidden. If every section is empty the pane shows "No results
for {query}". Each section shows a placeholder while its request is running.

## 7. Starter packs

`app.bsky.graph.searchStarterPacksV2` (in the SDK) adds a fourth results tab, **Packs**.

There is no starter-pack screen in the app, and packs now appear in three places (Explore,
Summary, the Packs tab), so a tap needs somewhere to go. **Builder's call, the main one
to review:** the epic includes a minimal native starter-pack screen with a header (name,
creator, description), the member list with a Follow button per row, and the pack's feeds.
It takes the detail-pane role. "Follow all" is left out. It is the last slice of the
stack, so packs can be dropped from the epic without touching anything else. The
alternative is to open packs in the browser, which is cheaper but leaves the app for a
core discovery surface.

## 8. Result-row polish

- **People rows:** add the one-line bio and a Follow button.
- **Feed rows:** format the like count (48.9K, not 48902) and add Pin, matching the
  Discover cards.
- **Typeahead:** do not highlight matches inside the handle's domain suffix
  (`.bsky.social`).

## 9. Fixes folded into the epic

| Fix | Detail |
|---|---|
| Overlay reopens after a configuration change (`nubecita-uapw`) | On Medium/Expanded the docked search bar re-expands and raises the keyboard after a theme change, an unfold, or any activity recreation. Reproduced 4 of 4 on theme changes and again on unfold. |
| Feed opens full-window on wide screens | `FeedView` has no pane role. Give it the detail-pane role, as `Hashtag` has. Trends and feed taps depend on this. As of 2026-11-22 its only push sites are the two in Search (`SearchScreen`, `SearchFeedsScreen`); re-check at implementation time, including any deep-link path. Verify on a wide window that a feed opened from every tab it can be reached from lands beside a list, or full-screen where that tab has no list pane, and never in a cramped pane; and that posts opened from the feed stack above it in the same pane. |
| Loading indicator stays over a loaded feed | Seen twice about 10 s apart on the fold in the bench build. Confirm on production data; fix if real. |
| Docked typeahead narrower than the pane | Content underneath shows along its right edge. |
| Search pill start-aligned on phones | `SearchBar` has no `fillMaxWidth`; margins are 16dp left and about 50dp right. |
| Back from results skips the landing | Section 1. |
| Wrong placeholder copy on People and Feeds | Goes away: the placeholder becomes Explore and Summary. |
| Bench sign-out never completes | The bench auth fake returns success but the session never changes. Make it flip the session to logged-out, then use it to verify that `SearchPaneViewModel.onCleared()` runs on logout. |

`nubecita-uapw` and `nubecita-vrba.14` (trending in the empty Search state) move under
this epic; the old Search epic `nubecita-vrba` stays as it is.

## 10. Verification

- **Live check before the Posts tab switches to v2** (production debug build, signed in):
  free-text results against v1 for a handful of queries; latency with `allTime=true`;
  whether a `since` preset needs `allTime`; how often `hitsTotal` is present; the
  `following` filter; the `BadQueryString` path. If plain-query relevance is clearly
  worse on v2, stop and bring that back before going further.
- **Unit tests:** the store, both ViewModels, filter-to-parameter mapping, and each
  repository's first-page entry, including two concurrent callers producing one request
  and a refresh replacing the entry.
- **Screenshot tests** for every new composable: Explore and Summary at pane width, the
  phone landing, the chip row with and without active filters, the sheet, people and feed
  rows. Include a long-label locale for the chip row.
- **Bench fakes** for every new repository. The post-search fake must honour at least one
  filter so a smoke test can show a filter visibly changing the list.
- **Journeys:** update `search.journey.xml` for the new landing, and add a fold journey
  covering Explore → Summary → item → back → back, a fold and unfold mid-search, and a tab
  switch and return with scroll position kept. Run `tab_state_retention` and
  `pane_boundary_state_retention` as well, since the pane touches a scene placeholder.
- **Scrolling:** stable keys and content types in every lazy list, no nested scrolling in
  the same direction, fixed image sizes. Check the Compose compiler reports for the new
  composables, and do the device pass on the real foldable; emulators cannot show 120 Hz.

## Left to the builder

- Names, package layout, and how the stack is sliced. A workable order: the standalone
  fixes; `FeedView` pane role; v2 migration with shared first pages; filters; the store
  and back handling; Explore data; phone landing; Explore pane; Summary; Packs tab and
  screen; row polish; journeys.
- Section limits (how many trends, feeds, people and packs to fetch and show).
- The language list in the filter (the user's content languages first is a sensible
  start).
- Loading placeholders and motion.
