package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable

/**
 * One frame's worth of state for a single feed entry — what the user
 * conceptually thinks of as "a post in my timeline" — projected into
 * the shape the renderer needs.
 *
 * Two variants:
 *
 * - [Single] — a standalone feed post. The wire-level
 *   `app.bsky.feed.defs#feedViewPost` carries no `reply`, OR carries
 *   one whose `parent` is the lexicon's `BlockedPost` / `NotFoundPost`
 *   variant (we silently fall back to standalone rendering for those —
 *   see the `feature-feed` capability spec). The renderer treats this
 *   as a single `PostCard`.
 *
 * - [ReplyCluster] — a cross-author or cross-time reply. The post the
 *   user follows ([leaf]) is a reply; the thread starter ([root]) and
 *   the immediate parent ([parent]) are projected from the wire
 *   `replyRef`. The renderer treats this as a single LazyColumn item
 *   internally laid out as root + optional fold + parent + leaf, joined
 *   by avatar-gutter connector lines (see `:designsystem`'s
 *   `ThreadCluster`). [hasEllipsis] indicates intermediate posts were
 *   elided between root and parent — the heuristic
 *   `replyRef.grandparentAuthor != null && grandparentAuthor.did !=
 *   root.author.did` is described in the `feature-feed` capability spec.
 *
 * `FeedItemUi` does NOT carry per-feed metadata (e.g. repost-attribution,
 * feed-context-string from `getFeed` responses). That metadata stays on
 * the leaf [PostUi] (`repostedBy` and similar fields). This sealed
 * type's job is to express cluster-vs-single rendering shape, not to
 * enrich per-post metadata.
 *
 * The sealed-interface declaration enables exhaustive `when` checking
 * at the render dispatch — adding a future variant (e.g. a
 * `SelfThreadChain` for `nubecita-m28.2` Section B's same-author
 * detection) would force every render dispatch site to handle it.
 */
@Stable
public sealed interface FeedItemUi {
    /**
     * Stable identifier used as the LazyColumn `key` lambda.
     *
     * For [Single] this is the post's URI. For [ReplyCluster] this is
     * the leaf's URI — pagination + scroll-position are anchored on
     * the leaf because that's "the post in your timeline" from the
     * user's perspective; root + parent are context.
     */
    public val key: String

    public data class Single(
        val post: PostUi,
    ) : FeedItemUi {
        override val key: String get() = post.id
    }

    public data class ReplyCluster(
        val root: PostUi,
        val parent: PostUi,
        val leaf: PostUi,
        val hasEllipsis: Boolean,
    ) : FeedItemUi {
        override val key: String get() = leaf.id
    }
}
