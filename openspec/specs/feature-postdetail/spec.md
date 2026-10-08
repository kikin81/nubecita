# feature-postdetail Specification

## Purpose
The thread view: `PostDetailRoute` over `PostThreadRepository` as the single caller of `getPostThread`, the sealed `ThreadItem` projection of a thread response and load-status state machine, the focus post's visual emphasis, embeds and media-viewer navigation, pull-to-refresh, the floating reply affordance, and the scroll-reactive toolbar.
## Requirements
### Requirement: Image tap dispatches a media-viewer navigation effect

The screen SHALL emit `PostDetailEffect.NavigateToMediaViewer(postUri: String, imageIndex: Int)` on every image tap inside the focus post and carousel slides. The screen collector MUST invoke hoisted callback `onNavigateToMediaViewer(postUri, imageIndex)`, wired in `PostDetailNavigationModule` as `LocalMainShellNavState.current.add(MediaViewerRoute(postUri, imageIndex))`. The obsolete coming-soon Snackbar and debug log MUST be removed.

#### Scenario: Image tap on focus single-image post emits effect

- **WHEN** the user taps the rendered image inside a single-image Focus post
- **THEN** `PostDetailEffect.NavigateToMediaViewer(postUri = post.uri, imageIndex = 0)` is sent through the screen's effect channel

#### Scenario: Image tap on carousel slide emits effect with slide index

- **WHEN** the user taps the second slide of a three-image Focus carousel
- **THEN** `PostDetailEffect.NavigateToMediaViewer(postUri = post.uri, imageIndex = 1)` is sent

#### Scenario: Effect collector invokes hoisted navigation callback

- **WHEN** the screen's `LaunchedEffect` collector receives `PostDetailEffect.NavigateToMediaViewer(postUri, imageIndex)`
- **THEN** the collector invokes `onNavigateToMediaViewer(postUri, imageIndex)` exactly once; no Snackbar is dispatched and no Timber breadcrumb is logged

#### Scenario: Entry block wires the callback to MainShell navigation

- **WHEN** `PostDetailNavigationModule`'s entry block constructs the `PostDetailScreen` Composable
- **THEN** the `onNavigateToMediaViewer` parameter is wired as `{ uri, index -> navState.add(MediaViewerRoute(postUri = uri, imageIndex = index)) }` where `navState` is `LocalMainShellNavState.current`; the `MediaViewerRoute` symbol is imported from `:feature:mediaviewer:api`

#### Scenario: Snackbar fallback string and Timber breadcrumb are removed

- **WHEN** the source tree of `:feature:postdetail:impl` is grepped for `media_viewer_coming_soon` and for the nubecita-e02 Timber tag string
- **THEN** zero matches are returned in committed source — both have been deleted as part of this change

### Requirement: The post-detail toolbar swaps its title for the focus post's author on scroll

`PostDetailScreenContent`'s `topBar` SHALL render a `PostDetailTopBar` whose title is scroll-reactive. At rest it displays localized "Post" (`R.string.postdetail_title`). Once the focus post scrolls under the app bar, the title is replaced by an author block displaying avatar (28dp `NubecitaAvatar`) and display name (falling back to `@handle`). The toolbar tracks only the focus post author and is non-interactive.

#### Scenario: Toolbar reads "Post" at rest

- **WHEN** the post-detail screen renders at scroll position 0
- **THEN** the toolbar title is the localized "Post" string, and no avatar or author name is present in the bar. (Note: the resting "Post" title is semantically identical to the previous inline bar, but wrapping it in `AnimatedContent` sub-pixel-shifts the Scaffold body — so 5 focus-bearing LIGHT screenshot baselines move by a few shadow pixels. That is expected and imperceptible; see `design.md` Risks. It is NOT a byte-for-byte-unchanged guarantee.)

#### Scenario: Author appears once the focus card scrolls under the bar

- **WHEN** the user scrolls until the focus card's top edge is tucked at least `enterThresholdPx` (56dp) above the bottom of the app bar
- **THEN** the "Post" title fades out in place, and the author block slides in from the layout-direction start edge while fading in.

#### Scenario: Author disappears on the way back to the top

- **WHEN** the author block is showing and the user scrolls back until the focus card's top edge has come back down past `exitThresholdPx` (40dp)
- **THEN** the author block slides back out toward the start edge and fades out, and the "Post" title fades back in. The transition is the exact reverse of the entry.

