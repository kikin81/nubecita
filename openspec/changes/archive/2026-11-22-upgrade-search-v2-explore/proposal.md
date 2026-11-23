# Proposal

## Why

Search is thin: the Posts tab offers only Top and Latest, the landing has nothing trending, and on foldables and tablets the right half of the window says "Select a post to read" while the left half clips two carousels. Bluesky now offers a v2 post search with real filters and discovery endpoints (trends, suggested feeds, people, starter packs) that the app does not use. A journey run on 2026-11-22 also found fold and phone bugs in the current surface.

## What Changes

- The Posts tab searches with `app.bsky.feed.searchPostsV2` and gains filters: sort, people you follow, media, replies, language and date, with "All time" as the default date. An estimated result count is shown when the server provides one.
- The search landing gains a Trending list on every width.
- On Medium and Expanded widths the right pane is never a dead end: it shows Explore (suggested feeds, people to follow, starter packs) before a search, a Summary of the search after one, and the opened item after a tap. The first result is never opened automatically.
- A fourth results tab, Packs, lists starter packs; a new starter-pack screen shows a pack's members and feeds.
- People rows gain a bio line and Follow; feed rows gain a formatted like count and Pin; typeahead no longer highlights inside the `.bsky.social` suffix.
- System back from results returns to the landing (clearing the search) instead of jumping to the Feed tab.
- A feed opened on a wide window opens in the detail pane instead of taking the whole window.
- Fixes: the search bar no longer re-expands with the keyboard after a configuration change; the collapsed pill fills the width on phones; the docked dropdown matches the pane; bench sign-out completes.

## Non-goals

- Filtering by author, mention or link domain; "search this thread" and "search this profile" entry points; filter-only searches with an empty query.
- A Summary on Compact width.
- Restoring a submitted search after process death.
- "Follow all" on a starter pack.
- `nubecita-0rj4` (image taps in search results) and `nubecita-f34c` (search analytics).
- A supporting-pane scene strategy in the main shell.

## Capabilities

### New Capabilities
- `feature-starterpack`: viewing a starter pack — its header, members with follow, and feeds.

### Modified Capabilities
- `feature-search`: post filters and result count, Trending and Explore on the landing, the detail pane's Explore and Summary states, the Packs tab, richer result rows, back behaviour from results, and search-bar behaviour across configuration changes.
- `feature-feed`: a single-feed screen takes the detail-pane role on wide windows.

## Impact

- Code: `:feature:search:impl` (most of the change), `:feature:feed:impl` (one navigation entry), `:core:auth` bench fakes, new `:feature:starterpack:{api,impl}`, `:app` (new module wiring, journeys).
- APIs: `app.bsky.feed.searchPostsV2`, `app.bsky.graph.searchStarterPacksV2`, `app.bsky.graph.getStarterPack`, `app.bsky.graph.getList` (all in atproto-kotlin 10.0.0); `app.bsky.unspecced.getTrends`, `getSuggestedFeeds`, `getSuggestedUsersForExplore`, `getSuggestedStarterPacks` through hand-written request and response classes, as `getPopularFeedGenerators` is called today. No SDK release is needed.
- Baseline deviations: none from MVI, Compose, Hilt or Coil. Two points worth calling out: ViewModels created in the list-detail placeholder slot are scoped to the logged-in shell entry rather than to the Search entry, and they hold scroll position themselves because saved instance state does not survive there. No Room changes.
- Network: on wide windows a search requests the first page of feeds and packs up front (shared with their tabs if visited). Phones make no additional calls.
- Tracking: bd epic `nubecita-7d66`. Design: `docs/superpowers/specs/2026-11-22-search-v2-explore-design.md`. Plan: `docs/superpowers/plans/2026-11-22-search-v2-explore.md`.
