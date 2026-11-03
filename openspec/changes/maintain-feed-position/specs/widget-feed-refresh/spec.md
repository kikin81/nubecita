## MODIFIED Requirements

### Requirement: Background refresh of the widget feed cache

The system SHALL refresh the home-screen widget's feed(s) into `:core:feed-cache` from a background WorkManager worker, so the widget can show fresh content without the app being opened. The MVP SHALL refresh the signed-in account's Following and fixed Discover partitions. Work SHALL only run for a signed-in user.

The refresh SHALL be **additive** for any partition that supports a bounded prepend: the worker SHALL write newer entries above the existing rows and SHALL NOT clear the partition. The destructive clear-and-replace path SHALL be reserved for a cold partition and for a reader-initiated refresh in the app.

This is not an optimization. The worker runs on a period while the app is backgrounded — exactly the window in which a reader's persisted scroll anchor matters, and where the foreground guard does not apply. A clear-and-replace would silently delete the reader's scrollback and the row their anchor points at, between sittings.

#### Scenario: Backgrounded refresh updates the cache
- **WHEN** the worker runs while the app is backgrounded and the user is signed in, on a warm partition
- **THEN** it writes newer entries above the existing rows, leaves the existing rows and their positions intact, and then triggers a widget re-render

#### Scenario: Signed out
- **WHEN** the worker runs while the user is signed out
- **THEN** it does nothing (success no-op) and performs no fetch or cache write

#### Scenario: A reader's saved position survives a background refresh
- **WHEN** a reader backgrounds the app mid-partition with a saved anchor, and the worker then refreshes that partition
- **THEN** the anchored entry is still present and still resolvable when the reader returns

#### Scenario: A cold partition still loads from the top
- **WHEN** the worker runs against an empty partition
- **THEN** it fetches the newest page and populates the partition from position 0

#### Scenario: A partition with no bounded prepend is refreshed as before
- **WHEN** the worker refreshes a generator feed, whose XRPC method accepts no `since`
- **THEN** it uses the existing clear-and-replace refresh, because no bounded prepend is possible and no reader position is persisted for that feed

### Requirement: Refresh triggers off-scroll eviction

After refreshing a feed partition, the worker SHALL run the count-cap trim (`:core:feed-cache` `trimToCap`) off the active-scroll path to bound cache growth. The trim SHALL count rows rather than position values, and SHALL NOT delete the reader's anchor or the entries above it.

This is load-bearing: the worker trims after every successful refresh, while the app is backgrounded. A trim that retained the newest `cap` rows unconditionally would, after a large catch-up, delete the anchor and the reader's entire unread backlog in exactly the window this feature exists to protect.

#### Scenario: Trim after refresh
- **WHEN** the worker successfully refreshes a feed partition
- **THEN** it trims that partition to the retention cap (default 500 entries, off-scroll), leaving the partition's cursor intact

#### Scenario: Trim counts rows over a gapped position range
- **WHEN** the worker trims a partition whose positions contain gaps left by a bounded prepend window
- **THEN** the newest `cap` rows are retained by ordinal, not the newest `cap` position values

#### Scenario: Trim preserves a reader's unread backlog
- **WHEN** the worker trims a partition holding more entries above the anchor than the cap would retain
- **THEN** the anchor and the entries above it survive, under the hard ceiling that bounds worst-case growth