#### Scenario: Threads with ancestors keep the "Post" title until the focus card passes

- **GIVEN** a thread whose focus post has one or more ancestors rendered above it, so the list opens at the root ancestor
- **WHEN** the user scrolls through the ancestors but the focus card has not yet reached the app bar
- **THEN** the toolbar still reads "Post". The author appears only once the focus card itself passes under the bar — never while the focus post is still below the fold.

#### Scenario: No focus post resolved

- **WHEN** the screen is in `InitialLoading`, `InitialError`, or any state where `PostDetailState.focusPost` is null
- **THEN** the toolbar reads "Post" at every scroll position and no author block can appear.

### Requirement: The swap threshold is a hysteresis band evaluated by a pure function

The show/hide author decision SHALL be computed by internal pure function `shouldShowAuthorInBar(focusIndex, firstVisibleItemIndex, focusItemTopPx, enterThresholdPx, exitThresholdPx, currentlyShown)` returning `Boolean`. It uses distinct enter (56dp) and exit (40dp) thresholds. When the focus card is not in `visibleItemsInfo`, it compares `focusIndex` against `firstVisibleItemIndex` to determine visibility.

#### Scenario: Focus card scrolled entirely off the top

- **WHEN** `focusIndex < firstVisibleItemIndex` and the focus card is absent from `visibleItemsInfo`
- **THEN** the function returns `true` — the author is shown.

#### Scenario: Focus card still below the fold

- **WHEN** the focus card is absent from `visibleItemsInfo` and `focusIndex >= firstVisibleItemIndex`
- **THEN** the function returns `false` — the author is not shown.

#### Scenario: Hysteresis band holds state

- **GIVEN** the focus card is visible and tucked 48dp under the bar (between the 40dp exit and 56dp enter thresholds)
- **WHEN** `currentlyShown` is `true`
- **THEN** the function returns `true` (it stays shown).
- **WHEN** `currentlyShown` is `false`
- **THEN** the function returns `false` (it stays hidden).

#### Scenario: No focus row

- **WHEN** `focusIndex` is `-1`
- **THEN** the function returns `false` regardless of every other argument.

### Requirement: The toolbar's motion honours reduce-motion and layout direction

The title swap SHALL use `AnimatedContent` with `SizeTransform(clip = false)` and motion specs from `MaterialTheme.motionScheme` (`defaultSpatialSpec()` for slide, `defaultEffectsSpec()` for fade). The horizontal slide offset SHALL be a fixed 24dp with sign derived from `LocalLayoutDirection`. Outgoing title fades without translating.

#### Scenario: Reduce-motion is honoured without a feature-local branch

- **WHEN** the system reduce-motion preference is enabled, so `NubecitaTheme` installs `NubecitaMotionScheme(isReduced = true)`
- **THEN** the slide and fade collapse to the scheme's short linear tweens, with no reduce-motion conditional written inside `:feature:postdetail:impl`.

#### Scenario: RTL slides in from the correct edge

- **WHEN** the app renders under an RTL layout direction
- **THEN** the author block slides in from the **right** (the visual start edge), because the slide offset's sign is taken from `LocalLayoutDirection`. It does not slide in from the left.

#### Scenario: Slide speed is independent of name length

- **WHEN** the author's display name is very long versus very short
- **THEN** the block travels the same fixed 24dp over the same spec in both cases. The slide distance does not scale with the rendered content width.

### Requirement: The scroll-reactive toolbar introduces no ViewModel or contract change

`PostDetailViewModel`, `PostDetailState`, `PostDetailEvent`, and `PostDetailEffect` SHALL be unchanged by this change. Scroll position terminates at the screen Composable, consistent with the project's rule that ViewModels do not observe Compose-runtime scroll state.

The author is derived from the existing `PostDetailState.focusPost` extension; the focus row index is derived as `items.indexOfFirst { it is ThreadItem.Focus }` at the screen layer.

#### Scenario: VM sources are untouched

- **WHEN** `PostDetailViewModel.kt` and `PostDetailContract.kt` are diffed before and after this change
- **THEN** there are no additions or modifications.

#### Scenario: Existing VM tests pass unmodified

