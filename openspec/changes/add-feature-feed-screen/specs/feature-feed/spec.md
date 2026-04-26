## ADDED Requirements

### Requirement: `FeedScreen` is the canonical render-side composable for the Following timeline

The system SHALL replace the placeholder `FeedScreen` in `:feature:feed:impl` with a production composable that renders the full `FeedState` lifecycle. `FeedScreen` MUST be the only composable bound to the `Feed` Nav3 entry; the entry installer in `FeedNavigationModule` MUST resolve `FeedViewModel` via `hiltViewModel()` and pass it (or rely on the default parameter) to `FeedScreen`. No alternate composable in the project may render the Following timeline.

#### Scenario: Nav3 entry composes the production FeedScreen

- **WHEN** the `Feed` `NavKey` is on the back stack and the Nav3 entry composes
- **THEN** the entry's content SHALL invoke `FeedScreen(...)` and the composable SHALL obtain `FeedViewModel` via `hiltViewModel()` (directly or via the default parameter)

#### Scenario: No second feed-rendering composable exists

- **WHEN** the source tree is searched for `@Composable` functions that read `FeedState`
- **THEN** the only match in production code SHALL be `FeedScreen.kt`

### Requirement: Screen renders a state-shape matrix that is total over `FeedState`

The system's `FeedScreen` MUST render exactly one of the following branches based on `FeedState.loadStatus` and `FeedState.posts`. The branches MUST be exhaustive — every `(loadStatus, posts.isEmpty())` combination produced by the VM MUST map to a defined branch.

| `loadStatus`                              | `posts.isEmpty()` | Render                                                                |
|-------------------------------------------|-------------------|-----------------------------------------------------------------------|
| `InitialLoading`                          | `true`            | `LazyColumn` of `PostCardShimmer` rows (initial-load skeleton)        |
| `InitialError(error)`                     | `true`            | Full-screen `FeedErrorState(error)` with a retry button               |
| `Idle`                                    | `true`            | `FeedEmptyState`                                                      |
| `Idle` or `Refreshing`                    | `false`           | `LazyColumn` of `PostCard(state.posts[i])`                            |
| `Appending`                               | `false`           | `LazyColumn` of `PostCard(state.posts[i])` plus a tail shimmer row    |

`Refreshing` MUST NOT swap the list out — the existing posts SHALL stay visible while the `PullToRefreshBox` indicator overlays them.

#### Scenario: Empty + idle renders the empty state

- **WHEN** `FeedState.loadStatus == Idle` and `FeedState.posts.isEmpty()`
- **THEN** `FeedScreen` SHALL render the `FeedEmptyState` composable and SHALL NOT render a `LazyColumn` of `PostCard`

#### Scenario: Initial loading renders shimmer rows

- **WHEN** `FeedState.loadStatus == InitialLoading` and `FeedState.posts.isEmpty()`
- **THEN** `FeedScreen` SHALL render a `LazyColumn` of `PostCardShimmer` rows and SHALL NOT render any `PostCard`

#### Scenario: Initial error renders the retry layout

- **WHEN** `FeedState.loadStatus is FeedLoadStatus.InitialError` and `FeedState.posts.isEmpty()`
- **THEN** `FeedScreen` SHALL render `FeedErrorState` with a retry button that dispatches `FeedEvent.Retry` on click

#### Scenario: Loaded list renders posts

- **WHEN** `FeedState.posts.isNotEmpty()` and `FeedState.loadStatus == Idle`
- **THEN** `FeedScreen` SHALL render a `LazyColumn` whose item count equals `FeedState.posts.size` and each item is a `PostCard` bound to the corresponding `PostUi`

#### Scenario: Appending shows tail shimmer

- **WHEN** `FeedState.posts.isNotEmpty()` and `FeedState.loadStatus == Appending`
- **THEN** the `LazyColumn` SHALL contain `FeedState.posts.size` `PostCard` rows followed by exactly one `PostCardShimmer` tail row

### Requirement: `LazyColumn` items use `PostUi.id` as the stable key

