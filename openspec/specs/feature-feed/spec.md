# feature-feed Specification

## Purpose
The Following timeline: `FeedViewModel` over `FeedRepository` and the pure `FeedViewPostMapper`, a sealed `FeedLoadStatus` state machine covering initial load, refresh and cursor-paged append, and `FeedScreen`'s render contract — thread de-duplication and self-thread chains, stable list keys and hoisted scroll state, inline video, list-detail metadata, the compose FAB, and the reply/tab-re-tap entry points.
## Requirements
### Requirement: `FeedViewModel` is the canonical entry point for the Following timeline

The system SHALL expose `net.kikin.nubecita.feature.feed.impl.FeedViewModel` as the only `ViewModel` that orchestrates `app.bsky.feed.getTimeline`. `FeedViewModel` MUST extend `MviViewModel<FeedState, FeedEvent, FeedEffect>` and MUST be `@HiltViewModel`-annotated. Every screen rendering the Following timeline (today: the placeholder; tomorrow: the real `FeedScreen` from `nubecita-1d5`) MUST consume this ViewModel via `hiltViewModel()` rather than instantiating its own.

#### Scenario: A screen consumes FeedViewModel

- **WHEN** the Nav3 entry for `Feed` composes its content
- **THEN** the composable obtains `FeedViewModel` via `hiltViewModel()` and forwards `FeedEvent`s through `viewModel::handleEvent`

#### Scenario: No second timeline ViewModel exists

- **WHEN** the source tree is searched for classes calling `FeedService.getTimeline` (transitively or directly)
- **THEN** the only call site SHALL be `DefaultFeedRepository`

### Requirement: `FeedRepository` is the only layer that calls `FeedService` directly

The system SHALL expose `internal interface FeedRepository` in `:feature:feed:impl` with `suspend fun getTimeline(cursor: String?, limit: Int = TIMELINE_PAGE_LIMIT): Result<TimelinePage>`. `DefaultFeedRepository` MUST be the only class in `:feature:feed:impl` that imports `io.github.kikin81.atproto.app.bsky.feed.FeedService`. `FeedViewModel` MUST inject the interface rather than the concrete class.

#### Scenario: VM injects the interface

- **WHEN** `FeedViewModel`'s constructor is inspected
- **THEN** it MUST declare a `private val feedRepository: FeedRepository` parameter (interface type) and MUST NOT declare `DefaultFeedRepository` or `FeedService`

#### Scenario: Single import of FeedService

- **WHEN** the project source is grepped for `import io.github.kikin81.atproto.app.bsky.feed.FeedService`
- **THEN** the only match in production code SHALL be `DefaultFeedRepository.kt`

### Requirement: `FeedViewPostMapper` is pure and total over the response shape

The system SHALL expose internal mapping functions in `:feature:feed:impl` package `data`: `FeedViewPost.toPostUiOrNull(): PostUi?`, `PostViewEmbedUnion?.toEmbedUi(): EmbedUi`, and text/facet decoders. `toPostUiOrNull` MUST return `null` without throwing when `record` JSON cannot be decoded as a valid `app.bsky.feed.post`. The mapper MUST be pure and free of I/O or Android dependencies.

#### Scenario: Spec-conforming post produces a non-null PostUi

- **WHEN** `toPostUiOrNull()` is called on a fixture `FeedViewPost` whose `post.record` is a well-formed `app.bsky.feed.post` JSON
- **THEN** the result is a non-null `PostUi` whose `text` matches the record's `text` field and whose `facets` matches the decoded facet array

#### Scenario: Malformed record returns null

- **WHEN** `toPostUiOrNull()` is called on a `FeedViewPost` whose `post.record` is missing the required `text` field or contains a type-incompatible value
- **THEN** the function returns `null` and does NOT throw

#### Scenario: Repository drops nulls

- **WHEN** `DefaultFeedRepository.getTimeline` decodes a response containing one well-formed and one malformed `FeedViewPost`
- **THEN** the returned `TimelinePage.posts` contains exactly one `PostUi` (the well-formed one) and the call returns `Result.success`

### Requirement: Pagination cursor advances only on successful append

The system SHALL preserve `FeedState.nextCursor` on append failure. After a successful `LoadMore`, the VM updates `nextCursor` to the returned cursor. On `LoadMore` failure, `nextCursor` MUST remain unchanged for retry. On a successful response with `cursor == null`, the VM MUST set `endReached = true` and treat subsequent `LoadMore` calls as no-ops.

#### Scenario: Successful append advances the cursor

- **WHEN** `FeedState.nextCursor == "page-3"` and `LoadMore` succeeds with response cursor `"page-4"`
- **THEN** `FeedState.nextCursor` becomes `"page-4"` and `endReached == false`

#### Scenario: Failed append preserves the cursor

- **WHEN** `FeedState.nextCursor == "page-3"` and `LoadMore` fails (e.g. network exception)
- **THEN** `FeedState.nextCursor` remains `"page-3"`, `loadStatus` returns to `Idle`, and a `FeedEffect.ShowError` is emitted

#### Scenario: End-of-feed disables LoadMore

- **WHEN** the most recent successful page returned `cursor == null` and the VM has set `endReached = true`
- **THEN** subsequent `LoadMore` events SHALL NOT call the repository and SHALL NOT change state

### Requirement: Embed dispatch in the mapper mirrors PostCard v1 scope

The system's `toEmbedUi` function in `:core:feed-mapping` SHALL map `PostViewEmbedUnion` exhaustively to `EmbedUi` variants: `Empty`, `Images`, `Video`, `External`, `Record`, `RecordUnavailable`, `RecordWithMedia`, or `Unsupported(typeUri)`. Wrapper construction helpers MUST be shared between top-level and `RecordWithMedia` dispatch. `:feature:feed:impl` MUST consume these shared helpers without inline duplication.

#### Scenario: Images embed maps to EmbedUi.Images

- **WHEN** `toEmbedUi` is called with a `PostViewEmbedUnion.AppBskyEmbedImagesView` carrying two image items
- **THEN** the result is `EmbedUi.Images(items)` where `items` is an `ImmutableList` of two `ImageUi`, each populated from the corresponding source image (url, altText, aspectRatio)

#### Scenario: External embed maps to EmbedUi.External with precomputed domain

