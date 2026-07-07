# core-feeds-reordering

## ADDED Requirements

### Requirement: Reorder pinned feeds with cross-client merge
`PinnedFeedsRepository` SHALL expose `suspend fun reorderPinnedFeeds(orderedPinnedUris: List<String>): Result<Unit>` that persists a new pinned order to AT Proto preferences and the Room `saved_feeds` cache. To avoid clobbering changes made on another client, it SHALL re-read the current preferences at commit time and rebuild `SavedFeedsPrefV2.items` as: the pinned entries ordered per `orderedPinnedUris`, with any server-pinned URI absent from that list appended in server order (never dropped), followed by the unpinned saved entries in their prior relative order. Foreign preferences SHALL be preserved. The `FOLLOWING_FEED_URI` sentinel SHALL be mapped back to its `type="timeline"` entry rather than treated as a feed URI.

#### Scenario: Reorder persists the new order
- **WHEN** `reorderPinnedFeeds` is called with a new URI order
- **THEN** the AT Proto `SavedFeedsPrefV2.items` and the Room `saved_feeds` positions reflect that order, and foreign preferences are preserved

#### Scenario: Concurrent cross-client pin is not dropped
- **WHEN** a feed was pinned on another client after this screen loaded, so it is present in the freshly-read server prefs but absent from `orderedPinnedUris`
- **THEN** the commit appends that feed (in server order) instead of unpinning or dropping it

### Requirement: Durable commit-on-exit
The reorder commit SHALL run on an injected `@ApplicationScope` coroutine so it outlives the screen's `viewModelScope`. It SHALL be triggered on screen-exit (`ViewModel.onCleared`) and on app-background (lifecycle `ON_STOP`), and SHALL be skipped when the current order equals the last committed order (dirty check).

#### Scenario: Commit survives screen teardown
- **WHEN** the user reorders feeds and leaves the screen (pop or app-background)
- **THEN** the reorder is committed via the application scope even though the `viewModelScope` is cancelled

#### Scenario: No-op reorder is not committed
- **WHEN** the screen exits and the current order equals the last committed order
- **THEN** no `putPreferences` write is performed

### Requirement: Stomp-guard against refresh
The repository SHALL guard `reorderPinnedFeeds` and the cache-reconciliation section of `refresh()` with a single `Mutex`, and expose a `reorderPending` flag read/written inside that lock. While a reorder is pending, `refresh()` SHALL skip saved-feed reconciliation so it cannot overwrite the locally reordered state before the network write settles. Concurrent reorder commits SHALL be serialized by the same `Mutex` (last write wins).

#### Scenario: Refresh does not stomp a pending reorder
- **WHEN** a `refresh()` runs while a reorder commit is in flight
- **THEN** `refresh()` skips saved-feed reconciliation and the local reordered order is preserved until the commit completes

### Requirement: Rollback on network failure
If the `putPreferences` write fails during a reorder commit, the repository SHALL restore the prior Room `position` values (captured before the write) before clearing `reorderPending`, so the local cache never lingers ahead of the server's canonical order. This mirrors the existing optimistic rollback in `pinFeed`/`unpinFeed`.

#### Scenario: Failed commit rolls back local positions
- **WHEN** `reorderPinnedFeeds` writes new Room positions and then `putPreferences` fails
- **THEN** the Room positions are restored to their pre-commit values and `reorderPending` is cleared
