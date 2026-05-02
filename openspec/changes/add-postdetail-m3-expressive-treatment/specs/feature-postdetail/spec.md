## ADDED Requirements

### Requirement: `PostDetailRoute` is the canonical NavKey for the post-detail surface

The system SHALL expose `net.kikin.nubecita.feature.postdetail.api.PostDetailRoute(postUri: String)` as the only `androidx.navigation3.runtime.NavKey` that navigates to the post-detail screen. The single field is a plain `String` (not the lexicon-typed `AtUri` value class) so the NavKey serialization format stays a single primitive — call sites construct `AtUri(postUri)` only at the XRPC boundary, mirroring the same pattern `:feature:feed:impl`'s like/repost path uses for `PostUi.id`. Every entry into the post-detail surface — feed PostCard body tap, ThreadFold "View full thread", future deep-link / permalink routing — MUST construct a `PostDetailRoute` instance. The route MUST live in `:feature:postdetail:api` (NavKey-only module per the api/impl convention in `CLAUDE.md`).

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

The system SHALL expose an `internal interface PostThreadRepository` in `:feature:postdetail:impl` with the method `suspend fun getPostThread(uri: String): Result<ImmutableList<ThreadItem>>`. The repository's `uri` parameter is a plain `String` matching `PostDetailRoute.postUri`; the wrap to `AtUri` (and any `depth` / `parentHeight` lexicon parameters) lives inside the implementation. The repository returns the already-mapped `ImmutableList<ThreadItem>` rather than a raw response wrapper — projection is the repository's responsibility, mediated by `PostThreadMapper` (which delegates to `:core:feed-mapping`'s shared helpers per the `core-feed-mapping` capability).

The default implementation MUST be the only class in `:feature:postdetail:impl` that imports the atproto-kotlin client surface for `app.bsky.feed.getPostThread`. `PostDetailViewModel` MUST inject the interface, never the concrete class. The interface and its implementation MUST stay `internal` to `:feature:postdetail:impl` until a second consumer (notifications, search, deep-link landings) requires the same fetch surface — at that point a follow-on change promotes them to a shared module.

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

The system SHALL expose `PostDetailViewModel` extending `MviViewModel<PostDetailState, PostDetailEvent, PostDetailEffect>` per the project's MVI conventions. `PostDetailState` MUST carry an `items: ImmutableList<ThreadItem>` field (flat) and a `loadStatus: PostDetailLoadStatus` field (sealed sum). `PostDetailLoadStatus` is a `sealed interface` with exactly the variants:

- `Idle` — no load is in flight
- `InitialLoading` — first load (no items yet)
- `Refreshing` — pull-to-refresh in progress; existing items still rendered
- `InitialError(error: PostDetailError)` — sticky; the screen renders a full-screen retry layout

`PostDetailError` is a sibling sealed interface with variants `Network`, `Unauthenticated`, `NotFound`, and `Unknown(cause: String?)`. The "post not found" condition is surfaced via `PostDetailLoadStatus.InitialError(PostDetailError.NotFound)` — NOT a top-level `PostDetailLoadStatus.NotFound` variant. The "blocked root" condition is surfaced via a single `ThreadItem.Blocked` row in `items` (with `loadStatus == Idle`) — NOT a top-level `PostDetailLoadStatus.BlockedRoot` variant. The state MUST NOT use a flat `isLoading: Boolean` — these lifecycle phases are mutually exclusive per the project's MVI flat-vs-sealed rule in `CLAUDE.md`.

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

The screen SHALL render a circle-shaped floating reply affordance in the `Scaffold`'s `floatingActionButton` slot. The affordance MUST be implemented via `androidx.compose.material3.FloatingActionButton` (or an M3 Expressive FAB variant if available at the catalog's material3 version) — never via a hand-positioned `Box` / custom drawing. On tap, the screen MUST emit a navigation `UiEffect` that the screen's effect collector pushes via `LocalMainShellNavState.current.add(<composer NavKey from nubecita-8f6.3>)` — the same effect-collector pattern PostDetailScreen already uses for `NavigateToPost` / `NavigateToAuthor`. (PostCard's existing `onReply` callback is a no-op as of m28.5.1 — both `FeedViewModel.OnReplyClicked` and PostDetailScreen's reply slot drop the gesture — so there is no pre-existing reply-navigation implementation to mirror; this requirement establishes the first one.) The affordance MUST always be visible — no hide-on-scroll behavior in v1 (an explicitly-deferred decision per `design.md`).

