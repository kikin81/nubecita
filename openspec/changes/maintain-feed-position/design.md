# Design — Maintain your position (bead `nubecita-lgoo.5`)

## Intent

A reader who opens Nubecita several times a day should resume the Following timeline where they left it, see that new posts exist without being yanked to the top, and be able to reach those new posts in one tap. Nothing about that should cost more battery than the app spends today.

The deeper intent is to retire the app's second feed data path. There are two `FeedRepository` types in the repo — `:core:feed-cache`'s offline-first one, read by the widget and the Wear companion, and `:feature:feed:impl`'s package-internal network-only one, read by the screen. Position persistence is the forcing function to collapse them; shipping a third, position-only persistence layer beside both would have been faster and would have left the next feature facing the same fork.

## Goals

- Resume the Following feed at the entry the reader was last looking at, across process death, with no visible load state on a warm cache.
- Fetch only what is genuinely new, using a server-bounded range rather than paging backwards and hoping.
- Prepend new entries without moving the reader's viewport by a pixel.
- Spend less network and less resident memory than the current implementation, not more.
- Leave the Following feed readable with no network at all.

## Non-goals

See `proposal.md`. In short: Following only; no offline writes; no setting; no count on the pill; no time-based cut-off; widget and Wear surfaces untouched.

## Anti-goals

Things that would make this a failure even if the feature technically worked:

