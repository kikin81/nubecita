## ADDED Requirements

### Requirement: `PostDetailRoute` is the canonical NavKey for the post-detail surface

The system SHALL expose `net.kikin.nubecita.feature.postdetail.api.PostDetailRoute(uri: <AtUri-typed>)` as the only `androidx.navigation3.runtime.NavKey` that navigates to the post-detail screen. Every entry into the post-detail surface — feed PostCard body tap, ThreadFold "View full thread", future deep-link / permalink routing — MUST construct a `PostDetailRoute` instance. The route MUST live in `:feature:postdetail:api` (NavKey-only module per the api/impl convention in `CLAUDE.md`).

#### Scenario: Feed PostCard body tap navigates via PostDetailRoute

- **WHEN** the user taps the body region of a `PostCard` in `FeedScreen` (excluding the avatar and action-row regions)
- **THEN** `FeedViewModel` emits a `FeedEffect.NavigateToPost(uri)` and the screen's collector calls `LocalMainShellNavState.current.add(PostDetailRoute(uri))` — no other `NavKey` type is constructed for this transition

#### Scenario: ThreadFold tap navigates via PostDetailRoute

- **WHEN** a user taps a `ThreadFold` rendered inside a feed `ReplyCluster` and the cluster's leaf URI differs from the current focus
- **THEN** the `onClick` dispatches a navigation that adds `PostDetailRoute(leafUri)` to `LocalMainShellNavState`

### Requirement: `PostThreadRepository` is the only layer that calls `getPostThread`

The system SHALL expose an `internal interface PostThreadRepository` in `:feature:postdetail:impl` with at minimum a single method `suspend fun getPostThread(uri: AtUri, depth: Int = …, parentHeight: Int = …): Result<ThreadResponse>`. The default implementation MUST be the only class in `:feature:postdetail:impl` that imports `io.github.kikin81.atproto.app.bsky.feed.FeedService` (or equivalent client surface for `app.bsky.feed.getPostThread`). `PostDetailViewModel` MUST inject the interface, never the concrete class. The interface and its implementation MUST stay `internal` to `:feature:postdetail:impl` until a second consumer (notifications, search, deep-link landings) requires the same fetch surface — at that point a follow-on change promotes them to a `:core:feed` (or equivalent) module.

#### Scenario: ViewModel injects the interface

- **WHEN** `PostDetailViewModel`'s constructor is inspected
- **THEN** it MUST declare a `private val postThreadRepository: PostThreadRepository` parameter (interface type) and MUST NOT declare the concrete default class or the atproto-kotlin client directly

#### Scenario: Single import of the thread service

- **WHEN** the source tree of `:feature:postdetail:impl` is searched for imports of `io.github.kikin81.atproto.app.bsky.feed.FeedService` (or the equivalent `getPostThread` carrier)
- **THEN** the only matching import is in the default `PostThreadRepository` implementation

### Requirement: `PostDetailViewModel` state machine has a sealed load-status sum

The system SHALL expose `PostDetailViewModel` extending `MviViewModel<PostDetailState, PostDetailEvent, PostDetailEffect>` per the project's MVI conventions. `PostDetailState` MUST carry a single `loadStatus: PostDetailLoadStatus` field where `PostDetailLoadStatus` is a `sealed interface` with at minimum the variants `Idle`, `InitialLoading`, `Refreshing`, `InitialError(error: PostDetailError)`, `NotFound`, and `BlockedRoot`. The state MUST NOT use a flat `isLoading: Boolean` — these lifecycle phases are mutually exclusive per the project's MVI flat-vs-sealed rule in `CLAUDE.md`.

#### Scenario: Initial load transitions Idle → InitialLoading → Idle

- **WHEN** `PostDetailViewModel` is constructed and a `PostDetailEvent.Load` fires
- **THEN** `loadStatus` transitions `Idle → InitialLoading`, and on a successful `getPostThread` response transitions to `Idle` with `threadItems` populated

#### Scenario: Refresh from a loaded state

