## ADDED Requirements

### Requirement: `FeedViewPostMapper` exposes `toFeedItemUiOrNull` as the entry-point projection

`:feature:feed:impl/data/FeedViewPostMapper.kt` SHALL expose an `internal fun FeedViewPost.toFeedItemUiOrNull(): FeedItemUi?` extension as the entry-point mapper from a wire `FeedViewPost` to a renderable `FeedItemUi`. (The existing mapper API is module-`internal`; this matches that visibility.) The function SHALL return:

- `FeedItemUi.Single(leaf)` when `reply` is null or when `reply.parent` is a non-`PostView` lexicon variant (`BlockedPost`, `NotFoundPost`).
- `FeedItemUi.ReplyCluster(root, parent, leaf, hasEllipsis)` when `reply.parent` is a `PostView` AND `reply.root` is a `PostView`. The `hasEllipsis` field SHALL be `true` when `replyRef.grandparentAuthor != null && grandparentAuthor.did != root.author.did`, else `false`.
- `null` when the leaf post itself cannot be projected (malformed `post.record` JSON, unparseable `createdAt`) — same contract as the prior `toPostUiOrNull(...)`.

The existing `internal fun FeedViewPost.toPostUiOrNull()` SHALL remain available unchanged for the existing test surface, and SHALL be re-implemented to share its projection logic with the new entry point via a private `PostView`-receiver helper. New consumers (the repository, future feature mappers) SHALL use `toFeedItemUiOrNull` exclusively; `toPostUiOrNull` is retained for backwards compatibility with the existing 18+ mapper tests and will be removed in a future cleanup ticket once those tests migrate.

The mapper SHALL log a `Timber.w(...)` when falling back from `ReplyCluster` to `Single` due to a `BlockedPost` / `NotFoundPost` parent — production builds use Timber's release tree (no-op), so this is only visible in dev builds.

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

`FeedScreenViewState.Loaded`'s `posts: ImmutableList<PostUi>` field SHALL be renamed to `feedItems: ImmutableList<FeedItemUi>` and changed type. The `FeedViewModel`'s projection from session state to view state SHALL invoke `toFeedItemUiOrNull(...)` per `FeedViewPost` (instead of `toPostUiOrNull(...)`) and collect the non-null results into the immutable list.

Pagination + scroll behavior is unchanged: each `FeedItemUi` (whether `Single` or `ReplyCluster`) is one logical feed entry with one stable identifier (the leaf's URI, exposed via a `FeedItemUi.key` property or computed at the LazyColumn `key` lambda).

#### Scenario: Loaded carries FeedItemUi instead of PostUi

- **WHEN** `FeedViewModel`'s projection runs against a session state with N timeline entries that all map to non-null `FeedItemUi`
- **THEN** `FeedScreenViewState.Loaded.feedItems.size == N`

#### Scenario: LazyColumn key is the leaf's URI

- **WHEN** `LoadedFeedContent` invokes `LazyColumn { items(feedItems, key = { ... }) { ... } }`
- **THEN** the `key` for `FeedItemUi.Single(post)` SHALL be `post.id`, and for `FeedItemUi.ReplyCluster(...)` SHALL be `leaf.id` — pagination + scroll-position are anchored on the leaf in either case

### Requirement: `FeedScreen` dispatches on `FeedItemUi` to render `PostCard` or `ThreadCluster`

`LoadedFeedContent` SHALL dispatch on each `FeedItemUi` variant:

- `FeedItemUi.Single(post)` — render `PostCard(post = post, callbacks = ..., videoEmbedSlot = ...)` exactly as before.
- `FeedItemUi.ReplyCluster(root, parent, leaf, hasEllipsis)` — render `ThreadCluster(root = root, parent = parent, leaf = leaf, hasEllipsis = hasEllipsis, callbacks = ..., leafVideoEmbedSlot = ...)`.

`ThreadCluster` SHALL receive the `videoEmbedSlot` parameter for the **leaf** only; root + parent inside the cluster receive `videoEmbedSlot = null` (static-poster fallback for any video embeds in those posts).

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
