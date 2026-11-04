## MODIFIED Requirements

### Requirement: `FeedRepository` is the only layer that calls `FeedService` directly

The Following timeline SHALL be read from `:core:feed-cache` as `Flow<PagingData<FeedItemUi>>`, collected in the screen via `collectAsLazyPagingItems`. `:feature:feed:impl` SHALL NOT declare its own feed repository, its own `FeedService` call site, or its own network pagination cursor; `internal interface FeedRepository`, `DefaultFeedRepository` and `TimelinePage` are removed from the module.

`FeedViewModel` SHALL remain the canonical presenter and SHALL keep `FeedState` for every non-paged field. This is the dual-flow pattern: `PagingData` alongside `UiState`, never remote data wrapped in a sum type inside `UiState`.

#### Scenario: VM injects the interface

- **WHEN** `FeedViewModel`'s constructor is inspected
- **THEN** it MUST declare the `:core:feed-cache` repository **interface** type, and MUST NOT declare a concrete implementation or `FeedService`

#### Scenario: Single import of FeedService

- **WHEN** `:feature:feed:impl` production source is grepped for `import io.github.kikin81.atproto.app.bsky.feed.FeedService`
- **THEN** there SHALL be no match — the only remaining call site in the app is inside `:core:feed-cache`

#### Scenario: The screen collects paged items

- **WHEN** `FeedScreen` renders the loaded timeline
- **THEN** it SHALL consume `LazyPagingItems<FeedItemUi>` collected from the cache-backed flow

#### Scenario: Non-paged state stays flat on FeedState

- **WHEN** `FeedState` is inspected
- **THEN** every field other than the paged items SHALL be a concrete UI-ready value or a per-screen sealed status, and no field SHALL wrap remote data in a generic `Async`/`Result` type

### Requirement: `LazyColumn` items use `PostUi.id` as the stable key

The feed list MUST pass a stable, variant-namespaced `key` for every row, and a `contentType` so the view-type dispatch fast-path applies.

Keys SHALL be: `Single` → the post URI; `ReplyCluster` → `"cluster:"` + the **leaf's** URI; `SelfThreadChain` → `"chain:"` + the **first** post's URI; `Blocked` → `"blocked:"` + URI; `NotFound` → `"notfound:"` + URI.

A chain anchors on its first post, not its leaf, because a chain can gain a post between sessions — changing its leaf, its key, and stranding a persisted anchor. A cluster is one reply with its ancestors as context, so its leaf does not move that way, and it SHALL NOT be keyed on its root: two replies into one thread are two clusters sharing a root, which this change's de-duplication scope explicitly allows to coexist across a prepend seam. The namespace is mandatory: a duplicate `LazyColumn` key is a crash.

Every variant SHALL also expose the post it occupies a timeline slot for, separately from its key. Deletion-removal and the one-item-per-post pass SHALL read that, not the key: the two held the same value before namespacing, so a caller reading the key would keep compiling and silently stop matching.

#### Scenario: Two replies into one thread do not collide

- **WHEN** a catch-up prepends a newer reply into a thread whose older reply is still rendered below the seam
- **THEN** the two items' keys differ, and the list renders without a duplicate-key failure

#### Scenario: Deleting a grouped item's leaf removes it

- **WHEN** the post a `SelfThreadChain` occupies a timeline slot for is deleted
- **THEN** the item leaves the list, while deleting a post shown only as that item's context leaves it standing

#### Scenario: Item key is the PostUi id

- **WHEN** the feed list renders a `FeedItemUi.Single`
- **THEN** its key SHALL be the post's URI, unchanged from before this change

#### Scenario: Item content type is constant

- **WHEN** the feed list is constructed
- **THEN** each row SHALL declare a `contentType` constant for its variant

#### Scenario: A grouped key is stable when the chain is extended

- **WHEN** a self-thread chain gains a newer reply between two sessions
- **THEN** its key is unchanged, and a persisted anchor pointing at that chain still resolves

#### Scenario: A chain and a standalone post sharing a root do not collide

- **WHEN** the list contains a `SelfThreadChain` whose first post is P and a `Single` for P
- **THEN** their keys differ, and the list renders without a duplicate-key failure

### Requirement: Pull-to-refresh dispatches `FeedEvent.Refresh` and reflects `FeedLoadStatus.Refreshing`

`FeedScreen` SHALL keep its `PullToRefreshBox`, with `isRefreshing` bound to the refreshing status and `onRefresh` dispatching `FeedEvent.Refresh` exactly once per gesture release.

`FeedEvent.Refresh` SHALL mean **start fresh**: it clears the partition, discards the persisted anchor, and loads the newest page from a null cursor. It is the only reader-reachable destructive path. Launch and foreground resume SHALL NOT dispatch it.

#### Scenario: Pull-to-refresh dispatches Refresh

- **WHEN** the user performs a pull-to-refresh gesture on the loaded feed
- **THEN** `FeedEvent.Refresh` SHALL be dispatched to `FeedViewModel.handleEvent` exactly once

#### Scenario: Refreshing status drives the indicator

