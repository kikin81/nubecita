Each numbered group is one stacked PR (bottom to top), landed together with `gh stack merge` so the fix ships as one release. Every task starts from a failing test, written against stateful fakes (one in-memory table answering the position queries), not stubbed return values — the stubbed tests are how the original stall went unnoticed.

## 1. Markers never replace an item (`:core:database`, `:core:feed-cache`)

- [ ] 1.1 Narrow `FeedItemDao.clearSeam` to active seams (`seam_exhausted` false or null); remove `FeedItemDao.seam()` if it still has no production caller, else narrow it to the active seam. Instrumented `FeedItemDaoTest`: clearing leaves a passive marker and every item in place.
- [ ] 1.2 Reserve each window's bottom slot: the room check ends one slot before the first occupied position (entry or item). Unit test in `FeedCatchUpStackedWindowsTest`: a fill run until its window is full leaves the slot above the first occupied position empty in both tables.
- [ ] 1.3 Write the active seam at the fill's next free position instead of `base + projected`. Unit test: after a page whose projection dropped rows, the seam's position holds no entry, and the next page's write replaces it.
- [ ] 1.4 Never upsert a marker over a projected item: skip the write and record a non-fatal when the target slot holds one. Unit test reproducing the device state (window abutting an occupied row at -1000): the item survives, no member row is orphaned.

## 2. Window exhaustion completes the fill (`:core:feed-cache`)

- [ ] 2.1 On a full window with the range unexhausted: promote `pending_boundary` to `boundary_since`, clear `fill_cursor`, `fill_next_position` and `pending_boundary`, write a passive gap marker in the reserved slot, return `CAUGHT_UP` — one transaction. Unit test: the next catch-up fetches with `since` = the promoted boundary and no cursor, into a new window above everything cached.
- [ ] 2.2 The recovery path: a partition persisted in today's stalled state (fill cursor set, no room) completes on the next catch-up. Unit test from the Fold's snapshot values (`fill_next_position = -1000`, exhausted seam at -1000); assert the following catch-up issues a network fetch.
- [ ] 2.3 The null-`pending_boundary` fallback: clear the fill state, keep the boundary, record a non-fatal. Unit test asserts no stall and that a re-fetched, already-rendered post is not projected twice.
- [ ] 2.4 Second overflow: two consecutive budget-capped fills leave two passive markers, each at its own position with an unchanged key. Unit test over two catch-ups.
- [ ] 2.5 Decide the open question on a distinct outcome (`CAUGHT_UP_WITH_GAP`); if adopted, map it to end-of-pagination in `FeedRemoteMediator` and the worker exactly like `CAUGHT_UP`, and log it. Update `FeedRemoteMediatorTest` accordingly.

## 3. Re-projection keeps markers (`:core:database`, `:core:feed-cache`)

- [ ] 3.1 Add a DAO delete for item rows and their members only (seams kept). Instrumented `FeedItemDaoTest`.
- [ ] 3.2 `FeedProjectionWriter.reproject`: split stored entries at marker positions, project each segment independently, write each segment's items packed from its first entry position. `FeedProjectionWriterTest`: a passive marker and an active seam both survive; every item is above the marker that ends its segment; a thread root present on both sides of a marker yields an item in each segment.
- [ ] 3.3 A fill in progress resumes correctly after a re-projection: unit test runs fill page 1, a re-projection, then fill page 2, and asserts page 2 lands below page 1 without overwriting any item.

## 4. Feed surface and verification (`:feature:feed:impl`, device)

- [ ] 4.1 Confirm the passive marker renders the capped state and never starts a fill; the active seam still fills on scroll. Add a `FeedSeamRow` screenshot fixture for a passive marker sitting between two posts if none covers it.
- [ ] 4.2 `FeedViewModelTest`: after a budget-capped catch-up, `ShowNewest` triggers a fresh catch-up (the fake records a fetch) and reopens at the top.
- [ ] 4.3 Update the KDoc that encodes the old single-seam assumption (`FeedCatchUp.writeSeam`, `FeedItemDao`, `FeedItemPagingSource`) and the `FeedRemoteKeyEntity` docs for the completion transition.
- [ ] 4.4 Device pass on the Fold, bench-free, against the saved stalled cache (`/tmp/nubedb`, or a fresh capture from a partition in the stalled state): open the feed and confirm a network fetch happens and new posts land above the gap marker; tap the pill and land on the newest post; confirm the post at the old marker position is intact; change a reply preference and confirm the marker survives. Record each against its scenario.
- [ ] 4.5 Top-of-stack gate: `:app:assembleDebug`, touched-module lint (`:core:database:lintDebug`, `:core:feed-cache:lintProductionDebug`, `:feature:feed:impl:lintProductionDebug`), bench compile, compose-expert review of the cumulative diff if any `@Composable` changed.