The system's `FeedScreen` MUST pass `key = { it.id }` to the `items` block when rendering `PostCard` rows. The list MUST also pass `contentType = { "post" }` (or an equivalent constant string) so the `LazyColumn`'s view-type dispatch fast-path applies.

#### Scenario: Item key is the PostUi id

- **WHEN** `FeedScreen` constructs its `LazyColumn`
- **THEN** the `items` invocation SHALL declare `key = { it.id }`

#### Scenario: Item content type is constant

- **WHEN** `FeedScreen` constructs its `LazyColumn`
- **THEN** the `items` invocation SHALL declare a constant `contentType` (e.g., `"post"`) for every row

### Requirement: Pull-to-refresh dispatches `FeedEvent.Refresh` and reflects `FeedLoadStatus.Refreshing`

The system's `FeedScreen` SHALL wrap its `LazyColumn` in `androidx.compose.material3.pulltorefresh.PullToRefreshBox`. The `isRefreshing` parameter MUST be bound to `state.loadStatus == FeedLoadStatus.Refreshing`. The `onRefresh` lambda MUST dispatch `FeedEvent.Refresh` exactly once per gesture release.

#### Scenario: Pull-to-refresh dispatches Refresh

- **WHEN** the user performs a pull-to-refresh gesture on the loaded feed
- **THEN** `FeedEvent.Refresh` SHALL be dispatched to `FeedViewModel.handleEvent` exactly once

#### Scenario: Refreshing status drives the indicator

- **WHEN** `FeedState.loadStatus == FeedLoadStatus.Refreshing`
- **THEN** `PullToRefreshBox.isRefreshing` SHALL evaluate to `true`

#### Scenario: Idle status hides the indicator

- **WHEN** `FeedState.loadStatus == FeedLoadStatus.Idle`
- **THEN** `PullToRefreshBox.isRefreshing` SHALL evaluate to `false`

### Requirement: Append-on-scroll triggers `FeedEvent.LoadMore` exactly once per threshold crossing

The system's `FeedScreen` SHALL dispatch `FeedEvent.LoadMore` when the last visible item index in the `LazyListState` exceeds `posts.size - 5`. The trigger MUST emit at most once per crossing of the threshold (a scroll-up-then-down that re-crosses the threshold MAY emit again only if the threshold was first un-crossed). The trigger MUST NOT emit when `state.endReached == true` or when `state.loadStatus != FeedLoadStatus.Idle`.

The implementation SHALL use `snapshotFlow` over `LazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index` with `distinctUntilChanged` so that recomposition without a layout-info change does not re-trigger the dispatch.

#### Scenario: Threshold crossing while idle dispatches LoadMore

- **WHEN** the user scrolls so that the last visible item index becomes greater than `state.posts.size - 5`, `state.endReached == false`, and `state.loadStatus == Idle`
- **THEN** `FeedEvent.LoadMore` SHALL be dispatched exactly once

#### Scenario: End-reached suppresses the trigger

- **WHEN** the user scrolls past the threshold while `state.endReached == true`
- **THEN** `FeedEvent.LoadMore` SHALL NOT be dispatched

#### Scenario: Refresh-in-flight suppresses the trigger

- **WHEN** the user scrolls past the threshold while `state.loadStatus == FeedLoadStatus.Refreshing`
- **THEN** `FeedEvent.LoadMore` SHALL NOT be dispatched

#### Scenario: Recomposition without layout change does not re-trigger

- **WHEN** the screen recomposes for any reason (state field change, parent recomposition) without a change in `LazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index`
- **THEN** `FeedEvent.LoadMore` SHALL NOT be dispatched as a side effect of that recomposition

### Requirement: `LazyListState` is hoisted via `rememberSaveable` for back-nav and config-change retention

The system's `FeedScreen` SHALL construct its `LazyListState` via `rememberSaveable(saver = LazyListState.Saver) { LazyListState() }` (or the equivalent `rememberLazyListState` overload that participates in `SaveableStateHolder`). The first-visible-item-index and offset MUST survive (a) navigation away and back to the `Feed` entry and (b) configuration change (rotation).

