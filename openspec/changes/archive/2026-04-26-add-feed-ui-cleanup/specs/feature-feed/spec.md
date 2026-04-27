## ADDED Requirements

### Requirement: `FeedScreen` propagates `Scaffold` inset padding to every state branch

`FeedScreen`'s outer `Scaffold` MUST consume the `innerPadding` lambda value in **all** state branches it dispatches to: `FeedScreenViewState.InitialLoading`, `FeedScreenViewState.Empty`, `FeedScreenViewState.InitialError`, and `FeedScreenViewState.Loaded`. Branches MUST NOT silently drop the `padding` reference. Scrollable surfaces (`LazyColumn` inside `InitialLoading` and inside `LoadedFeedContent`) consume the padding via `contentPadding = padding`; non-scrollable full-screen surfaces (`FeedEmptyState`, `FeedErrorState`) consume it via the new `contentPadding` parameter on those composables (see the next requirement). The Scaffold itself MUST stay `fillMaxSize()` (or unspecified, deferring to its default) so the underlying surface continues to extend behind translucent system bars — only the content inside the inset region is repositioned.

#### Scenario: InitialLoading branch consumes padding

- **WHEN** `FeedScreenViewState.InitialLoading` is rendered inside the Scaffold lambda
- **THEN** the shimmer `LazyColumn` SHALL be passed `contentPadding = padding` and SHALL NOT add its own outer `Modifier.padding(padding)` (which would clip the scrollable surface from extending behind the system bars)

#### Scenario: Empty branch consumes padding

- **WHEN** `FeedScreenViewState.Empty` is rendered inside the Scaffold lambda
- **THEN** `FeedEmptyState` SHALL be invoked with `contentPadding = padding`

#### Scenario: InitialError branch consumes padding

- **WHEN** `FeedScreenViewState.InitialError` is rendered inside the Scaffold lambda
- **THEN** `FeedErrorState` SHALL be invoked with `contentPadding = padding`

#### Scenario: Loaded branch consumes padding

- **WHEN** `FeedScreenViewState.Loaded` is rendered inside the Scaffold lambda
- **THEN** `LoadedFeedContent` SHALL be invoked with `contentPadding = padding`, and its inner `LazyColumn` SHALL pass `contentPadding = padding` so the first/last items respect insets while the list itself extends behind the system bars

#### Scenario: Cold start on a 120Hz device renders no content under the status bar

- **WHEN** the app cold-starts on an Android 14+ device with edge-to-edge enabled and the feed loads successfully
- **THEN** the first `PostCard`'s top edge SHALL be visually below the status bar's height; the status bar area SHALL show the underlying surface color, NOT a content overlap

### Requirement: `FeedEmptyState` and `FeedErrorState` accept a `contentPadding` parameter

`FeedEmptyState` and `FeedErrorState` MUST accept an optional `contentPadding: PaddingValues = PaddingValues()` parameter. The composables apply it via `Modifier.padding(contentPadding)` to their root layout BEFORE applying any internal padding (the existing `MaterialTheme.spacing.s6` horizontal / `s8` vertical chrome). The default of `PaddingValues()` preserves backward-compatibility for previews and screenshot tests that invoke these composables directly without a host Scaffold.

#### Scenario: Default contentPadding leaves preview output unchanged

- **WHEN** `FeedEmptyState()` or `FeedErrorState(error)` is invoked without specifying `contentPadding`
- **THEN** the rendered output SHALL be byte-identical to the pre-change output — existing screenshot baselines remain valid

#### Scenario: Hosted contentPadding applies before internal chrome

- **WHEN** `FeedScreen`'s Scaffold dispatches to `FeedEmptyState(onRefresh, contentPadding = padding)` with non-zero top inset
- **THEN** the empty-state Column's top edge SHALL be inset by `padding.calculateTopPadding()`, and the existing horizontal/vertical chrome SHALL apply within that inset region

### Requirement: `LoadedFeedContent` consumes Scaffold padding without clipping the scroll surface

`LoadedFeedContent` MUST accept a `contentPadding: PaddingValues` parameter and propagate it to the inner `LazyColumn`'s `contentPadding`. The `PullToRefreshBox` and the `LazyColumn` themselves MUST stay `fillMaxSize()` (no outer `Modifier.padding(contentPadding)`) so that scroll behavior + the pull-to-refresh indicator continue to extend behind translucent system bars. The pagination snapshot-flow logic (lastVisibleIndex threshold) is unaffected — `LazyColumn`'s `visibleItemsInfo` already accounts for `contentPadding`.

#### Scenario: First item respects top inset

- **WHEN** `LoadedFeedContent` renders with `contentPadding.top` of 48dp (status bar)
- **THEN** the first `PostCard` in the list SHALL appear 48dp below the screen's top edge when the list is scrolled to position 0

#### Scenario: Last item respects bottom inset

- **WHEN** the user scrolls to the end of the loaded posts on a gesture-nav device with `contentPadding.bottom` of 24dp
- **THEN** the last `PostCard`'s bottom edge SHALL be visually above the system gesture bar

#### Scenario: Pull-to-refresh indicator anchors below the status bar

- **WHEN** the user initiates pull-to-refresh on a fully-scrolled-to-top feed
- **THEN** the spinning indicator SHALL appear at or below the status bar's bottom edge — NOT under the status bar
