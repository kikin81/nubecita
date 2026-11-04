## Why

Every time you open Nubecita, your place in the Following timeline is gone.

In-memory caching already works within a session — scroll the feed, switch to another feed, come back, and your position is intact. But the feed screen holds its posts in a plain `ImmutableList` on the ViewModel, so process death takes them with it. On the next launch the screen fetches page 1, renders a timeline that has nothing to do with the one you were reading, and the posts you had not got to yet are somewhere below the fold with no way back to them. If you read in sittings — a few minutes at a time, several times a day — the app silently discards your progress between every one.

Third-party Bluesky clients ship this as "Maintain your position" and users ask for it by that name. It is also the single most-requested behaviour that the app's own architecture already *almost* supports: `:core:feed-cache` is a complete offline-first Room cache with a Paging 3 `RemoteMediator`, DID-keyed partitions, a 500-post cap and eviction — and the feed screen does not read a single row of it. The widget and the Wear companion do. The app's own most-used surface has a second, network-only data path.

This change makes the Following feed read from that cache, remembers where you were, and fetches only what is actually new.

## What Changes

**The persisted unit widens from a post to a feed entry.** `FeedPostMappers` serializes the bare wire `PostView` into `postBlob` and states outright that `FeedViewPost.reason` — the repost overlay — "is NOT persisted"; the entry's reply root/parent goes the same way. A flat widget row does not miss them. `FeedItemUi.ReplyCluster` and `FeedItemUi.SelfThreadChain` are built from exactly that dropped context, so the cache as built today cannot represent the app's feed. The blob becomes the `FeedViewPost`, with the denormalized columns the grouping needs. Room schema **v6 → v7**, committed `7.json`.

**The `FeedItemUi` projection moves to the write side.** `FeedItemDedupe` is a two-pass pipeline — `dedupeByThreadRoot`, then the cluster-context pass — whose KDoc documents that the passes are non-commutative and that reposts are exempt, tuned against 216 live timeline entries. It needs a whole page in hand. A `pagingMap` over a `PagingSource` sees items one at a time, so running it downstream of Paging would break the invariant at precisely the page boundaries. Projecting at write time, per fetched page, is both the only place the inputs exist and strictly more correct than today: the grouping stops being re-derived on every fetch and becomes a durable property of the partition.

**The `RemoteMediator` gains a PREPEND path.** Today it has none — a REFRESH calls `clearPartition` and replaces the partition. PREPEND fetches `getTimeline(since = <the anchored entry's startCursor>)`, a server-bounded range, and writes the new entries at **negative** positions above the anchor. Nothing renumbers, so the active `PagingSource` is not wholesale-invalidated, and `trimToCap`'s `DELETE WHERE position >= (SELECT MIN(position) + :cap)` arithmetic holds unchanged.

**The background widget worker stops clearing the partition.** `FeedRefresher.refresh()` runs `clearPartition` + `upsert(page 1)` in one transaction, and the widget worker calls it every 15 minutes whenever a widget is placed or a watch is paired. Its guard skips only while the app is *foregrounded* — the exact opposite of when a saved position matters. Left alone, it would delete the user's scrollback and the row the anchor points at. It moves onto the non-destructive PREPEND path.

**The feed screen migrates to `PagingData` and restores the anchor.** `Flow<PagingData<FeedItemUi>>` + `collectAsLazyPagingItems`, keeping the `PostInteractionHandler` delegation, the optimistic `outgoing:` rows, the inline composer entry points and the committed screenshot baselines. One anchor row per partition holds the post URI and its offset, keyed like the cache so `clearAccount` purges it on sign-out.

**A new-posts pill.** Three overlapping avatars from the prepended batch plus an up arrow, floating over the feed; tapping scrolls to top and dismisses it. No count. New entries land above your position without moving it. When more arrived than one page holds, a seam sits above where you were reading and scrolling toward it fetches the next page in the `since` range — infinite scroll keeps working upward instead of dying. At a bounded page count the seam offers jump-to-newest instead of grinding on.

## Capabilities

### Modified Capabilities