- **WHEN** `./gradlew :feature:postdetail:impl:testDebugUnitTest` runs after this change merges
- **THEN** every pre-existing test method passes without source-level modification.

### Requirement: `PostDetailRoute` is the canonical NavKey for the post-detail surface

The system SHALL expose `net.kikin.nubecita.feature.postdetail.api.PostDetailRoute(postUri: String)` as the only `NavKey` that navigates to the post-detail screen. The field is a primitive `String`. All post-detail navigation entries MUST construct `PostDetailRoute`. The route MUST reside in `:feature:postdetail:api`.

#### Scenario: Feed PostCard body tap navigates via PostDetailRoute

- **WHEN** the user taps the body region of a `PostCard` in `FeedScreen` (excluding the avatar and action-row regions)
- **THEN** `FeedViewModel` emits a `FeedEffect.NavigateToPost(postUri)` and the screen's collector calls `LocalMainShellNavState.current.add(PostDetailRoute(postUri = postUri))` — no other `NavKey` type is constructed for this transition

#### Scenario: ThreadFold tap navigates via PostDetailRoute

- **WHEN** a user taps a `ThreadFold` rendered inside a feed `ReplyCluster` and the cluster's leaf URI differs from the current focus
- **THEN** the `onClick` dispatches a navigation that adds `PostDetailRoute(postUri = leafUri)` to `LocalMainShellNavState`

#### Scenario: NavKey carries a primitive string

- **WHEN** `PostDetailRoute` is serialized via the `kotlinx.serialization` Nav3 surface
- **THEN** the encoded form is a single string field; no nested AtUri wrapper appears in the persisted nav state

### Requirement: `PostThreadRepository` is the only layer that calls `getPostThread`

The system SHALL expose `internal interface PostThreadRepository` in `:feature:postdetail:impl` with `suspend fun getPostThread(uri: String): Result<ImmutableList<ThreadItem>>`. Its default implementation MUST be the only class in `:feature:postdetail:impl` that imports `app.bsky.feed.getPostThread`. `PostDetailViewModel` MUST inject the interface, which projects responses via `PostThreadMapper`.

#### Scenario: ViewModel injects the interface

- **WHEN** `PostDetailViewModel`'s constructor is inspected
- **THEN** it MUST declare a `private val postThreadRepository: PostThreadRepository` parameter (interface type) and MUST NOT declare the concrete default class or the atproto-kotlin client directly

#### Scenario: Repository returns mapped ThreadItems

- **WHEN** `getPostThread(uri)` resolves successfully
- **THEN** the `Result.success` value is an `ImmutableList<ThreadItem>` with embed slots populated via `:core:feed-mapping` — no caller of the repository needs to perform projection

#### Scenario: Single import of the thread service

- **WHEN** the source tree of `:feature:postdetail:impl` is searched for imports of the atproto-kotlin client surface carrying `getPostThread`
- **THEN** the only matching import is in the default `PostThreadRepository` implementation

### Requirement: `PostDetailViewModel` state machine has a sealed load-status sum

`PostDetailViewModel` SHALL extend `MviViewModel<PostDetailState, PostDetailEvent, PostDetailEffect>`. `PostDetailState` MUST contain `items: ImmutableList<ThreadItem>` and sealed sum `loadStatus: PostDetailLoadStatus` (`Idle`, `InitialLoading`, `Refreshing`, `InitialError(error: PostDetailError)`). `PostDetailError` has variants `Network`, `Unauthenticated`, `NotFound`, `Unknown`. The state MUST NOT use a flat `isLoading: Boolean`.

#### Scenario: Initial load transitions Idle → InitialLoading → Idle

- **WHEN** `PostDetailViewModel` is constructed and a `PostDetailEvent.Load` fires
- **THEN** `loadStatus` transitions `Idle → InitialLoading`, and on a successful `getPostThread` response transitions to `Idle` with `items` populated

#### Scenario: Refresh from a loaded state

- **WHEN** the user pulls to refresh while `loadStatus == Idle` and `items` is already populated
- **THEN** `loadStatus` transitions `Idle → Refreshing`, the existing `items` are preserved during the in-flight fetch, and on success the list is replaced and `loadStatus` returns to `Idle`

#### Scenario: Initial fetch failure surfaces an error variant

