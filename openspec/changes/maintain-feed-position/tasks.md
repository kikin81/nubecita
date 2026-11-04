# Tasks — maintain-feed-position (epic `nubecita-lgoo.5`)

Cross-links: bead `nubecita-lgoo.5` (epic) and its six children. Reference `proposal.md` and `design.md` (D1–D14). One `gh stack`, one PR per child, landed with a single `gh stack merge --squash --yes`. Each child PR is titled as a Conventional Commit and carries `Closes: <child-id>` in its body.

Gate per `CLAUDE.md`: each child branch lints its touched modules only before `gh stack submit`; the **top** branch runs the full gate (`:app:assembleDebug`, every touched module's lint, and the compose-expert review against the cumulative `git diff origin/main...HEAD`) before `gh stack merge`.

Flavor status of the touched modules, checked (`git grep -l nubecita.android.flavors -- <module-dir>`) rather than inferred — these pick the task names:

| Module | Variant | Lint / unit-test task |
|---|---|---|
| `:feature:feed:impl` | **flavored** | `lintProductionDebug` / `testProductionDebugUnitTest` |
| `:core:feed-cache` | **flavored** | `lintProductionDebug` / `testProductionDebugUnitTest` |
| `:core:database` | plain | `lintDebug` / `testDebugUnitTest` |
| `:core:widget-sync` | plain | `lintDebug` / `testDebugUnitTest` |
| `:core:feed-mapping` | plain | `lintDebug` / `testDebugUnitTest` |
| `:data:models` | plain | `lintDebug` / `testDebugUnitTest` |

Both flavored modules ship a `bench` source set, so the `bench` variant must compile too — the feed's bench fakes are part of task 5.3, not an afterthought.

`:feature:search:impl` also consumes `FeedItemUi`, but only via `toFlatFeedItemUiSingle` — grep confirms it never constructs `ReplyCluster` or `SelfThreadChain`, so the key renaming in task 5.2 does not reach it (`Single`'s key stays the bare post URI).

## 1. Persist feed entries, not bare posts (`nubecita-lgoo.5.1`, D2 + D13)

- [x] 1.1 Widen `FeedPostEntity` to carry the per-entry context the grouping needs: the blob becomes the serialized `FeedViewPost`, plus the denormalized columns the write-time projection and the anchor lookup actually query (reply root URI, reply parent URI, repost-by DID). Bump `NubecitaDatabase` to `version = 7`, commit `core/database/schemas/.../7.json`. **Tests:** extend `FeedPostDaoTest` for the new columns (round-trip, partition scoping, index-served lookups).
- [x] 1.2 Write the v6 → v7 migration. Drop `feed_post` / `feed_remote_keys` rows (D13 — the `reason`/`reply` data was never persisted and cannot be recovered locally); preserve recent searches and cached actors. Prefer `@AutoMigration`; hand-write a `Migration` in `Migrations.kt` if the row drop makes the diff unresolvable. **Tests:** a migration test asserting non-feed data survives and the feed tables come back empty.
- [x] 1.3 Rewrite `FeedPostMappers` so `toFeedPostEntity` serializes the whole `FeedViewPost` and the read mapper reconstructs repost attribution and reply context. Delete the "reason is NOT persisted" carve-out from the KDoc. **Tests:** extend the mapper's unit tests — a reposted entry round-trips with attribution, a reply entry round-trips with root+parent, a malformed blob still degrades to a skipped row rather than throwing.
- [x] 1.4a Add the by-URI post store (D24): `cached_post`, one row per post per account, holding the blob plus the denormalized columns, with reference-aware `deleteUnreferenced` rather than position-based trimming (a context post holds no position). An **unreadable** context post (`blockedPost` / `notFoundPost`) is stored as a row with an `unavailable` discriminator and no blob — the design said "the blob round-trips the tombstone", which stopped being true once posts were de-duplicated by URI, and dropping the row would silently turn a blocked reply into a root post. **Tests:** `CachedPostDaoTest` — one stored copy serves a timeline item and another item's cluster context; a context post that was never a timeline entry is stored and hydrates; a tombstone row survives; `deleteUnreferenced` keeps referenced posts; account scoping and per-account URI isolation.
- [x] 1.4b Rewire both grains onto the store: drop `entry_blob` and the duplicated scalars from `feed_post`, leaving an entry envelope (position, URI reference, repost label, reply root/parent URIs), and read through a join. This cascades — `FeedRemoteMediator<Int, FeedPostEntity>`'s type parameter, the `Pager` in `DefaultFeedRepository`, `FeedRefresher`'s single-table write, and `head`'s return type all change with it, as does the `core-feed-cache` spec requirement that `feed_post` indexes `author_did` (that column moves). **Tests:** the joined read returns the same `PostUi` values as today; a partition row with no matching stored post is skipped rather than throwing.
- [x] 1.5 Add the projected-item table (D15 + D23 + D25): one row per rendered `FeedItemUi` at a display position, holding the structural layout (variant, ordered member URIs, suppressed-reply count) plus a row-type discriminator so a seam marker is a row rather than an inferred gap. Add the prepend cursor columns to `feed_remote_keys` (D18): boundary `since`, in-range fill cursor, pending new boundary. **Tests:** DAO tests for the projection table (ordered read, partition scoping, purge-with-account), seam-marker round-trip, and independent read/write of the three cursors.
- [x] 1.6 Rebuilt `FeedPostScaleTest`'s fixture around whole feed entries and all four tables.

  The earlier note said the blob was the problem. It wasn't, after the split: `cached_post` stores a wire `postView`, so a `postView`-shaped blob is now correct. What the fixture understated was the **count** — a reply entry contributes three posts, three membership rows and a cluster item, not one of each — and that the projection tables were absent entirely.

  The fixture now generates a deterministic variant mix (~60% plain, 20% repost, 20% reply — a stated assumption, not a measurement), draws thread roots and parents from a shared pool so the by-URI store's de-duplication is exercised rather than bypassed, and seeds envelopes, posts, items and members together. The REFRESH batch is salted so its page is genuinely new content; without that it regenerated the URIs the seed had already written and every upsert degraded into an UPDATE of an identical row, which measured cheaper than production.

  **Measured, 11 partitions × 500 entries:** `feed_post`=5250, `cached_post`=5700, `feed_item`=5510, `feed_item_member`=7700. **On disk 27.98 MB**, up from 21.32 MB before the projection existed — about **5.2 KB per cached entry**, which is the figure D19's cap budget should be denominated from. Bound is 256 MB, so there is ample headroom; the per-entry number is the useful part, not the total.

  **Timings are not reported as a trend.** REFRESH now measures ~42 ms, stable over three runs, against a 1000 ms bound — but that is *lower* than the pre-split ~188 ms despite writing more rows, and the two code states differ in post count, URI distribution and preceding database content all at once. Cross-code-state emulator timings are not comparable; treat ~42 ms as the current figure and re-measure in place, not against it.

  **What the measurement actually found is a growth bug, recorded in 3.3 below:** the counts show `feed_post` trimmed to 5250 while `feed_item` sits at 5510 and `cached_post` and `feed_item_member` are unbounded. `trimToCap` bounds one table of four.

## 2. Write-time `FeedItemUi` projection (`nubecita-lgoo.5.2`, D3 + D7)

- [x] 2.1 Moved `FeedItemDedupe` and `FeedViewPostMapper` from `:feature:feed:impl/data/` into **`:core:feed-mapping`** rather than into `:core:feed-cache` directly — that is where `toPostUiCore` and the moderation helpers already live, it is unflavored, and both the cache's write path and the feature can depend on it. The non-commutative pass order and the repost exemption are byte-identical; only the package line changed. Relocated `FeedViewPostMapperTest`, `FeedItemDedupeTest`, `FeedThreadRootDedupeTest` and `FeedChainProjectionTest` with assertions untouched, which is the relocation's proof: `:core:feed-mapping` 152/152, `:feature:feed:impl` 196/196. The JSON fixtures had to move with them (they load off the test classpath, so the first run failed with `fixture … not found` — the tests were relocated but their resources were not); the three `DefaultFeedRepositoryTest` still uses stay behind, the other eight moved. `:core:feed-mapping` gains `libs.timber`.
- [x] 2.2 The projection is computed at write time and persisted.

  **D7's semantics.** `dedupeByThreadRoot` takes `seenRoots` and `visibleFromAbove`, defaulting to empty so every existing call site is byte-identical. Seeding them is what distinguishes the directions: APPEND seeds from the partition above (a second reply into a thread already on screen is still dropped, which the accumulated list used to give for free), PREPEND seeds neither (a thread resurfaces with its newer reply), REFRESH has nothing above. Two tests feed identical input through both directions and assert opposite outcomes — the only check that catches an implementation accepting the parameters and ignoring them. The `size < 2` early return went, being unsafe once a single item can duplicate a root above it.

  **The read mapper was a prerequisite, not a nicety.** The page-boundary chain merge needs the partition's tail *as a `FeedItemUi`*, so the write path needed the projection's read path first. `FeedProjectionMappers.toFeedItemUi` rehydrates a row + ordinal-ordered members + stored posts, written once for its three callers (keyset source, `head`, the writer). It needed `thread_root_uri` (the APPEND seed, stored rather than recovered by stripping an `item_key` prefix) and `has_ellipsis` (a cluster cannot be rebuilt without it), plus `seenThreadRoots` / `renderedPostUris` / `tailItem`.

  **The merge was moved, not reimplemented.** `mergeChainBoundary`, `ChainBoundaryMerge` and `stripLeadingTailOverlap` — 206 lines including cursor-resync overlap stripping and defence-in-depth deduplication — moved verbatim from `FeedViewModel` into `:core:feed-mapping`, so the VM and the writer share one definition. `:feature:feed:impl` staying at 196/196 is what makes "verbatim" checkable. `threadRootId` is public for the same reason: the writer stores the root and the dedupe seeds on it, and two derivations would silently miss.

  **`FeedProjectionWriter`** projects a page, merges the chain boundary (before de-duplication, since absorbing the tail changes an item's thread root and therefore whether it is dropped), seeds from the partition, and writes `feed_item` + `feed_item_member`. An absorbed tail's row is deleted — leaving it would render the same posts twice — and the seed is read *after* that delete, or the absorbed root would be used to drop the chain that absorbed it. Called inside the refresher's and mediator's existing transaction, so no reader sees a projection disagreeing with its entry rows.

  Both call sites assert the write happens: `coVerifyOrder` in the refresher, and in the mediator that APPEND projects exactly once while PREPEND projects nothing. Without those the relaxed mocks would have let the projection write be deleted silently.

  **Left for D23a:** the writer reads live viewer and preferences. The fingerprint that makes a background write project with the preferences the reader last saw applied belongs to the re-projection task; this is the single place it changes.

- [x] 2.3 Filter relocated and applied, preference fingerprint recorded, and local re-projection in place (D26 + D27).

  `shouldDisplayInFollowingFeed` and its `FeedReplyFilterTest` moved to `:core:feed-mapping` — 19 tests moved exactly (`:core:feed-mapping` 159 → 178, `:feature:feed:impl` 196 → 177), assertions untouched, which is the relocation's proof. `FeedReplyFilterWiringTest` stayed behind: it drives the repository through mocks, so it belongs with the repository.

  The planned dependency would have inverted the layering, so the **value type** moved instead — see **D26**. `FeedViewPrefs` is now in `:data:models` (`@Immutable`), its wire parsing stays in `:core:feeds`, and `:core:feed-mapping` needs no new dependency. `:core:feed-cache` gains `:core:feeds` for the preferences repository it reads.

  The filter runs in `FeedProjectionWriter` **on the wire entries, before projection**, because the predicate reads `reply.root` / `reply.parent` and the repost reason — context the projection consumes and does not carry forward. Follow-scope is decided by `FeedType.isFollowScoped`, written as an exhaustive `when` so a new feed type is a compile error rather than a silent default. Tests put the same page and the same preferences through a Following feed and a generator and assert opposite outcomes.

  **Fingerprint done; re-projection is a decision, not a task.** `feed_partition_state` records the preferences each partition's projection was computed with — a new per-partition table rather than a column on the cursor row, and the home the scroll anchor will share. `projectionPrefsFingerprint` covers every input that feeds the projection and nothing else, with the label map sorted so equal preferences cannot produce different strings. The writer records it on every projection; `initialize()` compares it on open.

  **D27 recorded a conflict between my own decisions, now resolved.** Local re-projection needs the wire entry, and the two-grain split had removed it from disk. The envelope now carries `entry_context_blob` — its own `reason` and `reply`, taken by re-encoding through the same serializers that read them back, with the post excluded since it lives once in the by-URI store. `FeedProjectionWriter.reproject` rebuilds the entries from envelopes plus stored posts and rewrites the partition with no network; `initialize()` calls it on a mismatch and reports `SKIP_INITIAL_REFRESH`. The reader's place survives a preference change, which was D22's whole reason for choosing re-projection. An absent fingerprint counts as not stale, so upgrading re-projects nothing.

  **D23a's worker clause turned out to be unnecessary** and is retracted in D27: a fingerprint cannot be projected with, and the concern does not arise because `head` reads raw rows and already combines live preferences.

  The anchor-dependent tests from the original text ("the reader's place survives", "a filtered-out anchor resolves downward") still belong with task 5.1, which owns the anchor storage — re-projection preserves ordering and content keys, but there is no anchor yet to assert against — persist the prefs a projection was built with, re-project locally on open when the live fingerprint differs, and have the background worker project with the persisted one rather than live preferences. The anchor-related tests in the original task text ("the reader's place survives", "a filtered-out anchor resolves downward") need the anchor storage from task 5.1, which does not exist yet.

- [x] 2.4 The top-up moved into the mediator (D23b).

  `append()` now follows a page that projects to **zero** items instead of reporting it. A fetched page can legitimately render nothing — the reply tuners can drop every entry, de-duplication the rest — and calling that end-of-pagination stops the feed while the server still has content, while calling it success-with-nothing leaves Paging no reason to ask again. Bounded at `MAX_TOP_UP_ROUNDS = 3`, mirroring the limit `DefaultFeedRepository` used, because an aggressively filtered account could otherwise walk its whole timeline inside one `load()`. A barren page that is also the last page still reports the end rather than spending a fetch to confirm it.

  `writeRefresh` / `writeAppend` now return the item count, which is deliberately not the page size — that gap is the whole signal.

  **Also fixed a bug I introduced in 1.5:** append wrote the cursor row with a whole-row `upsert`, which would have cleared the prepend cursors added in that task — exactly what the DAO's own KDoc says not to do. It uses `updateAppendCursor` now, with a test asserting the targeted update *and* that no whole-row upsert happens.

  **Worth knowing:** a relaxed MockK answers an `Int` with `0`, which now *means* "barren page", so two existing mediator tests started topping up into unstubbed fetches. The writer mock now defaults to a productive page and the top-up tests override it.

  **Not covered here:** a REFRESH page that projects to zero. It is reachable — a reader with `hideReplies` and a page of all replies — and leaves the feed momentarily empty until Paging appends, where the top-up applies. The entry rows are written unfiltered, so `head` and the widget are unaffected. Worth a follow-up in the screen migration if it shows up on the device pass.

- [x] 2.5 `FeedItemPagingSource` pages the projection on `position`, and `pagedFeed` returns `Flow<PagingData<FeedItemUi>>` backed by it.

  Keyed on `position` because Room's generated source answers `getRefreshKey` with an **offset**: the `InvalidationTracker` is table-level, so any write invalidates the source — including one that added rows the reader cannot see — and the same offset then names an item N rows further down. A position does not move, because positions are assigned once and never renumbered. The test that matters asserts exactly this: the refresh key is unchanged after three rows arrive above, and reloading at it returns the same item.

  **Both directions, not just forward.** A reader restored at an anchor sits mid-partition and must be able to scroll back towards newer items, so `load` branches on `LoadParams.Prepend` and reads `itemsBefore` descending, reversed to display order. A forward-only source would have stranded them.

  Invalidation is wired by hand — the generated source would have done it, this one must — observing `feed_item`, `feed_item_member` and `cached_post`, with the observer removed when the source is invalidated since a `PagingSource` is single-use. `initialPosition` is the `Pager`'s initial key, which is how a restored anchor will enter Paging in 5.x.

  **`pagedFeed` gained a `pagingSourceFor` seam**, matching the existing `mediatorFor` one, because the repository now constructs its own source and a JVM test could otherwise no longer serve fixed rows.

  **One test moved rather than being kept passing.** `moderation-filtered entity is dropped from the paged stream` asserted a job this layer no longer has — moderation is applied where the projection is computed. It is replaced by a pass-through assertion, and the dropping behaviour is covered against the rehydration mapper, where it now lives.

- [x] 2.6 `head` keeps reading the raw entry rows, and two tests now hold it there.

  It was already true after 1.4b, so the work here was the guarantee rather than the change. The valuable assertion is the structural one — `head` never touches `feedItemDao` — because routing it through the projection is the change someone is most likely to make later, and it would silently apply the reply tuners and de-duplication to the widget and the watch, which curate nothing and expect every entry. The second test pins the visible consequence: two entries sharing a thread root both survive `head`, where the projection would collapse them to one.

## 3. `since`-bounded PREPEND on the mediator (`nubecita-lgoo.5.3`, D4 + D5)

- [x] 3.1 `FeedNetworkSource` takes `since` and returns `startCursor`.

  `FeedNetworkPage` is a named type now rather than a `Pair`: it carries three values, two of them nullable cursors pointing in opposite directions, and `page.startCursor` says which end of the feed it describes where a third tuple slot would not. (Destructuring still works positionally, so that is readability, not a guarantee — the first version of this KDoc overstated it.)

  `FeedType.supportsSince` is deliberately separate from `isFollowScoped` even though they agree today: one is about the viewer's preferences, the other about the protocol. A `since` passed for a generator feed is a caller error, so it is **logged and dropped** rather than silently ignored — a dropped `since` turns a bounded catch-up into an unbounded walk backwards through the whole feed.

  The tests assert against the **URL that goes on the wire**, not the request object: a `since` that is built but never serialized would do exactly that damage with nothing else noticing. Confirmed too that the SDK really does expose `since` on `getTimeline`/`getListFeed` and `startCursor` on their responses — it compiles, which is the design's protocol claim verified rather than trusted.

- [x] 3.2 Implement `LoadType.PREPEND` in `FeedRemoteMediator`: fetch within the range from the persisted boundary cursor, allocate downward inside the reserved negative window relative to the partition's current minimum (D17), write back the fill and boundary cursors (D18), leave the append cursor untouched, and report exhaustion when the returned cursor equals the `since` passed. Keep the early `endOfPaginationReached = true` return for feeds with no `since`. **Tests:** mediator unit tests — additive write, no renumbering, a second fill page landing *below* the first without overwriting it, cursor write-back so a second prepend makes progress, an interrupted fill resuming from the persisted fill cursor, budget stop, and the generator-feed no-op.
- [x] 3.3 Rewrite `trimToCap` to count rows rather than position values, make it anchor-aware (D17 + D19), **extend it to every grain** — 1.6's measurement shows it bounds `feed_post` alone, leaving `feed_item`, `feed_item_member` and `cached_post` to grow without limit — and make it reference-aware for the by-URI post store (D24) — a post survives until no live partition row and no live projection row references it, evaluated in the same pass: retain the newest `cap` rows or everything from the anchor upward, whichever is larger, under a hard ceiling. Denominate the catch-up budget against `DEFAULT_CAP` so a full catch-up cannot fill the partition by itself. **Tests:** extend `FeedPostDaoTest` — trim over a gapped range keeps `cap` rows (the old `MIN(position) + cap` arithmetic keeps far fewer); a partition with more above-anchor entries than `cap` keeps the anchor and its backlog; the ceiling bounds worst-case growth.

  **As built:** the anchor reaches the trim as a parameter (`anchorItemPosition`), not as stored state — resolving the saved anchor (a URI plus an offset, D6) to a position belongs to the anchor work in group 5, and there is no anchor to protect before it. Callers pass `null` until then and get the plain row-count trim. The two grains are tied by **content** rather than by a shared row count: a self-thread chain folds several entries into one item, so trimming `feed_post` and `feed_item` to the same number would strand a surviving item whose entries are gone and leave D27's local re-projection unable to rebuild it. `oldestReferencedByProjection` keeps whatever the surviving projection still points at.

## 4. Worker moves off the destructive refresh (`nubecita-lgoo.5.4`, D10)

- [x] 4.1 Point `WidgetRefreshRunner` at the additive prepend for `since`-capable partitions, keeping the destructive refresh for a cold partition and for generator feeds. Its control flow is unchanged: per-feed independence, retry only on total failure, the foreground guard, the consumer gate, and the `trimToCap` → prefetch sequencing. **Tests:** extend `WidgetRefreshRunnerTest` — a warm partition is added to rather than cleared, a cold partition still loads from the top, a generator feed still uses the destructive path, and every existing isolation/outcome assertion still holds.
- [x] 4.2 Add the regression this epic exists to prevent: a worker run against a warm partition with a saved anchor leaves the anchored entry present and resolvable. **Test:** a new case in `WidgetRefreshRunnerTest` (or an instrumented test in `:core:widget-sync` if the anchor read needs Room).

  **As built:** covered at two layers instead of one end-to-end instrumented test. `WidgetRefreshRunnerTest` asserts the mechanism is gone — a warm partition is never refreshed destructively — and a new `:core:database` instrumented test, `FeedPositionSurvivesRefreshTest`, drives an additive write plus the trim's real SQL and asserts the anchored item still resolves at the same position with its entry intact. The repository's unit tests cover which cut-off the trim picks; what a mocked DAO cannot see is whether `LIMIT 1 OFFSET n` over a gapped range and `>` versus `>=` in the delete compose correctly, which is what the instrumented test exists for. Mutation-checked: removing the anchor extension fails it. An end-to-end test through `FeedCatchUp` would need a network stub inside a Hilt graph, and the extraction the worker reaches it through (`FeedCatchUp`, shared with Paging's PREPEND) is already covered by the mediator's tests against the real allocation.

  **Also in 4.1:** the prepend moved out of `FeedRemoteMediator` into a shared `FeedCatchUp`, mirroring how `FeedRefresher` is shared with the worker — the worker has no `Pager`, and the two callers must drive identical logic. `FeedRepository.catchUp` returns a `CatchUpOutcome` rather than a boolean so the worker can fall back to the destructive refresh for a cold partition, a missing boundary cursor or a generator feed; `NOT_POSSIBLE` is decided from local state before any fetch, so the fallback costs no extra request.

## 5. Screen migration + anchor restore (`nubecita-lgoo.5.5`, D1 + D6 + D9 + D11 + D12)

- [x] 5.1 Add the anchor storage — one row per `(accountDid, feedType, feedUri)` holding entry URI + offset — beside the cached entries, purged by the same `clearAccount` call. **Tests:** DAO tests for round-trip, partition scoping, and purge-with-account.

  **As built:** three columns on `feed_partition_state` (schema v8, `@AutoMigration(7 → 8)`), not a table of its own — that row already exists per partition and is already purged by `clearAccount`. The stored identifier is the projection **item key**, not a bare entry URI: the reader's place is an item, a chain of several entries is one row, and the key is content-derived (D12) so it still resolves after a re-projection moved the row. The last known **position** is stored as well, because a dead anchor resolves downward (D11) and a key that no longer exists gives nothing to resolve downward *from* — it is also what 3.3's trim reads. Both writes on this row are targeted, so the anchor and the projection fingerprint cannot clobber each other, and `saveAnchor` inserts-if-absent because a reader can scroll a partition whose fingerprint was never written.
- [x] 5.2 Namespace the `FeedItemUi` keys (`"chain:"` + first post, `"cluster:"` + root) and anchor grouped variants on their first post (D12). Move the interaction overlay's delete-removal check off `item.key` onto an explicit leaf-URI accessor (D21) — it relies today on `key` being the leaf's id, which this task changes. **Tests:** extend `FeedItemUiTest` — keys are unique across a mixed list containing a chain and a `Single` sharing a root, and a chain's key is stable when extended; plus a case asserting deletion of a chain's leaf still removes the item.

  **As built:** two corrections came out of doing it, recorded as **D28** and **D29**. (1) A cluster keys on its **leaf**, not its root as D12 said: two replies into one thread are two clusters sharing a root, and D7 explicitly lets that pair coexist across a prepend seam — keyed on the root they collide, which is the duplicate-`LazyColumn`-key crash the namespace exists to prevent, and it would make the anchor's `item_key` lookup ambiguous. The stability argument holds for a chain, whose leaf moves when it gains a post; a cluster's does not. (2) The projection was missing the third de-duplication pass the in-memory screen ran (`dedupeByKey`), so once the screen reads the cache, two reposts of one post in a page — which the thread-root pass **exempts** from its drop — would reach `LazyColumn` with one key. It is restored as `dedupeByLeafPost`, keyed on `leafId` and seeded from the partition's rendered **leaves** in every direction including prepends, which also drops a `since`-bounded catch-up's overlap with the cached head. `renderedLeafUris` is a new DAO query; seeding from every rendered post instead would have overridden the cluster-context pass's repost exemption.
- [x] 5.3 Migrate `FeedViewModel` + `FeedScreen` to `collectAsLazyPagingItems`, deleting `:feature:feed:impl`'s `FeedRepository` / `DefaultFeedRepository` / `TimelinePage`. Keep the `PostInteractionHandler` delegation, the optimistic `outgoing:` rows, the inline composer entry points, `FeedLoadStatus`, the tab re-tap signal, the video coordinator, and `listPane{}` metadata. Re-express the three in-place list mutations (optimistic mute-hide with rollback, the `PostInteractionsCache` overlay, the outgoing-post projection) as `PagingData` transforms fed by their external flows with `cachedIn` (D21) — `PagingData` is immutable, so none of them can keep mutating a list in state. **Tests:** rework `FeedViewModelTest` and `FeedScreenViewStateTest` against `TestPager`/`asSnapshot`, keeping a case per optimistic mechanism; keep the existing Compose UI tests passing; update the bench fakes (`BenchFakeFeedRepository`, `BenchTimelineMapper`) and `FakeFeedRepository` in `androidTest`.

  **As built, and the decisions it forced:**

  - `FeedState` keeps only what Paging has no opinion about (autoplay, tap markers, outgoing rows). The load lifecycle is derived from `CombinedLoadStates` by a pure `toFeedLoadStatus(itemCount)`, so `FeedLoadStatus` survives as the vocabulary and the render matrix stays unit-tested without a Compose host.
  - The three optimistic mechanisms became transforms fed by their own flows, with `cachedIn` between the `Pager` and the transforms. Mute-hide is a set of DIDs filtered out, so its rollback is removing a DID rather than restoring a saved list.
  - **A generator feed now refreshes on open.** `initialize()` skipped the refresh for any warm partition, which for Discover would mean opening on stale algorithmic content with no route to the newest entries. D14 says those feeds keep today's behaviour exactly, so the skip is now conditional on the feed supporting a bounded catch-up.
  - **Seeding the interactions cache needed a new event.** `seed` is how wire data newer than a cached overlay replaces it, and Paging has no VM-visible "pages changed" hook, so the screen dispatches `ItemsLoaded`. It is keyed on the loaded posts' **URIs**, not the snapshot: seeding makes the cache emit, which re-runs the transforms and produces a new snapshot, so keying on the snapshot would have seeded its own output in a loop.
  - **The outgoing-row filter moved to the screen.** Whether a just-sent post is still needed depends on what the pages hold, which only `LazyPagingItems` knows. `notYetInFeed` is a pure function, unit-tested.
  - **The bench fixtures moved to `:core:feed-cache`.** Once the screen reads its pages from the cache, a fixture living in `:feature:feed:impl` could not reach the repository that serves them, and two bench `FeedRepository` bindings for one interface would collide. `BenchTimelineDto` / `BenchTimelineMapper` / `BenchFeedPostSource` and the JSON + image assets moved with it; the widget's head now comes from the same fixtures instead of four hardcoded samples.
  - **The feed's UI tests needed a signed-in session.** A partition is keyed by DID, so a signed-out graph has nothing to read — `TestAuthBindingsModule` fakes the session and the XRPC client provider. `:core:feed-cache`'s production `FeedRepositoryModule` is no longer `internal`, for the same reason `:core:auth`'s `AuthBindingsModule` is not: another module's `@TestInstallIn` has to name it.
  - **Deleted with their subjects:** `DefaultFeedRepositoryTest` (wire → page mapping, now `FeedNetworkSourceTest` plus the mappers' tests) and `FeedViewPrefsFilterInstrumentationTest` (it constructed the feature's `DefaultFeedRepository` to prove the reply-filter wiring; the filter is now `FeedProjectionWriter.filteredForFeed`, covered by that writer's own tests). Re-adding an instrumented assertion for the prefs binding would need an `androidTest` source set in `:core:feed-cache`, which this change does not add.
- [x] 5.4 Write and restore the anchor: record it off the scroll frame (debounced or lifecycle-snapshot), restore before first frame on a warm partition, resolve downward when the anchored entry is gone (D11). The collector must be cancelled with its composition and the ViewModel must not hold `LazyListState` (D9). **Tests:** unit tests for the resolve-downward and resolve-to-top cases; a Compose UI test asserting no initial-loading state renders on a warm partition.

  **As built:** `FeedRepository.anchor()` returns a resolved `FeedAnchor` (position, the resolved row's **key**, offset) rather than a bare position, because the two consumers need different things — the `Pager` opens at the position, the list scrolls to the key, which is the only identity a rendered row has. A dead anchor resolves downward and returns **offset 0**: the stored offset measured a scroll into a row that is gone, and applying it to a different row of a different height would land somewhere arbitrary. A save whose key no longer has a row is dropped rather than stored, since a refresh landing between the settle and the write would otherwise leave the fallback resolving from a stale position.

  Recording and restoring both live in `PostFeedList` behind one nullable `anchoring` parameter: it is the only place that knows how many rows it puts above the paged items, which the restore's index arithmetic needs, and the call site then states explicitly which surfaces keep a position (`FeedViewScreen` passes null — a generator refreshes on open, so an anchor there would be written and never read). The recorder reads the first visible row's **LazyColumn key**, not its index, so no header/outgoing arithmetic is involved at all, and it is debounced 400 ms so a fling writes once on settle rather than per frame. Both collectors live in the composition and die with it; the ViewModel never sees `LazyListState` (D9).

  **Beyond the listed tests:** `FeedScreenAnchorInstrumentationTest` drives the round trip on a device — a settled scroll records a row key, and a stored anchor opens the feed at that row. Both it and the warm-partition shimmer test were mutation-checked (disabling the restore scroll, and delaying the first page to simulate a cold partition) because a passing UI test that never had a chance to fail proves nothing.
- [x] 5.5 Repoint pull-to-refresh at the destructive path (D8) and confirm launch does not dispatch `Refresh`. **Tests:** extend the existing pull-to-refresh UI test — the anchor is cleared after a refresh, and launch with a warm partition dispatches no `Refresh`.

  **As built:** there was no existing pull-to-refresh UI test to extend — the `isRefreshing` mapping was covered at the view-state level only — so both cases are new instrumented tests. `openingTheFeed_doesNotBehaveLikeAPullToRefresh` is the one that matters: launch used to mean "fetch the top" and now means "show me where I was", and if opening ever cleared the place again every session would start at the top with no unit test noticing. `pullToRefresh_dropsTheReadersPlace` drives the real gesture; it has to be made from the top of the list, so that test deliberately stores no anchor (the gesture clears the place whether or not one was stored). The gesture reaching the VM at all is the discriminating part — `items.refresh()` alone would leave the anchor in place.
- [x] 5.6 Verify the screenshot surface. Regenerate only the baselines this change actually alters and `git checkout --` the macOS antialiasing noise (`scripts/triage-screenshot-failures.py`); never commit a whole regeneration. CI's `screenshot` job is the authority. **Tests:** `FeedScreenScreenshotTest` / `FeedViewScreenScreenshotTest` — unchanged where the rendering is unchanged.

  **As built:** no baselines committed, because the rendering did not change. The one risk worth checking was whether a Paging-backed preview renders at all under layoutlib — `collectAsLazyPagingItems` collects a flow, and an empty render would have meant every Loaded baseline silently became a blank feed. Regenerated locally purely to look: the fixtures render exactly as before. The 10 images the regeneration touched are macOS antialiasing noise — max delta 1/255 across 1–3 pixels, all in the FAB's shadow — measured rather than assumed, and discarded with `git checkout --`. CI's `screenshot` job remains the authority.

## 6. Pill, seam, and jump-to-newest (`nubecita-lgoo.5.6`, D5 + D14)

- [x] 6.1 Derive pill visibility from undismissed projected entries above the anchor, and persist the dismissal marker on the anchor row (D20) — not from an in-session prepend result, or the background worker's writes raise no pill. **Tests:** unit tests — a background prepend raises the pill on the next composition; a dismissal survives process death; no entries above the anchor means no pill.

  **As built:** the dismissal is a **position**, not a flag — `pill_dismissed_position` on `feed_partition_state` (schema v9, AutoMigration). A flag would be wrong in both directions: left set, a later arrival above the mark would never raise the pill again; cleared on each write, dismissing would not stick. As a position it reads naturally — anything above the mark is new, anything below has been seen. The waterline is the dismissal, else the anchor, else position 0, which is where a destructive refresh starts writing, so a prepend's negative positions are "above" and a reader who never scrolled still gets told. "Survives process death" is exactly "it is a column", which the DAO round-trip and the 8 → 9 migration test pin. Faces come from a `Flow` query joining the projection rows above the waterline to their leaf posts, capped at three and distinct by author — three faces from three people says more than one person three times.
- [x] 6.2 Build the pill: up to three overlapping author avatars from the prepended batch plus an upward affordance, no count, floating over the feed; tap scrolls to top and dismisses. Localize any content description into `es-419` and `pt-BR` in the same commit and run the module's own lint. **Tests:** a screenshot test for the pill at 1/2/3 avatars in light + dark, and a UI test for tap → scroll-to-top → dismissed.

  **As built:** the upward affordance is `ExpandMore` rotated half a turn. The icon subset has no upward chevron, and half a turn of this one is exactly the shape the design asks for — cheaper than regenerating the icon font for a single codepoint, and the rotation is visual only, with the pill's content description carrying the meaning. The pill renders over the list in a `Box` rather than as a list item, so it stays put while the timeline scrolls under it and takes no slot that the anchor's index arithmetic would have to account for. Its visibility is `authors.isNotEmpty()` and nothing else: the pill owns no state, because what raises it is rows in the database above the reader (D20). Baselines are **not** committed — the screenshot tests were rendered locally only to confirm the facepile geometry, and the images come from the `update-baselines` bot on CI's Linux host, per the project rule.
- [x] 6.3 Map the cache's seam marker row to a feed-local UI item in the paging transform and render it between prepended entries and previously-read content, filling on scroll proximity within the `since` range (D23). The marker is a projection row, not a `FeedItemUi` variant — do not touch the shared sealed type, which `:feature:search:impl` also exhausts. **Tests:** UI test — the seam renders from a marker row and is never inferred from a position gap; scrolling within the threshold triggers exactly one fill per crossing; the seam is removed when the range is exhausted; an interrupted fill still shows a seam after process death.

  **As built:** the cache's paging element widened from `FeedItemUi` to a sealed `CachedFeedRow` — `Item(item)` or `Seam(position, exhausted)` — rather than the marker being mapped to a feed-local type in the screen's transform. The transform shape could not carry it: a `PagingData` transform cannot emit a row the element type has no case for, so a feed-local item would have meant a second parallel list and index arithmetic between the two. The sealed row keeps `:feature:search:impl`'s exhaustive `when (FeedItemUi)` untouched, which was the actual constraint, and gives the `LazyColumn` one key space (`CachedFeedRow.key`, with the seam's `"seam:<position>"` prefix) so Paging's `itemKey` still handles both. The VM's optimistic transforms pass a seam through untouched — mute-filtering it would leave exactly the bare break the row exists to prevent.

  **Proximity is composition.** A `LazyColumn` composes ahead of the viewport, so the row entering the composition *is* the reader coming within the threshold; the fill is a `LaunchedEffect` in the row, which restarts only when the row leaves and re-enters — exactly one fill per crossing, with no scroll-position threshold of our own to race Paging's. The repeat guard in the VM is in-flight-only, keyed by seam position: the row may ask again (and a tap means the same thing), but a fill that narrowed the gap without closing it leaves the same seam to be filled on the next crossing. The mutation check confirms the wiring discriminates — deleting the `LaunchedEffect` fails `reachingTheSeam_fillsItExactlyOnce` and `anExhaustedRange_leavesNoSeamBehind` and nothing else.
- [x] 6.4 At the page cap, swap the seam's affordance for jump-to-newest, routed through the destructive refresh. **Tests:** UI test — reaching the cap renders the jump affordance, and tapping it clears the partition and lands at the top. Assert no seam state renders a bare visual break.

  **As built:** one row composable with two shapes, chosen by the marker's own `exhausted` flag, which `FeedCatchUp` writes when the window filled before the range closed. Exhausted renders the capped copy plus jump-to-newest; jump-to-newest is literally the pull-to-refresh path (`items.refresh()` + the VM dropping its own state, D8) plus `scrollToItem(0)`, because "newest" has to mean the top of the replaced partition and a reader left mid-list would be looking at the middle of a timeline they just asked to start over. Both states carry text, so neither renders a bare break.
- [x] 6.5 Confirm no pill, seam, or anchor appears on Discover or custom feeds (D14). **Tests:** a UI test per surface asserting the Following-only gating.

  **As built — and it found a real gap.** The pill was already gated (the VM only subscribes to `newPostAuthors` when `feedType.supportsSince`) and the seam is gated structurally, since only `FeedCatchUp` writes one and it never runs for a feed without `since`. The **anchor was not**: `FeedScreen` hosts Discover through its chip row and passes `FeedAnchoring` at every feed, so a generator partition was storing a place and seeking the `Pager` to it. Gated in `DefaultFeedRepository.anchor` / `saveAnchor` rather than per screen — D14 is a property of the feed type, not of a surface, and every future caller would otherwise have to remember it. The repository test asserts the row is never even read for a generator key, since reading it would mean a later caller could still get a place out of it. The surface test (`FeedViewScreenFollowingOnlyInstrumentationTest`) supplies both faces and a stored anchor on purpose: a surface that wired either up would pass with them absent and fail here.

## 7. Top-of-stack gate

- [x] 7.1 `./gradlew :app:assembleDebug`, plus lint for every touched module using the correct flavored/plain task names.

  As built: eight touched modules, three of them flavored (`:core:feed-cache`,
  `:core:feeds`, `:feature:feed:impl` — checked with `git grep -l
  nubecita.android.flavors`, not inferred from the path). All clean.

- [x] 7.2 Run the compose-expert skill against the **cumulative** stack diff (`git diff origin/main...HEAD` from the top branch), not the top PR's own diff.

  As built: nine added `@Composable` lines across six files, so the gate fired.
  One critical finding, now fixed: the seam's self-fill was keyed on
  composition, and a `LazyColumn` prefetch precomposes rows the reader never
  reaches — `precompose` runs `setContent` on an *active* subcomposition, which
  is where a `LaunchedEffect` launches — so an off-screen row fired a network
  catch-up, repeatedly, every time a scroll reversal discarded and rebuilt the
  composition. Gated on placement instead; premeasure does not place.

  A second finding, that the pill's 40dp `Surface` had no reserved touch
  target, was **wrong** and withdrawn: Material3's clickable `Surface` overload
  calls `minimumInteractiveComponentSize()` itself, confirmed both in
  `Surface.kt` and by a tap probe on device (hits land through 46dp, stop at
  50dp, pill drawn 4–44dp). Nothing changed for it.

- [x] 7.3 Device pass on the plugged-in foldable, bench flavor: background/foreground several times mid-partition and confirm the position holds; drive 5+ cycles with LeakCanary for the scroll-state collector (D9 — LeakCanary does **not** watch Compose state holders, so inspect the collector's cancellation directly).

  As built, and **deliberately not as written** — neither the flavor nor the
  device in the task could exercise this feature:

  - *Not bench.* `:core:feed-cache`'s bench `FakeFeedRepository` returns `null`
    from `anchor`, an empty flow from `newPostAuthors`, and `NOT_POSSIBLE` from
    `catchUp`. A bench pass cannot see a restore, a pill, or a seam — it would
    have been a pass that measures nothing.
  - *Not the foldable.* `:app` sets no `applicationIdSuffix` (deliberately —
    `google-services.json` is keyed to the one id), so every variant shares
    `net.kikin.nubecita`. The foldable carries the owner's real signed-in
    release build; any test install replaces it and destroys that session.

  Run instead on the emulator's existing debuggable install, production flavor,
  signed in against a real timeline — which also exercised the schema migration
  over a populated database. Verified: the Room migration ran clean; the
  new-posts pill rendered with **three distinct faces**; a full `am force-stop`
  and relaunch restored the same anchor row at the same offset; tapping the
  pill scrolled to top and dismissed it; a second kill/relaunch confirmed the
  dismissal was persisted as a waterline rather than cleared in-session; six
  background/foreground cycles mid-partition left the viewport pixel-identical;
  five rotation round-trips (ten Activity recreations) showed no stale scene and
  no retained instances under LeakCanary. No crashes in logcat throughout.

  Not reachable on device: the **seam** and **jump-to-newest**, which need a gap
  wider than one catch-up page and a partition at the page cap. Both stay covered
  by `FeedScreenSeamInstrumentationTest` (7 tests) and
  `FeedSeamRowInstrumentationTest` (3), the latter reproducing the prefetch shape
  with a `Layout` that measures its child and places none of it.

- [ ] 7.4 Confirm the stack's atomic merge lands as **one** `Release` run (`gh run list --workflow=release.yaml --limit 5`).

  Pre-flight for this is ready: the stack's release type is **minor** — three
  `feat:`-titled children (#1082, #1084, #1085), and the maximum across the stack
  wins — so the epic reaches Play without relabelling. Runs after
  `gh stack merge`.
