package net.kikin.nubecita.core.posts.internal

import io.github.kikin81.atproto.app.bsky.feed.BlockedPost
import io.github.kikin81.atproto.app.bsky.feed.GetPostThreadResponseThreadUnion
import io.github.kikin81.atproto.app.bsky.feed.NotFoundPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPost
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPostParentUnion
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPostRepliesUnion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.database.model.ThreadSnapshotKind
import net.kikin.nubecita.core.feedmapping.applyModeration
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.ThreadItem

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
 *
 * The mapping runs in two stages so a cached thread can be re-moderated:
 * [toRawThreadEntries] flattens the response into [RawThreadEntry]s (the wire
 * posts, unmoderated — what a thread snapshot stores), and
 * [List.toThreadItems] maps those with the viewer's current prefs.
 *
 * Content moderation runs here, off the render path, against the cached
 * [prefs] + [viewerDid], using `dropFiltered = false`: post-detail is NOT a
 * list surface, so a hard-filtered focus / ancestor / reply is KEPT in the
 * thread (never dropped) and its media is covered instead — the cover's
 * `overridable` flag then governs whether it can be revealed. Dropping a post
 * here would tear a hole in the reply chain.
 */
internal fun GetPostThreadResponseThreadUnion.toThreadItems(
    prefs: ModerationPrefs,
    viewerDid: String?,
): ImmutableList<ThreadItem> = toRawThreadEntries().toThreadItems(prefs, viewerDid)

/**
 * One flattened thread position before moderation: [kind] is a
 * [ThreadSnapshotKind], [post] the wire post (null for blocked / not-found
 * slots), [depth] the reply nesting level (0 for ancestors and the focus),
 * [authorDid] the blocked author for a blocked slot.
 */
internal data class RawThreadEntry(
    val kind: String,
    val uri: String,
    val depth: Int,
    val authorDid: String? = null,
    val post: PostView? = null,
)

/** Flattens the response: ancestors root-most first, the focus, then replies depth-first. */
internal fun GetPostThreadResponseThreadUnion.toRawThreadEntries(): List<RawThreadEntry> =
    when (this) {
        is BlockedPost -> listOf(RawThreadEntry(ThreadSnapshotKind.BLOCKED, uri.raw, depth = 0, authorDid = author.did.raw))
        is NotFoundPost -> listOf(RawThreadEntry(ThreadSnapshotKind.NOT_FOUND, uri.raw, depth = 0))
        is ThreadViewPost ->
            buildList {
                addAll(collectAncestors(parent))
                add(RawThreadEntry(ThreadSnapshotKind.FOCUS, post.uri.raw, depth = 0, post = post))
                replies?.forEach { addAll(it.toRawReplies(depth = 1)) }
            }
        else -> emptyList()
    }

/**
 * Maps raw entries with the viewer's current [prefs] + [viewerDid].
 *
 * Matches the single-pass mapping it replaced: a focus that fails to map
 * yields the empty list (the VM's "thread unavailable"); an ancestor that
 * fails is skipped; a reply that fails is dropped together with its whole
 * subtree (every following entry deeper than it).
 */
internal fun List<RawThreadEntry>.toThreadItems(
    prefs: ModerationPrefs,
    viewerDid: String?,
): ImmutableList<ThreadItem> {
    val out = mutableListOf<ThreadItem>()
    var droppedReplyDepth: Int? = null
    for (entry in this) {
        val skipDepth = droppedReplyDepth
        if (skipDepth != null) {
            if (entry.depth > skipDepth) continue
            droppedReplyDepth = null
        }
        when (entry.kind) {
            ThreadSnapshotKind.BLOCKED -> out += ThreadItem.Blocked(uri = entry.uri, authorDid = entry.authorDid.orEmpty())
            ThreadSnapshotKind.NOT_FOUND -> out += ThreadItem.NotFound(uri = entry.uri)
            else -> {
                val mapped =
                    entry.post
                        ?.toPostUiCore(viewerDid)
                        ?.applyModeration(entry.post.labels, viewerDid, prefs, dropFiltered = false)
                when (entry.kind) {
                    ThreadSnapshotKind.FOCUS -> out += ThreadItem.Focus(post = mapped ?: return persistentListOf())
                    ThreadSnapshotKind.ANCESTOR -> if (mapped != null) out += ThreadItem.Ancestor(post = mapped)
                    ThreadSnapshotKind.REPLY ->
                        if (mapped != null) {
                            out += ThreadItem.Reply(post = mapped, depth = entry.depth)
                        } else {
                            droppedReplyDepth = entry.depth
                        }
                }
            }
        }
    }
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
private fun collectAncestors(start: ThreadViewPostParentUnion?): List<RawThreadEntry> {
    val ancestors = mutableListOf<RawThreadEntry>()
    var cursor: ThreadViewPostParentUnion? = start
    while (cursor != null) {
        when (val node = cursor) {
            is ThreadViewPost -> {
                ancestors += RawThreadEntry(ThreadSnapshotKind.ANCESTOR, node.post.uri.raw, depth = 0, post = node.post)
                cursor = node.parent
            }
            is BlockedPost -> {
                ancestors += RawThreadEntry(ThreadSnapshotKind.BLOCKED, node.uri.raw, depth = 0, authorDid = node.author.did.raw)
                cursor = null
            }
            is NotFoundPost -> {
                ancestors += RawThreadEntry(ThreadSnapshotKind.NOT_FOUND, node.uri.raw, depth = 0)
                cursor = null
            }
            else -> cursor = null
        }
    }
    return ancestors.asReversed()
}

/**
 * Depth-first flatten of the replies subtree. Each reply emits its entry
 * followed by its own recursively-flattened children. Depth starts at the
 * caller-supplied value and increments per level of nesting — top-level
 * replies get `depth = 1`, replies-to-replies `depth = 2`, etc. Unavailable
 * slots carry their depth too, so a dropped parent reply can drop them.
 */
private fun ThreadViewPostRepliesUnion.toRawReplies(depth: Int): List<RawThreadEntry> =
    when (this) {
        is ThreadViewPost ->
            buildList {
                add(RawThreadEntry(ThreadSnapshotKind.REPLY, post.uri.raw, depth = depth, post = post))
                replies?.forEach { child -> addAll(child.toRawReplies(depth = depth + 1)) }
            }
        is BlockedPost -> listOf(RawThreadEntry(ThreadSnapshotKind.BLOCKED, uri.raw, depth = depth, authorDid = author.did.raw))
        is NotFoundPost -> listOf(RawThreadEntry(ThreadSnapshotKind.NOT_FOUND, uri.raw, depth = depth))
        else -> emptyList()
    }