- **WHEN** the user pulls to refresh while `loadStatus == Idle` and the thread items are already populated
- **THEN** `loadStatus` transitions `Idle → Refreshing`, the existing `threadItems` are preserved during the in-flight fetch, and on success the list is replaced and `loadStatus` returns to `Idle`

#### Scenario: Initial fetch failure surfaces an error variant

- **WHEN** `getPostThread` returns a network or parsing failure on the first load
- **THEN** `loadStatus` becomes `InitialError(error)` carrying the typed error; `threadItems` remains empty; the screen renders an error state with a retry affordance

#### Scenario: Blocked-root response

- **WHEN** `getPostThread` returns a `#blockedPost` for the requested URI's root
- **THEN** `loadStatus` becomes `BlockedRoot`; the screen renders a placeholder explaining the post is unavailable

### Requirement: `ThreadItem` is the sealed projection of a thread response

The system SHALL expose a sealed `ThreadItem` projection in `:feature:postdetail:impl` with at minimum the variants `Ancestor(post: PostUi)`, `Focus(post: PostUi)`, `Reply(post: PostUi, depth: Int)`, `Fold(elidedCount: Int)`, `Blocked(uri: AtUri)`, and `NotFound(uri: AtUri)`. The mapper from `app.bsky.feed.defs#threadViewPost` MUST produce a single `Focus`, zero or more `Ancestor`s in chronological order from oldest to immediate-parent, and zero or more `Reply`s in the order returned by the server. `#blockedPost` and `#notFoundPost` siblings MUST map to the corresponding placeholder variants — never silently dropped.

#### Scenario: Mapper produces one Focus per thread response

- **WHEN** the mapper consumes any `#threadViewPost` response
- **THEN** the resulting `List<ThreadItem>` contains exactly one `Focus` variant

#### Scenario: Blocked sibling preserved as Blocked variant

- **WHEN** the mapper encounters a `#blockedPost` reference among the focus post's replies
- **THEN** the result includes a `Blocked(uri = …)` entry at the position the reply would have occupied

### Requirement: Focus Post visual emphasis via container color and shape

The screen SHALL render the `ThreadItem.Focus` `PostCard` inside a `Surface` whose `color = MaterialTheme.colorScheme.surfaceContainerHigh` and `shape = RoundedCornerShape(24.dp)`. Ancestors, replies, and folds MUST render with the existing `PostCard` defaults on `MaterialTheme.colorScheme.surface`. Focus emphasis MUST NOT come from a typography weight bump, MUST NOT come from a border / outline, and MUST NOT come from a custom shape morph or hand-rolled drawing — only from the container color + shape pair. The `surfaceContainerHigh ↔ surface` delta MUST remain visible in BOTH the default `Light` and `Dark` themes; this is enforced by paired screenshot tests under `feature/postdetail/impl/src/screenshotTest/`.

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

The screen SHALL render a circle-shaped floating reply affordance in the `Scaffold`'s `floatingActionButton` slot. The affordance MUST be implemented via `androidx.compose.material3.FloatingActionButton` (or an M3 Expressive FAB variant if available at the catalog's material3 version) — never via a hand-positioned `Box` / custom drawing. On tap, the screen MUST construct the composer `NavKey` shipped by `nubecita-8f6.3` and push it via `LocalMainShellNavState.current.add(...)`. The affordance MUST always be visible — no hide-on-scroll behavior in v1 (an explicitly-deferred decision per `design.md`).

#### Scenario: FAB visible at all scroll positions

- **WHEN** the user scrolls the LazyColumn from the top of the ancestors region to the bottom of the replies region
- **THEN** the floating reply FAB stays visible at the same anchor without animation or fade

#### Scenario: FAB tap pushes composer route

- **WHEN** the user taps the floating reply FAB
- **THEN** the screen's effect collector invokes `LocalMainShellNavState.current.add(<composer NavKey from nubecita-8f6.3>)` — the same call shape used by the PostCard reply button

### Requirement: Multi-image embed renders via M3 carousel at the focus position