- **WHEN** the feed's load status is refreshing
- **THEN** `PullToRefreshBox.isRefreshing` SHALL evaluate to `true`

#### Scenario: Idle status hides the indicator

- **WHEN** the feed's load status is idle
- **THEN** `PullToRefreshBox.isRefreshing` SHALL evaluate to `false`

#### Scenario: Refresh discards the saved position

- **WHEN** `FeedEvent.Refresh` completes
- **THEN** the partition holds only the newest page, the anchor row for that partition is cleared, and the list is at the top

#### Scenario: Launch does not refresh destructively

- **WHEN** the app is launched with a warm partition and a saved anchor
- **THEN** no destructive refresh occurs and the anchor is restored

### Requirement: The feed renders at most one item per thread root within a session

The feed SHALL render at most one item per thread root, retaining the first occurrence in order. Root ID is derived as: `ReplyCluster` uses root ID, `SelfThreadChain` uses first post ID, `Single` uses post ID, and tombstones have no root (never dropped). Reposts are never dropped. De-duplication is a pure function over `List<FeedItemUi>` applied before cluster-context de-duplication.

The scope is now the **written page plus the partition below the insertion point**, since the projection is persisted rather than rebuilt per session. A page written by APPEND therefore still de-duplicates against everything already above it, exactly as the accumulated in-memory list did. An entry written by a PREPEND is **exempt** from roots below the seam: a newer reply into a thread the reader has already scrolled past is a new event above their position.

#### Scenario: Two replies to the same thread arrive in one page

- **WHEN** a page contains two `ReplyCluster` items whose `root` is the same post
- **THEN** only the first SHALL be rendered and the second SHALL be dropped

#### Scenario: Replies to the same thread span two pages

- **WHEN** a reply into thread R is rendered from page 1 and a second reply into thread R arrives in an appended page 2
- **THEN** the page-2 item SHALL be dropped, because it is older than the item already shown

#### Scenario: A standalone post reserves its own thread root

- **WHEN** a `Single` for post P is rendered and a later item is a `ReplyCluster` whose root is P
- **THEN** the later cluster SHALL be dropped

#### Scenario: A repost is never dropped by thread-root de-duplication

- **WHEN** an item whose leaf carries a repost attribution shares a thread root with an item already rendered
- **THEN** the reposted item SHALL still be rendered

#### Scenario: Refresh resurfaces the newest reply in a thread

- **WHEN** the viewer refreshes the feed and a newer reply into an already-seen thread has since been posted
- **THEN** that newer reply SHALL be rendered, because the partition is rebuilt and no seen-root state survives a destructive refresh

#### Scenario: A post rendered only as a dropped cluster's parent is not lost

- **WHEN** the list contains a cluster rooted at R, a second cluster rooted at R whose parent is post L, and a `Single` for L
- **THEN** the second cluster SHALL be dropped for reusing root R, and the `Single` for L SHALL still be rendered, because nothing else renders L

#### Scenario: Tombstones are never de-duplicated

- **WHEN** the list contains `Blocked` or `NotFound` items
- **THEN** they SHALL be retained regardless of any thread root

#### Scenario: Prepend resurfaces the newest reply in a thread

- **WHEN** a prepend brings a newer reply into a thread whose root already appears below the seam
- **THEN** the prepended reply SHALL be rendered, and the earlier occurrence SHALL remain where the reader already read it

## ADDED Requirements

### Requirement: The Following feed restores the reader's position on launch

The system SHALL record the anchored entry (URI + offset within the row) as the reader scrolls, and SHALL restore it when the Following feed is next composed with a warm partition. Restore SHALL complete before the first frame the reader sees, with no interstitial load state when the partition is warm.

#### Scenario: A warm partition renders at the saved position with no spinner

- **WHEN** the feed is composed with a warm partition and a resolvable anchor
- **THEN** the first frame shows the anchored entry at its saved offset, and no initial-loading state is rendered

#### Scenario: A cold partition behaves as it does today

- **WHEN** the feed is composed with an empty partition
- **THEN** the initial-loading state is rendered and the newest page is fetched

### Requirement: Anchor writes stay off the scroll frame and outlive nothing

The anchor SHALL be written off the scroll frame — debounced, or snapshot on lifecycle — never once per scroll event, because a Room write per frame is both a battery cost and a jank source on the 120hz path.

The collector that observes scroll state SHALL be cancelled with the composition that launched it, and the ViewModel SHALL NOT retain a reference to `LazyListState`.

#### Scenario: Anchor writes do not run per frame

- **WHEN** the reader scrolls continuously
- **THEN** the number of anchor writes SHALL be bounded by the debounce or lifecycle policy, not by the number of scroll events

#### Scenario: The scroll collector dies with its composition

- **WHEN** the feed screen leaves composition
- **THEN** the scroll-observing collector is cancelled and no reference to `LazyListState` survives on the ViewModel

### Requirement: New entries prepend above the reader's position without moving the viewport

On launch, foreground resume, and tab re-tap, the system SHALL prepend newly fetched entries above the reader's position. The viewport SHALL NOT move: the anchored row stays at its offset while rows are inserted above it.

