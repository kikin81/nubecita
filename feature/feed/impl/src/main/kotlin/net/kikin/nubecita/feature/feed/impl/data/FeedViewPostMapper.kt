package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.ReasonRepost
import io.github.kikin81.atproto.app.bsky.feed.ReplyRef
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi
import timber.log.Timber

/**
 * Maps a [FeedViewPost] (the wire model returned by `app.bsky.feed.getTimeline`)
 * to the UI-ready [PostUi].
 *
 * Delegates the per-post projection (record decode, embed dispatch,
 * author / viewer mapping) to `:core:feed-mapping`'s [toPostUiCore], then
 * layers in the feed-specific `repostedBy` overlay sourced from
 * [FeedViewPost.reason] (which lives at the per-feed-entry level, not
 * on [PostView] itself).
 *
 * Returns `null` when the embedded `post.record` JSON cannot be decoded
 * as a well-formed `app.bsky.feed.post` record (missing required `text`
 * / `createdAt`, type-incompatible value). The repository's `mapNotNull`
 * filter then drops the entry. The function MUST NOT throw — every
 * spec-conforming `FeedViewPost` produces a non-null `PostUi`.
 */
internal fun FeedViewPost.toPostUiOrNull(): PostUi? {
    val core = post.toPostUiCore() ?: return null
    val repostedBy = (reason as? ReasonRepost)?.by?.let { it.displayName ?: it.handle.raw }
    return if (repostedBy != null) core.copy(repostedBy = repostedBy) else core
}

/**
 * Maps a [FeedViewPost] to the renderable [FeedItemUi] sealed type — the
 * entry-point used by the repository and any future consumer that wants
 * cluster-vs-single rendering shape.
 *
 * Production semantics:
 * - `reply == null` → returns [FeedItemUi.Single].
 * - `reply.parent` is a `PostView` AND `reply.root` is a `PostView` AND
 *   both project successfully → returns [FeedItemUi.ReplyCluster] with
 *   `hasEllipsis = grandparentAuthor != null && grandparentAuthor.did != root.author.did`.
 * - `reply.parent` is `BlockedPost` / `NotFoundPost` / `Unknown` →
 *   returns [FeedItemUi.Single] and emits a `Timber.w` log so the
 *   fallback frequency is visible in dev builds (production tree is no-op).
 * - Leaf record cannot be projected (malformed JSON, unparseable
 *   createdAt) → returns `null`; same contract as [toPostUiOrNull].
 *
 * Designed to subsume `nubecita-im8`'s "Replying to @handle" header by
 * rendering the parent post inline — the context is implicit. Detailed
 * rationale in the openspec change `add-feed-cross-author-thread-cluster`.
 */
internal fun FeedViewPost.toFeedItemUiOrNull(): FeedItemUi? {
    val leaf = toPostUiOrNull() ?: return null
    val replyRef = reply ?: return FeedItemUi.Single(leaf)

    val parentPostView = replyRef.parent as? PostView
    if (parentPostView == null) {
        Timber.w(
            "Reply parent is non-PostView (likely BlockedPost/NotFoundPost) for leaf=${leaf.id}; falling back to Single rendering",
        )
        return FeedItemUi.Single(leaf)
    }
    val rootPostView =
        replyRef.root as? PostView ?: run {
            Timber.w("Reply root is non-PostView for leaf=${leaf.id}; falling back to Single rendering")
            return FeedItemUi.Single(leaf)
        }

    val parent = parentPostView.toPostUiCore() ?: return FeedItemUi.Single(leaf)
    val root = rootPostView.toPostUiCore() ?: return FeedItemUi.Single(leaf)

    return FeedItemUi.ReplyCluster(
        root = root,
        parent = parent,
        leaf = leaf,
        hasEllipsis = replyRef.hasEllipsisRelativeToRoot(rootAuthorDid = root.author.did),
    )
}

/**
 * Heuristic used to decide whether a `ThreadFold` ("View full thread")
 * indicator goes between root and parent.
 *
 * The lexicon doesn't expose a precise count of intermediate posts, but
 * `replyRef.grandparentAuthor` (the author of the post one level above
 * `parent`) gives us a useful signal: if it's non-null AND distinct
 * from `root.author.did`, then there's at least one post (the
 * grandparent) sitting between root and parent — i.e., the chain is not
 * `root → parent → leaf` but rather `root → ... → grandparent → parent → leaf`.
 *
 * False negatives are possible (the lexicon may not always populate
 * `grandparentAuthor` reliably for very deep threads), but the failure
 * mode is "no fold rendered when there should be one" which only loses
 * a hint, not correctness. False positives are not possible from this
 * heuristic alone.
 */
private fun ReplyRef.hasEllipsisRelativeToRoot(rootAuthorDid: String): Boolean {
    val gp = grandparentAuthor ?: return false
    return gp.did.raw != rootAuthorDid
}
