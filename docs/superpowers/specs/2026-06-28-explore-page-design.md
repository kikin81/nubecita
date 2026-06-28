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
- **Horizontal card carousels** for both suggested accounts and discover feeds (Google News-style cards). Rationale: compact — accounts and feeds both sit near the top without much vertical scroll. One keyed `LazyRow` per section with stable items keeps 120hz smooth (the nested-scroll caveat is about *many* stacked horizontal rows, not two).
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
- `PinnedFeedsRepository` exposes `observePinnedFeeds(): Flow<…>` emitting **from Room immediately**, plus `refresh()` that fetches network (`getPreferences` → resolve URIs via `getFeedGenerators` for metadata) and write-throughs to Room.
- **`refresh()` must never destroy the last-good cache** (Gemini): if `getFeedGenerators` fails — fully or partially — do **not** `replaceAll` with empty/incomplete rows. Either **abort the DB write** (treat as a failed refresh, keep the existing cache) or **merge** (retain the old cached metadata for any URI that didn't resolve). And write via **diff/upsert against existing rows, not a table-nuke**, so already-rendered pinned icons don't flicker on refresh.
- Add `suspend fun pinFeed(uri): Result<Unit>` / `unpinFeed(uri): Result<Unit>` — a `putPreferences` read-modify-write of the `SavedFeedsPrefV2` `items` under a **write-mutex** (mirrors `:core:moderation`), with **write-through to Room** (optimistic update + rollback on `putPreferences` failure), idempotent. **Non-destructive (Gemini):** AT Proto distinguishes *saved* from *pinned*. `pinFeed` upserts the item with `pinned=true` (adding it if absent); `unpinFeed` sets the existing item's **`pinned=false`** — it does **NOT** drop the item, so a feed saved on another client (official app/web) is never silently deleted from the user's account. (A separate "remove from saved" action, out of scope here, would be the only thing that drops an item.)
- **Cache granularity:** the whole saved-feeds set with each item's `pinned` flag (one `SavedFeedsPrefV2` blob) — serves "pinned feeds fast" and a future saved-feeds-management screen.

Feature modules depend on `:core:feeds`, never `:core:database` directly.

### Component B — Custom-feed view screen (`:feature:feedview:{api,impl}`)

A second timeline screen — needed the moment any custom feed is tapped, hence its own module.

- `:api`: `FeedView(feedUri: String, displayName: String) : NavKey`, registered `@MainShell` as a sub-route.
- `:impl`: `FeedViewViewModel` paginates `app.bsky.feed.getFeed(feed=uri, cursor, limit)`, maps via `:core:feed-mapping`, exposes the same `FeedLoadStatus` lifecycle (idle / initial-loading / refreshing / appending / initial-error) the home timeline uses.
- **Reuse the existing feed-list rendering** (PostCard + paginated `LazyColumn` + like/repost/mute wiring). **Coupling finding (verified):** the home feed is already split `FeedScreen` (stateful) → `FeedScreenContent` (stateless) with a reusable `PostCallbacks` bundle, **but `FeedScreenContent` is tightly coupled to home chrome** (~20 params: feed-switcher chips, pinned-lists sheet, collapsing chip-row nested-scroll, tab-retap, video coordinator) — not reusable wholesale. The genuinely shared core is the **PostCard item-rendering loop** (PostCard + video/quoted/blocked/notFound/same-author-chain slots, driven by `PostCallbacks`) — a bounded ~150-line block.
- **Step B0 (timeboxed extraction):** before the FeedView screen, extract a shared `PostFeedList(items, listState, callbacks, videoSlot, …)` composable from `FeedScreenContent` (Home keeps its chrome and delegates the list); both Home and FeedView consume it. The one real coupling that travels with it is the **video-coordinator + per-video tap dispatcher** — if that balloons the timebox, fall back to a **bridging composable** that shares PostCard + slot wiring while each screen owns its own video coordination. B0 ships as its own small PR ahead of B.
- TopAppBar shows the feed name + a **Pin/Pinned toggle** (reuses Component A) so the feed can be pinned from inside the view too.

### Component C — Discover state (`:feature:search:impl`)

**Data:** new `SuggestionsRepository` (`search/.../data`) wrapping `ActorService.getSuggestions` + `FeedService.getSuggestedFeeds`, mapped to UI models (reuse `AuthorUi`; `FeedGeneratorUi` already exists in search). Suggested feeds cross-reference Component A's saved set to seed each feed's `isPinned`.

**State** — `SearchPhase.Discover` gains, alongside `recentSearches`:
- `suggestedAccounts: ImmutableList<SuggestedAccountUi>` (did, handle, name, avatar, `isFollowing`, `followUri`, optional `mutuals` from `viewer.knownFollowers`); dismissed accounts are filtered out session-locally
- `suggestedFeeds: ImmutableList<SuggestedFeedUi>` (uri, name, creator, avatar, `isPinned`, plus `preview: ImmutableList<PostPreviewUi>?` and a per-card `previewStatus` — idle/loading/loaded/error — populated lazily)
- a per-section load status; **each section loads independently and is hidden on empty/error** (one failing section never blanks the page).

**Behavior:**
- On entering Discover (blank query): fire both fetches concurrently; populate each section as it returns. **Load once on success** (battery-conscious, no polling), but **auto-retry on re-entering Discover if the previous attempt errored or returned empty** (Gemini) — so a transient failure self-heals on the next visit instead of leaving a permanent empty screen. Pull-to-refresh always refetches.
- **Inline Follow** → optimistic `isFollowing` flip + `FollowRepository.follow/unfollow`; tap row → `NavigateTo(Profile(did))`.
- **Inline Pin** → optimistic `isPinned` flip + `PinnedFeedsRepository.pinFeed/unpinFeed` (write-through to Room); tap row → `NavigateTo(FeedView(uri, name))`.
- Both optimistic flips use the **targeted flag-flip rollback** (flip back on current state inside `setState`, no snapshot clobber); failures → snackbar via `SearchEffect`.

**Layout (approved):** vertically stacked sections, each a horizontal `LazyRow` of cards:
1. **Recent searches** (chips, existing)
2. **Suggested accounts** — Threads-style **account cards**: elevated rounded surface, large centered avatar (+ verified badge), display name, **"N mutuals"** with small overlapping mutual avatars (from `viewer.knownFollowers` when present; omitted otherwise), and a full-width **Follow** button. Optional **dismiss `×`** (session-local hide; no stable "not interested" endpoint exists). Card tap → `Profile(did)`.
3. **Discover feeds** — Google News-style **rich feed-preview cards**: a header (feed avatar + name + creator + a **Pin** toggle), then a preview of the feed's **2–3 most-recent posts** (lightweight: author handle/avatar + text snippet + optional thumbnail). Card tap → `FeedView(uri, name)`.

Section headers; ~10 items capped per section; **no "see more" in v1**. Inline Follow/Pin/dismiss buttons live on the card and don't trigger the card tap.

**Battery-safe lazy previews (feed cards):** `getSuggestedFeeds` returns metadata only — the 2–3 sample posts need a `getFeed(feed=uri, limit=3)` per feed. To honor "battery is top priority", **only fetch previews for cards actually on-screen** (drive off the `LazyRow`'s visible items via `snapshotFlow` on `LazyListState`, with a small prefetch buffer), **cache each fetched preview** so re-scroll never refetches, and never fetch the full ~10 up front. A card with no preview yet shows a small shimmer/placeholder where the posts will land.
- **No fling spam (Gemini):** gate the fetch on the carousel being **settled** — only fire for cards still visible once `listState.isScrollInProgress == false` (and/or debounce the `snapshotFlow`), so a fast fling doesn't fire-and-cancel a burst of requests for cards flying past.

## Error / empty handling

- Each Discover section loads and fails independently; a failed/empty section is silently hidden. **If ALL sections error/empty and there are no recent searches**, don't revert to the bare search-bar void (reads like a bug) — show a **subtle "Pull to refresh to discover content" hint** (lightweight text/illustration) so it's clear the system tried (Gemini). Combined with the auto-retry-on-re-enter above, the surface self-heals.
- Follow/pin failures roll back the optimistic flip + show a snackbar.
- `:core:feeds` `refresh()` failure leaves the last good Room cache in place (offline-first).

## Testing

- **A:** `SavedFeedDao` (in-memory Room) — upsert/observe/setPinned; `@AutoMigration` schema test; `PinnedFeedsRepository` — offline-first (emit-from-Room, refresh write-through), **refresh on `getFeedGenerators` failure keeps the last-good cache (abort/merge, no table-nuke)**, **`unpinFeed` sets `pinned=false` and does NOT drop the item**, pin/unpin read-modify-write + write-through + rollback, idempotency, mutex serialization.
- **B:** `FeedViewViewModel` pagination (initial / append / error / refresh) + a screenshot test of the feed-view screen.
- **C:** `SuggestionsRepository` mapping; `SearchViewModel` Discover tests (independent section load, empty/error hide, optimistic follow/pin + rollback, session-local account dismiss); **feed-card preview lazy-load** (visible-item-triggered fetch, cached/no-refetch, never all-up-front, per-card placeholder→loaded→error); screenshot tests (Discover populated, feed card with preview, empty).

## Out of scope (explicit)

- **Trending topics** (unspecced endpoint) and **interest chips** — deferred; revisit if/when a trending/topic surface is built.
- **A "Following / Suggested for you" source toggle** (the Google News segmented control) — v1 shows suggestions only; pinned feeds live elsewhere.
- **"See more" pagination** for Discover sections — v1 shows the first page only.
- **Cache-first profile loading** (me-tab fast open) — separate epic **nubecita-dwmf** (shares the Room-cache pattern; designed later).
- **Full offline support** — A lays the foundation for the user's own feeds; broader offline is future.

## Delivery

One Explore epic in bd; implement and PR **A → B0 → B → C** in sequence (B0 extracts `PostFeedList`; C consumes A + B), via the standard subagent-driven flow.
