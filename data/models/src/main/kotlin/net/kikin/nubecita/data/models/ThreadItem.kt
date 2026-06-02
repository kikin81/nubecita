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
     * Placeholder for elided posts — the lexicon's `parentHeight` /
     * `depth` parameters cap the tree at finite levels and posts past
     * the cap collapse into a "View more" affordance. m28.5.1's mapper
     * does NOT emit `Fold` items (the screen renders the slice the
     * server returned, no client-side fold). Reserved for m28.5.2's
     * visual treatment.
     */
    @Immutable
    public data class Fold(
        val above: String,
    ) : ThreadItem {
        override val key: String get() = "fold:$above"
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