- **WHEN** `getPostThread` returns a network or parsing failure on the first load
- **THEN** `loadStatus` becomes `InitialError(PostDetailError.Network)` (or the corresponding `PostDetailError` variant); `items` remains empty; the screen renders an error state with a retry affordance

#### Scenario: Not-found surfaced as InitialError, not a top-level variant

- **WHEN** `getPostThread` returns a 404 or surfaces `#notFoundPost` at the focus position
- **THEN** `loadStatus` becomes `InitialError(PostDetailError.NotFound)` — there is no `PostDetailLoadStatus.NotFound` variant

#### Scenario: Blocked root surfaced as a ThreadItem row, not a top-level variant

- **WHEN** `getPostThread` returns `#blockedPost` for the requested URI's root
- **THEN** `loadStatus` is `Idle`; `items` contains a single `ThreadItem.Blocked` row carrying the focus URI; the screen renders the row's "post is unavailable" placeholder via the standard ThreadItem dispatch — there is no `PostDetailLoadStatus.BlockedRoot` variant

### Requirement: `ThreadItem` is the sealed projection of a thread response

The system SHALL expose sealed `ThreadItem` in `:feature:postdetail:impl` with variants `Ancestor(post: PostUi)`, `Focus(post: PostUi)`, `Reply(post: PostUi, depth: Int)`, `Fold(elidedCount: Int)`, `Blocked(uri: AtUri)`, and `NotFound(uri: AtUri)`. The mapper MUST produce exactly one `Focus`, zero or more `Ancestor`s, and zero or more `Reply`s, preserving blocked and missing posts.

#### Scenario: Mapper produces one Focus per thread response

- **WHEN** the mapper consumes any `#threadViewPost` response
- **THEN** the resulting `List<ThreadItem>` contains exactly one `Focus` variant

#### Scenario: Blocked sibling preserved as Blocked variant

- **WHEN** the mapper encounters a `#blockedPost` reference among the focus post's replies
- **THEN** the result includes a `Blocked(uri = …)` entry at the position the reply would have occupied

### Requirement: Focus Post visual emphasis via container color and shape

The screen SHALL render `ThreadItem.Focus` `PostCard` inside a `Surface` with `color = MaterialTheme.colorScheme.surfaceContainerHigh` and `shape = RoundedCornerShape(24.dp)`. Ancestors, replies, and folds MUST use default `surface`. Focus emphasis MUST NOT use typography weight bumps, outlines, or custom drawing. The contrast delta MUST be visible in both Light and Dark themes.

#### Scenario: Focus post container in light theme

- **WHEN** `PostDetailScreen` renders a thread with three items (one Ancestor, one Focus, one Reply) under `NubecitaTheme(darkTheme = false)`
- **THEN** the snapshot shows the Focus PostCard wrapped in a Surface whose background reads as `surfaceContainerHigh` and whose corners are rounded at 24dp; the Ancestor and Reply render on the standard `surface` background

#### Scenario: Focus post container in dark theme

- **WHEN** the same three-item thread renders under `NubecitaTheme(darkTheme = true)`
- **THEN** the snapshot shows the same hierarchy — Focus on `surfaceContainerHigh`, Ancestors / Replies on `surface` — with the contrast delta still perceivable in dark mode

#### Scenario: No typography weight bump on focus

- **WHEN** the Focus PostCard is inspected in a Compose semantics tree
- **THEN** the body `TextStyle.fontWeight` matches the `bodyLarge` default used for ancestors and replies — emphasis MUST come from the container, not the type

### Requirement: Floating reply composer affordance

The screen SHALL render a circular `FloatingActionButton` in the `Scaffold`'s `floatingActionButton` slot that emits a navigation effect pushing the composer route via `LocalMainShellNavState.current`. It MUST remain visible across scroll. `LazyColumn` MUST apply bottom `contentPadding` (~80–100dp) so the final reply scrolls clear of the FAB.

#### Scenario: FAB visible at all scroll positions

- **WHEN** the user scrolls the LazyColumn from the top of the ancestors region to the bottom of the replies region
- **THEN** the floating reply FAB stays visible at the same anchor without animation or fade

#### Scenario: FAB tap pushes composer route

- **WHEN** the user taps the floating reply FAB
- **THEN** the screen's effect collector invokes `LocalMainShellNavState.current.add(<composer NavKey from nubecita-8f6.3>)` — the same call shape used by the PostCard reply button

