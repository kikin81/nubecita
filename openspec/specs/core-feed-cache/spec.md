# core-feed-cache Specification

## Purpose
`:core:feed-cache` is the offline-first source of truth for feeds, shared by the app feed and the home-screen widgets. It fetches feeds from the AT Protocol, caches them in a **DID-keyed** Room store (denormalized per-position **wire-post snapshots**), exposes a Paging 3 stream (`RemoteMediator`) for the app feed and a non-Paging **head query** for widgets, and re-maps cached posts to `:data:models` `PostUi` with **fresh moderation on read**. Single-account MVP, but the schema is DID-keyed so multi-account needs no migration. Implemented in `nubecita-lgoo.1` (sub-project A of the Glance feed widgets epic); storage validated at power-user scale (design D-A2). Saved-feeds metadata for the Pro picker is **not** owned here — it reuses `:core:feeds:PinnedFeedsRepository`.

## Requirements

### Requirement: `:core:feed-cache` is the single offline-first feed data source

The system SHALL provide a `:core:feed-cache` module that owns feed fetching, wire→`PostUi` mapping, and the offline cache, exposed through a repository interface. Room entities SHALL NOT cross the module boundary — feed data SHALL be returned as `:data:models` `PostUi`. Reads SHALL return `Flow`/`PagingData`; writes SHALL be `suspend`.

#### Scenario: Feed data crosses the boundary as a UI model
- **WHEN** a caller obtains feed data from `:core:feed-cache`
- **THEN** it receives `:data:models` `PostUi` values, never `FeedPostEntity`/`FeedRemoteKeyEntity`

#### Scenario: Both app and widget can depend on the source
- **WHEN** the app feed and a home-screen widget need feed data
- **THEN** both depend on `:core:feed-cache` (a non-UI `:core` module), not on `feature/feed/impl`

### Requirement: Feed posts are cached in a DID-keyed offline store

The system SHALL persist fetched feed entries in a `feed_post` table keyed by `(accountDid, feedType, feedUri, position)`, and paging cursors in a `feed_remote_keys` table keyed by `(accountDid, feedType, feedUri)`. `feedType` SHALL distinguish Following, Discover, custom, and list feeds. `feedUri` SHALL hold the feed-generator or list AT-URI (empty sentinel for Following). `feed_post` SHALL index `uri`; the by-author index belongs to the post store, where the author is.

#### Scenario: Fetched posts are stored under the account partition
- **WHEN** a feed page is fetched for the signed-in account
- **THEN** its entries are written to `feed_post` under that account's DID, feed type, and feed URI, preserving server order via `position`

#### Scenario: Lookups by uri and author do not full-scan
- **WHEN** an entry is looked up by `uri` (e.g. deep-link or interaction-state overlay), or a post by `authorDid`
- **THEN** the query is served by an index, not a full table scan — `uri` on the entry table, `authorDid` on the post store

### Requirement: A `feed_post` row is an entry envelope, not post content

A `feed_post` row SHALL be an entry **envelope**: a position, a reference to the post it shows, and the per-entry `reason` and `reply` context that belongs to the timeline rather than to the post — the entire input to the `ReplyCluster` and `SelfThreadChain` projection. It SHALL carry no post content.

The table SHALL keep its present grain — every fetched entry, undeduplicated — so `head`, by-uri overlays and deep links are unaffected by the grouping projection. `position` SHALL be signed.

#### Scenario: Repost attribution survives a cache round-trip
- **WHEN** a timeline entry carrying a `reason` of `ReasonRepost` is written and read back
- **THEN** the projected item retains the repost attribution

#### Scenario: Reply context survives a cache round-trip
- **WHEN** a timeline entry carrying `reply.root` and `reply.parent` is written and read back
- **THEN** the projection can produce a `ReplyCluster` for that entry

### Requirement: Paged feed loading via `RemoteMediator`

The system SHALL expose a Paging 3 `Pager`/`Flow<PagingData<FeedItemUi>>` for a given `(accountDid, feedType, feedUri)` backed by a `RemoteMediator` that fetches from the AT Protocol and write-throughs to the cache.

`LoadType.PREPEND` SHALL be supported for a feed whose XRPC method accepts `since`, and SHALL remain a no-op for one that does not.

