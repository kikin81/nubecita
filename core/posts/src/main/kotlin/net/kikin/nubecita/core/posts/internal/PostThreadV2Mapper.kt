package net.kikin.nubecita.core.posts.internal

import io.github.kikin81.atproto.app.bsky.unspecced.GetPostThreadOtherV2Response
import io.github.kikin81.atproto.app.bsky.unspecced.GetPostThreadV2Response
import io.github.kikin81.atproto.app.bsky.unspecced.ThreadItemBlocked
import io.github.kikin81.atproto.app.bsky.unspecced.ThreadItemNoUnauthenticated
import io.github.kikin81.atproto.app.bsky.unspecced.ThreadItemNotFound
import io.github.kikin81.atproto.app.bsky.unspecced.ThreadItemPost
import io.github.kikin81.atproto.runtime.OpenUnionMember
import net.kikin.nubecita.core.database.model.ThreadSnapshotKind
import net.kikin.nubecita.data.models.ThreadPositionUi

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
 * kind this app does not model is skipped; the endpoint is experimental
 * upstream and a new kind must not cost the reader the rest of the thread.
 *
 * `hasOtherReplies` becomes one trailing [ThreadSnapshotKind.OTHER_REPLIES]
 * entry, so the fact survives the snapshot cache with the thread it belongs to.
 */
internal fun GetPostThreadV2Response.toRawThreadEntries(): List<RawThreadEntry> {
    val seen = HashSet<String>()
    val entries =
        thread.mapNotNull { item ->
            val uri = item.uri.raw
            val entry = item.value.toRawThreadEntry(uri = uri, wireDepth = item.depth) ?: return@mapNotNull null
            if (seen.add(uri)) entry else null
        }
    if (!hasOtherReplies) return entries
    val focusUri = entries.firstOrNull { it.kind == ThreadSnapshotKind.FOCUS }?.uri ?: return entries
    return entries + RawThreadEntry(kind = ThreadSnapshotKind.OTHER_REPLIES, uri = focusUri, depth = 0)
}

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
