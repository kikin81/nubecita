## MODIFIED Requirements

### Requirement: `FeedRepository` is the only layer that calls `FeedService` directly

The system SHALL expose an `internal interface FeedRepository` in `:feature:feed:impl` fetching pages for supported feed kinds, each returning `Result<TimelinePage>`:
- `getTimeline`: Following timeline (`app.bsky.feed.getTimeline`).
- `getFeed`: Generator/custom feed (`app.bsky.feed.getFeed`).
- `getListFeed`: List feed (`app.bsky.feed.getListFeed`).
All responses MUST flow through `toFeedItemsUi()` mapping. `DefaultFeedRepository` MUST be the sole class in `:feature:feed:impl` importing `FeedService`. `FeedViewModel` MUST inject the interface.

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

The system SHALL host the main Feed via `FeedHost` backed by `FeedHostViewModel` owning chip list and selection (`feedChips`, `pinnedLists`, `selectedFeedUri`, `FeedHostStatus`). `FeedHostViewModel` MUST NOT own per-feed timeline state. Each feed MUST render as a `FeedPane` keyed by `feedUri` wrapped in `SaveableStateHolder`. Switching feeds MUST restore loaded posts, cursor, and scroll position without re-fetching. At most one pane MUST be composed at a time.

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

The chip row SHALL render pinned feeds as individual `FilterChip`s in pinned order and collapse pinned lists into a single disclosure chip when ≥1 list is pinned. The disclosure chip MUST open a `ModalBottomSheet` single-select radio list of pinned lists; selecting a list MUST make it the active feed and relabel the chip. Exactly one feed is selected at a time. A selected chip MUST keep its avatar visible and MUST NOT swap the leading slot to the default checkmark.

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

The chip row SHALL hide on downward scroll and reveal on upward scroll via nested scroll on the active pane's `LazyListState`, resetting to shown on feed switch. The chip row MUST be a child of the Feed's `Scaffold` content so it spans full width on compact widths and list-pane width (412dp medium / 440dp expanded) when Feed renders as the list pane of `ListDetailPaneScaffold`. The detail pane retains its own `TopAppBar`.

#### Scenario: Header hides while reading and returns

- **WHEN** the user scrolls the active feed downward and then upward
- **THEN** the chip row slides out of view on the downward scroll and slides back on the
  upward scroll

#### Scenario: List-pane width on tablet

- **WHEN** the Feed renders as the list pane at expanded width
- **THEN** the chip row spans the list-pane width (≈440dp), not the full window width
