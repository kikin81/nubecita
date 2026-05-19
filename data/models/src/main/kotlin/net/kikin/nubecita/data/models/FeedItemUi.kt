package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

/**
 * One frame's worth of state for a single feed entry — what the user
 * conceptually thinks of as "a post in my timeline" — projected into
 * the shape the renderer needs.
 *
 * Three variants:
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
 * - [SelfThreadChain] — a same-author chain detected across consecutive
 *   feed entries. ≥ 2 posts, all by the same author, in chronological
 *   reply order (root-most first). The renderer treats this as a single
 *   LazyColumn item containing N stacked `PostCard`s joined by
 *   continuous avatar-gutter connector lines via `PostCard`'s existing
 *   `connectAbove` / `connectBelow` flags. The chain is detected at the
 *   feed-page level and merged across pagination boundaries (see the
 *   `feature-feed` capability spec for the strict link rule).
 *
 * `FeedItemUi` does NOT carry per-feed metadata (e.g. repost-attribution,
 * feed-context-string from `getFeed` responses). That metadata stays on
 * the leaf [PostUi] (`repostedBy` and similar fields). This sealed
 * type's job is to express cluster-vs-single rendering shape, not to
 * enrich per-post metadata.
 *
 * The sealed-interface declaration enables exhaustive `when` checking
 * at the render dispatch — adding a fourth variant in the future would
 * force every render dispatch site to handle it.
 */
@Stable
public sealed interface FeedItemUi {
    /**
     * Stable identifier used as the LazyColumn `key` lambda.
     *
     * For [Single] this is the post's URI. For [ReplyCluster] and
     * [SelfThreadChain] this is the leaf's URI — pagination +
     * scroll-position are anchored on the leaf because that's "the post
     * in your timeline" from the user's perspective; preceding posts
     * are context.
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

    /**
     * A same-author self-reply chain — `posts.size >= 2`, all sharing
     * `author.did`, ordered root-most first. Producers (the feed
     * mapper + page-boundary merge in the feed VM) MUST uphold the
     * `size >= 2` and same-author invariants; a single-post "chain" is
     * a [Single], not a [SelfThreadChain].
     *
     * Reposted entries (`reason is ReasonRepost` in the wire) are
     * excluded from chain links by the producer, so every `PostUi`
     * inside `posts` carries `repostedBy = null`.
     */
    public data class SelfThreadChain(
        val posts: ImmutableList<PostUi>,
    ) : FeedItemUi {
        override val key: String get() = posts.last().id
    }

    /**
     * `app.bsky.feed.defs#blockedPost` placeholder — the viewer has
     * blocked the author (or is blocked by them) and the AppView is
     * returning a tombstone in place of the post. The wire never emits
     * this variant for [FeedViewPost.post] directly (that field is
     * always a `PostView`); a [Blocked] entry appears here only via the
     * UGC moderation epic's client-side optimism — after the viewer
     * blocks an author the feed VM may swap in-list posts by that
     * author with [Blocked] entries until the next refresh, so the
     * tombstone surfaces immediately rather than waiting for the next
     * `getTimeline` round-trip to elide them.
     *
     * [authorDid] is preserved so the tombstone's "Unblock" affordance
     * can dispatch the unblock RPC. [uri] is preserved for deep-link /
     * debug parity with [ThreadItem.Blocked] in `:feature:postdetail`.
     */
    public data class Blocked(
        val uri: String,
        val authorDid: String,
    ) : FeedItemUi {
        override val key: String get() = "blocked:$uri"
    }

    /**
     * `app.bsky.feed.defs#notFoundPost` placeholder — the post was
     * deleted or never existed. URI preserved for parity with
     * [ThreadItem.NotFound]; no author info on the wire, so no
     * affordance beyond the inline message.
     */
    public data class NotFound(
        val uri: String,
    ) : FeedItemUi {
        override val key: String get() = "notfound:$uri"
    }
}
