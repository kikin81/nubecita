package net.kikin.nubecita.feature.postdetail.impl.data

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.data.models.PostUi

/**
 * One row in the post-detail screen's flattened thread.
 *
 * The lexicon's `app.bsky.feed.defs#threadViewPost` is a recursive
 * parent / replies tree rooted at the focused post. The mapper
 * ([net.kikin.nubecita.feature.postdetail.impl.data.toThreadItems]) flattens
 * the tree into a top-to-bottom list:
 *
 *   root-most ancestor → ... → immediate parent → focus → top-level
 *   replies → nested replies (depth-first)
 *
 * Sibling unavailable variants — `app.bsky.feed.defs#blockedPost` and
 * `app.bsky.feed.defs#notFoundPost` — appear inline at the position
 * they occupied in the source tree. The two unavailable variants
 * surface no `PostUi` (the lexicon never carries a renderable post for
 * them); the screen renders a thin "post unavailable" stub.
 *
 * The `key` lambda input for `LazyColumn` encodes each variant's role
 * so the same `PostUi.id` appearing as both an ancestor of one screen
 * and the focus of another (after the user navigates "up" through the
 * chain) doesn't collide in the LazyList recycler.
 *
 * Design note: this sealed type is screen-local, NOT a shared model.
 * It lives in `:feature:postdetail:impl` and is internal — the
 * `:feature:postdetail:api` boundary only exposes the `PostDetailRoute`
 * NavKey; callers that need detail rendering go through the screen
 * Composable, not through the ThreadItem list.
 */
@Immutable
internal sealed interface ThreadItem {
    val key: String

    /** A post above the focus in the parent chain. */
    @Immutable
    data class Ancestor(
        val post: PostUi,
    ) : ThreadItem {
        override val key: String get() = "ancestor:${post.id}"
    }

    /** The focused post — the one the user tapped to open this screen. */
    @Immutable
    data class Focus(
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
    data class Reply(
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
    data class Fold(
        val above: String,
    ) : ThreadItem {
        override val key: String get() = "fold:$above"
    }

    /**
     * `app.bsky.feed.defs#blockedPost` — the viewer cannot see this
     * post because of a block relationship. The URI is preserved for
     * deep-link / debug / future "view in browser" affordances.
     */
    @Immutable
    data class Blocked(
        val uri: String,
    ) : ThreadItem {
        override val key: String get() = "blocked:$uri"
    }

    /**
     * `app.bsky.feed.defs#notFoundPost` — the post was deleted or
     * never existed. URI preserved for the same reasons as [Blocked].
     */
    @Immutable
    data class NotFound(
        val uri: String,
    ) : ThreadItem {
        override val key: String get() = "notfound:$uri"
    }
}
