## Context

Feed fetching lives in `feature/feed/impl` (`DefaultFeedRepository`: cursor-based `getTimeline`/`getFeed`/`getListFeed`, manual `FeedLoadStatus` pagination, no cache, no Paging 3). `NubecitaDatabase` is at schema v4 (`RecentSearchEntity`, `ActorEntity`). This change is sub-project **A** (`nubecita-lgoo.1`) of the Glance feed widgets epic; it builds the offline-first foundation both the app feed and the home-screen widgets will read. Epic-level decisions and rationale: `docs/superpowers/specs/2026-06-08-glance-feed-widgets-design.md` (D1–D11). This doc covers the A-specific HOW.

## Goals / Non-Goals

**Goals**
- A `:core:feed` module owning feed fetch, mapping, and an offline cache — the single source of truth, depended on by both the app and (later) the widget worker.
- DID-keyed, denormalized Room cache (`feed_post` + `feed_remote_keys`), schema v5.
- A Paging 3 `Pager` + `RemoteMediator` (for the app feed, consumed by E) and a head DAO query (for the widget, used by C).
- Eviction that never invalidates an actively-scrolling `PagingSource`.

**Non-Goals** (separate sub-projects/epics)
- Widget refresh worker (B), the Glance widgets + config activity (C), Pro gating (D).
- Migrating the app feed screen to `PagingData`/dual-flow MVI (E) — A only *provides* the `Pager`; `feature/feed`'s state model is untouched here.
- Multi-account (schema is DID-keyed now; the rest is a future epic) and profile/thread offline caches (sibling future capabilities).

## Decisions

### D-A1. New `:core:feed` module (not `:core:posts`, not in-feature)
A widget cannot depend on a feature `:impl`, and `:core:posts` is single-post/thread. A dedicated `:core:feed` (`nubecita.android.library` + `nubecita.android.hilt`) is the clean shared home. Depends on `:core:database`, `:core:auth` (session DID + `XrpcClientProvider`), `:core:feed-mapping` (reuse existing wire→`PostUi` mappers), `:data:models`, `atproto`. *Alternative — grow `:core:posts`:* conflates single-post and feed-list concerns; rejected.

### D-A2. Denormalized snapshot rows (epic D4)
`feed_post` stores a per-position snapshot: queryable columns `uri`, `cid`, `authorDid`, `indexedAt`, `position`, `text`, plus the rest of the rendered post (`embed`, counts, viewer state) as a **serialized blob via a `TypeConverter`**. No joins on the read path, trivial eviction. *Alternative — normalized post/author/membership:* hot-path joins + GC-based eviction; rejected for a snapshot feed (see epic D4 trade-off analysis).

