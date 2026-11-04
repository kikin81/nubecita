package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.BlockedPost
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.NotFoundPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.ReasonRepost
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import net.kikin.nubecita.core.database.model.CachedFeedEntry
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedPostEntity
import net.kikin.nubecita.core.feedmapping.applyModeration
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.PostUi
import kotlin.time.Instant

/**
 * Write-through mappers: project one wire [FeedViewPost] into the two rows a
 * cached partition needs — the entry envelope ([FeedPostEntity]) and the posts
 * it references ([CachedPostEntity]).
 *
 * They are deliberately separate functions over the same entry rather than one
 * returning a pair, because the two have different cardinality: one entry is
 * always one envelope, but it contributes between one and three posts (its own,
 * plus a reply's root and parent). Callers write both inside one transaction.
 *
 * ---
 *
 * The entry envelope: position, the URI of the post it shows, and the
 * per-entry context that belongs to the timeline rather than to the post.
 *
 * [position] is supplied by the caller — the RemoteMediator owns position
 * allocation; this mapper does not invent one.
 *
 * The per-entry `reason` and `reply` are the entire input to the
 * `ReplyCluster` / `SelfThreadChain` projection, which is why they are
 * persisted at all. A reply slot that is a tombstone contributes no URI here
 * (the column points at posts the viewer can read) but still contributes a
 * [CachedPostEntity] row via [toCachedPosts], so the projection can render it.
 */
fun FeedViewPost.toFeedPostEntity(
    feedKey: FeedKey,
    position: Int,
): FeedPostEntity =
    FeedPostEntity(
        accountDid = feedKey.accountDid,
        feedType = feedKey.feedType.name,
        feedUri = feedKey.feedUri,
        position = position,
        uri = post.uri.raw,
        repostedByDid = (reason as? ReasonRepost)?.by?.did?.raw,
        repostedByLabel = repostedByDisplay(),
        replyRootUri = (reply?.root as? PostView)?.uri?.raw,
        replyParentUri = (reply?.parent as? PostView)?.uri?.raw,
        entryContextBlob = entryContextBlob(),
    )

/**
 * Every post this entry contributes to the by-URI store: its own, plus a
 * reply's root and parent when present.
 *
 * Reply context is included because it usually is **not** a timeline entry of
 * its own — it arrives only inside this entry's `reply` field, so if it is not
 * captured here it is not captured at all, and the cluster projection would
 * have a root it cannot render.
 *
 * An unreadable slot (`blockedPost` / `notFoundPost`) still yields a row,
 * marked [CachedPostEntity.unavailable] with no blob. Omitting it would leave
 * the projection unable to tell "this reply had a parent you cannot see" from
 * "this is a root post", and those render differently.
 *
 * Duplicates within one page are expected — the same post can be one entry's
 * subject and another's root — and are harmless: the DAO upserts by
 * `(accountDid, uri)`, so the last write wins and exactly one row results.
 */
fun FeedViewPost.toCachedPosts(feedKey: FeedKey): List<CachedPostEntity> =
    buildList {
        add(post.toCachedPost(feedKey))
        reply?.let { ref ->
            ref.root.toCachedPostOrTombstone(feedKey)?.let(::add)
            ref.parent.toCachedPostOrTombstone(feedKey)?.let(::add)
        }
    }

/**
 * Read mapper: project one joined [CachedFeedEntry] into a UI [PostUi], or
 * `null` when it cannot be rendered.
 *
 * Returns `null` when:
 * - the stored post is a tombstone or carries no blob (nothing to render);
 * - the blob is malformed (a single corrupt row must not fail a whole page);
 * - [toPostUiCore] cannot produce a well-formed `PostUi`;
 * - moderation drops the post (`dropFiltered = true`, matching the feed/list
 *   contract — a resolved HIDE removes the post; warned media is covered).
 *
 * The repost attribution comes from the **entry**, not the post: the same post
 * reposted by two people is two entries, and the post itself cannot say which.
 * It is read from the stored label rather than re-derived, so the read path
 * needs no second lookup for the reposting account's profile.
 */
fun CachedFeedEntry.toPostUi(
    viewerDid: String?,
    prefs: ModerationPrefs,
): PostUi? {
    val view = post.toPostViewOrNull() ?: return null
    val postUi = view.toPostUiCore(viewerDid) ?: return null
    return postUi
        .copy(repostedBy = repostedByLabel)
        .applyModeration(
            labels = view.labels,
            viewerDid = viewerDid,
            prefs = prefs,
            dropFiltered = true,
        )
}

/**
 * Decode a stored post back to the wire [PostView], or `null` when there is
 * nothing to decode — an unreadable tombstone, an absent blob, or malformed
 * JSON.
 *
 * This is deliberately the only place a stored blob is deserialized: the
 * write-time grouping projection needs the same wire post the read path needs,
 * and two decode sites would be two places for the error handling — and the
 * `CacheJson` configuration — to drift apart.
 */
