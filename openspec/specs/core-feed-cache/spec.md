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

The system SHALL persist fetched feed posts in a `feed_post` table keyed by `(accountDid, feedType, feedUri, position)` (the post's wire form serialized into a `post_blob` column alongside denormalized query columns), and paging cursors in a `feed_remote_keys` table keyed by `(accountDid, feedType, feedUri)`. `feedType` SHALL distinguish Following, Discover, custom, and list feeds; `feedUri` SHALL hold the feed-generator / list AT-URI for those (and an empty sentinel for Following). The store SHALL index `uri` and `authorDid`.

#### Scenario: Fetched posts are stored under the account partition
- **WHEN** a feed page is fetched for the signed-in account
- **THEN** its posts are written to `feed_post` under that account's DID, feed type, and feed URI, preserving server order via `position`

#### Scenario: Lookups by uri and author do not full-scan
- **WHEN** a post is looked up by `uri` (e.g. deep-link or interaction-state overlay) or by `authorDid`
- **THEN** the query is served by an index, not a full table scan

### Requirement: Paged feed loading via `RemoteMediator`

The system SHALL expose a Paging 3 `Pager`/`Flow<PagingData<PostUi>>` for a given `(accountDid, feedType, feedUri)` backed by a `RemoteMediator` that fetches from the AT Protocol and write-throughs to the cache.

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
- **WHEN** `LoadType.PREPEND` is requested
- **THEN** the mediator immediately returns success with `endOfPaginationReached = true`

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

The system SHALL re-map cached posts to `PostUi` using the current signed-in viewer DID and moderation preferences at read time, and SHALL drop moderation-filtered (resolved HIDE) posts. A change to moderation preferences or the active account SHALL be reflected on the next load without rebuilding the pager.

#### Scenario: Moderation-filtered post is dropped
- **WHEN** a cached post resolves to HIDE under the current moderation preferences
- **THEN** it is omitted from both the paged stream and the head query

#### Scenario: Preference change reflects on the next load
- **WHEN** moderation preferences (or the active account) change
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