Because the FAB floats above the LazyColumn at a fixed anchor, the LazyColumn MUST apply a bottom `contentPadding` equal to at least `FAB height + standard edge spacing` (target ~80–100dp) so the user can scroll the bottom-most reply completely above the FAB. Without this padding the FAB permanently occludes the lower half of the last reply when the user reaches the end of the thread — captured as a screenshot test in the with-replies fixture.

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

The system SHALL update `PostThreadMapper` to delegate every `ThreadItem.{Ancestor, Focus, Reply}`'s `PostUi.embed` slot to the shared `toEmbedUi` dispatch in `:core:feed-mapping`. The previously-shipped `EmbedUi.Empty` placeholder (m28.5.1's deferred-mapping shortcut) MUST be removed — every post in a thread response MUST be projected with the same embed-dispatch behavior the feed produces, so single-image, multi-image, video, external, record, and recordWithMedia embeds all render correctly on `PostDetailScreen`. Without this requirement satisfied, the carousel and image-tap requirements below cannot be exercised on real thread responses.

`#blockedPost` and `#notFoundPost` siblings continue to map to `ThreadItem.Blocked` / `ThreadItem.NotFound` rows (no `EmbedUi` projection — they have no embed slot).

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

### Requirement: Image tap dispatches a media-viewer navigation effect

The screen SHALL emit a `PostDetailEffect.NavigateToMediaViewer(uri: AtUri, imageIndex: Int)` effect on every image tap inside the Focus post and inside the multi-image carousel slides. The screen's effect collector MUST handle the effect by routing to the fullscreen media viewer destination via `LocalMainShellNavState.current.add(...)`.

If the fullscreen media viewer route does not yet exist in `:core:common:navigation`, the collector MUST (a) log a debug Timber entry tagged `PostDetailScreen` and (b) surface a transient Snackbar on the screen's `SnackbarHostState` reading "Fullscreen viewer coming soon" (or equivalent localized string). The Snackbar gives immediate tactile feedback that the tap registered, without blocking the user's flow the way a dialog would. The tap MUST NOT crash, and a follow-up bd issue MUST be filed tracking the missing destination. When the destination route ships in a follow-up change, the Snackbar branch is removed and the effect collector calls `LocalMainShellNavState.current.add(<media viewer NavKey>)` instead — call sites in PostCard and the carousel slides do not change.

#### Scenario: Image tap on focus single-image post emits effect

- **WHEN** the user taps the rendered image inside a single-image Focus post
- **THEN** `PostDetailEffect.NavigateToMediaViewer(uri = post.uri, imageIndex = 0)` is sent through the screen's effect channel

#### Scenario: Image tap on carousel slide emits effect with slide index

- **WHEN** the user taps the second slide of a three-image Focus carousel
- **THEN** `PostDetailEffect.NavigateToMediaViewer(uri = post.uri, imageIndex = 1)` is sent

#### Scenario: Missing media-viewer route surfaces a snackbar

- **WHEN** the effect collector receives `NavigateToMediaViewer` and the media-viewer `NavKey` type is not yet declared in `:core:common:navigation`
- **THEN** the collector logs a Timber debug entry tagged `PostDetailScreen` AND shows a transient Snackbar on the screen's `SnackbarHostState` with the "coming soon" copy; the user-visible result is a tap that produces a brief acknowledgment but no navigation transition — never a crash, never a blocking dialog

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