fun CachedPostEntity.toPostViewOrNull(): PostView? {
    if (unavailable != null) return null
    val blob = postBlob ?: return null
    return runCatching { CacheJson.decodeFromString(PostView.serializer(), blob) }.getOrNull()
}

/**
 * The repost attribution to display for this entry, or `null` when it is not a
 * repost.
 *
 * `displayName ?: handle` mirrors `:feature:feed:impl`'s `FeedViewPostMapper`
 * exactly, and must keep mirroring it: the same post read from the network and
 * from the cache has to render the same attribution, or a cached repost would
 * change its label the moment the feed refreshed.
 */
private fun FeedViewPost.repostedByDisplay(): String? = (reason as? ReasonRepost)?.by?.let { it.displayName ?: it.handle.raw }

/**
 * This entry's own wire fields — `reason` and `reply` — as a JSON object, or
 * null when it carries neither.
 *
 * Taken by re-encoding the entry and keeping those two keys, rather than
 * hand-building them: the values go out through the same serializers that read
 * them back, so an open-union variant this client does not model still
 * round-trips instead of being silently flattened.
 *
 * The post is deliberately excluded. It lives once in the by-URI store, and
 * including it here would be the duplication the two-grain split removed.
 */
private fun FeedViewPost.entryContextBlob(): String? {
    val encoded = CacheJson.encodeToJsonElement(FeedViewPost.serializer(), this).jsonObject
    val context =
        buildJsonObject {
            encoded["reason"]?.let { put("reason", it) }
            encoded["reply"]?.let { put("reply", it) }
        }
    return if (context.isEmpty()) null else CacheJson.encodeToString(JsonObject.serializer(), context)
}

/**
 * Rebuilds the wire entry from its stored parts: the post from the by-URI
 * store, its own `reason` / `reply` from [FeedPostEntity.entryContextBlob].
 *
 * This is what makes a local re-projection faithful. The denormalized columns
 * could suggest an entry's shape, but they reduce `reason` to a DID and a label
 * and `reply` to two URIs — feed that to the grouping rules and `linksTo`, the
 * repost exemption and the cluster shape all decide on something subtly unlike
 * what the server sent.
 *
 * Returns null when the post is missing or either blob is malformed; a single
 * unreadable row is skipped rather than failing the whole re-projection.
 */
fun FeedPostEntity.toFeedViewPostOrNull(post: CachedPostEntity?): FeedViewPost? {
    val postJson = post?.postBlob ?: return null
    return runCatching {
        val wire =
            buildJsonObject {
                put("\$type", JsonPrimitive("app.bsky.feed.defs#feedViewPost"))
                put("post", CacheJson.parseToJsonElement(postJson))
                entryContextBlob?.let { blob ->
                    CacheJson.parseToJsonElement(blob).jsonObject.forEach { (key, value) -> put(key, value) }
                }
            }
        CacheJson.decodeFromJsonElement(FeedViewPost.serializer(), wire)
    }.getOrNull()
}

private fun PostView.toCachedPost(feedKey: FeedKey): CachedPostEntity =
    CachedPostEntity(
        accountDid = feedKey.accountDid,
        uri = uri.raw,
        cid = cid.raw,
        authorDid = author.did.raw,
        indexedAt = parseInstantOrEpoch(indexedAt.raw),
        text = extractRecordText(),
        postBlob = CacheJson.encodeToString(PostView.serializer(), this),
    )

/**
 * A reply slot as a stored row: the post itself when readable, a URI-only
 * tombstone when blocked or not found, and `null` for an unrecognized
 * open-union variant — there is no URI to key a row on, so there is nothing to
 * store.
 */
private fun Any?.toCachedPostOrTombstone(feedKey: FeedKey): CachedPostEntity? =
    when (this) {
        is PostView -> toCachedPost(feedKey)
        is BlockedPost ->
            CachedPostEntity(
                accountDid = feedKey.accountDid,
                uri = uri.raw,
                authorDid = author.did.raw,
                unavailable = UNAVAILABLE_BLOCKED,
            )
        is NotFoundPost ->
            CachedPostEntity(
                accountDid = feedKey.accountDid,
                uri = uri.raw,
                unavailable = UNAVAILABLE_NOT_FOUND,
            )
        else -> null
    }

/**
 * Decode the post's `record` to its `text`, or `""` when it can't be decoded.
 *
 * No `viewerDid` on purpose: this reads the record text only and discards the
 * viewer state, so ownership would be computed and thrown away.
 */
private fun PostView.extractRecordText(): String = toPostUiCore()?.text ?: ""

private fun parseInstantOrEpoch(raw: String): Instant = runCatching { Instant.parse(raw) }.getOrDefault(Instant.fromEpochMilliseconds(0))

internal const val UNAVAILABLE_BLOCKED = "blocked"
internal const val UNAVAILABLE_NOT_FOUND = "notfound"