#### Scenario: Prepending does not scroll the list

- **WHEN** N new entries are prepended while the reader is anchored mid-partition
- **THEN** the anchored entry remains at the same offset and the reader's visible content is unchanged

#### Scenario: Tab re-tap still scrolls to top

- **WHEN** the reader re-taps the Feed tab
- **THEN** the list scrolls to top per the existing tab re-tap convention, independently of any prepend

### Requirement: A new-posts pill surfaces prepended entries and scrolls to top on tap

When undismissed projected entries exist above the reader's anchor, the system SHALL render a floating pill over the feed showing up to three overlapping author avatars drawn from those entries, plus an upward affordance. The pill SHALL NOT display a count.

Tapping the pill SHALL scroll the list to the top and dismiss it. Ignoring it SHALL have no effect on the reader's position or on fetching.

#### Scenario: The pill appears whenever entries sit above the anchor

- **WHEN** the partition holds undismissed projected entries above the reader's anchor
- **THEN** the pill is shown with avatars drawn from those entries

#### Scenario: Tapping the pill scrolls to top and dismisses it

- **WHEN** the reader taps the pill
- **THEN** the list scrolls to the top and the pill is no longer rendered

#### Scenario: No pill when nothing is new

- **WHEN** a prepend returns an exhausted range with no new entries
- **THEN** no pill is shown

#### Scenario: The pill does not appear on a feed without `since`

- **WHEN** the active feed is a generator or custom feed
- **THEN** no pill is rendered, because no bounded prepend is possible

### Requirement: Pill visibility is derived from the partition, not from an in-session fetch

Pill visibility SHALL be derived from the partition's contents relative to the anchor, and its dismissal SHALL be persisted.

The background worker prepends while the app is backgrounded, which is the main case a reader encounters, so a pill driven by an in-session prepend result would leave those writes silent and show the reader nothing on return.

#### Scenario: A background prepend raises the pill on return

- **WHEN** the background worker prepends entries while the app is backgrounded, and the reader then reopens the feed
- **THEN** the pill is shown, because visibility is derived from the partition

#### Scenario: A dismissed pill stays dismissed across process death

- **WHEN** the reader dismisses the pill, and the process is killed and the feed reopened with no further prepend
- **THEN** the pill is not shown again

### Requirement: An incomplete catch-up renders a seam that fills on scroll

When more entries exist in the `since` range than the prepend fetched, the system SHALL render a seam between the prepended entries and the reader's previously-read content. Scrolling toward the seam SHALL fetch the next page within the range.

A seam SHALL NEVER be a bare visual break: it always carries either a fill indication or the jump-to-newest affordance.

#### Scenario: Scrolling toward the seam fills it

- **WHEN** the reader scrolls within the fetch threshold of the seam and the range is unexhausted
- **THEN** the next page within the range is fetched and inserted, and the seam moves accordingly

#### Scenario: The seam disappears when the range is exhausted

- **WHEN** a fill returns a cursor equal to the `since` value
- **THEN** the seam is removed and the timeline is continuous

#### Scenario: A seam always carries an affordance

- **WHEN** a seam is present for any reason
- **THEN** it renders either a fill-in-progress indication or the jump-to-newest affordance

#### Scenario: The seam is rendered from a paged marker row

- **WHEN** the feed's paging transform encounters the cache's seam marker row
- **THEN** it maps that row to a feed-local seam item and renders it in list order — the seam is never inferred from a gap in positions, which a keyset source would skip

### Requirement: Reaching the catch-up budget offers jump-to-newest

When the configured budget is reached with the range still unexhausted, the seam SHALL offer **jump to newest**, which performs the destructive refresh rather than another fill.

#### Scenario: The budget offers jump-to-newest rather than another fill

- **WHEN** the catch-up budget is reached with the range unexhausted
- **THEN** the seam renders a jump-to-newest affordance

#### Scenario: Jumping to newest starts a fresh timeline

- **WHEN** the reader taps jump-to-newest
- **THEN** the destructive refresh runs and the reader lands at the top of a fresh timeline

### Requirement: Optimistic list mutations survive the migration to `PagingData`

The system SHALL retain the three in-place list mutations the feed performs today — the optimistic mute-hide with rollback, the `PostInteractionsCache` overlay, and the outgoing-post projection — expressed as `PagingData` transforms fed by their external flows and cached so the transform is not re-run per recomposition.

The deletion check in the interaction overlay SHALL resolve the leaf post URI explicitly rather than reading `item.key`, which this change repoints at a group's first post.

#### Scenario: An optimistic mute still hides the post immediately

- **WHEN** the reader mutes an author from a post's overflow menu
- **THEN** the post is removed from the list before the network call completes, and is restored if the call fails

#### Scenario: An interaction overlay still applies to paged items

- **WHEN** a like or repost is recorded in the interactions cache
- **THEN** the corresponding paged item reflects it without a refetch

#### Scenario: Deleting a chain's leaf removes the chain

- **WHEN** the leaf post of a `SelfThreadChain` is deleted
- **THEN** the item is removed from the list, even though the item's key is derived from the chain's first post
