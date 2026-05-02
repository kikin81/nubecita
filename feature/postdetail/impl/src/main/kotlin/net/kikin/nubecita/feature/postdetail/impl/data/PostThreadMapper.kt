package net.kikin.nubecita.feature.postdetail.impl.data

import io.github.kikin81.atproto.app.bsky.feed.BlockedPost
import io.github.kikin81.atproto.app.bsky.feed.GetPostThreadResponseThreadUnion
import io.github.kikin81.atproto.app.bsky.feed.NotFoundPost
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPost
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPostParentUnion
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPostRepliesUnion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.feedmapping.toPostUiCore

/**
 * Maps `app.bsky.feed.getPostThread`'s `thread` open-union response onto
 * the screen-local flat [ThreadItem] list rendered by `PostDetailScreen`.
 *
 * The dispatch is total over the three known lexicon variants and
 * gracefully degrades on the open-union `Unknown` fallback (returns the
 * empty list — the VM treats that the same as a `NotFound` thread):
 *
 * - `app.bsky.feed.defs#threadViewPost` → walks up the parent chain
 *   producing `[Ancestor]` (root-most first), then [ThreadItem.Focus],
 *   then walks down the replies tree depth-first emitting
 *   [ThreadItem.Reply] with incrementing `depth`. Sibling unavailable
 *   variants ([BlockedPost] / [NotFoundPost]) appear inline at the
 *   position they occupied.
 *
 * - `app.bsky.feed.defs#blockedPost` (focus-position) →
 *   single-element list containing [ThreadItem.Blocked].
 *
 * - `app.bsky.feed.defs#notFoundPost` (focus-position) →
 *   single-element list containing [ThreadItem.NotFound].
 *
 * `null` is never returned. A focus whose embedded record fails to
 * decode (malformed JSON, type-incompatible value, unparseable
 * `createdAt`) yields the empty list — the same surface the VM uses for
 * "thread unavailable".
 *
 * Per-post projection (record decode, embed dispatch, author / viewer
 * mapping) is delegated to `:core:feed-mapping`'s [toPostUiCore] —
 * single source of truth shared with `:feature:feed:impl`.
 */
internal fun GetPostThreadResponseThreadUnion.toThreadItems(): ImmutableList<ThreadItem> =
    when (this) {
        is BlockedPost -> persistentListOf(ThreadItem.Blocked(uri = uri.raw))
        is NotFoundPost -> persistentListOf(ThreadItem.NotFound(uri = uri.raw))
        is ThreadViewPost -> flattenThreadViewPost()
        else -> persistentListOf()
    }

private fun ThreadViewPost.flattenThreadViewPost(): ImmutableList<ThreadItem> {
    val focusPost = post.toPostUiCore() ?: return persistentListOf()
    val out = mutableListOf<ThreadItem>()
    out += collectAncestors(parent)
    out += ThreadItem.Focus(post = focusPost)
    replies?.forEach { reply -> out += reply.toReplyItems(depth = 1) }
    return out.toImmutableList()
}

/**
 * Walks the parent chain bottom-up, then reverses so the returned
 * list is ordered root-most first → immediate parent last. Stops the
 * walk at the first [BlockedPost] / [NotFoundPost] sibling — the
 * lexicon never threads a parent through an unavailable post (those
 * variants don't carry a `parent` field), and the open-union
 * `Unknown` fallback also terminates the walk.
 */
private fun collectAncestors(start: ThreadViewPostParentUnion?): List<ThreadItem> {
    val ancestors = mutableListOf<ThreadItem>()
    var cursor: ThreadViewPostParentUnion? = start
    while (cursor != null) {
        when (val node = cursor) {
            is ThreadViewPost -> {
                node.post.toPostUiCore()?.let { ancestors += ThreadItem.Ancestor(post = it) }
                cursor = node.parent
            }
            is BlockedPost -> {
                ancestors += ThreadItem.Blocked(uri = node.uri.raw)
                cursor = null
            }
            is NotFoundPost -> {
                ancestors += ThreadItem.NotFound(uri = node.uri.raw)
                cursor = null
            }
            else -> cursor = null
        }
    }
    return ancestors.asReversed()
}

/**
 * Depth-first flatten of the replies subtree. Each reply emits one
 * [ThreadItem.Reply] (or unavailable variant) followed by its own
 * recursively-flattened children. Depth starts at the caller-supplied
 * value and increments per level of nesting — top-level replies get
 * `depth = 1`, replies-to-replies get `depth = 2`, etc.
 */
private fun ThreadViewPostRepliesUnion.toReplyItems(depth: Int): List<ThreadItem> =
    when (this) {
        is ThreadViewPost -> {
            val replyPost = post.toPostUiCore()
            if (replyPost == null) {
                emptyList()
            } else {
                buildList {
                    add(ThreadItem.Reply(post = replyPost, depth = depth))
                    replies?.forEach { child -> addAll(child.toReplyItems(depth = depth + 1)) }
                }
            }
        }
        is BlockedPost -> listOf(ThreadItem.Blocked(uri = uri.raw))
        is NotFoundPost -> listOf(ThreadItem.NotFound(uri = uri.raw))
        else -> emptyList()
    }
