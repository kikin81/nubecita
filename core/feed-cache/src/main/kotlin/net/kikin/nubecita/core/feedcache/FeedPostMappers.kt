package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.ReasonRepost
import net.kikin.nubecita.core.database.model.FeedPostEntity
import net.kikin.nubecita.core.feedmapping.applyModeration
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.PostUi
import kotlin.time.Instant

/**
 * Write-through mapper: project one wire [FeedViewPost] into the denormalized
 * [FeedPostEntity] for a cache partition.
 *
 * The whole wire `FeedViewPost` is serialized into [FeedPostEntity.entryBlob];
 * the queryable columns ([uri][FeedPostEntity.uri], [cid][FeedPostEntity.cid],
 * [authorDid][FeedPostEntity.authorDid], [indexedAt][FeedPostEntity.indexedAt],
 * [text][FeedPostEntity.text]) are denormalized projections used for indexed
 * lookups without deserializing the blob.
 *
 * [position] is supplied by the caller — the RemoteMediator assigns sequential
 * positions per partition; this mapper does not invent one.
 *
 * The per-entry [FeedViewPost.reason] and [FeedViewPost.reply] are persisted
 * with the entry, both inside the blob and denormalized into
 * [repostedByDid][FeedPostEntity.repostedByDid],
 * [replyRootUri][FeedPostEntity.replyRootUri] and
 * [replyParentUri][FeedPostEntity.replyParentUri]. They are the entire input to
 * the `ReplyCluster` / `SelfThreadChain` projection, so dropping them — as this
 * mapper did while the cache served only the flat widget surface — left the
 * partition unable to represent the app's own feed.
 *
 * A reply root or parent that is a `BlockedPost` / `NotFoundPost` / unknown
 * union variant denormalizes to `null`: there is no readable post to point at.
 * The blob still round-trips the tombstone, so the projection can render it.
 *
 * `text` and `indexedAt` are read from the wire post:
 * - `text` is decoded from the post's `record` once here (so the column is a
 *   plain queryable projection); a record that can't be decoded yields `""`.
 * - `indexedAt` is the POST's `indexedAt` (appview ingestion time), not the
 *   record's `createdAt`. An unparseable timestamp falls back to the Unix
 *   epoch — the blob still round-trips the true value on read.
 */
fun FeedViewPost.toFeedPostEntity(
    feedKey: FeedKey,
    position: Int,
): FeedPostEntity {
    val view = post
    return FeedPostEntity(
        accountDid = feedKey.accountDid,
        feedType = feedKey.feedType.name,
        feedUri = feedKey.feedUri,
        position = position,
        uri = view.uri.raw,
        cid = view.cid.raw,
        authorDid = view.author.did.raw,
        indexedAt = parseInstantOrEpoch(view.indexedAt.raw),
        text = view.extractRecordText(),
        repostedByDid = (reason as? ReasonRepost)?.by?.did?.raw,
        replyRootUri = (reply?.root as? PostView)?.uri?.raw,
        replyParentUri = (reply?.parent as? PostView)?.uri?.raw,
        entryBlob = CacheJson.encodeToString(FeedViewPost.serializer(), this),
    )
}

/**
 * Read mapper: deserialize [FeedPostEntity.entryBlob] back to the wire
 * [FeedViewPost], project its post to a UI [PostUi] via the shared
 * `:core:feed-mapping` mapper, then apply moderation.
 *
 * This returns the entry's **post** only, which is what the widget and Wear
 * `head` surfaces consume. Surfacing the entry-level repost attribution and the
 * reply context into the grouped `FeedItemUi` projection is the write-time
 * projection's job, not this mapper's.
 *
 * Returns `null` when:
 * - the blob is absent or malformed (can't decode as a [PostView]);
 * - [toPostUiCore] can't produce a well-formed `PostUi` (malformed embedded
 *   record, unparseable `createdAt`);
 * - moderation drops the post (`dropFiltered = true`, matching the feed/list
 *   contract — a resolved HIDE removes the post; warned media is covered, not
 *   dropped).
 *
 * @param viewerDid the signed-in viewer's DID; the viewer's own content is
 *   never filtered or covered.
 * @param prefs the resolved content-filter preferences.
 */
fun FeedPostEntity.toPostUi(
    viewerDid: String?,
    prefs: ModerationPrefs,
): PostUi? {
    val blob = entryBlob ?: return null
    val entry =
        runCatching { CacheJson.decodeFromString(FeedViewPost.serializer(), blob) }
            .getOrNull() ?: return null
    val view = entry.post
    val postUi = view.toPostUiCore(viewerDid) ?: return null
    return postUi.applyModeration(
        labels = view.labels,
        viewerDid = viewerDid,
        prefs = prefs,
        dropFiltered = true,
    )
}

/**
 * Decode the post's `record` to its `text`, or `""` when it can't be decoded.
 *
 * No `viewerDid` on purpose: this reads the record text only and discards the
 * viewer state, so ownership would be computed and thrown away.
 */
private fun PostView.extractRecordText(): String = toPostUiCore()?.text ?: ""

private fun parseInstantOrEpoch(raw: String): Instant = runCatching { Instant.parse(raw) }.getOrDefault(Instant.fromEpochMilliseconds(0))
