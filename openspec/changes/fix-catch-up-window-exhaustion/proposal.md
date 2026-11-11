## Why

A reader who gets more than ~250 new posts between opens stops receiving new posts in the Following feed at all — not for that session, but for every session after it, until they pull to refresh or tap "jump to newest" on the gap marker, both of which throw away the place the feed exists to keep. The catch-up that fills its 250-entry window (`FeedCatchUp.CATCH_UP_ENTRY_BUDGET`) leaves its fill state behind, so every later catch-up resumes into the full window, finds no room, and returns "caught up" without fetching (bd `nubecita-2xk0`, reproduced on device after 1.375.1). A gap in the timeline is acceptable; a permanently stalled ingestion pipeline is not.

The same path has a second defect: the gap marker is upserted at the first *occupied* position, replacing the post that was there. On device the post at position -1000 vanished from the feed and its member row was left orphaned under the marker.

## What Changes

- Reaching the window's end **completes** the catch-up with a gap instead of pausing it: the boundary advances to the newest page the fill fetched, the fill state (`fill_cursor`, `fill_next_position`, `pending_boundary`) is cleared, and a **passive gap marker** is left between the fill and the older content. The next catch-up starts fresh from the newest post and fetches only what arrived since.
- Seam markers become two kinds: at most one **active seam** (a fill in progress, fillable on scroll) and any number of **passive gap markers** (unfillable, offering jump-to-newest). Completing or advancing a fill touches only the active seam; passive markers persist until a destructive refresh or eviction.
- A marker **never replaces an item**. Each window reserves its bottom slot for the gap marker, and the active seam sits at the fill's next free slot — positions that hold no entry.
- Local re-projection **keeps markers**: it writes each item at its first member's entry position instead of packing items from the partition minimum, and deletes item rows only. `feed_item` and `feed_post` occupancy then agree, so a marker slot can never be claimed by a re-projected item.
- No schema change. No new UI surface — the passive marker reuses the existing capped seam rendering ("There is more here than we keep" + Jump to newest).

## Non-goals

- Filling a passive gap on demand ("load these"). It needs a per-gap cursor (a schema change) and is deferred until readers ask; jump-to-newest remains the way to make the timeline continuous.
- Changing the 250-entry budget or the 500-row retention cap (rejected option C — it only moves the threshold and destabilises the retention bounds).
- An automatic destructive refresh on overflow (rejected option B — it punishes the reader for being away).
- Generator feeds: they have no `since` and refresh on open (D14 of `maintain-feed-position`); untouched.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `core-feed-cache`: the budget scenario now completes the catch-up with a gap and advances the boundary; the prepend's persisted state includes the next fill position and is cleared on exhaustion; window allocation reserves a marker slot; seam markers split into one active seam and passive gap markers that persist; re-projection preserves markers.
- `feature-feed`: reaching the budget no longer stops new posts from arriving — later catch-ups continue above the gap, and jump-to-newest becomes optional rather than the only way forward.

## Impact

- `:core:feed-cache` — `FeedCatchUp` (exhaustion transition, marker placement, scoped seam clearing), `FeedProjectionWriter.reproject` (entry-position item placement, seam-preserving clear).
- `:core:database` — `FeedItemDao`: seam clearing scoped to active seams, a clear-items-only query for re-projection; the "at most one seam" read (`seam()`) documented or removed. No new columns, no migration.
- `:feature:feed:impl` — no behaviour change expected beyond the existing seam rendering; verify the passive marker renders the capped state and the active seam still fills on scroll.
- Baseline: stays on MVI / Compose / Hilt / Room / Paging; no deviation.