- **WHEN** `toEmbedUi` is called with a `PostViewEmbedUnion.AppBskyEmbedExternalView` carrying `external.uri = "https://www.example.com/article"`
- **THEN** the result is `EmbedUi.External(uri = "https://www.example.com/article", domain = "example.com", title, description, thumbUrl)` — the `www.` prefix is stripped at mapping time so the render layer never re-parses the URI

#### Scenario: Record embed (viewRecord) maps to EmbedUi.Record with a fully populated QuotedPostUi

- **WHEN** `toEmbedUi` is called with a `PostViewEmbedUnion.AppBskyEmbedRecordView` whose `record` is a `RecordViewRecord` carrying author + uri + cid + a decodable `value` containing text + createdAt
- **THEN** the result is `EmbedUi.Record(quotedPost)` where `quotedPost.uri == record.uri.raw`, `quotedPost.cid == record.cid.raw`, `quotedPost.author` is the mapped `AuthorUi`, `quotedPost.text` is the decoded post text, `quotedPost.createdAt` is the parsed RFC3339 instant, `quotedPost.facets` is the (possibly empty) facet list, and `quotedPost.embed` is the inner-embed mapping per the separate requirement below

#### Scenario: Record embed unavailable variants map to EmbedUi.RecordUnavailable with the matching Reason

- **WHEN** `toEmbedUi` is called with a `RecordView` whose `record` union member is `RecordViewNotFound` / `RecordViewBlocked` / `RecordViewDetached` respectively
- **THEN** the result is `EmbedUi.RecordUnavailable(Reason.NotFound)` / `Reason.Blocked` / `Reason.Detached` accordingly

#### Scenario: RecordWithMedia embed maps to EmbedUi.RecordWithMedia with composed record + media

- **WHEN** `toEmbedUi` is called with a `PostViewEmbedUnion.AppBskyEmbedRecordWithMediaView` whose `record` resolves to a `RecordViewRecord` (decodable) and whose `media` is an `ImagesView` carrying two image items
- **THEN** the result is `EmbedUi.RecordWithMedia(record = EmbedUi.Record(quotedPost), media = EmbedUi.Images(items))` where the inner `quotedPost` is constructed by the same `RecordViewRecord.toEmbedUiRecord` helper used by the top-level `Record` arm, and `items` is the same `ImmutableList<ImageUi>` the top-level `Images` arm would produce — single source of truth for both wrapper constructions

#### Scenario: RecordWithMedia with malformed media falls through to EmbedUi.Unsupported

- **WHEN** `toEmbedUi` is called with a `RecordWithMediaView` whose `media` is a `VideoView` with a blank `playlist` field (or whose `media` is the open-union `Unknown` variant)
- **THEN** the result is `EmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia")` — the whole composition falls through. The record side is NOT rendered standalone; half-rendering a recordWithMedia loses the post's communicative intent.

#### Scenario: RecordWithMedia with unavailable record still renders the media

- **WHEN** `toEmbedUi` is called with a `RecordWithMediaView` whose `record.record` is `RecordViewNotFound` and whose `media` is a valid `ImagesView`
- **THEN** the result is `EmbedUi.RecordWithMedia(record = EmbedUi.RecordUnavailable(Reason.NotFound), media = EmbedUi.Images(items))` — the unavailable record degrades gracefully (per its lexicon-defined `viewNotFound` shape) while the media still renders

#### Scenario: Unknown embed maps to EmbedUi.Unsupported

- **WHEN** `toEmbedUi` is called with `PostViewEmbedUnion.Unknown` carrying a `$type` of `"app.bsky.embed.somethingNew"`
- **THEN** the result is `EmbedUi.Unsupported(typeUri = "app.bsky.embed.somethingNew")`

#### Scenario: Helpers are sourced from `:core:feed-mapping`

- **WHEN** `:feature:feed:impl/data/FeedViewPostMapper.kt` is inspected
- **THEN** the `toEmbedUi` dispatch and the three wrapper-construction helpers (`ImagesView.toEmbedUiImages`, `VideoView.toEmbedUiVideo`, `ExternalView.toEmbedUiExternal`) are imported from `net.kikin.nubecita.core.feedmapping.*` rather than declared inline; the file's own declarations are limited to feed-specific concerns (cursor handling, `repostedBy` extraction from `feedViewPost.reason`, etc.)

### Requirement: `FeedState` exposes a sealed `FeedLoadStatus` for mutually-exclusive load modes

`FeedState` MUST declare `loadStatus: FeedLoadStatus` of sealed type `FeedLoadStatus` with variants `Idle`, `InitialLoading`, `Refreshing`, `Appending`, and `InitialError(error: FeedError)`. The state MUST NOT use independent boolean fields for these modes. `posts: ImmutableList<PostUi>`, `nextCursor: String?`, and `endReached: Boolean` remain flat fields.

#### Scenario: Initial load transitions through InitialLoading

- **WHEN** `Load` is dispatched on a freshly-constructed VM
- **THEN** `FeedState.loadStatus` transitions `Idle → InitialLoading → Idle` on success (or `Idle → InitialLoading → InitialError(...)` on failure)

#### Scenario: Refresh and append never coexist

- **WHEN** the VM is observed at any point during its lifecycle
- **THEN** `loadStatus` SHALL be exactly one of {`Idle`, `InitialLoading`, `Refreshing`, `Appending`, `InitialError`} — there is no representable state where the VM is both refreshing and appending simultaneously

### Requirement: Initial-load error is sticky in state; refresh and append errors are effects

The system's VM MUST set `loadStatus = FeedLoadStatus.InitialError(error)` only when initial-load fails and `posts.isEmpty()`. The host screen renders a full-screen retry layout against this sticky state. Refresh and append failures (when `posts` is non-empty) MUST set `loadStatus` back to `Idle` and emit `FeedEffect.ShowError(error)` for snackbar display. The `posts` list MUST be preserved across refresh / append failures.

#### Scenario: Initial-load failure populates InitialError

- **WHEN** the VM is fresh (`posts.isEmpty()`) and `Load` dispatch's repository call fails
- **THEN** `loadStatus = FeedLoadStatus.InitialError(error)`, `posts.isEmpty()`, and no `FeedEffect.ShowError` is emitted (the sticky state IS the error display)

