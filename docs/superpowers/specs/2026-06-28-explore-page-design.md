# Explore Page (Search Discover State) — Design

**Status:** Approved design, ready for implementation planning.
**Date:** 2026-06-28
**Goal:** Replace the empty no-query Search state with a useful Discover surface — suggested accounts (with inline Follow) and suggested feeds (with inline Pin + open) — and build the two reusable feed primitives that requires.

## Background

`:feature:search:impl` already has the query path (typeahead + Posts/People/Feeds result tabs + recent searches). The **no-query state** (`SearchPhase.Discover`) currently renders **only recent searches**, so a new user sees an empty page below the search bar (the reported gap).

The official Bluesky app's Explore shows: interest chips, trending topics, suggested accounts, and discover feeds. We deliberately scope to what stable AT Proto endpoints support well.

## Decisions

- **Stable endpoints only.** Suggested accounts via `app.bsky.actor.getSuggestions` and suggested feeds via `app.bsky.feed.getSuggestedFeeds` — both stable and already generated in our atproto-kotlin SDK.
- **No trending topics in v1.** `app.bsky.unspecced.getTrendingTopics` is the only trending source; it's in the `unspecced` namespace (not generated in our SDK; can change/break without notice). Deferred — would be a best-effort, gracefully-degrading add later.
- **No "Your interests" chips in v1.** Those chips edit interest prefs to tune *algorithmic/trending* discovery, which we're not building — without a trending/topic-feed destination they have nowhere to go.
- **Vertical lists**, not horizontal carousels (most scannable, big tap targets for inline actions, no nested horizontal-in-vertical scroll — best for 120hz; matches the official app).
- **Inline Follow** on suggested accounts; **inline Pin** on suggested feeds.

## Architecture — three components, sequenced A → B → C

Delivered as one **Explore epic**, three sequential PRs (C composes A + B). A and B are reusable beyond Search.

### Component A — Room-cached saved feeds

Foundation for inline Pin *and* fast pinned-feed rendering everywhere ("no full offline yet, but future-proof").

**`:core:database`**
- New `SavedFeedEntity` (table `saved_feeds`) caching **display-ready** rows: `uri` (PK), `displayName`, `creatorHandle`, `avatarUrl`, `pinned: Boolean`, `position: Int`. Storing resolved generator metadata (not just URIs) is what makes pinned feeds render instantly with names/avatars — no network on the render path.
- `SavedFeedDao`: `observeSavedFeeds(): Flow<List<SavedFeedEntity>>`; `@Transaction suspend fun replaceAll(feeds)`; targeted `setPinned`/delete for write-through. Same-file `SavedFeedEntity.asExternalModel()`.
- Schema export bump → commit `{N}.json` + an `@AutoMigration` (per `:core:database` conventions).