Process-death back-stack persistence is out of scope here — the application's back stack is currently in-memory in `DefaultNavigator`. The migration to `rememberNavBackStack`-backed storage is tracked under `nubecita-3it`; once it lands, this same `rememberSaveable` shape will round-trip through process death without further changes to `FeedScreen`.

#### Scenario: Back-nav restores scroll position

- **WHEN** the user scrolls 30 items deep, navigates away from the Feed entry to another entry, and then navigates back
- **THEN** the `LazyColumn` SHALL be scrolled to approximately the same `firstVisibleItemIndex + firstVisibleItemScrollOffset` as before navigation

#### Scenario: Configuration change preserves scroll position

- **WHEN** the user scrolls 30 items deep and the activity is recreated (e.g., rotation)
- **THEN** the `LazyColumn` SHALL be scrolled to approximately the same position as before the recreate

### Requirement: Initial load is dispatched once on first composition

The system's `FeedScreen` SHALL dispatch `FeedEvent.Load` from a `LaunchedEffect` keyed on `Unit` on first composition. Subsequent recompositions SHALL NOT re-dispatch `Load`. The screen MAY assume `FeedViewModel.handleEvent(Load)` is idempotent (already guarded VM-side).

#### Scenario: First composition dispatches Load

- **WHEN** `FeedScreen` enters composition for the first time
- **THEN** exactly one `FeedEvent.Load` SHALL be dispatched

#### Scenario: Recomposition does not re-dispatch Load

- **WHEN** the screen recomposes due to a `FeedState` update
- **THEN** no additional `FeedEvent.Load` SHALL be dispatched as a result of the recomposition

### Requirement: `FeedEffect` is collected once and surfaces snackbar + navigation

The system's `FeedScreen` SHALL collect `viewModel.uiEffect` from a single `LaunchedEffect(Unit)` for the screen's lifetime. The effect handler MUST:

- Map `FeedEffect.ShowError(error)` to a snackbar shown via the screen-internal `SnackbarHostState`. Before showing, any current snackbar MUST be dismissed so consecutive errors do not stack.
- Map `FeedEffect.NavigateToPost(post)` to an injected `onNavigateToPost: (PostUi) -> Unit` callback supplied by the Nav3 entry installer.
- Map `FeedEffect.NavigateToAuthor(authorDid)` to an injected `onNavigateToAuthor: (String) -> Unit` callback supplied by the Nav3 entry installer.

The Nav3 entry installer MAY supply `{ }` no-op callbacks until `PostDetail` and `Profile` screens exist.

#### Scenario: ShowError emits a snackbar

- **WHEN** the VM emits `FeedEffect.ShowError(FeedError.Network)`
- **THEN** the screen's `SnackbarHostState` SHALL show a snackbar whose message is the network-error string resource

#### Scenario: Successive errors replace, not stack

- **WHEN** the VM emits two `FeedEffect.ShowError(...)` effects in quick succession
- **THEN** at most one snackbar is visible at a time; the second emission SHALL dismiss the first before showing its own

#### Scenario: NavigateToPost calls the host callback

- **WHEN** the VM emits `FeedEffect.NavigateToPost(post)` and the entry installer supplies `onNavigateToPost = capture`
- **THEN** `capture` SHALL be invoked exactly once with the `post` from the effect

### Requirement: `PostCallbacks` is `remember`-d once per screen instance

The system's `FeedScreen` SHALL construct exactly one `PostCallbacks` instance via `remember(viewModel) { PostCallbacks(...) }` and pass that same instance to every `PostCard` row. Constructing a fresh `PostCallbacks` per recomposition is forbidden because it defeats Compose's stability inference and forces every `PostCard` to recompose on every parent state change.

#### Scenario: PostCallbacks identity stable across recompositions

- **WHEN** `FeedScreen` recomposes due to a `FeedState` update that does not change `viewModel`
- **THEN** the `PostCallbacks` instance passed to each `PostCard` SHALL be referentially equal to the instance passed in the previous composition

#### Scenario: Each callback dispatches the matching FeedEvent

