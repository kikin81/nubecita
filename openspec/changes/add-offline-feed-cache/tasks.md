# Tasks — add-offline-feed-cache (bead nubecita-lgoo.1)

> Sub-project A of the Glance feed widgets epic (`nubecita-lgoo`). Foundation, no UI. Build `:core:feed` additively — no existing consumer is rewired here (feature/feed migration is sub-project E). Follow TDD: write the failing test first for each unit. Tests use JUnit Jupiter + Turbine + MockK; Room/migration tests per `:core:database` conventions.

## 1. Module scaffold + dependencies

- [ ] 1.1 Create `:core:feed` (`nubecita.android.library` + `nubecita.android.hilt`); register in `settings.gradle.kts`; namespace `net.kikin.nubecita.core.feed`.
- [ ] 1.2 Declare deps: `:core:database`, `:core:auth`, `:core:feed-mapping`, `:data:models`, `atproto`, `kotlinx-collections-immutable`, `:core:common`; add `androidx.paging:paging-runtime` (+ version catalog entry). Defer `paging-compose` to sub-project E.
- [ ] 1.3 `./gradlew :core:feed:assembleDebug :app:checkSortDependencies` green (empty module links into the graph).

## 2. Room cache (schema v4 → v5)

- [ ] 2.1 Add `FeedPostEntity` to `:core:database` — PK `(accountDid, feedType, feedUri, position)`; columns `uri`, `cid`, `authorDid`, `indexedAt`, `position`, `text`, `embedBlob`; `@Index` on `uri` and `authorDid` (D-A3/D11).
- [ ] 2.2 Add `FeedRemoteKeyEntity` — PK `(accountDid, feedType, feedUri)`, column `nextCursor: String?`.
- [ ] 2.3 Add a `TypeConverter` for the serialized embed/extras blob (kotlinx-serialization); unit-test round-trip.
- [ ] 2.4 Add `FeedPostDao` + `FeedRemoteKeyDao`: paging query (`PagingSource<Int, FeedPostEntity>` ordered by `position`), head query (`Flow<List<FeedPostEntity>>` `ORDER BY position LIMIT :n`), `@Transaction` clear+insert, count-cap delete, partition delete, cursor upsert/read. Reads `Flow`, writes `suspend`.
- [ ] 2.5 Bump `NubecitaDatabase` to v5, register the new entities + DAOs, add `@AutoMigration(from = 4, to = 5)` (additive); commit exported `core/database/schemas/.../5.json`.
- [ ] 2.6 Migration test (v4 → v5): tables created, existing v4 rows (recent searches, actors) preserved (`:core:database` androidTest or Room `MigrationTestHelper`).

## 3. Fetch + mapping in `:core:feed`

- [ ] 3.1 Define `FeedType` (`FOLLOWING`, `DISCOVER`, `CUSTOM`, `LIST`) and a `FeedKey(accountDid, feedType, feedUri)`. `LIST` is modeled now (zero-cost, avoids a later migration; `getListFeed` already exists).
- [ ] 3.2 Port the cursor-based fetch (`getTimeline`/`getFeed`) from `feature/feed/impl/DefaultFeedRepository` into a `:core:feed` network source returning `(posts, nextCursor)`; reuse `:core:feed-mapping` for wire→`PostUi`. Auth via `:core:auth` `XrpcClientProvider` (refresh-mutex path).
- [ ] 3.3 `fun FeedPostEntity.asExternalModel(): PostUi` (same-file extension); write-through mapper `PostUi`→`FeedPostEntity` with `position`/`embedBlob`. Unit-test both directions.

## 4. RemoteMediator + Pager

