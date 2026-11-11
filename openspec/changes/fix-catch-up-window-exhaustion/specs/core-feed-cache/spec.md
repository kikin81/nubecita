## MODIFIED Requirements

### Requirement: `since`-bounded prepend fetches only genuinely new entries

For a feed whose XRPC method accepts `since`, the system SHALL fetch newer entries by passing the partition's persisted boundary cursor as `since`, rather than paging backwards and matching URIs. The fetch SHALL be bounded by a configured number of fetched entries, never by elapsed wall-clock time.

Writes SHALL be additive: a prepend SHALL NOT clear the partition and SHALL NOT renumber existing rows.

Reaching the budget SHALL complete the catch-up rather than pause it: a full window SHALL NOT prevent any later catch-up from fetching entries newer than the newest one the partition holds.

#### Scenario: Only newer entries are fetched
- **WHEN** a prepend runs with the partition's boundary cursor as `since`
- **THEN** the response contains only entries newer than that position, and the anchored entry itself is not re-fetched

#### Scenario: Exhaustion is detected from the returned cursor
- **WHEN** a prepend response returns a cursor equal to the `since` value passed
- **THEN** the range is reported exhausted and no further prepend fetches occur for that boundary

#### Scenario: The budget stops an unbounded catch-up
- **WHEN** the configured entry budget is reached before the range is exhausted
- **THEN** fetching stops, a passive gap marker is left between the fill and the previously-read content, and the catch-up reports itself complete

#### Scenario: A full window does not stall later catch-ups
- **WHEN** a catch-up reached the budget with the range unexhausted, and a later catch-up runs on the same partition
- **THEN** the later catch-up fetches from the newest entry onward, bounded by the newest entry the earlier fill wrote, and writes its pages in a new window above everything cached

#### Scenario: A partition already stalled recovers without a refresh
- **WHEN** a catch-up runs on a partition whose persisted fill state points into a window with no free slot
- **THEN** the catch-up completes that fill with a gap, and the next catch-up fetches new entries, with no destructive refresh and the reader's anchor unchanged

### Requirement: A prepend persists its own cursor state

The system SHALL persist, per partition: the boundary `since` (the `startCursor` of the newest page written), the in-range fill cursor, the position the next fill page will be written at, and the `startCursor` returned by the newest fill page, which becomes the boundary once the fill completes.

The fill cursor and the next fill position SHALL be set together and cleared together. A fill completes either when the range is exhausted or when the budget is reached; on completion the newest fill page's `startCursor` SHALL become the boundary and the fill cursor, the next fill position and the pending boundary SHALL be cleared.

`startCursor` is a page-level response field, not a per-entry one, so the boundary cannot be derived from the anchor row. A prepend SHALL leave the **append** cursor untouched and SHALL write back its own state.

#### Scenario: A second prepend makes progress
- **WHEN** a prepend completes and a later prepend runs against the same partition
- **THEN** the later prepend reads the updated boundary and fetches only entries newer than the first prepend's newest entry

#### Scenario: An interrupted fill resumes rather than restarting
- **WHEN** a multi-page fill is interrupted by process death and the feed is reopened
- **THEN** the next fill resumes from the persisted in-range fill cursor, writing directly below the last page that landed

#### Scenario: Reaching the budget advances the boundary
- **WHEN** a fill reaches the budget with the range unexhausted
- **THEN** the boundary becomes the `startCursor` of the newest page that fill wrote, and the fill cursor, next fill position and pending boundary are cleared

#### Scenario: A completed fill with no pending boundary does not stall
- **WHEN** a fill reaches the budget and no pending boundary was recorded
- **THEN** the fill state is cleared, the boundary is left unchanged, a non-fatal is recorded, and re-fetched entries already shown are not rendered twice

#### Scenario: The append cursor is unaffected
- **WHEN** a prepend writes its cursor state
- **THEN** the partition's append cursor is unchanged and forward pagination continues from where it was

### Requirement: Prepends allocate inside a reserved negative window

Fill pages SHALL march downward from the top of a reserved negative window toward the anchor. Allocation SHALL be relative to the partition's current minimum position, never a fixed `-1`: `position` is part of the primary key and writes go through `@Upsert`, so a restart at `-1` would overwrite the previous batch rather than insert below it.

Each window SHALL reserve its bottom slot for a gap marker: fill entries SHALL stop one slot before the first occupied position below the window. The room check SHALL treat a position as occupied when either a stored entry or a projected item holds it.

Unused positions within the window SHALL be left empty; gaps are expected.

#### Scenario: Fill pages allocate downward inside the window
- **WHEN** a multi-page catch-up writes its second fill page, which is older than the first but newer than the anchor
- **THEN** it occupies the block immediately below the first page's block, nothing already written is renumbered, and no existing row is overwritten

