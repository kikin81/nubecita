# feature-feed Specification

## Purpose
TBD - created by archiving change add-feature-feed-foundation. Update Purpose after archive.
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

The system SHALL expose an `internal interface FeedRepository` in `:feature:feed:impl` with at minimum a single method `suspend fun getTimeline(cursor: String?, limit: Int = TIMELINE_PAGE_LIMIT): Result<TimelinePage>`. The `DefaultFeedRepository` implementation MUST be the only class in `:feature:feed:impl` that imports `io.github.kikin81.atproto.app.bsky.feed.FeedService`. `FeedViewModel` MUST inject the interface, never the concrete class. The interface and its implementation MUST stay `internal` to `:feature:feed:impl` until a second consumer (post detail, search) requires the same fetch surface — at that point a follow-on change promotes them to a `:core:feed` module.

#### Scenario: VM injects the interface

- **WHEN** `FeedViewModel`'s constructor is inspected
- **THEN** it MUST declare a `private val feedRepository: FeedRepository` parameter (interface type) and MUST NOT declare `DefaultFeedRepository` or `FeedService`

#### Scenario: Single import of FeedService

- **WHEN** the project source is grepped for `import io.github.kikin81.atproto.app.bsky.feed.FeedService`
- **THEN** the only match in production code SHALL be `DefaultFeedRepository.kt`

### Requirement: `FeedViewPostMapper` is pure and total over the response shape

The system SHALL expose top-level `internal` mapping functions in `:feature:feed:impl` package `data` (no class wrapper):

- `internal fun FeedViewPost.toPostUiOrNull(): PostUi?`
- `internal fun PostViewEmbedUnion?.toEmbedUi(): EmbedUi`
- Any helpers required to extract `text` / `facets` from `PostView.record: JsonObject`.

`toPostUiOrNull` MUST return `null` for inputs whose embedded `record` JSON cannot be decoded as a well-formed `app.bsky.feed.post` record (missing required fields, malformed types). It MUST NOT throw. Every spec-conforming `FeedViewPost` MUST yield a non-null `PostUi`. The mapper MUST NOT perform I/O, MUST NOT depend on Android types, and MUST be unit-testable as pure functions against fixture JSON.

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

The system SHALL preserve `FeedState.nextCursor` on append failure. After a successful `LoadMore`, the VM MUST update `nextCursor` to the cursor returned by the repository (which may be `null` to signal end-of-feed). On `LoadMore` failure (the repository returns `Result.failure`), the VM MUST leave `nextCursor` unchanged so a subsequent retry can re-attempt with the same cursor. On a successful response with `cursor == null`, the VM MUST set `endReached = true` and treat further `LoadMore` events as no-ops.

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

The system's `toEmbedUi` mapping function SHALL produce:

- `EmbedUi.Empty` for `null` (no embed)
- `EmbedUi.Images` for `app.bsky.embed.images#view` (1–4 images)
- `EmbedUi.Video` for `app.bsky.embed.video#view` (per the `feature-feed-video` spec)
- `EmbedUi.External` for `app.bsky.embed.external#view` (per `nubecita-aku`)
- `EmbedUi.Record` for `app.bsky.embed.record#viewRecord` (per `nubecita-6vq`)
- `EmbedUi.RecordUnavailable` for `app.bsky.embed.record#view{NotFound,Blocked,Detached}` and the `Unknown` open-union fallback (per `nubecita-6vq`)
- `EmbedUi.RecordWithMedia` for `app.bsky.embed.recordWithMedia#view` (per `nubecita-umn`)
- `EmbedUi.Unsupported(typeUri = ...)` for any unknown `Unknown`-variant payload from the open union

The `typeUri` field in `EmbedUi.Unsupported` MUST carry the fully-qualified lexicon NSID so PostCard's `PostCardUnsupportedEmbed` can render the friendly-name label per the design-system spec.

This dispatch MUST be exhaustive over the `PostViewEmbedUnion` sealed type. Future lexicon evolution that adds a new embed type MUST be handled in the same change that adds the variant — the sealed type makes this a compile error otherwise.

The construction of `EmbedUi.Images` / `EmbedUi.Video` / `EmbedUi.External` MUST go through three private wrapper-construction helpers (`ImagesView.toEmbedUiImages`, `VideoView.toEmbedUiVideo`, `ExternalView.toEmbedUiExternal`) shared between the top-level dispatch and the new media-side dispatch in `RecordWithMediaView.toEmbedUiRecordWithMedia`. Inline duplicated construction at the two call sites is forbidden — it would risk drift (e.g. an `aspectRatio` calculation tweak applied in one path and forgotten in the other).

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

### Requirement: `FeedState` exposes a sealed `FeedLoadStatus` for mutually-exclusive load modes