**`:core:feeds` → offline-first**
- `PinnedFeedsRepository` exposes `observePinnedFeeds(): Flow<…>` emitting **from Room immediately**, plus `refresh()` that fetches network (`getPreferences` → resolve URIs via `getFeedGenerators` for metadata) and **write-through** replaces the Room rows.
- Add `suspend fun pinFeed(uri): Result<Unit>` / `unpinFeed(uri): Result<Unit>` — a `putPreferences` read-modify-write of the `SavedFeedsPrefV2` `items` (append `SavedFeed(type="feed", value=uri, pinned=true, id=<fresh TID>)` / drop the match) under a **write-mutex** (mirrors `:core:moderation`'s preference-write pattern), with **write-through to Room** (optimistic update + rollback on `putPreferences` failure). Idempotent.
- **Cache granularity:** the whole saved-feeds set with each item's `pinned` flag (one `SavedFeedsPrefV2` blob) — serves "pinned feeds fast" and a future saved-feeds-management screen.

Feature modules depend on `:core:feeds`, never `:core:database` directly.

### Component B — Custom-feed view screen (`:feature:feedview:{api,impl}`)

A second timeline screen — needed the moment any custom feed is tapped, hence its own module.

- `:api`: `FeedView(feedUri: String, displayName: String) : NavKey`, registered `@MainShell` as a sub-route.
- `:impl`: `FeedViewViewModel` paginates `app.bsky.feed.getFeed(feed=uri, cursor, limit)`, maps via `:core:feed-mapping`, exposes the same `FeedLoadStatus` lifecycle (idle / initial-loading / refreshing / appending / initial-error) the home timeline uses.
- **Reuse the existing feed-list rendering** (PostCard + paginated `LazyColumn` + like/repost/mute wiring). Implementation note: verify whether `:feature:feed`'s list is extractable into a shared composable (preferred — extract-and-share) or currently private (then extract as part of B). Resolve in the plan.
- TopAppBar shows the feed name + a **Pin/Pinned toggle** (reuses Component A) so the feed can be pinned from inside the view too.

### Component C — Discover state (`:feature:search:impl`)

**Data:** new `SuggestionsRepository` (`search/.../data`) wrapping `ActorService.getSuggestions` + `FeedService.getSuggestedFeeds`, mapped to UI models (reuse `AuthorUi`; `FeedGeneratorUi` already exists in search). Suggested feeds cross-reference Component A's saved set to seed each feed's `isPinned`.

**State** — `SearchPhase.Discover` gains, alongside `recentSearches`:
- `suggestedAccounts: ImmutableList<SuggestedAccountUi>` (did, handle, name, avatar, `isFollowing`, `followUri`)
- `suggestedFeeds: ImmutableList<SuggestedFeedUi>` (uri, name, creator, avatar, `isPinned`)
- a per-section load status; **each section loads independently and is hidden on empty/error** (one failing section never blanks the page).

**Behavior:**
- On entering Discover (blank query, first time): fire both fetches concurrently; populate each section as it returns. **Load once per VM lifetime; pull-to-refresh to refetch** — battery-conscious, no polling.
- **Inline Follow** → optimistic `isFollowing` flip + `FollowRepository.follow/unfollow`; tap row → `NavigateTo(Profile(did))`.
- **Inline Pin** → optimistic `isPinned` flip + `PinnedFeedsRepository.pinFeed/unpinFeed` (write-through to Room); tap row → `NavigateTo(FeedView(uri, name))`.
- Both optimistic flips use the **targeted flag-flip rollback** (flip back on current state inside `setState`, no snapshot clobber); failures → snackbar via `SearchEffect`.

**Layout (approved):** Recent → Suggested accounts → Discover feeds; section headers; ~10 items capped per section; **no "see more" in v1** (show the first page).

## Error / empty handling

- Each Discover section loads and fails independently; a failed/empty section is silently hidden. If all sections are empty and there are no recent searches → just the search bar (current behavior).
- Follow/pin failures roll back the optimistic flip + show a snackbar.
- `:core:feeds` `refresh()` failure leaves the last good Room cache in place (offline-first).

## Testing

- **A:** `SavedFeedDao` (in-memory Room) — upsert/replaceAll/observe/setPinned; `@AutoMigration` schema test; `PinnedFeedsRepository` — offline-first (emit-from-Room, refresh write-through), pin/unpin read-modify-write + write-through + rollback, idempotency, mutex serialization.
- **B:** `FeedViewViewModel` pagination (initial / append / error / refresh) + a screenshot test of the feed-view screen.
- **C:** `SuggestionsRepository` mapping; `SearchViewModel` Discover tests (independent section load, empty/error hide, optimistic follow/pin + rollback); screenshot tests (Discover populated, empty).

## Out of scope (explicit)

- **Trending topics** (unspecced endpoint) and **interest chips** — deferred; revisit if/when a trending/topic surface is built.
- **"See more" pagination** for Discover sections — v1 shows the first page only.
- **Cache-first profile loading** (me-tab fast open) — separate epic **nubecita-dwmf** (shares the Room-cache pattern; designed later).
- **Full offline support** — A lays the foundation for the user's own feeds; broader offline is future.

## Delivery

One Explore epic in bd; implement and PR **A → B → C** in sequence (C consumes A + B), via the standard subagent-driven flow.
