package net.kikin.nubecita.feature.postdetail.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.app.bsky.feed.BlockedPost
import io.github.kikin81.atproto.app.bsky.feed.GetPostThreadResponseThreadUnion
import io.github.kikin81.atproto.app.bsky.feed.NotFoundPost
import io.github.kikin81.atproto.app.bsky.feed.Post
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPost
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPostParentUnion
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPostRepliesUnion
import io.github.kikin81.atproto.app.bsky.feed.ViewerState
import io.github.kikin81.atproto.runtime.AtField
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import kotlin.time.Instant

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
 * # Why the embed slot is collapsed to [EmbedUi.Empty]
 *
 * m28.5.1's scope is "data + VM + minimal loaded screen". Full embed
 * mapping (images, video, quotes, recordWithMedia) duplicates ~200
 * lines of `:feature:feed:impl/data/FeedViewPostMapper.kt` and the
 * shared bits will get extracted to a new `:core:feed-mapping` module
 * during m28.5.2's visual treatment. Until then, every projected
 * [PostUi] gets [EmbedUi.Empty] — the screen renders text + author +
 * action row only, which the task explicitly accepts ("Don't worry if
 * it looks plain — that's the point.").
 *
 * Earmark for the eventual extraction: the four feed-impl helpers
 * needed verbatim are `toPostUiCore`, `toAuthorUi`, `toViewerStateUi`,
 * and `valueOrEmpty`.
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

/**
 * `Json` instance used to decode the embedded `post.record: JsonObject`
 * payload as a strongly-typed [Post]. Mirrors `XrpcClient.DefaultJson`
 * (`ignoreUnknownKeys = true`) so server additions to the post record
 * schema don't break decoding for fields the mapper doesn't read.
 *
 * Local copy of the same instance maintained in
 * `:feature:feed:impl/data/FeedViewPostMapper.kt`. To be unified with
 * that copy when the shared mapping module is extracted in m28.5.2.
 */
private val recordJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

/**
 * Project a [PostView] into the UI-ready [PostUi]. Returns `null` when
 * the embedded `record: JsonObject` cannot be decoded as a well-formed
 * `app.bsky.feed.post` record (missing required `text` / `createdAt`,
 * type-incompatible value), or when the decoded `createdAt` is not a
 * parseable RFC3339 timestamp.
 *
 * m28.5.1 collapses the embed slot to [EmbedUi.Empty] regardless of the
 * lexicon-side embed type — see [toThreadItems]'s KDoc for the full
 * rationale and m28.5.2 follow-up.
 */
private fun PostView.toPostUiCore(): PostUi? {
    val postRecord =
        runCatching {
            recordJson.decodeFromJsonElement(Post.serializer(), record)
        }.getOrNull() ?: return null

    val createdAt =
        runCatching { Instant.parse(postRecord.createdAt.raw) }
            .getOrNull() ?: return null

    return PostUi(
        id = uri.raw,
        cid = cid.raw,
        author = author.toAuthorUi(),
        createdAt = createdAt,
        text = postRecord.text,
        facets = postRecord.facets.valueOrEmpty().toImmutableList(),
        embed = EmbedUi.Empty,
        stats =
            PostStatsUi(
                replyCount = (replyCount ?: 0L).toInt(),
                repostCount = (repostCount ?: 0L).toInt(),
                likeCount = (likeCount ?: 0L).toInt(),
                quoteCount = (quoteCount ?: 0L).toInt(),
            ),
        viewer = viewer.toViewerStateUi(isFollowingAuthor = author.viewer?.following != null),
        repostedBy = null,
    )
}

private fun ProfileViewBasic.toAuthorUi(): AuthorUi =
    AuthorUi(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeIf { it.isNotBlank() } ?: handle.raw,
        avatarUrl = avatar?.raw,
    )

private fun ViewerState?.toViewerStateUi(isFollowingAuthor: Boolean = false): ViewerStateUi =
    ViewerStateUi(
        isLikedByViewer = this?.like != null,
        isRepostedByViewer = this?.repost != null,
        isFollowingAuthor = isFollowingAuthor,
        likeUri = this?.like?.raw,
        repostUri = this?.repost?.raw,
    )

private fun <T> AtField<List<T>>.valueOrEmpty(): List<T> = (this as? AtField.Defined)?.value ?: emptyList()
