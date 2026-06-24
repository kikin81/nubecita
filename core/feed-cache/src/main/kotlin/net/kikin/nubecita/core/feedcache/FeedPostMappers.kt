package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
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
 * The whole wire `PostView` (`.post`) is serialized into [FeedPostEntity.postBlob]
 * (Option 1 — store the wire post, re-map on read); the queryable columns
 * ([uri][FeedPostEntity.uri], [cid][FeedPostEntity.cid],
 * [authorDid][FeedPostEntity.authorDid], [indexedAt][FeedPostEntity.indexedAt],
 * [text][FeedPostEntity.text]) are denormalized projections used for indexed
 * lookups without deserializing the blob.
 *
 * [position] is supplied by the caller — the RemoteMediator assigns sequential
 * positions per partition (a later PR); this mapper does not invent one.
 *
 * The per-feed-entry [FeedViewPost.reason] (a `ReasonRepost` overlay) is NOT
 * persisted: the blob is the bare `PostView`, so a cached repost re-maps with
 * `repostedBy = null`. Surfacing the repost author from the cache is a later
 * concern (it would need a separate column / blob field) and is out of scope
 * for the foundation.
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
        postBlob = CacheJson.encodeToString(PostView.serializer(), view),
    )
}

/**
 * Read mapper: deserialize [FeedPostEntity.postBlob] back to the wire
 * [PostView], project it to a UI [PostUi] via the shared `:core:feed-mapping`
 * mapper, then apply moderation.
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
    val blob = postBlob ?: return null
    val view =
        runCatching { CacheJson.decodeFromString(PostView.serializer(), blob) }
            .getOrNull() ?: return null
    val postUi = view.toPostUiCore() ?: return null
    return postUi.applyModeration(
        labels = view.labels,
        viewerDid = viewerDid,
        prefs = prefs,
        dropFiltered = true,
    )
}

/** Decode the post's `record` to its `text`, or `""` when it can't be decoded. */
private fun PostView.extractRecordText(): String = toPostUiCore()?.text ?: ""

private fun parseInstantOrEpoch(raw: String): Instant = runCatching { Instant.parse(raw) }.getOrDefault(Instant.fromEpochMilliseconds(0))