#### Scenario: Refresh failure with existing data emits a snackbar effect

- **WHEN** `posts` is non-empty and `Refresh` dispatch's repository call fails
- **THEN** `loadStatus` returns to `Idle`, `posts` is preserved unchanged, and exactly one `FeedEffect.ShowError(error)` is emitted

#### Scenario: Append failure preserves the cursor and emits a snackbar effect

- **WHEN** `posts` is non-empty, `nextCursor != null`, and `LoadMore` dispatch's repository call fails
- **THEN** `loadStatus` returns to `Idle`, `posts` is preserved, `nextCursor` is preserved, and exactly one `FeedEffect.ShowError(error)` is emitted

### Requirement: `FeedEvent` declares the full screen-interaction surface from day one

`FeedEvent` MUST include `OnPostTapped`, `OnAuthorTapped`, `OnLikeClicked`, `OnRepostClicked`, `OnReplyClicked`, and `OnShareClicked`. `FeedViewModel` handles tap and author events by emitting navigation effects (`FeedEffect.NavigateToPost`, `NavigateToAuthor`), and handles like, repost, reply, and share events as no-ops until write paths land.

#### Scenario: Like dispatch is a no-op on state and repository

- **WHEN** `OnLikeClicked(post)` is dispatched
- **THEN** `FeedState.posts` does NOT change, no repository call is made, and no `FeedEffect` is emitted

#### Scenario: PostTap emits a navigation effect

- **WHEN** `OnPostTapped(post)` is dispatched
- **THEN** exactly one `FeedEffect.NavigateToPost(post)` is emitted and no state field changes

### Requirement: `FeedScreen` is the canonical render-side composable for the Following timeline

The system SHALL replace the placeholder `FeedScreen` in `:feature:feed:impl` with a production composable that renders the full `FeedState` lifecycle. `FeedScreen` MUST be the only composable bound to the `Feed` Nav3 entry; the entry installer in `FeedNavigationModule` MUST resolve `FeedViewModel` via `hiltViewModel()` and pass it (or rely on the default parameter) to `FeedScreen`. No alternate composable in the project may render the Following timeline.

#### Scenario: Nav3 entry composes the production FeedScreen

- **WHEN** the `Feed` `NavKey` is on the back stack and the Nav3 entry composes
- **THEN** the entry's content SHALL invoke `FeedScreen(...)` and the composable SHALL obtain `FeedViewModel` via `hiltViewModel()` (directly or via the default parameter)

#### Scenario: No second feed-rendering composable exists

- **WHEN** the source tree is searched for `@Composable` functions that read `FeedState`
- **THEN** the only match in production code SHALL be `FeedScreen.kt`

### Requirement: Screen renders a state-shape matrix that is total over `FeedState`

`FeedScreen` MUST render exhaustively based on `(loadStatus, posts.isEmpty())`: `InitialLoading` with empty posts renders a shimmer list; `InitialError` with empty posts renders full-screen `FeedErrorState`; `Idle` with empty posts renders `FeedEmptyState`; non-empty posts at `Idle` or `Refreshing` render `PostCard` items; `Appending` renders posts plus a tail shimmer row. Existing posts stay visible during `Refreshing`.

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

`FeedScreen` SHALL dispatch `FeedEvent.LoadMore` when `visibleItemsInfo.lastOrNull()?.index > posts.size - 5`. It MUST emit at most once per threshold crossing using `snapshotFlow` with `distinctUntilChanged`. The trigger MUST NOT emit when `state.endReached == true` or when `state.loadStatus != FeedLoadStatus.Idle`.

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

`FeedScreen` SHALL construct its `LazyListState` via `rememberSaveable(saver = LazyListState.Saver) { LazyListState() }`. The scroll position (`firstVisibleItemIndex` and scroll offset) MUST survive navigation away and back to the feed entry, as well as activity configuration changes (such as rotation).

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

`FeedScreen` SHALL collect `viewModel.effects` in a single `LaunchedEffect(Unit)`. It MUST map `FeedEffect.ShowError(error)` to a snackbar (dismissing any existing snackbar first), `NavigateToPost(post)` to hoisted `onNavigateToPost(post)`, and `NavigateToAuthor(did)` to hoisted `onNavigateToAuthor(did)`.

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

`:feature:feed:impl` SHALL maintain `@Preview`s covering empty, initial-loading, error variants, loaded, refreshing, and appending in light/dark themes. It MUST maintain matching screenshot tests under `screenshotTest/` and Compose UI tests under `androidTest/` verifying pagination dispatch, retry click, pull-to-refresh, empty state, and scroll retention.

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

### Requirement: `FeedScreen` propagates `Scaffold` inset padding to every state branch

`FeedScreen`'s outer `Scaffold` MUST propagate `innerPadding` to all view branches: scrollable surfaces (`LazyColumn` in `InitialLoading` and `LoadedFeedContent`) pass `contentPadding = innerPadding`; non-scrollable layouts (`FeedEmptyState`, `FeedErrorState`) apply it via their `contentPadding` parameter. Inset padding MUST NOT be dropped.

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

`LoadedFeedContent` MUST accept `contentPadding: PaddingValues` and pass it to `LazyColumn`'s `contentPadding`. The `PullToRefreshBox` and `LazyColumn` themselves MUST remain `fillMaxSize()` without outer padding modifiers, allowing list content and the refresh indicator to scroll behind translucent system bars.

#### Scenario: First item respects top inset

- **WHEN** `LoadedFeedContent` renders with `contentPadding.top` of 48dp (status bar)
- **THEN** the first `PostCard` in the list SHALL appear 48dp below the screen's top edge when the list is scrolled to position 0

#### Scenario: Last item respects bottom inset

- **WHEN** the user scrolls to the end of the loaded posts on a gesture-nav device with `contentPadding.bottom` of 24dp
- **THEN** the last `PostCard`'s bottom edge SHALL be visually above the system gesture bar

#### Scenario: Pull-to-refresh indicator anchors below the status bar

- **WHEN** the user initiates pull-to-refresh on a fully-scrolled-to-top feed
- **THEN** the spinning indicator SHALL appear at or below the status bar's bottom edge — NOT under the status bar