- `core-feed-cache`: the persisted unit becomes a feed entry rather than a bare post; the `FeedItemUi` grouping projection runs at write time and is persisted; the `RemoteMediator` gains a `since`-bounded PREPEND path writing negative positions; schema v6 → v7.
- `feature-feed`: the Following screen reads `PagingData<FeedItemUi>` from the cache instead of its own network-only repository; scroll position persists across process death; new entries prepend above the read position behind a pill; pull-to-refresh becomes the explicit "start fresh" path. Thread-root de-duplication changes scope from per-session to per-partition (see **design.md** D7).
- `widget-feed-refresh`: the background worker refreshes through the non-destructive PREPEND path; the destructive refresh is reserved for a cold partition.

## Impact

- **Schema:** Room v6 → v7, committed `7.json`. The blob column's contents change shape, so the migration drops and refetches cached feed rows rather than attempting to rewrite blobs — the cache is a cache, and a cold partition is a correct state the `RemoteMediator` already handles via `LAUNCH_INITIAL_REFRESH`.
- **Modules:** `:core:database`, `:core:feed-cache`, `:core:feed-mapping` (the grouping moves in), `:core:widget-sync`, `:feature:feed:impl`. No new module, no new dependency — Paging 3 is already in the cache's surface.
- **Deleted:** `:feature:feed:impl`'s own `FeedRepository` / `DefaultFeedRepository` network path and `FeedItemDedupe` (relocated, not rewritten).
- **Battery:** strictly less work than today. Today every launch and every feed switch fetches page 1 unconditionally; after this, launch is one `since`-bounded call and a feed switch is a disk read. Seam fill runs only while the user scrolls toward it — foreground, user-driven. **No new background work exists in this change**, and the existing worker becomes cheaper, not more frequent. Nothing touches Doze.
- **Memory:** a reduction. Paging windowing replaces the unbounded in-memory `feedItems` list, so deep scrollback stops costing resident memory. The leak surface is the scroll plumbing, not the data — see **design.md** D9.
- **Disk:** `postBlob` grows by the `reason` + `reply` fields per entry. `FeedPostScaleTest` (`scale_powerUserCache_readsStayFlat_writesStayCheap_sizeBounded`) is the existing harness and must be re-run with the widened blob.
- **Deviation from baseline:** none architecturally — MVI, Compose, Hilt, Room and Coil are all unchanged in kind. The feed's `UiState` does gain a `PagingData` flow alongside its `FeedState`, which is the dual-flow pattern (`CLAUDE.md`'s flat-state rule still governs every non-paged field).

## Non-goals

- **Discover, custom feeds and lists.** `app.bsky.feed.getFeed` takes only `feed`, `limit` and `cursor` — no `since` — so a generator feed cannot bound a catch-up range and can only page backwards hoping for URI overlap. Those feeds keep today's behaviour exactly: no persisted position, no pill. Nothing regresses, and the asymmetry is invisible because only one surface changed. `getListFeed` *does* support `since` and is a natural follow-on, but is out of scope here.
- **Offline *writing*.** No queued likes, reposts or posts. Reading offline falls out of the cache; writing offline does not.
- **A user-facing setting.** No "maintain my position" toggle — the behaviour is the default and the only mode.
- **A count on the pill.** Decided against in brainstorming: the avatars convey "there is new stuff" without implying an inbox to clear.
- **A time-based staleness cut-off.** The catch-up range is bounded by pages fetched, never by wall-clock hours (see **design.md** D5).
- **Reworking the widget or Wear surfaces.** They keep reading `head(feedKey, n)` with no signature change, and the home-screen widget's rendering is unaffected — `WidgetPostItem` carries no repost field.

  One **Wear-visible fix** does fall out of the widened unit, and it is in scope rather than a non-goal: `WearPostMapping` maps `repostedBy` into its watch DTO, but the cache had no `reason` to read, so every cached repost reached the watch with no attribution and rendered as though it were an original post. The read mapper now surfaces it, deriving `displayName ?: handle` identically to `:feature:feed:impl`'s `FeedViewPostMapper` so the same post reads the same whether it came from the cache or the network. The DTO shape is unchanged — a previously-always-null field now carries a value — so no `:core:wear-contract` version bump is needed.
