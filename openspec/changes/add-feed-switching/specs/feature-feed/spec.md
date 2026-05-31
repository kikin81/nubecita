## MODIFIED Requirements

### Requirement: `FeedRepository` is the only layer that calls `FeedService` directly

The system SHALL expose an `internal interface FeedRepository` in `:feature:feed:impl`
that fetches a page for each supported feed kind, each method returning
`Result<TimelinePage>`:

- `suspend fun getTimeline(cursor: String?, limit: Int = TIMELINE_PAGE_LIMIT)` — the
  Following timeline (`app.bsky.feed.getTimeline`).
- `suspend fun getFeed(feedUri: String, cursor: String?, limit: Int = TIMELINE_PAGE_LIMIT)`
  — a generator/custom feed (`app.bsky.feed.getFeed`).
- `suspend fun getListFeed(listUri: String, cursor: String?, limit: Int = TIMELINE_PAGE_LIMIT)`
  — a list feed (`app.bsky.feed.getListFeed`).

All three responses are `List<FeedViewPost>` and MUST flow through the same
`toFeedItemsUi()` mapping, dedupe, and chain-merge so `TimelinePage` has one shape
regardless of kind. The `DefaultFeedRepository` implementation MUST be the only class in
`:feature:feed:impl` that imports `io.github.kikin81.atproto.app.bsky.feed.FeedService`.
`FeedViewModel` MUST inject the interface, never the concrete class. The interface and its
implementation MUST stay `internal` to `:feature:feed:impl` until a second consumer (post
detail, search) requires the same fetch surface — at that point a follow-on change
promotes them to a `:core:feed` module.

#### Scenario: VM injects the interface

- **WHEN** `FeedViewModel`'s constructor is inspected
- **THEN** it MUST declare a `private val feedRepository: FeedRepository` parameter
  (interface type) and MUST NOT declare `DefaultFeedRepository` or `FeedService`

#### Scenario: Single import of FeedService

- **WHEN** the project source is grepped for `import io.github.kikin81.atproto.app.bsky.feed.FeedService`
- **THEN** the only match in production code SHALL be `DefaultFeedRepository.kt`

#### Scenario: All kinds yield the same page shape

- **WHEN** `getTimeline`, `getFeed`, and `getListFeed` each decode a response of
  `FeedViewPost`s
- **THEN** each returns a `TimelinePage` whose `feedItems` were produced by the shared
  `toFeedItemsUi()` mapper, with no kind-specific mapping branch

## ADDED Requirements

### Requirement: `FeedViewModel` dispatches by feed kind

`FeedViewModel` SHALL accept the feed it renders as `(feedUri, kind)` bound once after
construction (e.g. a `FeedEvent.Bind(feedUri, kind)` collected in a
`LaunchedEffect(feedUri)`), and its initial load, refresh, and append paths MUST dispatch
on `kind`: `Following → getTimeline`, `Generator → getFeed(feedUri, …)`,
`List → getListFeed(feedUri, …)`. Pagination semantics (cursor advance only on a
successful append) MUST be identical across kinds.

#### Scenario: Generator feed fetches via getFeed

- **WHEN** a `FeedViewModel` is bound with `kind = Generator` and `feedUri = "at://…/feed/art"`
- **THEN** its load calls `FeedRepository.getFeed("at://…/feed/art", cursor = null, …)`
  and never calls `getTimeline`

#### Scenario: Following feed fetches via getTimeline

- **WHEN** a `FeedViewModel` is bound with `kind = Following`
- **THEN** its load calls `FeedRepository.getTimeline(cursor = null, …)`

### Requirement: The main Feed hosts a feed switcher with per-feed retention

The system SHALL host the main Feed through a `FeedHost` composable backed by a
`FeedHostViewModel : MviViewModel<FeedHostState, FeedHostEvent, FeedHostEffect>` that owns
only the chip list and the current selection (`feedChips`, `pinnedLists`,
`selectedFeedUri`, a mutually-exclusive `FeedHostStatus` of `Loading | Ready |
ErrorFallback`). `FeedHostViewModel` MUST NOT own per-feed timeline state. Each selected
feed MUST render as a `FeedPane` whose `FeedViewModel` is obtained via
`hiltViewModel(key = feedUri)` and is wrapped in a `SaveableStateHolder` via
`SaveableStateProvider(feedUri)`. Switching away from and back to a feed MUST restore its
loaded posts, pagination cursor, and scroll position without re-fetching. At most one pane
MUST be composed at a time.

#### Scenario: Switching back retains posts and scroll

- **GIVEN** the user has scrolled the Following feed and then selected another feed
- **WHEN** the user re-selects Following
- **THEN** Following's previously loaded posts and scroll position are restored with no
  network re-fetch

#### Scenario: Host holds no per-feed timeline state

- **WHEN** `FeedHostState` is inspected
- **THEN** it contains the chip list and selection only — no `feedItems`, cursor, or
  pagination fields (those live on each `FeedViewModel`)

#### Scenario: One pane composed at a time

- **WHEN** a feed is selected
- **THEN** only that feed's `FeedPane` (one `LazyColumn`) is in composition; other feeds'
  ViewModels remain retained but un-composed

### Requirement: Chips render pinned feeds; lists collapse into a disclosure chip

The chip row SHALL render each pinned feed (`Following`, `Generator`) as an individual
`FilterChip` in pinned order within a horizontally scrollable row, and SHALL collapse all
pinned lists into a single disclosure chip rendered only when ≥1 list is pinned. The
disclosure chip MUST open a `ModalBottomSheet` single-select radio list of the pinned
lists; selecting a list MUST make it the active feed and relabel the chip to its name.
Exactly one feed (or one list) is selected at a time. A selected chip MUST keep its feed
avatar/glyph visible (selection is shown via the filled container), and MUST NOT swap the
leading slot to the default selection checkmark.

#### Scenario: Lists collapse to one chip

- **WHEN** the user has pinned 2 feeds and 3 lists
- **THEN** the chip row shows 2 feed chips plus one `[ Lists ⌄ ]` disclosure chip (not 5
  individual chips)

#### Scenario: Selecting a list from the sheet

- **WHEN** the user taps the disclosure chip and selects a list in the bottom sheet
- **THEN** the sheet dismisses, that list becomes the active pane, and the disclosure chip
  relabels to `List: <name>` with selected styling

#### Scenario: Selected feed keeps its avatar

- **WHEN** a generator feed chip is selected
- **THEN** its leading slot still shows the feed avatar (selection indicated by the filled
  container), not a checkmark

### Requirement: The chip row scrolls away and is list-pane-scoped on tablet

The chip row SHALL hide on downward scroll and reveal on upward scroll, driven by a
nested-scroll connection to the active pane's `LazyListState`, and its reveal state MUST
reset to shown on feed switch. The chip row MUST be a child of the Feed's own `Scaffold`
content so it spans full width on compact widths and the list-pane width (412dp medium /
440dp expanded) when the Feed renders as the list pane of the `ListDetailPaneScaffold`. No
shell-level or full-window header is introduced; the detail pane retains PostDetail's own
`TopAppBar`.

#### Scenario: Header hides while reading and returns

- **WHEN** the user scrolls the active feed downward and then upward
- **THEN** the chip row slides out of view on the downward scroll and slides back on the
  upward scroll

#### Scenario: List-pane width on tablet

- **WHEN** the Feed renders as the list pane at expanded width
- **THEN** the chip row spans the list-pane width (≈440dp), not the full window width
