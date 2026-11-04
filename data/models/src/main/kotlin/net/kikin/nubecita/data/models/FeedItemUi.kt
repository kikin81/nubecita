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
     * Stable identifier used as the LazyColumn `key` lambda, and as the
     * reader's persisted scroll anchor.
     *
     * Grouped variants are **namespaced** and anchored on a post that does not
     * change as the group grows (D12):
     *
     * - [Single] — the post's own URI.
     * - [SelfThreadChain] — `"chain:"` plus the chain's FIRST post. A chain can
     *   gain a post between sessions, which changes its leaf; keyed on the leaf,
     *   an anchor saved in one session would resolve to nothing in the next.
     * - [ReplyCluster] — `"cluster:"` plus the leaf. A cluster is one reply with
     *   its ancestors as context, so its leaf does not move the way a chain's
     *   does.
     *
     * The namespace is not decoration. Keyed on the bare post id, a chain rooted
     * at P and a [Single] rendering P would share a key, and a duplicate
     * LazyColumn key throws. That collision used to be unreachable only because
     * of the "at most one item per thread root" invariant living in another
     * module — and that invariant is now per-page rather than global (D7), so
     * the namespace carries the safety locally instead of borrowing it.
     *
     * Use [leafId], not this, to ask *which post* an item occupies a timeline
     * slot for. The two were the same value before namespacing, and code that
     * leans on that equality breaks silently here.
     */
    public val key: String

    /**
     * The post this item occupies a timeline slot for — "the post in your
     * timeline", with any preceding posts being context.
     *
     * Deleting this post removes the item; deleting an ancestor shown only as
     * context leaves the reply standing, which is what the reader still wants
     * to see. It is also the identity a "one item per timeline post" pass
     * de-duplicates on, since two items rendering the same post is duplication
     * however differently they are keyed.
     *
     * Separate from [key] on purpose. They held the same value until the
     * grouped keys were namespaced, and every caller that read [key] meaning
     * "the leaf" would have kept compiling and started being wrong.
     */
    public val leafId: String

    /**
     * How many replies into this item's thread were suppressed by feed
     * de-duplication, or `0` when none were.
     *
     * At most one item per thread root is rendered, so N replies into one
     * thread collapse to a single card. Counting what was dropped keeps that
     * content discoverable instead of silently lost — the renderer surfaces it
     * as an affordance into the full thread. Measured in POSTS, not feed items:
     * a dropped self-thread chain contributes each of its posts, and a
     * suppressed post the viewer can already see (typically the thread root,
     * rendered as this item's context) contributes nothing.
     *
     * A deliberate divergence from the official client, which drops silently.
     * See `openspec/changes/fix-feed-thread-root-dedupe` decision D6.
     */
    public val suppressedReplyCount: Int

    public data class Single(
        val post: PostUi,
        override val suppressedReplyCount: Int = 0,
    ) : FeedItemUi {
        override val key: String get() = post.id

        override val leafId: String get() = post.id
    }

    public data class ReplyCluster(
        val root: PostUi,
        val parent: PostUi,
        val leaf: PostUi,
        val hasEllipsis: Boolean,
        override val suppressedReplyCount: Int = 0,
    ) : FeedItemUi {
        // The leaf, not the root. Two replies into one thread are two clusters
        // sharing a root, which D7 now allows to coexist across a prepend seam
        // — keyed on the root they would collide, and a duplicate LazyColumn
        // key throws. It would also make the anchor's item_key lookup ambiguous
        // between two rows. See the design's D28.
        override val key: String get() = "cluster:${leaf.id}"

        override val leafId: String get() = leaf.id
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
        override val suppressedReplyCount: Int = 0,
    ) : FeedItemUi {
        // The FIRST post: a chain that gains a post between sessions keeps its
        // key, so the reader's anchor still resolves to it.
        override val key: String get() = "chain:${posts.first().id}"

        override val leafId: String get() = posts.last().id
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

        override val leafId: String get() = uri

        // A tombstone has no thread root, so it is never a de-dup survivor.
        override val suppressedReplyCount: Int get() = 0
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

        override val leafId: String get() = uri

        // A tombstone has no thread root, so it is never a de-dup survivor.
        override val suppressedReplyCount: Int get() = 0
    }
}