### Requirement: `FeedScreen` hosts a `FeedVideoPlayerCoordinator` scoped to its composition lifetime and supplies the `videoEmbedSlot` to PostCard

`FeedScreen` MUST host a composition-scoped `FeedVideoPlayerCoordinator` created via `remember` and disposed via `DisposableEffect.onDispose { coordinator.release() }`. For each visible post, `LoadedFeedContent` supplies `videoEmbedSlot` and optional `quotedVideoEmbedSlot` (when `post.embed.quotedRecord != null`) to `PostCard`.

#### Scenario: Coordinator is composition-scoped to FeedScreen

- **WHEN** `FeedScreen` enters composition
- **THEN** exactly one `FeedVideoPlayerCoordinator` is constructed (via `remember`) AND a `DisposableEffect` registers `onDispose { coordinator.release() }` so the coordinator's `ExoPlayer` is released when the screen leaves composition

#### Scenario: Slot builder for top-level quoted post

- **WHEN** `LoadedFeedContent` renders a feed item whose `post.embed is EmbedUi.Record(quotedPost = qp)`
- **THEN** the `quotedVideoSlot` lambda passed to `PostCard.quotedVideoEmbedSlot` is non-null and is `remember`-keyed by `(qp.uri, coordinator)`

#### Scenario: Slot builder for quoted post inside RecordWithMedia

- **WHEN** `LoadedFeedContent` renders a feed item whose `post.embed is EmbedUi.RecordWithMedia(record = EmbedUi.Record(quotedPost = qp), ...)`
- **THEN** the `quotedVideoSlot` lambda passed to `PostCard.quotedVideoEmbedSlot` is non-null and is `remember`-keyed by `(qp.uri, coordinator)` — same as the top-level case, via the shared `EmbedUi.quotedRecord` extension

#### Scenario: Slot builder is null when the post carries no quoted content

- **WHEN** `LoadedFeedContent` renders a feed item whose `post.embed.quotedRecord` returns null (any of `Empty`, `Images`, `Video`, `External`, `RecordUnavailable`, `RecordWithMedia` whose `record` is `RecordUnavailable`, or `Unsupported`)
- **THEN** `quotedVideoSlot` is null and PostCard does not invoke any quoted-video composable for the item

### Requirement: Inner-embed mapping for quoted posts is bounded at one level by the type system

The system MUST map quoted post embeds via `RecordViewRecordEmbedsUnion?.toQuotedEmbedUi()`: `Empty`, `Images`, `Video`, `External`, `QuotedThreadChip` for inner `RecordView` (bounding recursion at one level), and `Unsupported` for other unions. Per-variant payload construction MUST be shared with parent mappers.

#### Scenario: Inner Images embed maps to QuotedEmbedUi.Images

- **WHEN** the mapper processes a `RecordViewRecord` whose `embeds.firstOrNull()` is an `ImagesView` with three image items
- **THEN** `QuotedPostUi.embed` is `QuotedEmbedUi.Images` with three `ImageUi` entries, structurally equal to what the parent `toEmbedUi` would have produced for the same `ImagesView`

#### Scenario: Inner Video embed with non-blank playlist maps to QuotedEmbedUi.Video

- **WHEN** the mapper processes a `RecordViewRecord` whose `embeds.firstOrNull()` is a `VideoView` with `playlist.raw == "https://video.bsky.app/.../playlist.m3u8"` and `aspectRatio.width=1920, aspectRatio.height=1080`
- **THEN** `QuotedPostUi.embed` is `QuotedEmbedUi.Video` with `playlistUrl == "https://video.bsky.app/.../playlist.m3u8"`, `aspectRatio == 1920f / 1080f`, `durationSeconds == null`

#### Scenario: Inner Video embed with blank playlist falls through to QuotedEmbedUi.Unsupported

- **WHEN** the mapper processes a `RecordViewRecord` whose `embeds.firstOrNull()` is a `VideoView` with `playlist.raw == ""`
- **THEN** `QuotedPostUi.embed` is `QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.video")` — same fallthrough rule as the parent video mapping

#### Scenario: Inner Record embed produces the QuotedThreadChip sentinel (one-level recursion bound)

- **WHEN** the mapper processes a `RecordViewRecord` whose `embeds.firstOrNull()` is itself a `RecordView` (a quote-of-a-quote)
- **THEN** `QuotedPostUi.embed` is `QuotedEmbedUi.QuotedThreadChip` — the mapper does NOT recurse and does NOT attempt to populate a nested `QuotedPostUi`

#### Scenario: Inner RecordWithMedia maps to QuotedEmbedUi.Unsupported with the lexicon URI

- **WHEN** the mapper processes a `RecordViewRecord` whose `embeds.firstOrNull()` is a `RecordWithMediaView`
- **THEN** `QuotedPostUi.embed` is `QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia")`

### Requirement: A malformed quoted record never drops the parent post

When a quoted record JSON cannot be decoded as a valid `app.bsky.feed.post` or carries an unparseable `createdAt`, the mapper MUST produce `EmbedUi.RecordUnavailable(Reason.Unknown)`. The parent post MUST still project to a non-null `PostUi` — malformed quoted records MUST NEVER drop the parent post.

#### Scenario: Quoted post with malformed value yields RecordUnavailable.Unknown but the parent still maps

- **WHEN** the mapper processes a parent `FeedViewPost` whose embed is a `RecordView` whose `RecordViewRecord.value` is a `JsonObject` that lacks the required `text` field
- **THEN** the parent `toPostUiOrNull()` returns a non-null `PostUi` whose `embed == EmbedUi.RecordUnavailable(Reason.Unknown)`

#### Scenario: Quoted post with malformed createdAt yields RecordUnavailable.Unknown

- **WHEN** the mapper processes a `RecordView` whose `RecordViewRecord.value.createdAt` decodes as the string `"not-a-date"` (passes JSON decode but fails `Instant.parse`)
- **THEN** `EmbedUi.RecordUnavailable(Reason.Unknown)` is produced; the parent post is unaffected

### Requirement: Feed entry registers `listPane{}` metadata in its `@MainShell` `EntryProviderInstaller`

