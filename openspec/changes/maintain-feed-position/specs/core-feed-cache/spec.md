## MODIFIED Requirements

### Requirement: Feed posts are cached in a DID-keyed offline store

The system SHALL persist fetched feed entries in a `feed_post` table keyed by `(accountDid, feedType, feedUri, position)` with entry blobs and query columns, and paging cursors in a `feed_remote_keys` table keyed by `(accountDid, feedType, feedUri)`. `feedType` SHALL distinguish Following, Discover, custom, and list feeds. `feedUri` SHALL hold the feed-generator or list AT-URI (empty sentinel for Following). The store SHALL index `uri` and `authorDid`.

The persisted blob SHALL be the whole wire `app.bsky.feed.defs#feedViewPost` — the post **together with** its per-entry `reason` (repost attribution) and `reply` (root / parent) context, which `FeedItemUi.ReplyCluster` and `FeedItemUi.SelfThreadChain` are projected from. This table SHALL keep its present grain: every fetched entry, undeduplicated, so `head`, by-uri overlays and deep links are unaffected by the grouping projection. `position` SHALL be signed.

#### Scenario: Fetched posts are stored under the account partition
- **WHEN** a feed page is fetched for the signed-in account
- **THEN** its entries are written to `feed_post` under that account's DID, feed type, and feed URI, preserving server order via `position`

#### Scenario: Lookups by uri and author do not full-scan
- **WHEN** a post is looked up by `uri` (e.g. deep-link or interaction-state overlay) or by `authorDid`
- **THEN** the query is served by an index, not a full table scan

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

## ADDED Requirements

### Requirement: `since`-bounded prepend fetches only genuinely new entries

For a feed whose XRPC method accepts `since`, the system SHALL fetch newer entries by passing the partition's persisted boundary cursor as `since`, rather than paging backwards and matching URIs. The fetch SHALL be bounded by a configured number of fetched entries, never by elapsed wall-clock time.

Writes SHALL be additive: a prepend SHALL NOT clear the partition and SHALL NOT renumber existing rows.

#### Scenario: Only newer entries are fetched
- **WHEN** a prepend runs with the partition's boundary cursor as `since`
- **THEN** the response contains only entries newer than that position, and the anchored entry itself is not re-fetched

#### Scenario: Exhaustion is detected from the returned cursor
- **WHEN** a prepend response returns a cursor equal to the `since` value passed
- **THEN** the range is reported exhausted and no further prepend fetches occur for that boundary

#### Scenario: The budget stops an unbounded catch-up
- **WHEN** the configured entry budget is reached before the range is exhausted
- **THEN** fetching stops, the partition retains a seam above the read position, and the consumer is informed the range is incomplete

### Requirement: A prepend persists its own cursor state

The system SHALL persist three cursors per partition: the boundary `since` (the `startCursor` of the newest page ever written), the in-range fill cursor, and the `startCursor` returned by the newest fill page, which becomes the boundary once the range is exhausted.

`startCursor` is a page-level response field, not a per-entry one, so the boundary cannot be derived from the anchor row. A prepend SHALL leave the **append** cursor untouched and SHALL write back its own state.

#### Scenario: A second prepend makes progress
- **WHEN** a prepend completes and a later prepend runs against the same partition
- **THEN** the later prepend reads the updated boundary and fetches only entries newer than the first prepend's newest entry

#### Scenario: An interrupted fill resumes rather than restarting
- **WHEN** a multi-page fill is interrupted by process death and the feed is reopened
- **THEN** the next fill resumes from the persisted in-range fill cursor

#### Scenario: The append cursor is unaffected
- **WHEN** a prepend writes its cursor state
- **THEN** the partition's append cursor is unchanged and forward pagination continues from where it was

### Requirement: Prepends allocate inside a reserved negative window

Fill pages SHALL march downward from the top of a reserved negative window toward the anchor. Allocation SHALL be relative to the partition's current minimum position, never a fixed `-1`: `position` is part of the primary key and writes go through `@Upsert`, so a restart at `-1` would overwrite the previous batch rather than insert below it.

Unused positions within the window SHALL be left empty; gaps are expected.

#### Scenario: Fill pages allocate downward inside the window
- **WHEN** a multi-page catch-up writes its second fill page, which is older than the first but newer than the anchor
- **THEN** it occupies the block immediately below the first page's block, nothing already written is renumbered, and no existing row is overwritten

#### Scenario: Allocation reads the current minimum
- **WHEN** a prepend runs against a partition that already holds prepended rows
- **THEN** the new rows are allocated relative to the partition's current minimum position and cannot collide with the earlier batch

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

The system SHALL compute the `FeedItemUi` grouping projection — reply-cluster and self-thread-chain detection, thread-root de-duplication, and cluster-context de-duplication — on the **write** path, over the whole fetched page, and SHALL persist the result. The read path SHALL NOT re-derive the grouping.

De-duplication SHALL apply within the page being written. An entry written by a prepend SHALL be exempt from thread roots already present below the seam.

#### Scenario: Grouping is computed with the whole page in hand
- **WHEN** a fetched page is written through to the cache
- **THEN** the two-pass projection runs over that entire page, in the specified non-commutative order, before any row is read by a consumer

#### Scenario: A chain is not split at a page boundary
- **WHEN** a self-thread chain spans the cut between two fetched pages
- **THEN** the persisted projection preserves the chain across that cut

#### Scenario: A newer reply into a seen thread surfaces on prepend
- **WHEN** a prepend brings a reply whose thread root already appears in an entry below the seam
- **THEN** the prepended entry is retained, because it is a new event above the reader's position

### Requirement: The projection is persisted at its own grain

The projection SHALL be stored as one row per rendered `FeedItemUi`, positioned in display order, holding the group's structural layout — variant, ordered member URIs, suppressed-reply count — and references to its posts. It SHALL NOT be expressed as grouping tags over `feed_post` rows.

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

### Requirement: Database migration v6 → v7

The system SHALL migrate `NubecitaDatabase` from version 6 to version 7 by widening the persisted feed-entry shape, adding the projected-item table, the prepend cursor columns and the scroll-anchor storage, and committing the exported `7.json` schema.

Because the blob's serialized shape changes and the per-entry `reason` / `reply` data was never persisted and cannot be recovered locally, the migration SHALL drop cached feed rows rather than rewrite blobs. Non-feed data SHALL be preserved.

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

### Requirement: A seam is a persisted marker row

When a catch-up leaves part of the range unfetched, the system SHALL write an explicit seam marker row into the projection table at the boundary position, carrying whether the range is exhausted. The seam SHALL NOT be inferred from a gap in the position sequence, which a keyset `PagingSource` skips.

The marker SHALL be a row-type discriminator on the projection table, never a new `FeedItemUi` variant — that sealed type is shared with features that have no notion of a seam.

#### Scenario: An incomplete catch-up writes a marker
- **WHEN** a prepend stops with the range unexhausted
- **THEN** a seam marker row exists at the boundary position between the prepended entries and the previously-read content

#### Scenario: The seam is paged like any other row
- **WHEN** the reader scrolls to the boundary
- **THEN** the seam row is emitted by the `PagingSource` in position order, with a stable key, rather than being inferred from a position gap

#### Scenario: Exhausting the range removes the marker
- **WHEN** a fill reports the range exhausted
- **THEN** the seam marker row is deleted and the timeline is continuous

#### Scenario: A half-filled seam survives process death
- **WHEN** a multi-page fill is interrupted and the feed is reopened
- **THEN** the seam marker is still present with its fill state, and scrolling to it resumes the fill