- [ ] 4.1 Implement `FeedRemoteMediator(feedKey)`: `REFRESH` → null cursor; `APPEND` → stored cursor; `PREPEND` → `Success(endOfPaginationReached = true)`; empty/last page → `endOfPaginationReached = true`; errors → `MediatorResult.Error`.
- [ ] 4.2 `REFRESH` writes in one `withTransaction`: clear the partition + remote key, then insert page 1 and the new cursor (D-A5). `APPEND` **also runs in one `withTransaction`**: query the partition's current `maxPosition` (default to `-1`/start at `0` if empty), assign sequential `position = maxPosition + 1…` to the new page, insert, and update the cursor — atomic so positions can't race a concurrent write. No eviction in either path.
- [ ] 4.3 Expose `FeedRepository.pagedFeed(feedKey): Flow<PagingData<PostUi>>` (`@OptIn(ExperimentalPagingApi)` `Pager` with the mediator + DAO `PagingSource`, mapped to `PostUi`).
- [ ] 4.4 `initialize()` returns `SKIP_INITIAL_REFRESH` when the partition has fresh-enough cached data (per-partition staleness/TTL check), else `LAUNCH_INITIAL_REFRESH` (D-A8) — so opening/switching to a feed renders its cached partition immediately and refreshes only when stale or on pull-to-refresh.
- [ ] 4.5 Mediator unit tests (Turbine + fake network + in-memory Room): REFRESH clears+loads only its own partition (leaves other partitions intact), APPEND uses stored cursor, end-of-pagination, PREPEND short-circuits, error path, and `initialize()` skips refresh when the partition is fresh.

## 5. Eviction (off the active-scroll path)

- [ ] 5.1 `suspend fun trimToCap(feedKey, cap = ~500)` on the repository — deletes `feed_post` beyond the newest `cap` per partition; NOT called from `APPEND` (D-A5). Does **not** touch `feed_remote_keys` — the cap retains posts, so the partition (and its single cursor row) stays live; the cursor is only removed when the whole partition is cleared (`clearAccount` / `REFRESH`).
- [ ] 5.2 `suspend fun clearAccount(accountDid)` — partition delete; wire to a `:core:auth` session-state observer (logout/account removal).
- [ ] 5.3 Tests: `trimToCap` keeps newest N posts and leaves the partition's cursor intact; `clearAccount` removes only that DID's `feed_post` + `feed_remote_keys` rows; assert no eviction occurs on an APPEND insert path.

## 6. Widget head query

- [ ] 6.1 `fun FeedRepository.head(feedKey, n): Flow<List<PostUi>>` — newest `n` by `position`, mapped to `PostUi`, no Paging.
- [ ] 6.2 Test: head returns ≤ n ordered posts from the cache; emits on cache change.

## 7. Saved feeds (for the Pro picker, later)

- [ ] 7.1 `SavedFeedsRepository.savedFeeds(): Result<ImmutableList<SavedFeed>>` — `app.bsky.actor.getPreferences` (pinned/saved) + feed-generator display metadata; map to a `:data:models` type. One-shot (no cache in A).
- [ ] 7.2 Test with a mocked atproto client: returns saved feeds + metadata; error path returns `Result.failure`.

## 8. DI wiring

- [ ] 8.1 Hilt module(s) in `:core:feed` binding `FeedRepository` / `SavedFeedsRepository` (+ DAOs from `:core:database`). No consumer rewired (feature/feed migration is E).
- [ ] 8.2 `./gradlew :app:assembleDebug` — Hilt graph still links with `:core:feed` present.

## 9. Verification

- [ ] 9.1 `:core:feed` + `:core:database` unit tests green; root `testDebugUnitTest` green (no fakes/implementors broken elsewhere).
- [ ] 9.2 `./gradlew :core:feed:lintDebug :core:database:lintDebug spotlessCheck :app:checkSortDependencies` green; committed `5.json` present.
- [ ] 9.3 Confirm entities never leak past `:core:feed` (only `:data:models` types in the public API); battery rule unaffected (no new always-on work — refresh scheduling is sub-project B).
- [ ] 9.4 **Scale stress test (D-A2):** populate a power-user-scale cache (e.g. ≥10 feed partitions × `cap` posts, with cross-feed overlap and representative embed blobs) and validate the design's assumptions: head/page queries stay flat (index prefix-scan, no full scan — verify via `EXPLAIN QUERY PLAN`), per-partition `REFRESH` clear+insert and `trimToCap` write times are low, and the DB file stays in the expected MB range. Record the numbers so we know the real threshold where the denormalized→hybrid migration (D-A2 reversibility) would be worth it.