The `@MainShell`-qualified `EntryProviderInstaller` provided by `:feature:feed:impl` for the `Feed` `NavKey` SHALL register the entry with `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { FeedDetailPlaceholder() })`. `FeedDetailPlaceholder` SHALL be an `internal` Composable defined in `:feature:feed:impl`. The placeholder SHALL NOT be promoted to `:designsystem` until at least one additional list-pane host needs the same shape.

#### Scenario: Feed installer wraps entry with listPane metadata

- **WHEN** the `:feature:feed:impl` `@MainShell` installer is examined
- **THEN** the `entry<Feed>(…)` call SHALL include a `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = …)` argument

#### Scenario: Placeholder lives in :feature:feed:impl, not :designsystem

- **WHEN** the source tree is searched for `FeedDetailPlaceholder`
- **THEN** the only definition site SHALL be inside `feature/feed/impl/src/main/`

### Requirement: `FeedDetailPlaceholder` displays a localized empty-state prompt

`FeedDetailPlaceholder` SHALL render a centered Composable consisting of, at minimum, a decorative icon and a textual prompt sourced from `R.string.feed_detail_placeholder_select` ("Select a post to read"). The prompt SHALL use a typography role no smaller than `MaterialTheme.typography.bodyLarge`. The icon SHALL declare `contentDescription = null` (decorative).

#### Scenario: Placeholder renders the localized prompt

- **WHEN** `FeedDetailPlaceholder()` is composed in a Compose-rule test
- **THEN** the rendered tree SHALL contain a node with text matching `Select a post to read` (or its locale-appropriate translation)

#### Scenario: Placeholder icon is decorative

- **WHEN** the source of `FeedDetailPlaceholder` is inspected
- **THEN** the contained `Icon` Composable SHALL pass `contentDescription = null`

### Requirement: `FeedScreen` consumes `LocalTabReTapSignal` and hosts the compose FAB

`FeedScreen` SHALL collect `LocalTabReTapSignal.current` via `LaunchedEffect` and call `listState.animateScrollToItem(0)`. In `Loaded` state, it renders an icon-only compose FAB in `Scaffold.floatingActionButton` (`FloatingActionButton` 56dp on Compact, `LargeFloatingActionButton` 96dp on Medium/Expanded). Tapping pushes `ComposerRoute` on Compact or opens the dialog overlay on Medium/Expanded.

#### Scenario: Compose FAB visible over a loaded feed at scroll position 0

- **WHEN** the feed has rendered into `FeedScreenViewState.Loaded` and `firstVisibleItemIndex == 0`
- **THEN** the `Scaffold.floatingActionButton` slot renders the compose FAB carrying `Icons.Default.Edit` and `contentDescription == R.string.feed_compose_new_post`

#### Scenario: Compose FAB visible after deep scroll

- **WHEN** the user scrolls so that `firstVisibleItemIndex >= 20`
- **THEN** the compose FAB remains visible without any `AnimatedVisibility` enter/exit transition firing — its visibility is independent of scroll position

#### Scenario: Compose FAB hidden during InitialLoading

- **WHEN** the feed view-state is `FeedScreenViewState.InitialLoading`
- **THEN** the `Scaffold.floatingActionButton` slot is empty (no compose FAB rendered)

#### Scenario: Compose FAB hidden during Empty / InitialError

- **WHEN** the feed view-state is `FeedScreenViewState.Empty` or `FeedScreenViewState.InitialError`
- **THEN** the `Scaffold.floatingActionButton` slot is empty (no compose FAB rendered)

#### Scenario: Compose FAB tap pushes ComposerRoute at Compact

- **WHEN** the compose FAB is visible, the active `WindowWidthSizeClass` is `COMPACT`, and the user taps it
- **THEN** `LocalMainShellNavState.current.add(ComposerRoute(replyToUri = null))` is invoked exactly once and the composer-launcher state holder is NOT mutated

#### Scenario: Compose FAB tap opens Dialog overlay at Medium/Expanded

- **WHEN** the compose FAB is visible, the active `WindowWidthSizeClass` is `MEDIUM` or `EXPANDED`, and the user taps it
- **THEN** the `MainShell`-scoped composer-launcher state holder transitions to `Open(replyToUri = null)` exactly once and `LocalMainShellNavState.current` is NOT mutated

#### Scenario: Compose FAB component is icon-only and badge-wrappable

- **WHEN** the source of the compose FAB is inspected
- **THEN** the FAB is `FloatingActionButton`, `LargeFloatingActionButton`, or `SmallFloatingActionButton` — it is NOT `ExtendedFloatingActionButton`

#### Scenario: Compose FAB scales to Large at Expanded width

- **WHEN** the active `WindowWidthSizeClass` is `EXPANDED` and the feed view-state is `Loaded`
- **THEN** the rendered FAB is `LargeFloatingActionButton` (96dp), not the Compact-default `FloatingActionButton`

#### Scenario: Re-tapping the active bottom-nav tab still scrolls Feed to top

- **WHEN** the user is on the Feed tab with `firstVisibleItemIndex > 0` and re-taps the Feed tab
- **THEN** MainShell emits `Unit` via `LocalTabReTapSignal`, the `FeedScreen` `LaunchedEffect` collector receives the emission, and `listState.animateScrollToItem(0)` runs

#### Scenario: VM is unchanged

- **WHEN** the source tree of `FeedViewModel` / `FeedState` / `FeedEvent` / `FeedEffect` is diffed before / after this change
- **THEN** there are NO additions or modifications. The compose FAB tap path does not pass through the VM.

### Requirement: Each post in the feed exposes a reply tap target that opens the composer in reply mode via the width-class-conditional launcher

Each `PostCard` in `FeedScreen`'s loaded list SHALL expose a reply affordance in its action row. Tapping invokes `launchComposer(replyToUri = post.uri.toString())`: pushing `ComposerRoute(replyToUri)` on Compact width, or opening the composer dialog on Medium/Expanded width. Reply navigation MUST bypass `FeedViewModel`.

#### Scenario: Reply tap at Compact width pushes ComposerRoute

- **WHEN** the active `WindowWidthSizeClass` is `COMPACT` and the user taps the reply affordance on a `PostCard` whose backing `PostUi.uri == AtUri("at://did:plc:abc/app.bsky.feed.post/xyz")`
- **THEN** `LocalMainShellNavState.current.add(ComposerRoute(replyToUri = "at://did:plc:abc/app.bsky.feed.post/xyz"))` is invoked exactly once and the composer-launcher state holder is NOT mutated