- **The list jumps.** Any viewport movement the reader did not initiate is worse than losing the position, because it reads as a bug rather than as a missing feature.
- **A duplicate `LazyColumn` key.** That is a crash, not a glitch. Any change to `FeedItemUi.key` has to be proved unique across a partition that now mixes entries written at different times.
- **New background work.** The battery budget is the binding constraint on this app (`CLAUDE.md`, and the widget worker's own consumer gate exists for exactly this reason). A position feature that adds a poll has failed.
- **A silently disjoint timeline.** A seam with no affordance is indistinguishable from the end of the feed.
- **Regressed screenshot baselines accepted as "new correct".** A committed baseline can pin a bug as correct. Every baseline change in this stack needs eyes on the image, not just a green job.

## Constraints

- `app.bsky.feed.getTimeline` supports `since` + `startCursor`; `app.bsky.feed.getFeed` does not. This asymmetry is upstream and not ours to fix.
- Room schema is at v6; `exportSchema = true`, so every bump commits `{N}.json`.
- `main` is protected. This epic lands as one `gh stack`, six children, one atomic merge, one release.
- Local `validateScreenshots` fails on macOS even on a clean `main`; CI's `screenshot` job is the authority.
- `:feature:feed:impl` is flavored — `:feature:feed:impl:lintProductionDebug`, `testProductionDebugUnitTest`, and the `bench` variant must compile.

## Decisions

### D1 — Migrate the feed onto `:core:feed-cache`; do not add a position-only store

**Rejected alternative:** a narrow Following-only snapshot (last N wire posts + cursor + anchor URI) written on backgrounding. It is smaller, needs no schema change, and does not touch Paging.

It was rejected because it is a third persistence layer. The next feature that wants durable feed data — Discover position, offline bookmarks, search history — hits the same fork again, and the two existing `FeedRepository` types become three. The migration also answers the memory question directly: Paging windowing replaces an unbounded in-memory list, so the reduction is structural rather than something to tune.

The cost is honest and is the bulk of this epic: the cache has to learn the right shape first (D2, D3).

### D2 — The persisted unit is a feed entry (`FeedViewPost`), not a post (`PostView`)

`FeedPostMappers.kt` states it plainly: "The per-feed-entry `FeedViewPost.reason` (a `ReasonRepost` overlay) is **NOT** persisted: the blob is the bare `PostView`." The entry's `reply` root/parent is dropped with it.

Those two fields are the entire input to `ReplyCluster` and `SelfThreadChain`. A widget rendering flat rows does not miss them; the app feed cannot exist without them. `postBlob` therefore holds the serialized `FeedViewPost`, and the denormalized columns gain what the grouping and the anchor need to query without deserializing.

### D3 — The `FeedItemUi` projection runs at write time, per page

`FeedItemDedupe`'s KDoc is explicit that its two passes are **not commutative** and that the ordering was settled by measuring 216 live timeline entries. The pipeline needs a whole page in hand. Paging hands a `pagingMap` one item at a time, so running the projection downstream of the `PagingSource` would break the invariant at page boundaries — the one place `feature-feed`'s existing "Page-boundary chain merge preserves chains across pagination cuts" requirement says it must hold.

So the projection moves to the cache write path, where the whole fetched page exists. This is strictly better than today, not merely relocated: the grouping currently gets re-derived on every fetch and nothing persists the decision, so a page boundary can already split a cluster. Projecting once at write time makes "at most one item per thread root" and the chain merge durable properties of the partition.

### D4 — PREPEND writes negative positions

`position` is a dense `Int` assigned `0..n` from the top of the newest page. A naive prepend would renumber every row, which rewrites the whole partition and invalidates the active `PagingSource` — defeating the entire point.

Positions may simply go negative. The primary key is `(account_did, feed_type, feed_uri, position)`, `ORDER BY position` still yields newest-first, and `maxPosition` still finds the oldest row. Prepended entries take negative positions and nothing already written moves.

A single prepend batch is the easy case. A **multi-page** catch-up is not, and the naive `-1, -2, -3…` allocation does not survive it: fill page 1 is the newest slice of the range, fill page 2 is older than page 1 but still newer than the anchor, so it has to sort between page 1's lowest position and `0` — where, if page 1 ended at `-1`, there is no integer room. Worse, the DAO writes via `@Upsert` with `position` in the primary key, so a second prepend restarting at `-1` would silently **overwrite** the first batch rather than insert below it. D17 settles the allocation.

### D5 — The catch-up range is bounded by pages fetched, never by elapsed time

A wall-clock threshold ("if you were away more than 24 hours, give up") is the wrong unit: it is a guess about volume derived from duration, and the relationship depends entirely on how many accounts the reader follows. The `since` range is already server-bounded, and `getTimeline`'s contract says the returned cursor equals the `since` value once the range is exhausted — so exhaustion is *knowable*, not estimated.

Bound the fill at a page count. On reaching it, the seam offers **jump-to-newest** rather than a "load more" that nobody taps twice (and rather than leaving the timeline disjointed, which is an anti-goal). That reuses the destructive-refresh path from D8, so the cap costs no new machinery.

**Left to the builder:** the actual page count, subject to the budget in D19. Note the two numbers are different: `PagingConfig.pageSize` is `PAGE_SIZE = 25`, but the network fetch uses `FeedNetworkSource.DEFAULT_LIMIT = 50`, and the catch-up budget is denominated in *fetched entries*, so it is the latter that matters.

### D6 — The anchor is an entry URI plus an offset, stored beside the cache

Position cannot be an index. Moderation toggles, mutes, a changed reply filter and the chain re-projection all change how many list rows the same entries become, so an index saved in one session points somewhere else in the next.

One row per partition, keyed `(account_did, feed_type, feed_uri)` exactly like the cache, holding the anchored entry's URI and its pixel offset within that row. It lives next to the cache rather than in DataStore so that sign-out purges it with the same `clearAccount` call that drops the posts — one delete, no orphan, no second lifecycle. Multi-account falls out for free because the DID is part of the key.

### D7 — Thread-root de-duplication becomes per-partition, and prepends are exempt across the seam

This is the subtle consequence of D3 and the one place the change alters existing specified behaviour.

`feature-feed` currently requires "at most one item per thread root **within a session**", and its scenario *Refresh resurfaces the newest reply in a thread* is justified by "the accumulated list is rebuilt and no seen-root state persists across refreshes". Persisting the projection makes seen-root state persist — so a newer reply into a thread the reader already saw would be dropped for reusing that root, and the thread would never resurface. That is a regression a reader would notice.

Resolution: the scope becomes **the page being written, plus the partition below the insertion point**, and a PREPEND is exempt from roots below the seam. An APPEND therefore still de-duplicates against everything already above it — exactly what the accumulated in-memory list did, so the existing "replies span two pages" and "dropped cluster's parent is not lost" scenarios hold unchanged. Only a prepend is exempt: a new reply is a new event, and the reader has already scrolled past the old occurrence. The destructive refresh (D8) rebuilds the partition and restores single-occurrence semantics wholesale.

This preserves the user-facing intent of the existing scenario while changing its mechanism, so the requirement is **modified, not merely re-specified** — `specs/feature-feed/spec.md` carries the new wording.

### D8 — Pull-to-refresh means "start fresh", and is the only destructive path

Readers use that gesture to mean two different things: "check for new stuff" and "I'm done with this, give me a clean timeline". Launch is now unambiguously the first. Pull-to-refresh takes the second: it clears the partition, drops the anchor, and starts over — the existing `FeedRefresher.refresh()` behaviour, unchanged, now reached only when the reader explicitly asks for it or the partition is cold.

Keeping one destructive path alive also means D5's jump-to-newest has somewhere to land.

### D9 — The leak surface is the scroll plumbing, not the data

Paging windowing *reduces* resident memory, so the data is not the risk. The risk is a `snapshotFlow` on `LazyListState` writing the anchor as the reader scrolls: a collector of that shape can outlive the screen that launched it.

LeakCanary will not catch it. Debug builds ship it and it watches ViewModels and Activities — **not Compose state holders**. So this needs review and a device pass, not a unit test: drive several background/foreground cycles and several tab switches, and confirm the collector is cancelled with the composition.

The anchor write must also be debounced or snapshot on lifecycle rather than written per scroll frame — a Room write per frame is both a battery cost and a jank source on the 120hz path.

### D10 — The background worker moves onto PREPEND

`FeedRefresher.refresh()` is `clearPartition` + `upsert(page 1)` in one transaction. `WidgetRefreshRunner` calls it every 15 minutes whenever a widget is placed or a watch is paired, and its guard skips only while the app is **foregrounded** — precisely inverted from when a persisted position matters. Unchanged, it deletes the reader's scrollback and the row the anchor points at.

The worker therefore refreshes through PREPEND. Its existing control flow is untouched: per-feed independence, retry only on total failure, the foreground guard, the consumer gate, and the `trimToCap` + prefetch sequencing all stay as specified in `widget-feed-refresh`. Only the write becomes additive.

### D11 — A dead anchor resolves downward

The anchored entry can be gone: deleted, its author newly muted or blocked, a moderation change, or trimmed by the cap. Restore then walks to the nearest surviving entry **below** the anchor, because the entries just under where the reader stopped are the ones they had not read. If nothing below survives, it is a fresh timeline.

### D12 — Grouped `FeedItemUi` keys anchor on the chain's first post, namespaced

`SelfThreadChain.key` is `posts.last().id` and `ReplyCluster.key` is the leaf's id, with the documented rationale that the leaf is "the post in your timeline" from the reader's perspective.

That holds within a session but not across one. A chain can gain a post between sessions, which changes its leaf, which changes its key — and the anchor pointing at the old key resolves to nothing. Keying on the chain's **first** post is stable under extension.

Keying on the bare first post id is not safe, though: a chain rooted at post P would be keyed identically to a `Single` rendering P, and a duplicate `LazyColumn` key is a crash. Today that collision is unreachable only because of the "at most one item per thread root" invariant living in a different file — and D7 just made that invariant per-page rather than global. So the key is **namespaced**: `"chain:${posts.first().id}"`, `"cluster:${root.id}"`. The namespace carries the safety locally instead of borrowing it from an invariant two modules away.

### D13 — The migration drops cached feed rows rather than rewriting blobs

`postBlob`'s *contents* change shape at v7 (bare `PostView` → `FeedViewPost`). Rewriting them in a migration would mean deserializing every row with the old serializer and re-encoding — in a migration, with no network to fill the fields that were never persisted in the first place. The `reason` and `reply` data does not exist locally to recover.

So v6 → v7 drops the cached feed rows and lets the partition refill. A cold partition is a correct, already-handled state: `FeedRemoteMediator.initialize()` returns `LAUNCH_INITIAL_REFRESH` when `maxPosition` is null. The cost is one cold start after upgrade, which is exactly what readers get today on every launch.

### D14 — Following only, because the protocol says so

`getTimeline` and `getListFeed` take `since` + `startCursor`, with the server signalling exhaustion. `getFeed` — generator feeds, i.e. Discover and every custom feed — takes only `feed`, `limit`, `cursor`. A generator feed cannot bound a catch-up range; it can only page backwards hoping for URI overlap, with no guarantee of ever meeting the cached head.

Those feeds therefore keep today's behaviour exactly. This asymmetry was explicitly accepted: readers hold a different mental model for an algorithmic feed than for a chronological one — tapping "new posts" on an algorithmic feed is *expected* to discard scroll history. When those feeds do land, the cache's existing refresh-replaces-partition behaviour is already the swap-and-discard they want, so nothing here blocks it.

## What is left to the builder

- The page cap in D5 (start 8–12, tune on device).
- The anchor-write debounce strategy in D9 — lifecycle snapshot, `debounce`, or both.
- Exactly which denormalized columns the widened entry needs at v7, driven by what the write-time projection and the anchor lookup actually query.
- Whether the `AutoMigration` resolves the v7 diff or a manual `Migration` is required (D13's row drop may force the manual path).
- The pill's precise motion and placement within the existing M3 Expressive token set, and its `surfaceContainerHigh` vs. floating-affordance treatment per `docs/design-system/surface-roles.md`.
- Stack ordering beyond the dependency edges already implied: D2 before D3 before D4, D4 before D10, and the screen migration after all of them.

## Decisions added after the builder-check review

A reviewer read the decisions above cold and reported what they would have had to guess. Three of their findings were contradictions in D3/D4/D5 as first written, not gaps; the decisions below settle them. One question survives as genuinely open — D22.

### D15 — The projection lives in its own table; raw entries stay for `head`

D3 said the projection is persisted but not in what shape, and the shape is the single largest unpriced item in the epic. Three constraints decide it:

- A `ReplyCluster`'s `root` and `parent` usually are **not** timeline entries — they come off the entry's `reply` fields — so they are posts with no position in the partition. Whatever holds the projection has to carry them.
- A `SelfThreadChain` spanning N posts is **one** rendered item. Room's generated `PagingSource` is strictly 1:1 row-to-item, so a chain cannot be produced by a `pagingMap` over `feed_post` rows.
- As first written, D3 silently broke the widget. `head(feedKey, n)` reads raw rows; applying the de-duplication passes on the write path *drops entries*, so fewer and different rows above position N means different widget content. The proposal's claim that the widget is untouched and task 2.3's "byte-identical `head`" were in direct conflict with D3.

So there are two grains, each with one owner:

- **`feed_post`** keeps today's shape and meaning: every wire entry, undeduped, positioned, trimmed, indexed by `uri` and `author_did`. It serves `head(feedKey, n)`, the by-uri interaction overlay and deep links. The widget and Wear surfaces are genuinely untouched, and task 2.3's byte-identical assertion is sound again.
- **A projected-item table** holds one row per rendered `FeedItemUi`, as a serialized blob carrying its context posts inline, positioned in display order. This is what the feed pages over and what the anchor resolves against.

The cost is two caps to manage rather than one, and a write that touches two tables in one transaction. The alternative — tagging raw rows with a group id and reassembling on read — is re-derivation at the read boundary, which is what D3 exists to avoid.

### D16 — The feed pages over a hand-written keyed `PagingSource`, not Room's generated one

This is the finding that would have shipped the anti-goal.

D4 claimed an additive write means "the active `PagingSource` is not wholesale-invalidated". That is not how Room behaves: the `InvalidationTracker` is table-level and invalidates on **any** write, renumbering or not. Paging then reloads with `getRefreshKey(state)`, which for the generated offset-based source is an **offset** — so after inserting N rows above, the same offset points N items lower. The list jumps. That is the one outcome the design calls worse than having no feature at all.

The feed therefore pages over a hand-written `PagingSource` keyed on `position`. Keyset pagination makes the refresh key a position *value*, and positions are stable by D4/D17, so a reload after invalidation lands on the same entry. This also answers the first-frame question D6 left open: anchor restore is `Pager(initialKey = <the anchored item's position>)`, read from the anchor row, so Paging's own initial load starts at the right place and no scroll correction is needed.

This is a real addition to task 2.3's scope and should be priced as such.

### D17 — Prepends allocate inside a reserved window, and the trim becomes row-count based

Fill pages march **downward** from the top of a reserved negative window toward the anchor:

- The window is `[-(budget), -1]`, where `budget` is the catch-up bound from D5 expressed in entries. It is known up front, because D5 bounds the catch-up.
- Fill page 1 (the newest slice) takes the most-negative end of the window; each subsequent fill page takes the block immediately below the previous one. Order is correct, nothing is renumbered, and nothing is overwritten.
- Allocation reads the current minimum rather than assuming `-1`, so a prepend into an already-prepended partition cannot collide.
- Unused positions at the `-1` end simply stay empty.

Gaps are therefore normal, which breaks `trimToCap` as written: `DELETE WHERE position >= (SELECT MIN(position) + :cap)` retains a *range of cap position values*, not `cap` rows, so with gaps it keeps far fewer entries than the cap. The trim is rewritten to be row-count based (retain the newest `cap` rows by ordinal, not by position arithmetic). That is a small DAO change and is strictly more robust than the arithmetic it replaces.

### D18 — A prepend persists three cursors, and "leave the cursor row intact" was wrong

The spec said to pass "the anchored entry's `startCursor`", but `startCursor` is a **page-level** response field — there is no per-entry `startCursor`, and the anchor row holds only a URI and an offset. `FeedRemoteKeyEntity` has exactly one cursor column, `next_cursor`. So a bounded catch-up needs state that does not exist yet:

- **S** — the boundary `since` for this partition: the `startCursor` of the newest page ever written. This is what a later prepend passes as `since`.
- **C** — the in-range fill cursor, advancing as each fill page lands, so an interrupted multi-page fill resumes rather than restarting.
- **S′** — the `startCursor` returned by the newest fill page, which becomes the next session's **S** once the range is exhausted.

The claim that a prepend "SHALL leave the partition's cursor row intact" was straightforwardly wrong: with nothing written back, the next prepend re-fetches the same range from a stale boundary and the fill can never progress. What it should have said is that a prepend leaves the **APPEND** cursor (`next_cursor`) alone and writes its own columns. A half-filled seam survives process death because **C** is persisted.

### D19 — The trim is anchor-aware, and the catch-up budget is denominated against the retention cap

`DEFAULT_CAP = 500` and `DEFAULT_LIMIT = 50`. A ten-page catch-up therefore writes up to ~500 entries *above* the anchor — and `trimToCap` retains the newest `cap` rows, which after such a prepend are the freshly fetched ones. The next worker run would delete the anchor and the entire unread tail, while backgrounded, which is precisely the window this feature exists to protect. The feature would have eaten itself at exactly the volume it was built for.

Two changes, together:

- The trim retains the newest `cap` rows **or** everything from the anchor upward, whichever is larger, under a hard ceiling that bounds worst-case growth. An anchor is a reader's unread backlog; the cap is a storage guard, and the guard must not delete the thing it exists to protect.
- The catch-up budget is denominated against the retention cap, not chosen independently: `budget` ≤ some fraction of `cap`, so a full catch-up cannot by itself fill the partition. This is the real constraint on D5's "left to the builder" page count.

### D20 — The pill is derived from the partition, not an in-session event

"The pill appears when a prepend writes a new entry" is not implementable as stated, because by D10 the background worker prepends while the app is **backgrounded** — the main case a reader cares about. An in-session event would mean the worker's writes raise no pill and the reader sees nothing on return.

The pill is therefore **derived**: it shows when projected items exist above the anchor, with its avatars read from those items. It survives process death for free and needs no prepend result plumbed through state. It needs one piece of storage the design did not have: a dismissal marker on the anchor row, so that tapping or scrolling past the new entries does not re-raise the pill on the next composition.

### D21 — Optimistic list mutations survive as `PagingData` transforms

Three mechanisms mutate the feed list in place today: the mute-hide-with-rollback in the `onOverflowAction` override, the `PostInteractionsCache` overlay collector, and the outgoing-post projection that combines on `uiState.map { it.feedItems }`. `PagingData` is immutable and re-derived per load, so each becomes a `PagingData` transform fed by its external flow, with a `cachedIn` so the transform is not re-run per recomposition. They are kept, not dropped — losing optimistic mute or interaction feedback would be a visible regression, and this is a known Paging pattern rather than new invention.

One specific trap: `applyInteractions` performs its delete-removal check against `item.key`, and its KDoc states this works *only* because `key` is the leaf's id. D12 moves a chain's key to its **first** post, which would make delete-of-leaf stop removing the item and delete-of-context start removing it. The deletion check moves to an explicit leaf-URI accessor rather than riding on `key`. Task 5.2 covers it.

### D22 — A preference change re-projects the partition

**Decided: re-project.** The alternatives were to invalidate the partition destructively, which throws away the reader's position to service a setting people touch rarely and so fights the whole feature, or to apply the change to future pages only, which is a visible regression against the behaviour `FeedViewPrefsFilterInstrumentationTest` pins today — you would flip "hide replies" and watch the replies already on screen stay put. Re-projecting costs a pass over the partition plus an invalidation, and it is the only option where the setting means what it says.

The projection is not a pure function of the wire page. `toFeedItemUiOrNull` takes `ModerationPrefs` and the viewer DID, and the page is additionally filtered through `shouldDisplayInFollowingFeed(feedViewPrefs, viewerDid)` *before* mapping — deliberately, "while the wire entries still carry the reply ancestry the predicate needs". All of those inputs change at runtime: adult-content prefs, mutes and blocks, and the Settings toggles `hideReplies`, `hideRepliesByUnfollowed`, `hideReposts`, `hideQuotePosts`. Today `pagedFeed` reads prefs *inside* the per-item map precisely so a toggle takes effect on the next load, and `FeedViewPrefsFilterInstrumentationTest` asserts that.

Baking the projection at write time means a toggle no longer changes what is already stored. The options are not equivalent, and they differ in what the reader sees:

1. **Re-project on change** — a re-projection pass over the partition plus a `PagingSource` invalidation. The toggle takes effect everywhere, the position is kept, and it is the most work.
2. **Invalidate destructively on change** — simple, and it throws away the reader's position, which fights the whole feature for a setting people flip rarely.
3. **Apply to future pages only** — cheapest, and a visible regression against the behaviour that instrumented test pins today: you flip "hide replies" and the replies already on screen stay.

The second half — where the filter lives — follows from that answer plus D15, so it is settled below as D23a rather than left open. The short version: prefs are an *input* to the projection, not something the cache owns, and the widget is untouched because `head()` reads raw rows.

## Decisions added after PR review

### D23 — The seam is a persisted marker row, not an absence

A reviewer pointed out a genuine hole: D17 leaves unused positions empty inside the reserved window, and a keyset `PagingSource` (`WHERE position > :key ORDER BY position`) simply returns the next existing rows. There is no row at a gap, so nothing renders. The spec requires that a seam "SHALL NEVER be a bare visual break", but nothing in the design said what *materializes* it — so the timeline would have come out visually disjoint with no fill trigger, which is one of this change's anti-goals.

The seam is therefore an explicit marker row written into the projection table at the boundary position, carrying the fill state (range exhausted or not). Three reasons to prefer it over synthesizing the gap inside the `PagingSource`:

- It gives the seam a real position and therefore a stable `LazyColumn` key, which a synthesized item would have to invent.
- Seam existence *is* state — whether the range is exhausted, and where the fill resumes — and D18 already requires a half-filled seam to survive process death. A marker row gets that for free; a synthesized one would need the state threaded in anyway.
- Gap detection by arithmetic is unreliable precisely because gaps are expected by D17. A sentinel is unambiguous.

The marker is **not** a new `FeedItemUi` variant. `FeedItemUi` lives in `:data:models` and is consumed by `:feature:search:impl` as well, so a new variant would force exhaustive-`when` changes across modules that have no notion of a seam (and would break the `bench` flavor's `when` blocks). Instead the projection table carries a row-type discriminator, and `:feature:feed:impl`'s own paging transform maps a seam row to a feed-local UI item. The blast radius stays inside the feature.

### D24 — Posts are stored once per account by URI; projection rows hold layout plus references

The same review flagged that serializing context posts inline into each projected item duplicates post data, so copies can diverge — stale display names and avatars, or a deleted post still rendering.

Part of that concern is already answered, and it is worth separating. Deletions and interaction state are applied **at read time** by `applyInteractions`, which merges by URI into `Single.post` and into a cluster's `root`, `parent` and `leaf` individually, dropping an item whose leaf is deleted. So a post the reader has interacted with or deleted does not go stale, and that is pre-existing behaviour rather than something this change introduces — `feed_post` has always stored a blob snapshot, which is what the widget already renders.

What *is* new is the duplication: a post can appear as a `Single` in one projection row and as another row's cluster context, and with inline blobs those are two independent copies.

The reviewer's proposed remedy — hydrate from `feed_post` on read — does not work here, and the reason is D15's: a cluster's root and parent are usually **not** timeline entries, so they have no row and no position in `feed_post`. Joining against it would lose exactly the posts that made inlining necessary.

So the shape is a third one: posts are stored **once per account, keyed by URI**, and both grains reference them. `feed_post` keeps its partition/position rows for order, `head` and eviction; a projection row holds the group's structural layout (variant, ordered member URIs, suppressed-reply count) plus references. Read hydrates by URI, which is the lookup the interaction overlay already performs and which `feed_post` already indexes.

Costs, stated plainly rather than buried: one more table, a join on read, and eviction becomes reference-aware — a context post must survive until no live projection row or partition row references it, which folds into the same trim pass as D19 rather than becoming its own mechanism. In exchange there is one copy of each post, a profile or label change updates every surface that references it, and non-entry context posts finally have a home that is not a duplicate.

## Decisions following from D22

### D23a — Preferences are an input to the projection, and the partition remembers which ones it was built with

Re-projecting needs the projection to be a function the cache can re-run, which needs the preferences at hand. It does **not** require the cache to own them.

`shouldDisplayInFollowingFeed` lives in `:feature:feed:impl/data/FeedReplyFilter.kt` and `FeedViewPrefs` lives in `:core:feeds`. The predicate moves to `:core:feed-mapping` — already the shared home for wire→UI mapping, already unflavored, already depended on by both sides — and the preferences are passed in as a parameter at write and re-project time. `:core:feed-cache` takes a dependency on `:core:feeds` for the value type only; it does not read preferences itself, so there is no hidden second source of truth.

The partition then stores a **preference fingerprint** alongside its rows: the prefs the projection was built with. On opening the feed, a fingerprint differing from the live one triggers a re-projection of the partition — a local pass over stored entries, not a refetch, so it costs no network and keeps the reader's place. If the anchored entry is filtered out by the new prefs, D11's resolve-downward already covers it.

Two consequences worth stating:

- **The widget stays untouched**, as promised. `head(feedKey, n)` reads raw `feed_post` rows by D15, so the Following widget is never reply-filtered and never sees the projection. The concern that moving the filter into the cache would silently filter the widget does not arise, because the filter applies to the projection and the widget does not read it.
- **The background worker projects with the persisted fingerprint**, not with live preferences it has no business reading. A background write therefore never reinterprets the feed under settings the reader has not seen applied; the app re-projects on open if the live prefs have since moved. One writer, one meaning, no flip-flop between the worker's view and the screen's.

### D23b — The filtered-empty-page top-up moves into the mediator

`DefaultFeedRepository` loops up to `MAX_TOP_UP_ROUNDS = 3` because an aggressively filtered page can come back empty and strand the caller. That loop is being deleted along with the repository, and the problem does not go away: with filtering and grouping both on the write path, a fetched page can project to **zero** items, which Paging reads as either end-of-pagination or a dead APPEND trigger — a feed that silently stops scrolling.

The top-up therefore moves into the `RemoteMediator`: when a fetched page projects to zero items and a next cursor exists, fetch again, bounded by the same round limit. It applies to **PREPEND fills too**, which the old loop never had to consider — a fill page that projects to nothing must not be mistaken for an exhausted range, since exhaustion has its own signal (the returned cursor equalling `since`, D5).

### D25 — Item membership is rows, not a delimited column

D15 said a projection row holds "ordered member URIs", which I first built as a newline-delimited TEXT column. That is wrong, and the reason is D24's eviction rule rather than tidiness.

A post must survive until nothing references it, so eviction has to ask *whether any projected item still uses this URI*. Against a delimited column that question is a chain of `LIKE` patterns with no index behind it — unanswerable at any real size, which means the reference check would have quietly degraded into "delete anything with no entry row". That is precisely how a cluster loses the root it renders: a reply's root usually has no entry row of its own.

Membership is therefore a `feed_item_member` row per `(item, ordinal, uri)`, and the eviction check is an indexed `NOT EXISTS`. `ordinal` carries the render order, and the two ends carry meaning: the first member gives the stable list key (D12), the last is the leaf. The leaf is additionally denormalized onto the item row so the interaction overlay's deletion check does not need a subquery on a hot path.

The cost is a second table and a join to read one item's members, against a reference check that actually works.

### D26 — `FeedViewPrefs` moves down to `:data:models`

Task 2.3 as planned had the reply filter land in `:core:feed-mapping` *and* `:core:feed-cache` take a dependency on `:core:feeds` for the preference type. Those two together do not work: the filter needs `FeedViewPrefs`, so `:core:feed-mapping` would need `:core:feeds`, which depends on `:core:auth`, `:core:common` and `:core:database`. A pure mapping module would transitively pull in Room, inverting the layering.

`FeedViewPrefs` is four booleans. It was sitting in a service module only because that is where its wire parsing lives. The value type moves to `:data:models` — annotated `@Immutable`, as that module requires — while `parseFeedViewPrefs`, `mergeFeedViewPrefs` and the `ReplyVisibility` projection stay in `:core:feeds` with the atproto union types they need.

`:core:feed-mapping` then needs no new dependency at all, because it already depends on `:data:models`. `:core:feed-cache` does gain `:core:feeds`, but for the preferences *repository* it reads at write time, which is a service-to-service edge rather than an inversion.

27 import sites moved with the type, across `:core:feeds`, `:feature:feed:impl` and `:feature:settings:impl`.

### D27 — Local re-projection needs the entry's wire context, which the grain split removed

D22 chose to **re-project** on a preference change rather than invalidate, specifically so the reader keeps their place. Building it exposed a conflict with D15/D24.

The projection's input is a wire `feedViewPost`: the post, plus `reason` and `reply` as open unions. After the two-grain split, `feed_post` is an envelope holding a post reference and three denormalized columns, and `cached_post` holds `postView` blobs. The wire entry is therefore **not recoverable from disk** — the `reason`'s `by` profile is reduced to a DID and a label, and the `reply` slots to two URIs. Reconstructing an approximation would feed the projection something subtly unlike what the server sent, and the grouping rules (`linksTo`, the repost exemption, the cluster shape) are exactly where that drift would show.

So two of my own decisions disagree, and the resolution is a real choice rather than a detail:

1. **Persist the entry's wire context** — a small `reason`/`reply` JSON blob on the envelope. It duplicates nothing (that data has no other home), makes faithful local re-projection possible, and keeps the reader's position on a preference change. Cost: a column, and the write path serializing it.
2. **Refetch on a preference change** — what is implemented now. Correct, cheap to build, and loses the reader's position exactly when they touched a setting, which is the thing D22 rejected.

What is implemented today is option 2, deliberately and visibly: `FeedRemoteMediator.initialize()` compares the live fingerprint against the partition's and returns `LAUNCH_INITIAL_REFRESH` on a mismatch. The preference applies correctly; the position does not survive. An absent fingerprint counts as *not* stale, so upgrading does not refetch every partition once for nothing.

Also settled here: **D23a's worker clause was unnecessary.** It said a background write should project with the persisted fingerprint rather than live preferences, to avoid reinterpreting the feed under settings the reader has not seen. But a fingerprint is a hash and cannot be projected with, and the concern does not arise: `head` — the widget and Wear read path — reads the raw entry rows, not the projection, and already combines live preferences so a toggle refreshes it immediately. No reader observes the projection while the worker writes, because the worker's foreground guard means the app is backgrounded. The worker projects with live preferences and records the fingerprint, like any other writer.