#### Scenario: Initial load (REFRESH)
- **WHEN** the feed is loaded with `LoadType.REFRESH`
- **THEN** the mediator fetches the first page from a null cursor and stores it

#### Scenario: Paging forward (APPEND)
- **WHEN** the consumer reaches the end of the loaded data with `LoadType.APPEND`
- **THEN** the mediator fetches the next page using the stored cursor and appends it

#### Scenario: End of pagination
- **WHEN** a load returns an empty page or the last page
- **THEN** the mediator reports `endOfPaginationReached = true` and no further append fetches occur

#### Scenario: No prepend on a reverse-chronological feed
- **WHEN** `LoadType.PREPEND` is requested for a generator feed, whose `app.bsky.feed.getFeed` method accepts no `since` and so cannot bound a catch-up range
- **THEN** the mediator immediately returns success with `endOfPaginationReached = true`

#### Scenario: Prepend is supported on a `since`-capable feed
- **WHEN** `LoadType.PREPEND` is requested for a feed whose XRPC method accepts `since` (Following, lists)
- **THEN** the mediator fetches newer entries within the bounded range and writes them at negative positions

### Requirement: Cached feeds are readable offline

The system SHALL serve a previously-cached feed from the local store when the network is unavailable, without blocking on a network call.

#### Scenario: Open a cached feed offline
- **WHEN** the device is offline and a feed that was cached earlier is opened
- **THEN** the cached posts render from `feed_post` with no network request

#### Scenario: Append failure preserves the cache
- **WHEN** an append network fetch fails
- **THEN** the previously cached pages remain readable and the error is surfaced without clearing the cache

#### Scenario: Switching feeds does not clear other partitions
- **WHEN** the user switches from one feed to another and back
- **THEN** each feed's `(accountDid, feedType, feedUri)` partition is read from cache and no other feed's rows are cleared — a `REFRESH`-clear only ever scopes to a single partition

#### Scenario: Opening a fresh-cached feed renders from cache without auto-refresh
- **WHEN** a feed with cached data is opened or switched to
- **THEN** its cached partition renders immediately (`initialize()` returns `SKIP_INITIAL_REFRESH`) and no network refresh runs until the user pulls to refresh or a background refresh runs

### Requirement: Read mapping reflects current viewer and moderation prefs

The system SHALL re-map cached posts to `PostUi` using the current signed-in viewer DID and moderation preferences at read time, and SHALL drop moderation-filtered (resolved HIDE) posts. A change to moderation preferences SHALL be reflected on the next load without rebuilding the pager. (The active account is part of the `FeedKey`/partition, so an account change is handled by a new pager against the new partition — not by dynamic re-mapping.)

#### Scenario: Moderation-filtered post is dropped
- **WHEN** a cached post resolves to HIDE under the current moderation preferences
- **THEN** it is omitted from both the paged stream and the head query

#### Scenario: Preference change reflects on the next load
- **WHEN** moderation preferences change
- **THEN** subsequently loaded items reflect the new state without recreating the pager

### Requirement: Widget head query

The system SHALL expose a non-Paging query returning the newest `n` cached posts for a `(accountDid, feedType, feedUri)` as a `Flow<List<PostUi>>` ordered by `position`, re-emitting when the cache, the viewer, or moderation preferences change.

#### Scenario: Read the head of a feed
- **WHEN** a caller requests the head of a feed with a limit `n`
- **THEN** it receives up to `n` posts ordered by `position`, sourced from the cache without using the Paging stack

### Requirement: Eviction does not disrupt an active scroll

The system SHALL bound cache growth without invalidating an actively-scrolling `PagingSource`. A `REFRESH` SHALL clear and reinsert the feed's partition in a single transaction. A count-cap trim (keeping the newest ~N per partition) SHALL be exposed for off-scroll invocation and SHALL NOT run during `APPEND`. Logout or account removal SHALL delete all rows for that account's DID.

#### Scenario: REFRESH clears then reinserts atomically
- **WHEN** a `REFRESH` load completes
- **THEN** the partition's `feed_post` rows and remote key are cleared and page 1 is reinserted within one transaction