#### Scenario: Reply tap at Medium/Expanded width opens the launcher overlay

- **WHEN** the active `WindowWidthSizeClass` is `MEDIUM` or `EXPANDED` and the user taps the reply affordance on the same `PostCard`
- **THEN** the `MainShell`-scoped composer-launcher state holder transitions to `Open(replyToUri = "at://did:plc:abc/app.bsky.feed.post/xyz")` exactly once and `LocalMainShellNavState.current` is NOT mutated

#### Scenario: VM is unchanged

- **WHEN** the source tree of `FeedViewModel` / `FeedState` / `FeedEvent` / `FeedEffect` is diffed before / after this requirement
- **THEN** there are NO additions related to reply navigation. The reply tap is a pure screen-layer concern.

#### Scenario: Reply affordance present on every loaded post

- **WHEN** `FeedScreen` is in `FeedScreenViewState.Loaded` with N posts visible
- **THEN** every `PostCard` exposes the reply tap target — no card is special-cased to omit it

### Requirement: The feed renders at most one item per thread root within a session

The feed SHALL render at most one item per thread root, retaining the first occurrence in list order. Root ID is derived as: `ReplyCluster` uses root ID, `SelfThreadChain` uses first post ID, `Single` uses post ID, and tombstones have no root (never dropped). Reposts are never dropped. De-duplication is a pure function over `List<FeedItemUi>` applied before cluster-context de-duplication.

#### Scenario: Two replies to the same thread arrive in one page

- **WHEN** a page contains two `ReplyCluster` items whose `root` is the same post
- **THEN** only the first SHALL be rendered and the second SHALL be dropped

#### Scenario: Replies to the same thread span two pages

- **WHEN** a reply into thread R is rendered from page 1 and a second reply into
  thread R arrives in page 2
- **THEN** the page-2 item SHALL be dropped, because it is older than the item
  already shown

#### Scenario: A standalone post reserves its own thread root

- **WHEN** a `Single` for post P is rendered and a later item is a
  `ReplyCluster` whose root is P
- **THEN** the later cluster SHALL be dropped

#### Scenario: A repost is never dropped by thread-root de-duplication

- **WHEN** an item whose leaf carries a repost attribution shares a thread root
  with an item already rendered
- **THEN** the reposted item SHALL still be rendered

#### Scenario: Refresh resurfaces the newest reply in a thread

- **WHEN** the viewer refreshes the feed and a newer reply into an
  already-seen thread has since been posted
- **THEN** that newer reply SHALL be rendered, because the accumulated list is
  rebuilt and no seen-root state persists across refreshes

#### Scenario: A post rendered only as a dropped cluster's parent is not lost

- **WHEN** the list contains a cluster rooted at R, a second cluster rooted at R
  whose parent is post L, and a `Single` for L
- **THEN** the second cluster SHALL be dropped for reusing root R, and the
  `Single` for L SHALL still be rendered, because nothing else renders L

#### Scenario: Tombstones are never de-duplicated

- **WHEN** the list contains `Blocked` or `NotFound` items
- **THEN** they SHALL be retained regardless of any thread root, because they
  carry no post from which a root can be derived

### Requirement: A de-duplicated feed item reports how many sibling replies were suppressed

A surviving feed item SHALL carry a suppressed-reply count displayed as an affordance leading to the full thread. The count is measured in total suppressed posts (not feed items). Posts already visible as context in the list are not counted. Every surviving item variant, including `Single`, carries the count.

#### Scenario: Suppressed siblings are counted in posts, not items

- **WHEN** two items into the same thread are dropped, one a `ReplyCluster` and
  one a `SelfThreadChain` of three posts
- **THEN** the surviving item SHALL report a suppressed-reply count of four

#### Scenario: A dropped standalone already visible as context is not counted

- **WHEN** a `Single` for post P is dropped because P is already rendered as the
  surviving item's `root` or `parent`
- **THEN** the suppressed-reply count SHALL NOT include P

#### Scenario: A surviving standalone post carries the count

- **WHEN** a `Single` for post P reserves thread root P and a later
  `ReplyCluster` rooted at P is dropped
- **THEN** the `Single` SHALL report a suppressed-reply count of one and SHALL
  render the affordance

#### Scenario: An item with no suppressed siblings shows no affordance

- **WHEN** an item's thread root appears exactly once in the list
- **THEN** its suppressed-reply count SHALL be zero and no affordance SHALL be
  rendered

### Requirement: `FeedViewPostMapper` exposes `toFeedItemUiOrNull` as the entry-point projection

`:feature:feed:impl` SHALL expose `internal fun FeedViewPost.toFeedItemUiOrNull(): FeedItemUi?` projecting wire posts to `FeedItemUi.Single`, `FeedItemUi.ReplyCluster(hasEllipsis)`, or `null` (for unparseable records). If a reply's parent is blocked or not found, it falls back to `Single` with a warning log.

#### Scenario: Reply with renderable parent + root produces ReplyCluster

- **WHEN** a `FeedViewPost` carries `reply.parent` and `reply.root` as `PostView` variants and `grandparentAuthor` is null
- **THEN** `toFeedItemUiOrNull()` returns `FeedItemUi.ReplyCluster(root, parent, leaf, hasEllipsis = false)`

#### Scenario: Reply with grandparentAuthor distinct from root.author triggers hasEllipsis

- **WHEN** a `FeedViewPost` carries `reply.parent` + `reply.root` as `PostView` variants AND `grandparentAuthor != null` AND `grandparentAuthor.did != root.author.did`
- **THEN** `toFeedItemUiOrNull()` returns `FeedItemUi.ReplyCluster(root, parent, leaf, hasEllipsis = true)`

#### Scenario: Reply with BlockedPost parent falls back to Single

- **WHEN** a `FeedViewPost` carries `reply.parent` as `BlockedPost` (or `NotFoundPost`)
- **THEN** `toFeedItemUiOrNull()` returns `FeedItemUi.Single(leaf)`
- **AND** `Timber.w(...)` is invoked describing the fallback

#### Scenario: Standalone post produces Single

- **WHEN** a `FeedViewPost` has `reply == null`
- **THEN** `toFeedItemUiOrNull()` returns `FeedItemUi.Single(leaf)`