### D-A3. DID-keyed, partitioned schema (epic D5)
`feed_post` PK `(accountDid, feedType, feedUri, position)`. `feedType` is an enum-ish discriminator (`FOLLOWING`, `DISCOVER`, `CUSTOM`, `LIST`); `feedUri` holds the generator AT-URI for `DISCOVER`/`CUSTOM`, the list AT-URI for `LIST`, and a sentinel/empty for `FOLLOWING`. **`LIST` is included now** (per #502 review): `getListFeed` is already a `DefaultFeedRepository` capability, so defining `LIST` in v5 is zero-cost and avoids a later v5→v6 migration + a split interim where list feeds can't use the cache. `@Index` on `uri` and `authorDid` (epic D11) for by-uri (deep-link / `:core:post-interactions` overlay) and by-author lookups that the composite PK doesn't serve.

### D-A4. Paging 3 + `RemoteMediator`; one writer per active feed
Introduce `androidx.paging` (paging-runtime; paging-compose deferred to E). `FeedRemoteMediator<Int, FeedPostEntity>` is the **only** writer of an app-active feed partition, driven by `load(LoadType, PagingState)`: `REFRESH` → null cursor, `APPEND` → stored cursor from `feed_remote_keys`, `PREPEND` → `Success(endOfPaginationReached = true)` (reverse-chron). `:core:feed` exposes a `Pager`/`Flow<PagingData<PostUi>>` (for E) and a separate **head query** `Flow<List<PostUi>>` (`ORDER BY position LIMIT n`) for the widget (C) — the widget never touches Paging.

### D-A5. Eviction off the active-scroll path (epic D6)
Any Room write to the queried table invalidates the live `PagingSource` (verified against the official RemoteMediator guide), so:
- **`REFRESH`**: in one `withTransaction`, clear the `(accountDid, feedType, feedUri)` partition + its remote key, insert page 1 — invalidation here is expected (pull-to-refresh).
- **Count-cap (~500/feed)**: a `suspend` maintenance trim exposed on the repository, invoked **off-scroll only** (callers: app background / feed-not-active, or the periodic worker in B) — never inside `APPEND`.
- **Logout / account removal**: `DELETE WHERE accountDid = ?` (off-scroll), wired to a `:core:auth` session-state observer.
`PagingConfig.maxSize` is unusable with `RemoteMediator`, so all eviction is DB-level deletes. *Alternative — trim on APPEND:* jumps the scroll; rejected.

### D-A6. Entities stay behind the repository
`FeedPostEntity`/`FeedRemoteKeyEntity` never leave `:core:feed`; `fun FeedPostEntity.asExternalModel(): PostUi` (same-file extension) returns a `:data:models` type. Reads return `Flow`/`PagingData`; writes are `suspend`; multi-statement writes are `@Transaction`. Mirrors `:core:database` conventions.

### D-A7. Saved-feeds fetch
A `SavedFeedsRepository`-style accessor in `:core:feed` (`app.bsky.actor.getPreferences` for the saved/pinned list + feed-generator display metadata) to back the Pro picker in C/D. No cache in A (one-shot fetch); revisit if C needs offline. May reuse existing custom-feeds work (`nubecita-lq9t.3.2`) / feed-switching (`nubecita-a580`).

## Risks / Trade-offs

- **Cross-feed state drift** (D-A2) → in-app interactions overlay live state via `:core:post-interactions`; `REFRESH` re-syncs. Acceptable for snapshot feeds.
- **Room invalidation is table-level, not partition-level** *(per #502 review — HIGH)* → *any* write to `feed_post` (any partition) invalidates *every* active `PagingSource` observing the table, and a `REFRESH`-clear deletes rows the user had scrolled past. Mitigations: (1) while the feed is foregrounded the app's `RemoteMediator` is the **sole writer**; (2) the widget refresh worker (B) is **foreground-guarded** — it writes the cache **only when the app is backgrounded** (reusing `AppForegroundSignal` from the DM worker), so no `PagingSource` is being collected at write time and there is nothing to disrupt; (3) user-initiated in-app pull-to-refresh invalidation is expected (Paging's `getRefreshKey` anchors). A separate widget-only head table was considered and **rejected** to preserve the single source of truth (epic D3); B inherits the foreground-guard constraint, recorded here so A's cache contract is explicit.
- **Schema migration** v4 → v5 → additive (two new tables, no column changes), so `@AutoMigration` should suffice; commit `5.json`. Rollback: the cache is rebuildable from the network, so a bad migration can be handled by `fallbackToDestructiveMigration` *only* on the feed tables if ever needed (decide in tasks).
- **Authenticated background fetch** (Following) → reuse the `:core:auth` refresh-mutex `XrpcClientProvider` path (same as the DM worker) so a refresh can't race the app into logout.
- **120hz** → A only *provides* the `Pager`; the perf-sensitive screen migration is E, tested there.

## Migration Plan

1. Land `:core:feed` with the cache, mediator, head query, and saved-feeds fetch behind a repository interface — **no consumer rewired yet** (additive; `feature/feed` keeps working via its current path or a thin delegation).
2. Commit schema `5.json` + `@AutoMigration(4 → 5)`; verify on a `:core:database` migration test.
3. Sub-project E later flips `feature/feed` onto the `Pager`; sub-projects B/C consume the worker hook + head query.

Rollback: revert the module; the v5 tables are additive and unused by existing screens until E, so no data-loss path for current features.

## Open Questions

- Head query `LIMIT n` value (widget post count) — finalize in C; A exposes it parameterized.
- Count-cap exact value (~500 starting point) and where the off-scroll trim is invoked from in A vs B.
- Saved-feeds: reuse vs new accessor relative to `nubecita-lq9t.3.2`.

*(Resolved: list feeds are modeled now via the `LIST` `feedType` — see D-A3.)*