#### Scenario: APPEND never triggers count-cap deletion
- **WHEN** an `APPEND` load stores a page
- **THEN** no count-cap deletion runs during that append (the live `PagingSource` is not invalidated mid-scroll)

#### Scenario: Off-scroll trim bounds growth
- **WHEN** the count-cap trim is invoked off-scroll
- **THEN** `feed_post` rows beyond the newest ~N per `(accountDid, feedType, feedUri)` are deleted, leaving the partition's cursor row intact (it is removed only when the whole partition is cleared)

#### Scenario: Account teardown clears its partitions
- **WHEN** the user logs out or an account is removed
- **THEN** all `feed_post` and `feed_remote_keys` rows for that `accountDid` are deleted

### Requirement: Database migration v4 → v5

The system SHALL migrate `NubecitaDatabase` from version 4 to version 5 by adding the `feed_post` and `feed_remote_keys` tables, preserving existing data, and committing the exported `5.json` schema.

#### Scenario: Upgrade preserves existing data
- **WHEN** the database upgrades from v4 to v5
- **THEN** `feed_post` and `feed_remote_keys` are created and existing v4 data (recent searches, cached actors) is preserved

### Requirement: `since`-bounded prepend fetches only genuinely new entries

For a feed whose XRPC method accepts `since`, the system SHALL fetch newer entries by passing the partition's persisted boundary cursor as `since`, not by paging backwards and matching URIs. The fetch SHALL be bounded by a number of fetched entries, never by elapsed time. Writes SHALL be additive: a prepend SHALL NOT clear the partition or renumber rows. Reaching the budget SHALL complete the catch-up, never pause it: a full window SHALL NOT stop later catch-ups fetching newer entries.

#### Scenario: Only newer entries are fetched
- **WHEN** a prepend runs with the partition's boundary cursor as `since`
- **THEN** the response contains only entries newer than that position, and the anchored entry itself is not re-fetched

#### Scenario: Exhaustion is detected from the returned cursor
- **WHEN** a prepend response returns a cursor equal to the `since` value passed
- **THEN** the range is reported exhausted and no further prepend fetches occur for that boundary

#### Scenario: The budget stops an unbounded catch-up
- **WHEN** the configured entry budget is reached before the range is exhausted
- **THEN** fetching stops, a passive gap marker is left between the fill and the previously-read content, and the catch-up reports itself complete

#### Scenario: A full window does not stall later catch-ups
- **WHEN** a catch-up reaches the budget with the range unexhausted, and a later catch-up runs on the same partition
- **THEN** the later catch-up fetches from the newest entry onward, bounded by the newest entry the earlier fill wrote, and writes its pages in a new window above everything cached

#### Scenario: A partition already stalled recovers without a refresh
- **WHEN** a catch-up runs on a partition whose persisted fill state points into a window with no free slot
- **THEN** the catch-up completes that fill with a gap, and the next catch-up fetches new entries, with no destructive refresh and the reader's anchor unchanged

### Requirement: A prepend persists its own cursor state

