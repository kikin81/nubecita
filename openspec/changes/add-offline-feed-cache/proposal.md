## Why

Feeds today are fetched ad-hoc inside `feature/feed/impl` (manual cursor pagination, no cache), so the app has no offline story and home-screen widgets have nothing to read — a widget cannot depend on a feature `:impl` module. This change builds the shared, offline-first foundation that both the app feed and the upcoming Glance widgets read from. It is sub-project **A** (bead `nubecita-lgoo.1`) of the Glance feed widgets epic (`nubecita-lgoo`); full rationale in `docs/superpowers/specs/2026-06-08-glance-feed-widgets-design.md`.

## What Changes

- **New `:core:feed` module** that owns feed fetching, mapping, and the offline cache, and becomes the single source of truth for feed data. Feed fetch + wire→`PostUi` mapping move out of `feature/feed/impl` so the app and (later) the widget worker both depend on `:core:feed`.
- **Room offline cache (DID-keyed, denormalized)** — `NubecitaDatabase` goes v4 → **v5**:
  - `feed_post`: one row per cached position, keyed `(accountDid, feedType, feedUri, position)`; queryable columns `uri`, `cid`, `authorDid`, `indexedAt`, `position`, `text` + a serialized `embed` blob; `@Index` on `uri` and `authorDid`.
  - `feed_remote_keys`: atproto paging cursor per `(accountDid, feedType, feedUri)`.
- **Paging 3 + `RemoteMediator`** (new dependency `androidx.paging`) exposing a `Pager`/`Flow<PagingData<PostUi>>` for the app feed (consumed later by sub-project E) **and** a head DAO query (`ORDER BY position LIMIT n`) for the widget (sub-project C, reads the head, not Paging).
- **Eviction off the active-scroll path**: `REFRESH` clears + reinserts the partition; a `~500/feed` count-cap trim runs off-scroll (never during `APPEND`, which would invalidate the live `PagingSource`); logout/account-removal deletes the account's partitions.
- **Saved-feeds fetch** (`app.bsky.actor.getPreferences` + feed-generator metadata) to back the Pro configurable-widget picker later.
- Single-account MVP, but the schema is **DID-keyed** so a future multi-account epic needs no migration.

## Capabilities

### New Capabilities
- `core-feed-cache`: the `:core:feed` module — feed fetching/mapping, the DID-keyed Room offline cache (`feed_post` + `feed_remote_keys`), the Paging 3 `RemoteMediator` with off-scroll eviction, the widget head query, and the saved-feeds fetch.

### Modified Capabilities
<!-- None. feature-feed continues to consume a feed repository unchanged at the spec level in A; migrating the feed screen to PagingData/dual-flow MVI is sub-project E (nubecita-lgoo.5) and modifies feature-feed there. -->

## Impact

- **New module:** `:core:feed` (`nubecita.android.library` + `nubecita.android.hilt`; depends on `:core:database`, `:core:auth`, `:core:feed-mapping`, `:data:models`, `atproto`).
- **`:core:database`:** schema v4 → v5 (committed `5.json` + `@AutoMigration`/manual `Migration`); new `feed_post` / `feed_remote_keys` entities + DAOs, kept behind the repository (`asExternalModel()` → `:data:models` `PostUi`).
- **New dependency:** `androidx.paging` (paging-runtime; paging-compose lands with sub-project E).
- **`feature/feed/impl`:** feed fetch/mapping relocates to `:core:feed`; the feed screen's behavior and MVI state model are unchanged in A (the PagingData migration is E).
- **Bead:** `nubecita-lgoo.1`.