#### Scenario: Bottom contentPadding clears the FAB

- **WHEN** the user scrolls to the bottom of a thread whose reply count fills the viewport
- **THEN** the LazyColumn's bottom `contentPadding` allows the final reply to scroll fully above the FAB anchor — no portion of any reply is occluded by the FAB at the resting scroll position

### Requirement: `PostThreadMapper` populates embed slots via `:core:feed-mapping`

`PostThreadMapper` SHALL delegate every `ThreadItem.{Ancestor, Focus, Reply}`'s `PostUi.embed` slot to shared `toEmbedUi` in `:core:feed-mapping`. `EmbedUi.Empty` placeholders MUST NOT be used for posts with embeds. Blocked and missing siblings continue to map to `ThreadItem.Blocked` / `ThreadItem.NotFound`.

#### Scenario: Focus post with images carries EmbedUi.Images

- **WHEN** the mapper consumes a `#threadViewPost` whose focus post's wire-level embed is `app.bsky.embed.images#view` carrying three image items
- **THEN** the resulting `ThreadItem.Focus.post.embed` is `EmbedUi.Images(items)` with three `ImageUi` entries — NOT `EmbedUi.Empty`

#### Scenario: Embed dispatch is byte-identical between feed and post-detail mappers

- **WHEN** the same wire-level embed is fed through `FeedViewPostMapper.toPostUiOrNull` and `PostThreadMapper`'s post projection
- **THEN** both produce the same `EmbedUi` value — both delegate to the same `:core:feed-mapping` `toEmbedUi` function, never declaring divergent local embed dispatch

### Requirement: Multi-image embed renders via M3 carousel at the focus position

The screen SHALL allow the standard `:designsystem` `PostCard` image-embed rendering to delegate to `HorizontalMultiBrowseCarousel` for any post with `images.size > 1` (per the `design-system` capability's added requirement). The Focus PostCard MUST honor this behavior. The single-image post path at the focus position MUST stay unchanged from the current PostCard rendering.

#### Scenario: Three-image focus post renders carousel

- **WHEN** `PostDetailScreen` renders a Focus post whose `EmbedUi.Images.images.size == 3`
- **THEN** the snapshot shows a `HorizontalMultiBrowseCarousel` with three slides, each loaded via the existing Coil image pipeline; the carousel uses M3's default `preferredItemWidth` token rather than attempting to clone the single-image embed dimensions (which use `fillMaxWidth() + heightIn(max = EMBED_HEIGHT)` and have no carousel-equivalent)

#### Scenario: Single-image focus post unchanged

- **WHEN** `PostDetailScreen` renders a Focus post whose `EmbedUi.Images.images.size == 1`
- **THEN** the snapshot is byte-for-byte identical to the equivalent fixture rendered through the unmodified PostCard single-image path

### Requirement: Pull-to-refresh wraps the LazyColumn

The screen SHALL wrap its `LazyColumn` in `androidx.compose.material3.pulltorefresh.PullToRefreshBox` mirroring the pattern in `feature/feed/impl/.../FeedScreen.kt`. Pulling MUST dispatch `PostDetailEvent.Refresh`. The refresh indicator anchors at the top of the screen content area (above ancestors), not scoped to the focus-post region.

#### Scenario: Pull triggers refresh event

- **WHEN** the user performs a pull gesture from the top of the LazyColumn
- **THEN** `PostDetailViewModel` receives a `PostDetailEvent.Refresh` and `loadStatus` transitions to `Refreshing`

#### Scenario: Refreshing state shows the pull indicator

- **WHEN** `loadStatus == Refreshing`
- **THEN** the `PullToRefreshBox` indicator is visible in its loading position

### Requirement: Screenshot test harness covers the visual contract

`:feature:postdetail:impl/src/screenshotTest/` SHALL maintain screenshot tests covering: focused post with ancestors, with replies, single post without thread, blocked root fallback, 3-image carousel focus post, and container contrast pair in both Light and Dark themes.

#### Scenario: Container hierarchy contrast captured in both themes

- **WHEN** `./gradlew :feature:postdetail:impl:validateDebugScreenshotTest` runs
- **THEN** at least two snapshot files exist that differ only in `darkTheme` parameter, both showing the focus + ancestor + reply hierarchy, and any drift in either fails the validation