The system SHALL persist, per partition, the boundary `since`, the in-range fill cursor, the next fill position, and the pending boundary (the newest fill page's `startCursor`, a page-level field no row carries). The fill cursor and next fill position SHALL be set and cleared together. A fill completes when the range is exhausted or the budget is reached; the pending boundary SHALL then become the boundary and the fill state SHALL be cleared. A prepend SHALL NOT touch the append cursor.

#### Scenario: A second prepend makes progress
- **WHEN** a prepend completes and a later prepend runs against the same partition
- **THEN** the later prepend reads the updated boundary and fetches only entries newer than the first prepend's newest entry

#### Scenario: An interrupted fill resumes rather than restarting
- **WHEN** a multi-page fill is interrupted by process death and the feed is reopened
- **THEN** the next fill resumes from the persisted in-range fill cursor, writing directly below the last page that landed

#### Scenario: Reaching the budget advances the boundary
- **WHEN** a fill reaches the budget with the range unexhausted
- **THEN** the boundary becomes the `startCursor` of the newest page that fill wrote, and the fill cursor, next fill position and pending boundary are cleared

#### Scenario: A completed fill with no pending boundary does not stall
- **WHEN** a fill reaches the budget and no pending boundary is recorded
- **THEN** the fill state is cleared, the boundary is left unchanged, a non-fatal is recorded, and re-fetched entries already shown are not rendered twice

#### Scenario: The append cursor is unaffected
- **WHEN** a prepend writes its cursor state
- **THEN** the partition's append cursor is unchanged and forward pagination continues from where it was

### Requirement: Prepends allocate inside a reserved negative window

Fill pages SHALL march downward through a reserved negative window allocated relative to the partition's current minimum position, never a fixed `-1`: writes upsert on `position`, so a restart there would overwrite the previous batch. Each window SHALL reserve its bottom slot for a gap marker: entries SHALL stop one slot before the first position held by a stored entry or a projected item. Unused positions SHALL be left empty; gaps are expected.

#### Scenario: Fill pages allocate downward inside the window
- **WHEN** a multi-page catch-up writes its second fill page, which is older than the first but newer than the anchor
- **THEN** it occupies the block immediately below the first page's block, nothing already written is renumbered, and no existing row is overwritten

#### Scenario: Allocation reads the current minimum
- **WHEN** a prepend runs against a partition that already holds prepended rows
- **THEN** the new rows are allocated relative to the partition's current minimum position and cannot collide with the earlier batch

#### Scenario: The bottom slot stays free for the marker
- **WHEN** a fill writes pages until its window is full
- **THEN** the slot directly above the first occupied position below the window holds no entry and no item

### Requirement: Eviction counts rows and preserves the reader's anchor

`trimToCap` SHALL retain the newest `cap` **rows** by ordinal, not the newest `cap` position values, and SHALL retain everything from the anchor upward when that is larger, under a hard ceiling bounding worst-case growth. The catch-up budget SHALL be denominated against the retention cap so a full catch-up cannot fill the partition by itself.

A storage guard SHALL NOT delete the anchor it exists to protect.

#### Scenario: Eviction counts rows, not position values
- **WHEN** `trimToCap` runs on a partition whose positions contain gaps
- **THEN** exactly the newest `cap` rows are retained — position arithmetic over a gapped range would retain far fewer entries than the cap

#### Scenario: Eviction never deletes the reader's unread backlog
- **WHEN** a catch-up has written more entries above the anchor than the retention cap would keep
- **THEN** the anchor and the entries above it survive, bounded by the hard ceiling

### Requirement: The `FeedItemUi` projection is computed at write time

The system SHALL compute the `FeedItemUi` grouping projection — cluster and chain detection, thread-root and cluster-context de-duplication, and a final one-item-per-post pass — on the **write** path, over the whole fetched page, and SHALL persist the result. The read path SHALL NOT re-derive it.

De-duplication SHALL apply within the page being written. An entry written by a prepend SHALL be exempt from thread roots already present below the seam.

#### Scenario: Grouping is computed with the whole page in hand
- **WHEN** a fetched page is written through to the cache
- **THEN** the projection's passes run over that entire page, in the specified non-commutative order, before any row is read by a consumer

#### Scenario: A chain is not split at a page boundary
- **WHEN** a self-thread chain spans the cut between two fetched pages
- **THEN** the persisted projection preserves the chain across that cut

#### Scenario: A newer reply into a seen thread surfaces on prepend
- **WHEN** a prepend brings a reply whose thread root already appears in an entry below the seam
- **THEN** the prepended entry is retained, because it is a new event above the reader's position

### Requirement: One item per timeline post, across pagination

The final projection pass SHALL retain at most one item per post occupying a timeline slot, and SHALL be seeded from the partition in **every** load direction, prepends included.

The prepend exemption on thread roots concerns a thread resurfacing with a newer reply, not the same post being drawn twice. The seed SHALL be the partition's rendered leaf posts, not every rendered post.

#### Scenario: A catch-up's overlap with the cached head is not rendered twice
- **WHEN** a `since`-bounded prepend returns entries already rendered in the partition
- **THEN** those entries are dropped from the written projection, while a newer reply into a thread below the seam is still retained

#### Scenario: Two reposts of one post in a single page collapse
- **WHEN** a page carries the same post twice because two accounts the viewer follows both reposted it
- **THEN** one item is written, even though the thread-root pass exempts reposts from its drop

#### Scenario: A repost of a context post is left to the cluster-context pass
- **WHEN** a page carries a reposted `Single` for a post that appears above only as another item's context
- **THEN** this pass does not drop it, because the seed holds rendered leaves rather than every rendered post

### Requirement: The projection is persisted at its own grain

The projection SHALL be stored as one row per rendered `FeedItemUi`, positioned in display order, holding the group's structural layout — variant, suppressed-reply count, stable list key — with its member posts recorded as **rows** carrying a render ordinal, never a delimited column. It SHALL NOT be expressed as grouping tags over `feed_post` rows.

#### Scenario: Cluster context with no partition position is retained
- **WHEN** a `ReplyCluster` is projected whose root and parent are not themselves timeline entries
- **THEN** those context posts are retrievable for that projected item and survive a read, despite holding no position in the partition

#### Scenario: A chain is one paged item
- **WHEN** a `SelfThreadChain` of N posts is read back
- **THEN** it is emitted as a single paged item, not N

#### Scenario: `head` is unaffected by the projection
- **WHEN** `head(feedKey, n)` is called
- **THEN** it reads the raw undeduplicated `feed_post` rows and its output is byte-identical to the behaviour before this change

### Requirement: The feed pages over a position-keyed `PagingSource`

The system SHALL page the projection via a `PagingSource` keyed on `position`, not Room's generated offset-based source, and SHALL express anchor restore as the `Pager`'s initial key.

Room's `InvalidationTracker` is table-level and invalidates on any write; an offset-based refresh key then resolves to a different entry than before, which is the viewport jump this change forbids.

#### Scenario: A refresh after an additive prepend lands on the same entry
- **WHEN** N entries are prepended and the `PagingSource` is invalidated by that write
- **THEN** the reload resolves to the same entry the reader was viewing, not one N items away

#### Scenario: Restore is expressed as the initial key
- **WHEN** the feed is opened with a resolvable anchor
- **THEN** the `Pager` is constructed with that entry's position as its initial key, and no post-hoc scroll correction is performed

### Requirement: Scroll anchor is persisted per feed partition

The system SHALL persist, per `(accountDid, feedType, feedUri)` partition, the URI of the entry the reader was last viewing, that entry's offset within its row, and a pill-dismissal marker. Anchors SHALL be stored alongside the cached entries so that clearing an account's partitions removes them in the same operation.

Resolution SHALL be by entry identity, never by list index.

#### Scenario: Anchor resolves to the same entry across process death
- **WHEN** the process is killed and the feed is reopened with a warm partition
- **THEN** the feed renders with the anchored entry at the reader's previous offset

#### Scenario: A missing anchor resolves downward
- **WHEN** the anchored entry is absent from the partition (deleted, moderated out, or evicted)
- **THEN** restore resolves to the nearest surviving entry **below** the anchor, and to the top of the partition only if none survives

#### Scenario: Sign-out purges anchors with the posts
- **WHEN** the user logs out or an account is removed
- **THEN** that account's anchor rows are deleted in the same operation that clears its `feed_post` and `feed_remote_keys` rows

### Requirement: Database migrations v7, v8 and v9

The system SHALL reach the persisted feed shape over three schema bumps, each committing its exported `{N}.json`. v7 → v8 and v8 → v9 SHALL be expressible as `@AutoMigration`.

Because the v7 blob shape changes and the per-entry `reason` / `reply` data was never persisted and cannot be recovered locally, v6 → v7 SHALL drop cached feed rows rather than rewrite blobs. Non-feed data SHALL be preserved.

#### Scenario: v7 widens the entry and adds the projection tables
- **WHEN** the database upgrades from v6 to v7
- **THEN** `feed_post` carries the whole entry, `cached_post`, `feed_item`, `feed_item_member` and `feed_partition_state` exist, and `feed_remote_keys` carries the prepend cursor columns

#### Scenario: v8 adds the reader's scroll anchor
- **WHEN** the database upgrades from v7 to v8
- **THEN** `feed_partition_state` gains the nullable anchor columns, and a partition with no anchor reads exactly like one the reader has never scrolled

#### Scenario: v9 adds the pill's dismissal waterline
- **WHEN** the database upgrades from v8 to v9
- **THEN** `feed_partition_state` gains the nullable dismissal position, and a partition with none falls back to the anchor

#### Scenario: Upgrade drops cached feed rows and preserves everything else
- **WHEN** the database upgrades from v6 to v7
- **THEN** `feed_post` and `feed_remote_keys` rows are dropped, the new tables and columns are created, and other v6 data (recent searches, cached actors) is preserved

#### Scenario: A cold partition after upgrade is a correct state
- **WHEN** a feed is opened for the first time after the v7 upgrade
- **THEN** `initialize()` reports `LAUNCH_INITIAL_REFRESH` and the partition refills from the network

### Requirement: Posts are stored once per account and referenced by URI

The system SHALL store each post once per account, keyed by URI, and SHALL have both the partition rows and the projection rows reference it rather than carry their own serialized copy. Read SHALL hydrate by URI.

Eviction SHALL be reference-aware: a post SHALL survive until nothing live references it, evaluated in the same trim pass as the count cap.

#### Scenario: One copy of a post serves every reference
- **WHEN** a post appears both as a standalone item and as another item's cluster context
- **THEN** both references resolve to the same stored post, and a change to it is visible from both

#### Scenario: A context post with no partition row is still stored
- **WHEN** a projected `ReplyCluster` references a root that never arrived as a timeline entry
- **THEN** that post is stored in the by-URI store and hydrates on read

#### Scenario: Eviction keeps a post that is still referenced
- **WHEN** the trim removes a partition row whose post is still referenced by a live projection row
- **THEN** the post itself survives, and is removed only once nothing references it

#### Scenario: A cluster's context post has no entry row and still survives
- **WHEN** eviction runs while a projected cluster references a root that never arrived as a timeline entry
- **THEN** that post survives, because the reference check consults membership as indexed rows rather than only the entry table — against a delimited column the question has no indexed answer, and the check would degrade into "delete anything with no entry row"

### Requirement: A seam is a persisted marker row

When a catch-up leaves part of the range unfetched, the system SHALL write an explicit marker row into the projection table, as a row-type discriminator and never a new `FeedItemUi` variant, and SHALL NOT infer the marker from a gap in positions, which a keyset `PagingSource` skips. A marker SHALL only be written at a position holding no projected item and SHALL NOT replace one; when no such position is available it SHALL be skipped and a non-fatal recorded.

#### Scenario: An incomplete catch-up writes a marker
- **WHEN** a prepend stops with the range unexhausted
- **THEN** an active seam marker row exists at the position the next fill page will be written at, between the prepended entries and the previously-read content

#### Scenario: The seam is paged like any other row
- **WHEN** the reader scrolls to the boundary
- **THEN** the seam row is emitted by the `PagingSource` in position order, with a stable key, rather than being inferred from a position gap

#### Scenario: A marker never replaces an item
- **WHEN** any marker is written
- **THEN** no projected item is removed or replaced and no member row is left under the marker

### Requirement: Seam markers are one active seam and any number of passive gap markers

An active seam marks a fill in progress: at most one exists per partition, at the position the next fill page will be written at, replaced by each fill page and removed when the range is exhausted. A passive gap marker marks where a completed fill stopped short: any number may exist, each persisting at its position until a destructive refresh or eviction removes it. Writing, moving or removing the active seam SHALL NOT remove any passive gap marker.

#### Scenario: Exhausting the range removes the marker
- **WHEN** a fill reports the range exhausted while the partition also holds passive gap markers from earlier fills
- **THEN** the active seam row is deleted and the timeline is continuous across that fill, and every passive gap marker from earlier fills is still present

#### Scenario: A half-filled seam survives process death
- **WHEN** a multi-page fill is interrupted and the feed is reopened
- **THEN** the active seam marker is still present with its fill state, and scrolling to it resumes the fill

#### Scenario: A second overflow keeps the first gap marked
- **WHEN** a fill reaches the budget, and a later fill also reaches the budget before the reader refreshes
- **THEN** both discontinuities carry a passive gap marker, each at its own position

#### Scenario: A passive marker keeps its key
- **WHEN** later fills are written above a passive gap marker
- **THEN** the marker's position and key are unchanged

### Requirement: The partition records the preferences its projection was built with

The system SHALL accept the viewer's moderation and feed-view preferences as a **parameter** to the projection, and SHALL persist a fingerprint of the preferences each partition's projection was built with. The cache SHALL NOT read preferences itself.

#### Scenario: A preference change re-projects the partition
- **WHEN** the feed is opened and the live preference fingerprint differs from the partition's
- **THEN** the stored entries are re-projected locally and the `PagingSource` is invalidated, with no network fetch

#### Scenario: Re-projection keeps the reader's place
- **WHEN** a re-projection runs while an anchor is set and the anchored entry survives the new preferences
- **THEN** the reader's position is retained

#### Scenario: A filtered-out anchor resolves downward
- **WHEN** a re-projection removes the anchored entry because the new preferences filter it
- **THEN** restore resolves to the nearest surviving entry below it

#### Scenario: A background write uses the persisted preferences
- **WHEN** the background worker writes to a partition
- **THEN** it projects using that partition's persisted fingerprint, not live preferences, so the partition's meaning does not change underneath the reader

#### Scenario: `head` is unaffected by preferences
- **WHEN** `head(feedKey, n)` is called on a partition whose projection is reply-filtered
- **THEN** it returns the raw unfiltered entries, so the widget surface is unchanged

### Requirement: A page that projects to zero items tops up rather than ending pagination

When a fetched page projects to zero items and a next cursor exists, the mediator SHALL fetch again, bounded by a fixed round limit, rather than reporting end of pagination.

This SHALL apply to prepend fills as well as appends. Range exhaustion has its own signal — the returned cursor equalling the `since` value — and SHALL NOT be inferred from an empty projection.

#### Scenario: An aggressively filtered append page tops up
- **WHEN** an appended page projects to zero items and a next cursor exists
- **THEN** the mediator fetches the following page, up to the round limit, before yielding a result

#### Scenario: Topping up stops at the round limit
- **WHEN** consecutive pages all project to zero items
- **THEN** the mediator stops after the round limit and does not loop indefinitely

#### Scenario: An empty fill page is not mistaken for exhaustion
- **WHEN** a prepend fill page projects to zero items while the returned cursor still differs from the `since` value
- **THEN** the range is not reported exhausted and the fill continues within its budget

### Requirement: Re-projection preserves seam markers

A local re-projection SHALL keep every seam marker row at its position. It SHALL delete and rewrite projected items and their members only, and SHALL project the stored entries in segments split at the marker positions, writing each segment's items within that segment so that no item is written at or beyond the marker below it.

De-duplication and chain merging during a re-projection SHALL NOT reach across a marker.

#### Scenario: A preference change keeps the gap marked
- **WHEN** the partition holds a passive gap marker and a preference change re-projects it
- **THEN** the marker is still present at its position, between the same entries as before

#### Scenario: A preference change keeps an active fill resumable
- **WHEN** a fill is in progress and a preference change re-projects the partition
- **THEN** the active seam is still present and the next fill page is written below the last page that landed, overwriting no item

#### Scenario: No item lands on a marker
- **WHEN** a re-projection writes the items of a segment
- **THEN** every item's position is above the marker that ends the segment

#### Scenario: Grouping stops at a marker
- **WHEN** the same thread root appears above and below a marker
- **THEN** each segment keeps its own item for it, as the prepend that wrote it did

### Requirement: A capped catch-up is reported distinctly

A catch-up that completes because it reaches the budget SHALL report a distinct outcome from one that exhausts its range, and SHALL log an analytics event carrying the feed type and the number of entries fetched. Callers that only need to know whether to keep paging SHALL treat both outcomes alike.

#### Scenario: A capped fill is distinguishable
- **WHEN** a catch-up completes because it reaches the budget
- **THEN** it reports the capped outcome, and an exhausted-range completion reports the plain caught-up outcome

#### Scenario: The cap is measured once per fill
- **WHEN** a fill reaches the budget
- **THEN** exactly one `feed_catch_up_exhausted` event is logged with the feed type and the entries fetched, and none is logged for a fill that exhausts its range

#### Scenario: Paging and the worker are unaffected by the distinction
- **WHEN** Paging's prepend or the background worker receives the capped outcome
- **THEN** each behaves as for the plain caught-up outcome: end of prepend pagination, no retry