#### Scenario: Allocation reads the current minimum
- **WHEN** a prepend runs against a partition that already holds prepended rows
- **THEN** the new rows are allocated relative to the partition's current minimum position and cannot collide with the earlier batch

#### Scenario: The bottom slot stays free for the marker
- **WHEN** a fill writes pages until its window is full
- **THEN** the slot directly above the first occupied position below the window holds no entry and no item

### Requirement: A seam is a persisted marker row

When a catch-up leaves part of the range unfetched, the system SHALL write an explicit marker row into the projection table carrying whether the range is still being filled. The marker SHALL NOT be inferred from a gap in the position sequence, which a keyset `PagingSource` skips.

There are two kinds of marker. An **active seam** marks a fill in progress; at most one exists per partition, it sits at the position the next fill page will be written at, and it is replaced by each fill page and removed when the range is exhausted. A **passive gap marker** marks where a completed fill stopped short; any number may exist, and each persists at its position until a destructive refresh or eviction removes it. Writing, moving or removing the active seam SHALL NOT remove any passive gap marker.

A marker SHALL only be written at a position that holds no projected item, and SHALL NOT replace one. If no such position is available, the marker SHALL NOT be written and a non-fatal SHALL be recorded.

The marker SHALL be a row-type discriminator on the projection table, never a new `FeedItemUi` variant — that sealed type is shared with features that have no notion of a seam.

#### Scenario: An incomplete catch-up writes a marker
- **WHEN** a prepend stops with the range unexhausted
- **THEN** an active seam marker row exists at the position the next fill page will be written at, between the prepended entries and the previously-read content

#### Scenario: The seam is paged like any other row
- **WHEN** the reader scrolls to the boundary
- **THEN** the seam row is emitted by the `PagingSource` in position order, with a stable key, rather than being inferred from a position gap

#### Scenario: Exhausting the range removes the marker
- **WHEN** a fill reports the range exhausted while the partition also holds passive gap markers from earlier fills
- **THEN** the active seam row is deleted and the timeline is continuous across that fill, and every passive gap marker from earlier fills is still present

#### Scenario: A half-filled seam survives process death
- **WHEN** a multi-page fill is interrupted and the feed is reopened
- **THEN** the active seam marker is still present with its fill state, and scrolling to it resumes the fill

#### Scenario: A second overflow keeps the first gap marked
- **WHEN** a fill reaches the budget, and a later fill also reaches the budget before the reader refreshes
- **THEN** both discontinuities carry a passive gap marker, each at its own position

#### Scenario: A marker never replaces an item
- **WHEN** any marker is written
- **THEN** no projected item is removed or replaced and no member row is left under the marker

#### Scenario: A passive marker keeps its key
- **WHEN** later fills are written above a passive gap marker
- **THEN** the marker's position and key are unchanged

## ADDED Requirements

### Requirement: Re-projection preserves seam markers

A local re-projection SHALL keep every seam marker row at its position. It SHALL delete and rewrite projected items and their members only, and SHALL project the stored entries in segments split at the marker positions, writing each segment's items within that segment so that no item is written at or beyond the marker below it.

De-duplication and chain merging during a re-projection SHALL NOT reach across a marker.

#### Scenario: A preference change keeps the gap marked
- **WHEN** the partition holds a passive gap marker and a preference change re-projects it
- **THEN** the marker is still present at its position, between the same entries as before

#### Scenario: A preference change keeps an active fill resumable
- **WHEN** a fill is in progress and a preference change re-projects the partition
- **THEN** the active seam is still present and the next fill page is written below the last page that landed, overwriting no item

#### Scenario: No item lands on a marker
- **WHEN** a re-projection writes the items of a segment
- **THEN** every item's position is above the marker that ends the segment

#### Scenario: Grouping stops at a marker
- **WHEN** the same thread root appears above and below a marker
- **THEN** each segment keeps its own item for it, as the prepend that wrote it did

### Requirement: A capped catch-up is reported distinctly

A catch-up that completes because it reached the budget SHALL report a distinct outcome from one that exhausted its range, and SHALL log an analytics event carrying the feed type and the number of entries fetched. Callers that only need to know whether to keep paging SHALL treat both outcomes alike.

#### Scenario: A capped fill is distinguishable
- **WHEN** a catch-up completes because it reached the budget
- **THEN** it reports the capped outcome, and an exhausted-range completion reports the plain caught-up outcome

#### Scenario: The cap is measured once per fill
- **WHEN** a fill reaches the budget
- **THEN** exactly one `feed_catch_up_exhausted` event is logged with the feed type and the entries fetched, and none is logged for a fill that exhausted its range

#### Scenario: Paging and the worker are unaffected by the distinction
- **WHEN** Paging's prepend or the background worker receives the capped outcome
- **THEN** each behaves as for the plain caught-up outcome: end of prepend pagination, no retry