#### Scenario: Malformed leaf record returns null

- **WHEN** the leaf post's record cannot be projected to `PostUi` (malformed `record` JSON or unparseable `createdAt`)
- **THEN** `toFeedItemUiOrNull()` returns `null` regardless of `reply` payload — repository's `mapNotNull` filter then drops the entry

### Requirement: `FeedScreenViewState.Loaded` carries `feedItems: ImmutableList<FeedItemUi>`

`FeedScreenViewState.Loaded` SHALL expose `feedItems: ImmutableList<FeedItemUi>`. `FeedViewModel` projects timeline entries to `FeedItemUi` via `toFeedItemUiOrNull(...)`. In `LazyColumn`, `items` uses the leaf post URI as the stable key (`post.id` for `Single`, `leaf.id` for `ReplyCluster`).

#### Scenario: Loaded carries FeedItemUi instead of PostUi

- **WHEN** `FeedViewModel`'s projection runs against a session state with N timeline entries that all map to non-null `FeedItemUi`
- **THEN** `FeedScreenViewState.Loaded.feedItems.size == N`

#### Scenario: LazyColumn key is the leaf's URI

- **WHEN** `LoadedFeedContent` invokes `LazyColumn { items(feedItems, key = { ... }) { ... } }`
- **THEN** the `key` for `FeedItemUi.Single(post)` SHALL be `post.id`, and for `FeedItemUi.ReplyCluster(...)` SHALL be `leaf.id` — pagination + scroll-position are anchored on the leaf in either case

### Requirement: `FeedScreen` dispatches on `FeedItemUi` to render `PostCard` or `ThreadCluster`

`LoadedFeedContent` SHALL render `PostCard` for `FeedItemUi.Single`, and `ThreadCluster` for `FeedItemUi.ReplyCluster`. In `ThreadCluster`, only the leaf post receives `videoEmbedSlot`; root and parent posts use `videoEmbedSlot = null` (static poster fallback).

#### Scenario: Single feed item renders PostCard

- **WHEN** a `Loaded` viewState contains a `FeedItemUi.Single(post)`
- **THEN** the rendered LazyColumn contains a `PostCard` for that post with default `connectAbove = false, connectBelow = false`

#### Scenario: ReplyCluster feed item renders ThreadCluster

- **WHEN** a `Loaded` viewState contains a `FeedItemUi.ReplyCluster(root, parent, leaf, hasEllipsis = true)`
- **THEN** the rendered LazyColumn contains exactly one `ThreadCluster` rendering: root `PostCard` (connectBelow=true) + `ThreadFold` + parent `PostCard` (connectAbove=true, connectBelow=true) + leaf `PostCard` (connectAbove=true)
- **AND** when `hasEllipsis = false`, the same shape minus the `ThreadFold`

#### Scenario: Cluster's leaf participates in video coordinator; root + parent do not

- **WHEN** a `ReplyCluster` is rendered and the host has supplied a non-null `videoEmbedSlot` to the FeedScreen
- **THEN** the leaf's `PostCard.videoEmbedSlot` receives the host's slot
- **AND** the root + parent `PostCard.videoEmbedSlot` are null (any video embeds in root + parent render via the static-poster fallback)

### Requirement: Feed mapping produces `SelfThreadChain` for consecutive same-author self-replies

`:feature:feed:impl` SHALL expose `internal fun List<FeedViewPost>.toFeedItemsUi(): ImmutableList<FeedItemUi>` grouping consecutive entries into `FeedItemUi.SelfThreadChain` when entries share the same author DID, parent URI matches previous post URI, neither is a repost, and both project to valid `PostUi`. Runs of size ≥ 2 form chains.

#### Scenario: Three consecutive same-author self-replies project to one SelfThreadChain

- **WHEN** the mapper receives a page of three `FeedViewPost` entries `[A.post1, A.reply2, A.reply3]` where `reply2.reply.parent.uri == post1.uri`, `reply3.reply.parent.uri == reply2.uri`, all share `author.did = A`, and none has a `ReasonRepost`
- **THEN** `toFeedItemsUi` returns one `FeedItemUi` whose type is `SelfThreadChain` and whose `posts.size == 3`.

#### Scenario: A broken link splits the chain

- **WHEN** the mapper receives `[A.post1, A.reply2, B.replyX, A.reply3]` where `A.reply3.reply.parent.uri != B.replyX.post.uri`
- **THEN** `toFeedItemsUi` returns three items: a `SelfThreadChain` of `[A.post1, A.reply2]`, then `Single(B.replyX)` (or `ReplyCluster`, depending on `B.replyX.reply`), then `Single(A.reply3)` (or `ReplyCluster`). The presence of the cross-author entry between the two `A` entries breaks the chain rule's adjacency requirement.

#### Scenario: Reposted entries cannot be chain links

- **WHEN** the mapper receives `[A.post1, A.reply2, A.reply3]` where `A.reply2` carries `reason = ReasonRepost`
- **THEN** `toFeedItemsUi` returns three `Single` items (or whatever the per-entry projection yields). The `ReasonRepost` on `A.reply2` disqualifies the link to `A.post1` and the link from `A.reply3`, regardless of URI matching.

#### Scenario: Same author but parent URI doesn't match prev produces no chain

- **WHEN** the mapper receives `[A.post1, A.post2, A.post3]` where `post3.reply.parent.uri == post1.uri` (skipping `post2` in the wire) but all three share `author.did = A`
- **THEN** `toFeedItemsUi` returns three `Single` items. Strict link rule rejects skip-ahead chains because the wire ordering doesn't form an unbroken parent → child chain (per design Decision 1).

#### Scenario: Per-entry projection paths are unchanged for non-chain entries

- **WHEN** the mapper receives a page where no two consecutive entries satisfy the chain link rule
- **THEN** `toFeedItemsUi` returns the same `ImmutableList<FeedItemUi>` that the previous per-entry pass produced, in the same order, with no item replaced or reordered.

### Requirement: Page-boundary chain merge preserves chains across pagination cuts

`FeedViewModel`'s `LoadMore` reducer SHALL merge chains across pagination boundaries by checking whether the existing tail links to the incoming page's head item. Leading cursor-resync overlaps are stripped before linking. A valid link prepends tail posts into the incoming chain.

