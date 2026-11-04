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
 * The returned [PostUi] carries the entry's repost attribution, which the bare
 * `toPostUiCore` leaves null because `reason` lives on the entry rather than on
 * the post. Before the blob widened there was nothing to read it from, so every
 * cached repost rendered as though it were an original post — visible on the
 * Wear surface, which maps `repostedBy` into its watch DTO. (The home-screen
 * widget is unaffected: `WidgetPostItem` carries no repost field.)
 *
 * Reconstructing the *reply* context into a grouped `FeedItemUi` is the
 * write-time projection's job, not this mapper's — this returns the entry's
 * post, which is what the `head` surfaces consume.
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
    val entry = toFeedViewPostOrNull() ?: return null
    val view = entry.post
    val postUi = view.toPostUiCore(viewerDid) ?: return null
    return postUi
        .copy(repostedBy = entry.repostedByDisplay())
        .applyModeration(
            labels = view.labels,
            viewerDid = viewerDid,
            prefs = prefs,
            dropFiltered = true,
        )
}

/**
 * Decode [FeedPostEntity.entryBlob] back to the wire [FeedViewPost], or `null`
 * when the blob is absent or malformed.
 *
 * This is deliberately the **only** place the blob is deserialized. The
 * write-time grouping projection needs the same wire entry that [toPostUi]
 * needs, and two decode sites would be two places for the error handling — and
 * the `CacheJson` configuration — to drift apart. A row that cannot be decoded
 * is skipped rather than throwing: a single corrupt blob must not take down a
 * whole page of an otherwise readable partition.
 */
fun FeedPostEntity.toFeedViewPostOrNull(): FeedViewPost? {
    val blob = entryBlob ?: return null
    return runCatching { CacheJson.decodeFromString(FeedViewPost.serializer(), blob) }.getOrNull()
}

/**
 * The repost attribution to display for this entry, or `null` when it is not a
 * repost.
 *
 * `displayName ?: handle` mirrors `:feature:feed:impl`'s `FeedViewPostMapper`
 * exactly, and must keep mirroring it: the same post read from the network and
 * read from the cache has to render the same attribution, or a cached repost
 * would change its label the moment the feed refreshed.
 */
private fun FeedViewPost.repostedByDisplay(): String? = (reason as? ReasonRepost)?.by?.let { it.displayName ?: it.handle.raw }

/**
 * Decode the post's `record` to its `text`, or `""` when it can't be decoded.
 *
 * No `viewerDid` on purpose: this reads the record text only and discards the
 * viewer state, so ownership would be computed and thrown away.
 */
private fun PostView.extractRecordText(): String = toPostUiCore()?.text ?: ""

private fun parseInstantOrEpoch(raw: String): Instant = runCatching { Instant.parse(raw) }.getOrDefault(Instant.fromEpochMilliseconds(0))
