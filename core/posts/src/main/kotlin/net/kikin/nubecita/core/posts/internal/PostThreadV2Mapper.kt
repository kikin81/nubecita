package net.kikin.nubecita.core.posts.internal

import io.github.kikin81.atproto.app.bsky.unspecced.GetPostThreadOtherV2Response
import io.github.kikin81.atproto.app.bsky.unspecced.GetPostThreadV2Response
import io.github.kikin81.atproto.app.bsky.unspecced.ThreadItemBlocked
import io.github.kikin81.atproto.app.bsky.unspecced.ThreadItemNoUnauthenticated
import io.github.kikin81.atproto.app.bsky.unspecced.ThreadItemNotFound
import io.github.kikin81.atproto.app.bsky.unspecced.ThreadItemPost
import io.github.kikin81.atproto.runtime.OpenUnionMember
import io.github.kikin81.atproto.runtime.UnknownOpenUnionMember
import net.kikin.nubecita.core.database.model.ThreadSnapshotKind
import net.kikin.nubecita.data.models.ThreadPositionUi
import timber.log.Timber

/**
 * Flattens `app.bsky.unspecced.getPostThreadV2` into [RawThreadEntry]s.
 *
 * The response is already a flat list in display order — parents root-most
 * first, the anchor, then replies depth-first with the author's own next post
 * sorted to the front of each level — so this keeps the order it was given and
 * never sorts. `depth` says what each item is: below zero an ancestor, zero the
 * focus, above zero a reply at that nesting level.
 *
 * A post the response carries twice keeps its first place: the list is keyed
 * by post, and a repeat would be a duplicate key further down. An item of a
 * kind this app does not model is skipped (and logged); the endpoint is
 * experimental upstream and a new kind must not cost the reader the rest of
 * the thread.
 *
 * The anchor is the exception. The SDK does not fail on an item it cannot
 * decode — a post whose shape has drifted comes back as an unknown kind, the
 * same as a brand-new one — so "skip what is unknown" applied to the anchor
 * would turn lexicon drift into a thread with no opened post, which the screen
 * reads as "post not found". An unreadable or absent anchor therefore throws
 * [UnreadableOrderedThreadException], and the repository falls back to the
 * older thread read.
 *
 * `hasOtherReplies` becomes one trailing [ThreadSnapshotKind.OTHER_REPLIES]
 * entry, so the fact survives the snapshot cache with the thread it belongs to.
 */
internal fun GetPostThreadV2Response.toRawThreadEntries(): List<RawThreadEntry> {
    val seen = HashSet<String>()
    var skippedKinds: MutableSet<String>? = null
    val entries =
        thread.mapNotNull { item ->
            val uri = item.uri.raw
            val entry = item.value.toRawThreadEntry(uri = uri, wireDepth = item.depth)
            if (entry == null) {
                if (item.depth == 0L) {
                    throw UnreadableOrderedThreadException("the anchor is a thread item this build cannot read: ${item.value.kindName()}")
                }
                (skippedKinds ?: mutableSetOf<String>().also { skippedKinds = it }) += item.value.kindName()
                return@mapNotNull null
            }
            if (seen.add(uri)) entry else null
        }
    // The server always returns the anchor, readable or as a tombstone. A
    // response without one is not a thread this build understands.
    if (thread.none { it.depth == 0L }) throw UnreadableOrderedThreadException("the response has no anchor item")
    skippedKinds?.let { kinds ->
        Timber.tag(TAG).w("getPostThreadV2 returned thread items this build cannot read; skipped kinds: %s", kinds.joinToString())
    }
    if (!hasOtherReplies) return entries
    val focusUri = entries.firstOrNull { it.kind == ThreadSnapshotKind.FOCUS }?.uri ?: return entries
    return entries + RawThreadEntry(kind = ThreadSnapshotKind.OTHER_REPLIES, uri = focusUri, depth = 0)
}

/**
 * The ordered thread read answered, but not with a thread this build can
 * render: its anchor item is missing or of a kind the SDK could not decode.
 */
internal class UnreadableOrderedThreadException(
    message: String,
) : Exception(message)

private const val TAG = "PostThreadV2Mapper"

/**
 * The replies `getPostThreadV2` left out, as reply entries. The endpoint
 * returns posts only; anything else is skipped.
 */
internal fun GetPostThreadOtherV2Response.toRawThreadEntries(): List<RawThreadEntry> {
    val seen = HashSet<String>()
    return thread.mapNotNull { item ->
        val uri = item.uri.raw
        val entry = item.value.toRawThreadEntry(uri = uri, wireDepth = item.depth.coerceAtLeast(1)) ?: return@mapNotNull null
        if (entry.kind == ThreadSnapshotKind.REPLY && seen.add(uri)) entry else null
    }
}

/** The wire `$type` of an item the SDK could not decode, for a log line. */
private fun OpenUnionMember.kindName(): String = (this as? UnknownOpenUnionMember)?.type?.takeIf { it.isNotEmpty() } ?: this::class.simpleName.orEmpty()

private fun OpenUnionMember.toRawThreadEntry(
    uri: String,
    wireDepth: Long,
): RawThreadEntry? {
    val kind =
        when {
            wireDepth < 0 -> ThreadSnapshotKind.ANCESTOR
            wireDepth == 0L -> ThreadSnapshotKind.FOCUS
            else -> ThreadSnapshotKind.REPLY
        }
    // Ancestors and the focus sit at depth 0, as on the v1 path; only replies nest.
    val depth = wireDepth.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    return when (this) {
        is ThreadItemPost -> {
            // Validated once here, so a snapshot never stores a pair that is not a position.
            val position = ThreadPositionUi.of(index = opThreadPostIndex, count = opThreadPostCount)
            RawThreadEntry(
                kind = kind,
                uri = uri,
                depth = depth,
                post = post,
                opThreadIndex = position?.index,
                opThreadCount = position?.count,
                moreReplies = moreReplies.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                moreParents = moreParents,
            )
        }
        is ThreadItemBlocked -> RawThreadEntry(ThreadSnapshotKind.BLOCKED, uri, depth = depth, authorDid = author.did.raw)
        // A post only signed-in viewers may see reads, to this viewer, like one that is not there.
        is ThreadItemNotFound, is ThreadItemNoUnauthenticated -> RawThreadEntry(ThreadSnapshotKind.NOT_FOUND, uri, depth = depth)
        else -> null
    }
}
