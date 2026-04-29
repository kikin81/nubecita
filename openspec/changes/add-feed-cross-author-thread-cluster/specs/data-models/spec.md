## ADDED Requirements

### Requirement: `:data:models` provides a `FeedItemUi` sealed type for feed projections

`:data:models` SHALL expose a public `FeedItemUi` sealed interface as the projection target for feed mappers (timeline, future profile-feed, future search-feed). The interface SHALL have exactly two variants:

- `data class Single(val post: PostUi) : FeedItemUi` — a standalone feed entry. The wire-level `app.bsky.feed.defs#feedViewPost` carries no `reply`, or carries one whose `parent` cannot be projected to a `PostUi` (lexicon `BlockedPost` / `NotFoundPost`).
- `data class ReplyCluster(val root: PostUi, val parent: PostUi, val leaf: PostUi, val hasEllipsis: Boolean) : FeedItemUi` — a cross-author or cross-time reply. `leaf` is the post that lives in the user's timeline; `parent` is `replyRef.parent`; `root` is `replyRef.root`. `hasEllipsis = true` indicates that intermediate posts were elided between root and parent.

`FeedItemUi` SHALL NOT carry per-feed metadata (e.g. repost-attribution, feed-context-string from the `app.bsky.feed.getFeed` response). That metadata SHALL remain on the leaf `PostUi` (`repostedBy` and similar fields). The sealed type's job is to express cluster-vs-single rendering shape, not to enrich per-post metadata.

`FeedItemUi` SHALL be `@Stable` so Compose can skip recomposition of feed item containers when the wrapping projection doesn't change.

#### Scenario: Single variant carries one PostUi

- **WHEN** a feed entry's `feedViewPost.reply` is null (or `replyRef.parent` is `BlockedPost`/`NotFoundPost`)
- **THEN** the mapper produces `FeedItemUi.Single(leaf)` with the leaf's `PostUi` projection

#### Scenario: ReplyCluster variant carries root + parent + leaf

- **WHEN** a feed entry's `feedViewPost.reply.parent` is a renderable `PostView` and `replyRef.root` is a renderable `PostView`
- **THEN** the mapper produces `FeedItemUi.ReplyCluster(root, parent, leaf, hasEllipsis)` where `root`, `parent`, `leaf` are full `PostUi` projections and `hasEllipsis` follows the heuristic in the `feature-feed` capability

#### Scenario: FeedItemUi is exhaustive at compile time

- **WHEN** Kotlin compiles a `when (item: FeedItemUi)` expression in a render dispatch (e.g. `FeedScreen.LoadedFeedContent`)
- **THEN** the compiler SHALL warn if any variant is unhandled — the sealed-interface declaration enables exhaustive `when` checking