- **WHEN** `PostCallbacks.onTap(post)` is invoked
- **THEN** `FeedEvent.OnPostTapped(post)` SHALL be dispatched to `viewModel.handleEvent`. The same correspondence SHALL hold for `onAuthorTap → OnAuthorTapped`, `onLike → OnLikeClicked`, `onRepost → OnRepostClicked`, `onReply → OnReplyClicked`, `onShare → OnShareClicked`.

### Requirement: Screen ships preview matrix, screenshot tests, and Compose UI tests

The system's `FeedScreen` SHALL ship with:

- `@Preview`s in `FeedScreen.kt` covering: empty, initial-loading,
  initial-error (per `FeedError` variant), loaded, refreshing, and
  appending — each in light + dark.
- Screenshot tests under `feature/feed/impl/src/screenshotTest/kotlin/...`
  capturing the same matrix on at least one device profile (the
  existing `:designsystem` profile).
- Compose UI tests under `feature/feed/impl/src/androidTest/kotlin/...`
  covering: pagination dispatch on threshold crossing, pull-to-refresh
  dispatch, retry click on the error layout, empty-state rendering, and
  back-nav scroll-position retention via `ActivityScenario.recreate`.

#### Scenario: Preview matrix exists

- **WHEN** the `:feature:feed:impl/src/main/kotlin` source tree is enumerated for `@Preview` annotations on functions in `FeedScreen.kt`
- **THEN** the count SHALL be at least the matrix size (empty, initial-loading, three error variants, loaded, refreshing, appending) × 2 (light + dark)

#### Scenario: Screenshot tests cover the matrix

- **WHEN** `./gradlew :feature:feed:impl:validateScreenshotTest` (or the equivalent AGP screenshot-test task) runs
- **THEN** screenshots SHALL exist for every state in the matrix in the previous scenario

#### Scenario: Compose UI test verifies pagination dispatch

- **WHEN** an instrumented test scrolls a `FeedScreen` populated with 25 items past the last-5-from-tail threshold while `loadStatus == Idle`
- **THEN** the test SHALL observe exactly one `FeedEvent.LoadMore` dispatched to a recording test double of `FeedViewModel`

#### Scenario: Compose UI test verifies retry dispatch

- **WHEN** an instrumented test renders `FeedScreen` with `loadStatus = InitialError(FeedError.Network)` and clicks the retry button
- **THEN** the test SHALL observe exactly one `FeedEvent.Retry` dispatched

#### Scenario: Compose UI test verifies scroll retention across recreate

- **WHEN** an instrumented test scrolls 30 items deep, calls `ActivityScenario.recreate`, and re-queries the `LazyColumn`'s `firstVisibleItemIndex`
- **THEN** the index after recreate SHALL be approximately equal to the index before (within ±2 to account for layout-info quantization)

#### Scenario: Compose UI test verifies scroll retention across back-nav

- **WHEN** an instrumented test builds a Nav3 graph with `Feed` and a stub `Detail` entry (using the same `rememberSaveableStateHolderNavEntryDecorator` + `rememberViewModelStoreNavEntryDecorator` decorators as `:app`), scrolls Feed 30 items deep, pushes `Detail`, then pops back to `Feed`
- **THEN** Feed's `LazyColumn` `firstVisibleItemIndex` after pop SHALL be approximately equal to the index before push (within ±2)

### Requirement: `FeedEmptyState` and `FeedErrorState` colocate in `:feature:feed:impl`

The system SHALL place `FeedEmptyState` and `FeedErrorState` composables under `feature/feed/impl/src/main/kotlin/.../ui/`. Neither composable MAY be exposed from `:designsystem` or any `:core:*` module in this change. Both MUST be `internal` to `:feature:feed:impl`. Promotion to `:designsystem` requires a follow-on change once a second screen needs the same shape.

#### Scenario: Empty-state composable is internal

- **WHEN** the source for `FeedEmptyState` is inspected
- **THEN** the function visibility SHALL be `internal` and the file SHALL live under `feature/feed/impl/src/main/kotlin/`

#### Scenario: No designsystem exposure

- **WHEN** `:designsystem/src/main/kotlin/` is searched for `FeedEmptyState` or `FeedErrorState`
- **THEN** there SHALL be no match