The screen SHALL allow the standard `:designsystem` `PostCard` image-embed rendering to delegate to `HorizontalMultiBrowseCarousel` for any post with `images.size > 1` (per the `design-system` capability's added requirement). The Focus PostCard MUST honor this behavior. The single-image post path at the focus position MUST stay unchanged from the current PostCard rendering.

#### Scenario: Three-image focus post renders carousel

- **WHEN** `PostDetailScreen` renders a Focus post whose `EmbedUi.Images.images.size == 3`
- **THEN** the snapshot shows a `HorizontalMultiBrowseCarousel` with three slides, each preferring the carousel's default item width

#### Scenario: Single-image focus post unchanged

- **WHEN** `PostDetailScreen` renders a Focus post whose `EmbedUi.Images.images.size == 1`
- **THEN** the snapshot is byte-for-byte identical to the equivalent fixture rendered through the unmodified PostCard single-image path

### Requirement: Image tap dispatches a media-viewer navigation effect

The screen SHALL emit a `PostDetailEffect.NavigateToMediaViewer(uri: AtUri, imageIndex: Int)` effect on every image tap inside the Focus post and inside the multi-image carousel slides. The screen's effect collector MUST handle the effect by routing to the fullscreen media viewer destination via `LocalMainShellNavState.current.add(...)`. If the fullscreen media viewer route does not yet exist in `:core:common:navigation`, the collector MUST log a debug Timber entry and treat the effect as a no-op — the tap MUST NOT crash, and a follow-up bd issue MUST be filed tracking the missing destination.

#### Scenario: Image tap on focus single-image post emits effect

- **WHEN** the user taps the rendered image inside a single-image Focus post
- **THEN** `PostDetailEffect.NavigateToMediaViewer(uri = post.uri, imageIndex = 0)` is sent through the screen's effect channel

#### Scenario: Image tap on carousel slide emits effect with slide index

- **WHEN** the user taps the second slide of a three-image Focus carousel
- **THEN** `PostDetailEffect.NavigateToMediaViewer(uri = post.uri, imageIndex = 1)` is sent

#### Scenario: Missing media-viewer route is a logged no-op

- **WHEN** the effect collector receives `NavigateToMediaViewer` and the media-viewer `NavKey` type is not yet declared in `:core:common:navigation`
- **THEN** the collector logs a Timber debug entry tagged `PostDetailScreen` and returns; the user-visible result is a tap with no transition, never a crash

### Requirement: Pull-to-refresh wraps the LazyColumn

The screen SHALL wrap its `LazyColumn` in `androidx.compose.material3.pulltorefresh.PullToRefreshBox` mirroring the pattern in `feature/feed/impl/.../FeedScreen.kt`. Pulling MUST dispatch `PostDetailEvent.Refresh`. The refresh indicator anchors at the top of the screen content area (above ancestors), not scoped to the focus-post region.

#### Scenario: Pull triggers refresh event

- **WHEN** the user performs a pull gesture from the top of the LazyColumn
- **THEN** `PostDetailViewModel` receives a `PostDetailEvent.Refresh` and `loadStatus` transitions to `Refreshing`

#### Scenario: Refreshing state shows the pull indicator

- **WHEN** `loadStatus == Refreshing`
- **THEN** the `PullToRefreshBox` indicator is visible in its loading position

### Requirement: Screenshot test harness covers the visual contract

The capability SHALL maintain a screenshot-test harness under `feature/postdetail/impl/src/screenshotTest/` whose baselines cover at minimum: focused-post-with-ancestors, with-replies, single-post-no-thread, blocked-root-fallback, multi-image-carousel-at-focus (3-image fixture), and the container-hierarchy contrast pair captured in BOTH `Light` and `Dark` themes. The light-vs-dark contrast pair MUST be present — a regression in either theme MUST surface as a failed snapshot, not as a missing fixture.

#### Scenario: Container hierarchy contrast captured in both themes

- **WHEN** `./gradlew :feature:postdetail:impl:validateDebugScreenshotTest` runs
- **THEN** at least two snapshot files exist that differ only in `darkTheme` parameter, both showing the focus + ancestor + reply hierarchy, and any drift in either fails the validation