The system's `FeedState` MUST declare a single field `loadStatus: FeedLoadStatus` of type `sealed interface FeedLoadStatus` with at minimum the variants `Idle`, `InitialLoading`, `Refreshing`, `Appending`, and `InitialError(error: FeedError)`. The state MUST NOT use independent boolean fields (`isInitialLoading`, `isRefreshing`, `isAppending`) for these modes — the type system MUST make invalid combinations unrepresentable. `posts: ImmutableList<PostUi>`, `nextCursor: String?`, and `endReached: Boolean` remain flat fields on the state per their independent semantics.

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

The system's `FeedEvent` sealed interface MUST include `OnPostTapped`, `OnAuthorTapped`, `OnLikeClicked`, `OnRepostClicked`, `OnReplyClicked`, and `OnShareClicked` variants from the initial implementation, even before the write-path follow-on ticket lands. The VM MAY handle the tap / author events by emitting `FeedEffect.NavigateToPost` / `NavigateToAuthor` and MUST handle the like / repost / reply / share events as no-ops in this initial implementation (no state mutation, no repository call, no effect).

The shape of these events is acknowledged-as-illusory lock-in: the write-path follow-on is allowed to amend the contract if it discovers the surface is wrong (e.g., needs optimistic UI state, undo support, confirm-on-failure follow-up events). The justification is that `nubecita-1d5` (the screen ticket) can wire `PostCallbacks` to dispatch real event names instead of `TODO` placeholders, regardless of when the write path lands.

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

The system's `FeedScreen` SHALL collect `viewModel.effects` from a single `LaunchedEffect(Unit)` for the screen's lifetime. The effect handler MUST:

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

### Requirement: `FeedScreen` hosts a `FeedVideoPlayerCoordinator` scoped to its composition lifetime and supplies the `videoEmbedSlot` to PostCard

`FeedScreen` MUST host a `FeedVideoPlayerCoordinator` scoped to its composition lifetime — created in the screen's `LoadedFeedContent` `remember { }` block and released via `DisposableEffect.onDispose`. The coordinator is the single owner of the screen's `ExoPlayer` instance per the `feature-feed-video` spec.

For each visible feed item, `LoadedFeedContent` MUST build a `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit` lambda keyed by `(post.id, coordinator)` and pass it as the `videoEmbedSlot` parameter to `PostCard`. The slot's body invokes `PostCardVideoEmbed(video, postId = post.id, coordinator)` for the autoplay path, falling through to the phase-B static-poster path under `LocalInspectionMode.current`.

For each visible feed item, `LoadedFeedContent` MUST ALSO build a `quotedVideoEmbedSlot: @Composable ((QuotedEmbedUi.Video) -> Unit)?` lambda when `post.embed.quotedRecord != null`, keyed by `(post.embed.quotedRecord!!.uri, coordinator)`, and pass it as the `quotedVideoEmbedSlot` parameter to `PostCard`. The slot is null when the post carries no quoted post (whether top-level or inside a `RecordWithMedia.record`).

The `EmbedUi.quotedRecord` extension property (defined in `:data:models`) is the canonical answer to "where does this post's quoted content live." `LoadedFeedContent` MUST NOT inline the chained-cast pattern (e.g. `(post.embed as? EmbedUi.Record)?.quotedPost?.uri`) at the slot-builder site — single source of truth in the model layer.

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

The system MUST expose an internal mapper extension `RecordViewRecordEmbedsUnion?.toQuotedEmbedUi(): QuotedEmbedUi` that dispatches the quoted post's `embeds` list (the lexicon allows multiple but in practice carries 0–1) and produces:

- `QuotedEmbedUi.Empty` for `null` (no inner embed) or an empty list — the mapper consumes `embeds.firstOrNull()`.
- `QuotedEmbedUi.Images` for an inner `ImagesView` (same payload as the parent `EmbedUi.Images` mapping).
- `QuotedEmbedUi.Video` for an inner `VideoView` whose `playlist` is non-blank; otherwise `QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.video")`.
- `QuotedEmbedUi.External` for an inner `ExternalView` (with the same precomputed `domain` as the parent `EmbedUi.External` mapping; the existing `displayDomainOf` helper is reused).
- `QuotedEmbedUi.QuotedThreadChip` for an inner `RecordView` — this is the recursion-bound sentinel; the mapper does NOT recurse into the doubly-quoted post.
- `QuotedEmbedUi.Unsupported("app.bsky.embed.recordWithMedia")` for an inner `RecordWithMediaView` (out of scope; tracked under `nubecita-umn`).
- `QuotedEmbedUi.Unsupported(typeUri)` for the `Unknown` open-union member, carrying the wire `$type`.

