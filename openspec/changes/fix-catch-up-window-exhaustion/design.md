## Context

The Following feed keeps the reader's place by catching up *above* the cached head instead of replacing it (`maintain-feed-position`, archived 2026-11-04). A catch-up passes the partition's boundary cursor as `since`, fetches newest-first in pages of up to 50, and writes each page into a **reserved window** of `CATCH_UP_ENTRY_BUDGET` (= `DEFAULT_CAP / 2` = 250) slots allocated below the partition's minimum position. A fill that needs more than one page persists `fill_cursor` + `fill_next_position` (+ `pending_boundary`, the newest page's `startCursor`) and resumes on the next call; when the server hands back `since`, the range is exhausted, `pending_boundary` is promoted to `boundary_since`, and the fill state is cleared (`nubecita-lrnv.1`, PR #1152).

When the window fills **before** the range is exhausted, `FeedCatchUp` today:

1. writes an exhausted seam at `nextFree` — which, by construction, is the first *occupied* position;
2. returns `CAUGHT_UP`;
3. leaves `fill_cursor`, `fill_next_position` and `pending_boundary` set, and `boundary_since` unpromoted.

Every later catch-up resumes at `fill_next_position`, finds zero room, rewrites the marker, and returns `CAUGHT_UP` without a network call. Ingestion is stalled until a destructive refresh. Device state on the Fold after 1.375.1: `fill_cursor = 19:32`, `fill_next_position = -1000`, exhausted seam at -1000 — and the post that had been at -1000 replaced by that seam, its `feed_item_member` row orphaned.

Two further facts shape the design:

- **Seam clearing is global.** `FeedItemDao.clearSeam` deletes every seam row in the partition; `FeedCatchUp` calls it on every fill page (`writeSeam` clears before writing) and on range exhaustion. The single-seam assumption is also baked into `FeedItemDao.seam()` ("At most one exists").
- **Re-projection drops markers and repacks.** `FeedProjectionWriter.reproject` clears the whole `feed_item` partition and writes every item contiguously from the lowest entry position (D27). Seam rows are lost on every preference change, and item positions stop corresponding to entry positions — the root cause of the overwrite folded into `nubecita-lrnv.2`.

Constraint from the live spec (`feature-feed`, "An incomplete catch-up renders a seam that fills on scroll"): *a seam SHALL NEVER be a bare visual break.*

## Goals / Non-Goals

**Goals:**

- A full window never stalls ingestion: the next catch-up fetches whatever arrived after the newest post the previous fill wrote.
- Every discontinuity in the timeline carries a marker, including after a second overflow and after a re-projection.
- No marker write ever replaces an item.
- The reader's place, item keys and the viewport are unaffected by any of the above.
- No schema change.

**Non-Goals:**

- Filling a passive gap on demand (needs a per-gap cursor column; deferred).
- Changing the budget or retention cap.
- Any automatic destructive refresh.
- Generator feeds (no `since`; refresh on open).

## Decisions

### D1 — Window exhaustion completes the fill with a gap

When the room check finds no space and the range is not exhausted, the catch-up performs one transaction:

- `boundary_since := pending_boundary` — the `startCursor` of the newest page this fill wrote, i.e. the newest post now cached;
- `fill_cursor := null`, `fill_next_position := null`, `pending_boundary := null`;
- a **passive gap marker** is written (D3, D4);

and returns `CAUGHT_UP`. The next catch-up is a *fresh* fill: no fill cursor, `since` = the promoted boundary, a new window allocated below the current minimum. It fetches only entries newer than the newest one already cached.

The transition is also the **recovery path** for partitions already stuck in today's state (fill cursor set, no room): the first catch-up after the upgrade takes it, so the Fold and any affected reader recover without a refresh or a migration.

*Alternatives:* leave the fill paused and add a separate "start fresh above" branch — two pieces of fill state to reconcile, and the stuck rows would need a migration; drop the unfetched range by resetting `boundary_since` to the server's newest — loses the guarantee that a fresh fill only fetches what is genuinely new.

**Fallback when `pending_boundary` is null.** For `getTimeline` a non-empty page always carries a cursor, so this should be unreachable; it is still handled, because a null here would otherwise restart the stall. The fill state is cleared and `boundary_since` is left as is, and a non-fatal is recorded. The next fill re-walks the range from the newest; the prepend projection already de-duplicates against every rendered leaf (`dedupeByLeafPost(visibleFromAbove = …)`), so re-fetched posts are stored again but not shown twice.

### D2 — Only the fill being completed is affected

Completion clears only the prepend state (`updatePrependCursors`), never `next_cursor` — the same targeted write the exhaustion path already uses. The append direction and the reader's anchor are untouched.

### D3 — Two kinds of marker: one active seam, any number of passive gaps

The existing `seam_exhausted` flag already distinguishes them; it becomes load-bearing:

| | Active seam (`seam_exhausted = false`) | Passive gap marker (`seam_exhausted = true`) |
|---|---|---|
| Meaning | A fill is in progress here | A completed fill stopped short here |
| Count | At most one per partition | Any number |
| UI | Fills on scroll (existing) | "There is more here than we keep" + Jump to newest (existing capped rendering) |
| Moves | Down by one page per fill page | Never |
| Removed by | The next fill page (replaced), range exhaustion | Destructive refresh, eviction |

`clearSeam` is narrowed to **active** seams. A fill page, range exhaustion and D1 completion therefore never touch earlier gap markers. `FeedItemDao.seam()` ("at most one") is either removed (no production caller) or narrowed to the active seam.

*Alternative considered:* keep a single marker and let a second overflow orphan the older hole. Rejected because it violates the live "never a bare visual break" requirement; the two-kind split costs no schema change, only a narrower `WHERE`.

### D4 — Markers occupy slots that hold no entry

A marker is a `feed_item` row keyed on position; `@Upsert` on an occupied position replaces the item. Both markers therefore get positions that can never hold an entry or an item:

- **Passive gap marker** — each window reserves its **bottom slot**. The room check treats the window as ending one slot before the first occupied position (`room = first - 1 - nextFree`), so entries stop one short, and on exhaustion the marker takes that slot. A fresh window allocated directly below an older one keeps the same reservation, so stacked overflows each get their own slot.
- **Active seam** — written at `fill_next_position`, the slot the next fill page will start at, instead of `base + projected`. That slot holds no entry yet (a fill page's entries occupy `base … base + n - 1`, so `base + projected` can coincide with an entry when the projection dropped rows). The next page's first item overwrites it in the same transaction that writes the new seam.

If no free slot exists — a partition already stuck in today's state, where the full window abuts an occupied row — the marker is **not** written over the occupant. An existing passive marker at that position is kept; otherwise the gap is left unmarked and a non-fatal is recorded. This is legacy-only: after this change every window keeps its reserved slot.

Marker writes never leave member rows behind: the slot is free, so there are none, and the D1 transaction asserts this in tests.

### D5 — Re-projection preserves markers by projecting in segments

`reproject` changes in two ways:

1. It deletes **item rows and their members only**; seam rows survive.
2. It splits the stored entries at the marker positions and projects each **segment** independently, writing its items packed from the segment's first entry position.

A segment's items are never more than its entries, and its entries all sit strictly above the next marker (markers occupy entry-free slots, D4), so packed items cannot reach the marker below. Projecting per segment also keeps D7's rule that de-duplication and chain merging never reach across a seam — a reply that arrived after the gap is a new event, as for any prepend.

*Alternative considered:* write each item at its first member's entry position, making `feed_item` and `feed_post` occupancy identical everywhere. Stronger, but it ties correctness to the projection preserving entry order exactly (clusters, chains, reposts of the same post), which nothing currently asserts. Segments get marker preservation with no ordering assumption; the stronger invariant can follow if the `nubecita-lrnv.2` guard ever proves insufficient.

### D6 — Keys and the viewport

- Item keys are content-derived (D12/D28 of `maintain-feed-position`) and do not change with position, so neither the new fill above a gap nor a segmented re-projection can move the item the reader is looking at; `LazyListState` keeps the first visible key in place.
- A passive marker's key is `seam:<position>` and its position never changes, so it is stable for its whole life.
- The active seam's key changes as it moves down a page at a time; that is today's behaviour and it sits below the newly written page, not under the reader.
- New fills are allocated below the partition minimum, i.e. above everything cached — the existing "prepend above the reader without moving the viewport" requirement covers them unchanged.
- Markers are already excluded from the saved anchor (the screen skips `seam:` keys) and from the pill's faces (they have no leaf post); nothing changes there.

## Risks / Trade-offs

- [Gap markers accumulate for a reader who overflows repeatedly] → each is one row; they are evicted with their neighbourhood by `trimToCap`, whose anchor-aware ceiling already bounds growth. Jump-to-newest on any of them clears all.
- [The unfetched middle of an overflowed range is lost for good] → accepted by the decision for option A; the marker says so. On-demand gap fill is the deferred follow-up.
- [`pending_boundary` unexpectedly null] → D1 fallback: no stall, possibly duplicate stored entries that the prepend de-duplication keeps off screen; a non-fatal makes it visible.
- [A legacy stuck partition has no free marker slot] → recovered by D1 anyway; the gap may be unmarked once. Non-fatal recorded.
- [Segmented re-projection changes de-duplication across a gap] → today a re-projection de-duplicates across the whole partition, so a thread root present both above and below a gap collapses to one item; after this change both survive. That matches what the prepends wrote in the first place and D7's exemption; it is a visible difference only after a preference change.

## Migration Plan

No schema change and no data migration. Partitions already stalled recover on their first catch-up after the update (D1). Rollback is a plain revert: the previous build reads the same columns; a partition whose fill was completed by this build simply looks like one with a promoted boundary and a capped seam, both states the old code understands.

## Open Questions

- Should the outcome be reported distinctly (e.g. `CatchUpOutcome.CAUGHT_UP_WITH_GAP`) for analytics on how often readers overflow? Paging and the worker treat it exactly like `CAUGHT_UP`. Leaning yes — it is the only signal for whether the deferred gap-fill is worth building.
- Should the passive marker's copy change? "There is more here than we keep" is accurate; a softer "Some posts from this time aren't shown" may read better once it is no longer the only way forward. Copy only; can follow.
