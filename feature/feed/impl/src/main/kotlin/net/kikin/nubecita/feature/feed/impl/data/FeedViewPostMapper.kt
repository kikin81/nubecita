package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.app.bsky.embed.ExternalView
import io.github.kikin81.atproto.app.bsky.embed.ImagesView
import io.github.kikin81.atproto.app.bsky.embed.RecordView
import io.github.kikin81.atproto.app.bsky.embed.RecordWithMediaView
import io.github.kikin81.atproto.app.bsky.embed.VideoView
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostViewEmbedUnion
import io.github.kikin81.atproto.app.bsky.feed.ReasonRepost
import io.github.kikin81.atproto.app.bsky.feed.ViewerState
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.runtime.UnknownOpenUnionMember
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import kotlin.time.Instant

/**
 * `Json` instance used to decode the embedded `post.record: JsonObject`
 * payload. Mirrors `XrpcClient.DefaultJson` (`ignoreUnknownKeys = true`)
 * so server additions to the post record schema (new langs, labels,
 * embed payload variants) don't break decode for fields the mapper
 * doesn't read.
 */
private val recordJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

/**
 * Maps a [FeedViewPost] (the wire model returned by `app.bsky.feed.getTimeline`)
 * to the UI-ready [PostUi].
 *
 * Returns `null` when the embedded `post.record` JSON cannot be decoded
 * as a well-formed `app.bsky.feed.post` record (missing required `text`,
 * type-incompatible value). The repository's `mapNotNull` filter then
 * drops the entry. The function MUST NOT throw — every spec-conforming
 * `FeedViewPost` produces a non-null `PostUi`.
 */
internal fun FeedViewPost.toPostUiOrNull(): PostUi? {
    val text = post.record.extractTextOrNull() ?: return null
    val createdAt = post.record.extractCreatedAtOrNull() ?: return null
    return PostUi(
        id = post.uri.raw,
        author = post.author.toAuthorUi(),
        createdAt = createdAt,
        text = text,
        facets = post.record.extractFacets(),
        embed = post.embed.toEmbedUi(),
        stats =
            PostStatsUi(
                replyCount = (post.replyCount ?: 0L).toInt(),
                repostCount = (post.repostCount ?: 0L).toInt(),
                likeCount = (post.likeCount ?: 0L).toInt(),
                quoteCount = (post.quoteCount ?: 0L).toInt(),
            ),
        viewer = post.viewer.toViewerStateUi(isFollowingAuthor = post.author.viewer?.following != null),
        repostedBy = (reason as? ReasonRepost)?.by?.let { it.displayName ?: it.handle.raw },
    )
}

/**
 * Maps the [PostViewEmbedUnion] open-union variant to PostCard's v1
 * [EmbedUi] surface (Empty / Images / Unsupported(typeUri)). Future
 * `EmbedUi` variants (External per nubecita-aku, Record per
 * nubecita-6vq, Video per nubecita-xsu, RecordWithMedia per
 * nubecita-umn) become compile errors at this `when` once they're added
 * to `EmbedUi`, surfacing the work needed.
 */
internal fun PostViewEmbedUnion?.toEmbedUi(): EmbedUi =
    when (this) {
        null -> EmbedUi.Empty
        is ImagesView ->
            EmbedUi.Images(
                items =
                    images
                        .map { image ->
                            ImageUi(
                                url = image.fullsize.raw,
                                altText = image.alt.takeIf { it.isNotBlank() },
                                aspectRatio = image.aspectRatio?.let { it.width.toFloat() / it.height.toFloat() },
                            )
                        }.toImmutableList(),
            )
        is ExternalView -> EmbedUi.Unsupported(typeUri = "app.bsky.embed.external")
        is RecordView -> EmbedUi.Unsupported(typeUri = "app.bsky.embed.record")
        is VideoView -> EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")
        is RecordWithMediaView -> EmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia")
        is PostViewEmbedUnion.Unknown -> EmbedUi.Unsupported(typeUri = type)
        // PostViewEmbedUnion is an open union (not sealed) — Kotlin can't prove
        // exhaustiveness across the known + Unknown variants alone. The
        // generator routes any unrecognized `$type` through the Unknown branch,
        // so this fallback is structurally unreachable; route it through the
        // UnknownOpenUnionMember `type` if a future variant slips past the
        // generator. Sentinel `"unknown"` keeps PostCard's friendly-name
        // label informative if we ever do hit this branch in production.
        else -> EmbedUi.Unsupported(typeUri = (this as? UnknownOpenUnionMember)?.type ?: "unknown")
    }

internal fun ProfileViewBasic.toAuthorUi(): AuthorUi =
    AuthorUi(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeIf { it.isNotBlank() } ?: handle.raw,
        avatarUrl = avatar?.raw,
    )

internal fun ViewerState?.toViewerStateUi(isFollowingAuthor: Boolean = false): ViewerStateUi =
    ViewerStateUi(
        isLikedByViewer = this?.like != null,
        isRepostedByViewer = this?.repost != null,
        isFollowingAuthor = isFollowingAuthor,
    )

/**
 * Returns the `text` field from the embedded `app.bsky.feed.post` record
 * JSON, or `null` if the field is missing or not a string. The lexicon
 * spec requires `text` on `app.bsky.feed.post`, so a missing value is a
 * malformed record — the caller (typically [toPostUiOrNull]) drops the
 * post.
 */
internal fun JsonObject.extractTextOrNull(): String? = runCatching { (this["text"]?.jsonPrimitive)?.content }.getOrNull()

internal fun JsonObject.extractCreatedAtOrNull(): Instant? =
    runCatching {
        val raw = (this["createdAt"]?.jsonPrimitive)?.content ?: return@runCatching null
        Instant.parse(raw)
    }.getOrNull()

internal fun JsonObject.extractFacets(): ImmutableList<Facet> {
    val rawFacets = this["facets"] as? JsonArray ?: return persistentListOf()
    return runCatching {
        recordJson
            .decodeFromJsonElement(ListSerializer(Facet.serializer()), rawFacets)
            .toImmutableList()
    }.getOrElse { persistentListOf() }
}