To avoid logic duplication between the parent and inner mappers, the per-variant payload construction MUST be extracted into private helpers (e.g. `ImagesView.toImageUiList()`, `VideoView.toVideoPayload()`) called from both dispatch sites. Wrapper-type duplication (`EmbedUi.Images` vs `QuotedEmbedUi.Images`) is acceptable because the underlying payloads (`ImmutableList<ImageUi>`, etc.) are shared.

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

The system MUST decode a `RecordViewRecord.value: JsonObject` defensively. If the embedded post record cannot be decoded as a valid `app.bsky.feed.post` (missing required `text` / `createdAt`, type-incompatible value), OR if the decoded `createdAt` is not a parseable RFC3339 timestamp, the mapper MUST produce `EmbedUi.RecordUnavailable(Reason.Unknown)` for that embed slot. The parent post MUST still map to a non-null `PostUi` — a malformed quoted record is NEVER a reason to drop the parent post from the feed.

This contract preserves the existing `FeedViewPostMapper` total-over-the-response-shape rule from the parent-post decoding path — both layers use the same `runCatching { ... }.getOrNull()` shape against the same shared `recordJson` instance.

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

### Requirement: `FeedScreen` consumes `LocalScrollToTopSignal` and hosts the compose FAB

`FeedScreen` SHALL opt into the `core-common-navigation` scroll-to-top contract AND host the compose-new-post entry point in its Scaffold's `floatingActionButton` slot. Two coordinated behaviors:

1. **Signal collector.** A `LaunchedEffect` keyed on `(LocalScrollToTopSignal.current, listState)` collects the flow and calls `listState.animateScrollToItem(0)` on each emission. Both keys are required so the collector restarts cleanly if either reference changes (e.g., a new MainShell composition or a fresh `LazyListState` from `rememberSaveable` after process death). This behavior is preserved verbatim from the prior scroll-to-top change.
2. **Compose FAB.** A badge-wrappable, icon-only FAB (`FloatingActionButton`, `LargeFloatingActionButton`, or `SmallFloatingActionButton` — NOT `ExtendedFloatingActionButton`, which the `:core:drafts` follow-up cannot cleanly badge) rendered in the existing `Scaffold.floatingActionButton` slot whenever the feed view-state is `FeedScreenViewState.Loaded`. The FAB size SHALL adapt to width class: `FloatingActionButton` (56dp) at Compact width, `LargeFloatingActionButton` (96dp) at Medium and Expanded widths per the M3 expressive guidance. The FAB MUST NOT be gated on scroll position — it is visible at `firstVisibleItemIndex == 0` and at any deeper position. Its content is an `Icon(Icons.Default.Edit, ...)` (or M3's expressive create-equivalent) with a localized content description (`R.string.feed_compose_new_post`). Its `onClick` invokes a width-class-conditional launcher: at Compact width it pushes `ComposerRoute()` onto `LocalMainShellNavState.current`; at Medium/Expanded widths it transitions the `MainShell`-scoped composer-launcher state holder to `Open(replyToUri = null)`. The FAB MUST NOT appear over `InitialLoading`, `Empty`, or `InitialError` view-states.

The `FeedViewModel` is unchanged. No new state field, no new event, no new effect. Both the compose FAB onClick and the signal collector run at the screen Composable layer; they don't cross the VM boundary (per the `mvi-foundation` capability's "VMs don't see CompositionLocals" rule).

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
- **THEN** MainShell emits `Unit` via `LocalScrollToTopSignal`, the `FeedScreen` `LaunchedEffect` collector receives the emission, and `listState.animateScrollToItem(0)` runs

#### Scenario: VM is unchanged

- **WHEN** the source tree of `FeedViewModel` / `FeedState` / `FeedEvent` / `FeedEffect` is diffed before / after this change
- **THEN** there are NO additions or modifications. The compose FAB tap path does not pass through the VM.

### Requirement: Each post in the feed exposes a reply tap target that opens the composer in reply mode via the width-class-conditional launcher

The system SHALL render a reply affordance on every `PostCard` in `FeedScreen`'s loaded list. Tapping the affordance MUST invoke the same width-class-conditional `launchComposer(replyToUri = post.uri.toString())` helper used by the Feed-tab compose FAB (see the *Adaptive container* requirement in `feature-composer`'s spec) — at Compact width that pushes `ComposerRoute(...)` onto `LocalMainShellNavState.current`; at Medium/Expanded width it transitions the `MainShell`-scoped composer-launcher state to `Open`. The affordance MUST be reachable through the existing `PostCard` action row (no new card-level state shape). The tap path MUST NOT involve `FeedViewModel` — navigation flows from the card's onClick lambda through a screen-level handler that calls the launcher directly. An earlier draft of this requirement hard-coded `LocalMainShellNavState.current.add(...)` for both width classes; that conflicted with the adaptive-container requirement and is corrected here.

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
