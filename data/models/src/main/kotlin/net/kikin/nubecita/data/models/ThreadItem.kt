package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * One row in a flattened post thread.
 *
 * The lexicon's `app.bsky.feed.defs#threadViewPost` is a recursive
 * parent / replies tree rooted at the focused post. `:core:posts`'
 * `PostThreadRepository` flattens the tree into a top-to-bottom list:
 *
 *   root-most ancestor → ... → immediate parent → focus → top-level
 *   replies → nested replies (depth-first)
 *
 * A thread the server returned only in part also carries [MoreParents],
 * [MoreReplies] and [OtherReplies] rows: none of them is a post, each is a way
 * to reach the rest.
 *
 * Sibling unavailable variants — `app.bsky.feed.defs#blockedPost` and
 * `app.bsky.feed.defs#notFoundPost` — appear inline at the position
 * they occupied in the source tree. The two unavailable variants
 * surface no `PostUi` (the lexicon never carries a renderable post for
 * them); the render layer renders a thin "post unavailable" stub.
 *
 * The [key] property for `LazyColumn` encodes each variant's role so the
 * same `PostUi.id` appearing as both an ancestor of one screen and the
 * focus of another (after the user navigates "up" through the chain)
 * doesn't collide in the LazyList recycler.
 *
 * Shared model (`:data:models`): produced by `:core:posts`'
 * `PostThreadRepository` and consumed by both the post-detail screen and
 * the fullscreen player's comments sheet (nubecita-6rdb.3). Like the
 * other `:data:models` types it is `@Immutable` and Compose-stable.
 */
@Immutable
public sealed interface ThreadItem {
    public val key: String

    /** A post above the focus in the parent chain. */
    @Immutable
    public data class Ancestor(
        val post: PostUi,
    ) : ThreadItem {
        override val key: String get() = "ancestor:${post.id}"
    }

    /** The focused post — the one the user tapped to open this screen. */
    @Immutable
    public data class Focus(
        val post: PostUi,
    ) : ThreadItem {
        override val key: String get() = "focus:${post.id}"
    }

    /**
     * A post below the focus in the replies tree.
     *
     * [depth] starts at `1` for direct replies and increments per
     * level of nesting. m28.5.1's minimal screen ignores the depth
     * (everything renders flat); reserved for the visual treatment
     * in m28.5.2 that indents nested replies.
     */
    @Immutable
    public data class Reply(
        val post: PostUi,
        val depth: Int,
    ) : ThreadItem {
        override val key: String get() = "reply:${post.id}"
    }

    /**
     * The thread continues above [postUri]: the server returned only part of
     * the parent chain (`moreParents` on the top-most post it did return).
     * Rendered as a "read earlier posts" row that opens [postUri].
     */
    @Immutable
    public data class MoreParents(
        val postUri: String,
    ) : ThreadItem {
        override val key: String get() = "moreparents:$postUri"
    }

    /**
     * [postUri] has [count] more replies than the server returned, and none of
     * them follows it in the list — the end of a reply chain, or of an author's
     * thread longer than one response. Rendered as a "read more replies" row
     * that opens [postUri]. [count] is the server's best effort, not exact.
     *
     * [depth] is the depth of the post it follows, so the row sits under it.
     */
    @Immutable
    public data class MoreReplies(
        val postUri: String,
        val count: Int,
        val depth: Int,
    ) : ThreadItem {
        override val key: String get() = "morereplies:$postUri"
    }

    /**
     * The server holds replies it left out of the thread (muted, or hidden by
     * the author's threadgate). Rendered as a "show more replies" row at the
     * end of the list; there is at most one.
     */
    @Immutable
    public data object OtherReplies : ThreadItem {
        override val key: String get() = "otherreplies"
    }

    /**
     * `app.bsky.feed.defs#blockedPost` — the viewer cannot see this
     * post because of a block relationship. The URI is preserved for
     * deep-link / debug / future "view in browser" affordances; the
     * `authorDid` is preserved so the tombstone's "Unblock" affordance
     * (wired under oftc.4) can dispatch the unblock RPC against the
     * right account. The wire's `BlockedPost.author.did` is always
     * present, so this field is non-nullable.
     */
    @Immutable
    public data class Blocked(
        val uri: String,
        val authorDid: String,
    ) : ThreadItem {
        override val key: String get() = "blocked:$uri"
    }

    /**
     * `app.bsky.feed.defs#notFoundPost` — the post was deleted or
     * never existed. URI preserved for the same reasons as [Blocked].
     */
    @Immutable
    public data class NotFound(
        val uri: String,
    ) : ThreadItem {
        override val key: String get() = "notfound:$uri"
    }
}