#### Scenario: Chain extends across a pagination boundary

- **WHEN** `feedItems = [Single(A.post1), Single(A.reply2)]` (these two formed two `Single`s because they appeared on different pages — page 1 ended on `A.post1`, page 2 begins with `A.reply2`)
- **AND** the existing tail-to-head link rule is satisfied (`A.reply2.reply.parent.uri == A.post1.uri`, same author, no reposts)
- **WHEN** the new page projects to `[Single(A.reply2), Single(A.reply3), Single(B.unrelated)]` and `A.reply3` further extends the chain
- **THEN** after the merge step, `feedItems` SHALL contain `[SelfThreadChain([A.post1, A.reply2, A.reply3]), Single(B.unrelated)]` — the merge popped the existing tail, reformed a chain spanning the boundary, and processed the next entry.

#### Scenario: Chain extension stops at the first non-linking entry

- **WHEN** `feedItems = [..., SelfThreadChain([A.post1, A.reply2])]` and the new page projects to `[Single(B.unrelated), Single(A.reply3)]` where `A.reply3.reply.parent.uri == A.reply2.uri`
- **THEN** the merge SHALL NOT skip over `B.unrelated` to extend the chain. The new page is appended as-is: `[..., SelfThreadChain([A.post1, A.reply2]), Single(B.unrelated), Single(A.reply3)]`.

#### Scenario: ReplyCluster tail does not extend into a chain

- **WHEN** `feedItems` ends with a `ReplyCluster` (a cross-author thread-cluster entry) and the new page's head is a `Single` whose `reply.parent.uri` matches the cluster's `leaf.id`
- **THEN** the merge SHALL leave the `ReplyCluster` intact. Chains are pure same-author by construction; a cross-author cluster preceding a same-author reply forms two distinct visual entries, not one chain.

#### Scenario: De-dupe by `FeedItemUi.key` continues to work after merge

- **WHEN** the merge produces a `SelfThreadChain` whose leaf is `A.reply2` and a subsequent `LoadMore` returns a page that re-includes `A.reply2` at its head
- **THEN** the existing `seen.add(it.key)` de-dupe step (keyed on `FeedItemUi.key`) SHALL drop the duplicate `A.reply2` entry. The chain's `key == A.reply2.id`, so the duplicate fails `seen.add` and is filtered out before merge attempts.

#### Scenario: Cursor-resync overlap at the page head extends rather than splits

- **WHEN** `feedItems` ends with `Single(A.post1)` and the new page's wire entries are `[A.post1, A.reply2, ...]` — the server replayed the existing tail's leaf as the first wire entry (cursor-resync overlap), and `A.reply2.reply.parent.uri == A.post1.uri`
- **THEN** the merge SHALL strip the leading overlap entry (`A.post1`) from both the wire and projected feed-items lists in lockstep, then run the link check against the next wire entry (`A.reply2`). Because the link rule passes, the result is `[..., SelfThreadChain([A.post1, A.reply2]), ...]` — the chain extends across the resync rather than rendering visually split with `A.post1` shown twice.

### Requirement: `SelfThreadChain` rendering uses existing `PostCard` connector flags

`FeedScreen` SHALL render `FeedItemUi.SelfThreadChain` as a single `LazyColumn` item containing stacked `PostCard`s: the first post has `connectBelow = true`, middle posts have `connectAbove = true, connectBelow = true`, and the last post has `connectAbove = true`.

#### Scenario: Chain renders with continuous gutter line

- **WHEN** a `SelfThreadChain` of 3 posts renders inside `FeedScreen`'s LazyColumn
- **THEN** the rendered output is one LazyColumn item whose visual surface contains 3 `PostCard`s stacked vertically, joined by `Modifier.threadConnector` lines through their avatar gutters: post 0 → connector line below the avatar only; post 1 → connector lines both above and below; post 2 → connector line above the avatar only.

#### Scenario: Quote-post embed does not collide with the gutter connector

- **WHEN** a chain renders a middle post that carries a `Record` or `RecordWithMedia` embed (a quote post)
- **THEN** the quote-post chrome (`PostCardQuotedPost`) renders inside the body content slot to the right of the avatar gutter, while the threadConnector line draws inside the gutter. The two surfaces SHALL NOT visually overlap. A screenshot fixture for "chain with quote-post middle" is the regression contract that locks this property.

### Requirement: Existing `Single` and `ReplyCluster` projection paths remain byte-for-byte unchanged

The introduction of chain detection SHALL NOT modify the existing per-entry projection or render paths for `FeedItemUi.Single` or `FeedItemUi.ReplyCluster`. Existing feed unit tests MUST pass without modification, and existing feed screenshot baselines (every fixture except the new same-author-chain fixtures) MUST stay byte-for-byte identical.

#### Scenario: Existing feed unit tests pass unchanged

- **WHEN** `./gradlew :feature:feed:impl:testDebugUnitTest` runs after this change merges
- **THEN** every existing test method SHALL pass without source-level modification.

#### Scenario: Existing feed screenshot baselines unchanged

- **WHEN** `./gradlew :feature:feed:impl:validateDebugScreenshotTest` runs after this change merges
- **THEN** every fixture that existed before this change SHALL match its baseline exactly. Only NEW fixtures (the same-author-chain renderings) introduce new baselines.

### Requirement: Existing FeedScreen test surface is unchanged

The introduction of the scroll-to-top consumer SHALL NOT modify any existing `FeedViewModel` unit test, any existing `FeedScreen` screenshot fixture (light or dark), or any existing string resource that didn't already exist. The change is additive: one new string (`feed_scroll_to_top` content description) and one new screenshot fixture covering the FAB-visible state.

#### Scenario: Existing screenshot baselines unchanged

- **WHEN** `./gradlew :feature:feed:impl:validateDebugScreenshotTest` runs after this change merges
- **THEN** every fixture that existed pre-merge matches its baseline byte-for-byte. The new `loaded-with-fab-visible-light` (or equivalent) fixture is the only addition.

#### Scenario: VM tests unchanged

- **WHEN** `./gradlew :feature:feed:impl:testDebugUnitTest` runs after this change merges
- **THEN** every existing test method passes without source-level modification.
