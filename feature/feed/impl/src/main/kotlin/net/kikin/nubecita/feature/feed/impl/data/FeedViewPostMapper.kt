package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.app.bsky.embed.ExternalView
import io.github.kikin81.atproto.app.bsky.embed.ImagesView
import io.github.kikin81.atproto.app.bsky.embed.RecordView
import io.github.kikin81.atproto.app.bsky.embed.RecordViewBlocked
import io.github.kikin81.atproto.app.bsky.embed.RecordViewDetached
import io.github.kikin81.atproto.app.bsky.embed.RecordViewNotFound
import io.github.kikin81.atproto.app.bsky.embed.RecordViewRecord
import io.github.kikin81.atproto.app.bsky.embed.RecordViewRecordEmbedsUnion
import io.github.kikin81.atproto.app.bsky.embed.RecordWithMediaView
import io.github.kikin81.atproto.app.bsky.embed.RecordWithMediaViewMediaUnion
import io.github.kikin81.atproto.app.bsky.embed.VideoView
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.Post
import io.github.kikin81.atproto.app.bsky.feed.PostViewEmbedUnion
import io.github.kikin81.atproto.app.bsky.feed.ReasonRepost
import io.github.kikin81.atproto.app.bsky.feed.ViewerState
import io.github.kikin81.atproto.runtime.AtField
import io.github.kikin81.atproto.runtime.UnknownOpenUnionMember
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import kotlin.time.Instant

/**
 * `Json` instance used to decode the embedded `post.record: JsonObject`
 * payload as a strongly-typed [Post]. Mirrors `XrpcClient.DefaultJson`
 * (`ignoreUnknownKeys = true`) so server additions to the post record
 * schema (new tags, labels, embed payload variants) don't break decode
 * for fields the mapper doesn't read.
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
 * as a well-formed `app.bsky.feed.post` record (missing required `text`
 * / `createdAt`, type-incompatible value). The repository's `mapNotNull`
 * filter then drops the entry. The function MUST NOT throw — every
 * spec-conforming `FeedViewPost` produces a non-null `PostUi`.
 *
 * The record is decoded into the lexicon-generated [Post] data class
 * rather than walked as a raw `JsonObject`. The compiler-checked field
 * access eliminates the string-key brittleness of manual extraction and
 * makes the malformed-record contract a single decode-or-fail call.
 */
internal fun FeedViewPost.toPostUiOrNull(): PostUi? {
    val postRecord =
        runCatching {
            recordJson.decodeFromJsonElement(Post.serializer(), post.record)
        }.getOrNull() ?: return null

    // `Datetime` is a value class wrapping the raw RFC3339 string and does
    // no validation at construction; a structurally-valid Post can still
    // carry a malformed timestamp string.
    val createdAt =
        runCatching { Instant.parse(postRecord.createdAt.raw) }
            .getOrNull() ?: return null

    return PostUi(
        id = post.uri.raw,
        author = post.author.toAuthorUi(),
        createdAt = createdAt,
        text = postRecord.text,
        facets = postRecord.facets.valueOrEmpty().toImmutableList(),
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
 * Maps the [PostViewEmbedUnion] open-union variant to PostCard's
 * [EmbedUi] surface (Empty / Images / Video / External / Record /
 * RecordUnavailable / RecordWithMedia / Unsupported).
 */
internal fun PostViewEmbedUnion?.toEmbedUi(): EmbedUi =
    when (this) {
        null -> EmbedUi.Empty
        is ImagesView -> toEmbedUiImages()
        is ExternalView -> toEmbedUiExternal()
        is RecordView -> toRecordOrUnavailable()
        is VideoView -> toEmbedUiVideo() ?: EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")
        is RecordWithMediaView -> toEmbedUiRecordWithMedia()
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

/**
 * Dispatches a [RecordView] over its `record` open-union member. The
 * 4 post-shaped variants map to [EmbedUi.Record] / [EmbedUi.RecordUnavailable];
 * everything else (Generator/List/StarterPack/Labeler "quote-a-feed"
 * cases plus the Unknown open-union fallback) falls through to
 * [EmbedUi.RecordUnavailable.Reason.Unknown] — none of those is a
 * post we can render, and "Quoted post unavailable" is the cheapest
 * honest user-facing answer. Future tickets can route the
 * non-post variants through their own Unsupported chips with proper
 * friendly-name labels.
 *
 * Return type is [EmbedUi.RecordOrUnavailable] (not the broader
 * `EmbedUi`) because every output is structurally one of the two.
 * Callers that need an `EmbedUi` get the upcast for free since the
 * marker extends `EmbedUi`. Callers that need to feed a
 * `RecordWithMedia.record` slot — which is `RecordOrUnavailable`-typed —
 * use it directly without an `as` cast.
 */
private fun RecordView.toRecordOrUnavailable(): EmbedUi.RecordOrUnavailable =
    when (val r = record) {
        is RecordViewRecord -> r.toEmbedUiRecord() ?: EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Unknown)
        is RecordViewNotFound -> EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound)
        is RecordViewBlocked -> EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Blocked)
        is RecordViewDetached -> EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Detached)
        else -> EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Unknown)
    }

/**
 * Composes a [RecordWithMediaView] into [EmbedUi.RecordWithMedia],
 * or falls the whole composition through to
 * [EmbedUi.Unsupported] when the media side is malformed (empty
 * video playlist, unknown lexicon variant). The asymmetry with the
 * record side — which always produces a [EmbedUi.RecordOrUnavailable]
 * via graceful unavailable variants — is deliberate: half-rendering
 * a recordWithMedia (media-only without the quote) loses the post's
 * communicative intent, but the record's lexicon-defined unavailable
 * shapes carry author intent.
 */
private fun RecordWithMediaView.toEmbedUiRecordWithMedia(): EmbedUi {
    val mediaPart =
        media.toMediaEmbed()
            ?: return EmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia")
    val recordPart = record.toRecordOrUnavailable()
    return EmbedUi.RecordWithMedia(record = recordPart, media = mediaPart)
}

/**
 * Maps a [RecordWithMediaViewMediaUnion] to [EmbedUi.MediaEmbed].
 * Returns `null` for the malformed-media cases (empty video
 * playlist, unknown lexicon variant) — caller falls the whole
 * composition through to [EmbedUi.Unsupported].
 *
 * Routes through the same wrapper-construction helpers
 * ([toEmbedUiImages], [toEmbedUiVideo], [toEmbedUiExternal]) used by
 * the parent [toEmbedUi] dispatch — single source of truth for
 * wrapper construction, no risk of drift between the two paths.
 */
private fun RecordWithMediaViewMediaUnion.toMediaEmbed(): EmbedUi.MediaEmbed? =
    when (this) {
        is ImagesView -> toEmbedUiImages()
        is VideoView -> toEmbedUiVideo()
        is ExternalView -> toEmbedUiExternal()
        else -> null
    }

/**
 * Decodes a [RecordViewRecord] into [EmbedUi.Record]. Returns `null`
 * (caller maps to `RecordUnavailable.Unknown`) when the embedded
 * `value: JsonObject` cannot be decoded as a valid `app.bsky.feed.post`
 * (missing required `text` / `createdAt`, type-incompatible value),
 * or when the decoded `createdAt` is not a parseable RFC3339 timestamp.
 *
 * The parent post is NEVER dropped because of a malformed quoted
 * record — that's the contract enforced by [FeedViewPost.toPostUiOrNull]'s
 * caller.
 */
private fun RecordViewRecord.toEmbedUiRecord(): EmbedUi.Record? {
    val rec =
        runCatching { recordJson.decodeFromJsonElement(Post.serializer(), value) }
            .getOrNull() ?: return null
    val parsedCreatedAt =
        runCatching { Instant.parse(rec.createdAt.raw) }
            .getOrNull() ?: return null
    return EmbedUi.Record(
        quotedPost =
            QuotedPostUi(
                uri = uri.raw,
                cid = cid.raw,
                author = author.toAuthorUi(),
                createdAt = parsedCreatedAt,
                text = rec.text,
                facets = rec.facets.valueOrEmpty().toImmutableList(),
                embed = embeds?.firstOrNull().toQuotedEmbedUi(),
            ),
    )
}

/**
 * Maps a quoted post's inner embed (the first element of the lexicon's
 * `embeds` list — multiples are theoretically allowed but practically
 * 0–1) to [QuotedEmbedUi]. The recursion bound is enforced here: a
 * nested [RecordView] maps to [QuotedEmbedUi.QuotedThreadChip]; the
 * mapper does NOT recurse into the doubly-quoted post.
 */
private fun RecordViewRecordEmbedsUnion?.toQuotedEmbedUi(): QuotedEmbedUi =
    when (this) {
        null -> QuotedEmbedUi.Empty
        is ImagesView -> QuotedEmbedUi.Images(items = toImageUiList())
        is VideoView ->
            toVideoPayload()?.let { p ->
                QuotedEmbedUi.Video(
                    posterUrl = p.posterUrl,
                    playlistUrl = p.playlistUrl,
                    aspectRatio = p.aspectRatio,
                    durationSeconds = p.durationSeconds,
                    altText = p.altText,
                )
            } ?: QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.video")
        is ExternalView ->
            QuotedEmbedUi.External(
                uri = external.uri.raw,
                domain = displayDomainOf(external.uri.raw),
                title = external.title,
                description = external.description,
                thumbUrl = external.thumb?.raw,
            )
        is RecordView -> QuotedEmbedUi.QuotedThreadChip
        is RecordWithMediaView -> QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia")
        is RecordViewRecordEmbedsUnion.Unknown -> QuotedEmbedUi.Unsupported(typeUri = type)
        // Open-union fallback — see toEmbedUi's same-shaped comment.
        else -> QuotedEmbedUi.Unsupported(typeUri = (this as? UnknownOpenUnionMember)?.type ?: "unknown")
    }

/**
 * Shared payload helpers — extracted from `toEmbedUi` and used by
 * both the parent dispatch and the inner-quote dispatch. Wrapper
 * variants ([EmbedUi.Images] vs [QuotedEmbedUi.Images], etc.)
 * remain duplicated to keep the recursion bound expressible at the
 * type system, but the underlying construction logic lives once.
 */
private fun ImagesView.toImageUiList(): ImmutableList<ImageUi> =
    images
        .map { image ->
            ImageUi(
                url = image.fullsize.raw,
                altText = image.alt.takeIf { it.isNotBlank() },
                aspectRatio = image.aspectRatio?.let { it.width.toFloat() / it.height.toFloat() },
            )
        }.toImmutableList()

/**
 * Wrapper-construction helpers — extracted so the parent
 * [toEmbedUi] dispatch and [toMediaEmbed] (used by
 * [toEmbedUiRecordWithMedia]) share one path. Inline construction
 * at both call sites would risk drift (e.g. an `aspectRatio`
 * calculation tweak applied in one path and forgotten in the other).
 *
 * These build on top of the payload helpers ([toImageUiList],
 * [toVideoPayload]) — the payload helpers stay because the
 * inner-quote dispatch (`toQuotedEmbedUi` from 6vq) needs the inner
 * data without the parent-style wrapper.
 */
private fun ImagesView.toEmbedUiImages(): EmbedUi.Images = EmbedUi.Images(items = toImageUiList())

private fun VideoView.toEmbedUiVideo(): EmbedUi.Video? =
    toVideoPayload()?.let { p ->
        EmbedUi.Video(
            posterUrl = p.posterUrl,
            playlistUrl = p.playlistUrl,
            aspectRatio = p.aspectRatio,
            durationSeconds = p.durationSeconds,
            altText = p.altText,
        )
    }

private fun ExternalView.toEmbedUiExternal(): EmbedUi.External =
    EmbedUi.External(
        uri = external.uri.raw,
        domain = displayDomainOf(external.uri.raw),
        title = external.title,
        description = external.description,
        thumbUrl = external.thumb?.raw,
    )

/**
 * The five fields required to render a Bluesky video embed
 * (`app.bsky.embed.video#view`). Returned as `null` from
 * [toVideoPayload] when the lexicon's required `playlist` is blank;
 * each call site maps that to its own `Unsupported` variant.
 *
 * Field-by-field notes:
 * - [posterUrl] is the optional `thumbnail` URL; null when absent.
 * - [aspectRatio] falls back to 16:9 (`1.777f`) when the lexicon's
 *   optional field is absent — the render layer needs a stable measure
 *   before the poster loads.
 * - [durationSeconds] is hard-coded to `null` in v1: the lexicon does
 *   NOT currently expose duration. Reserved for a future phase that
 *   sources it from a lexicon evolution or HLS manifest parsing
 *   (tracked in `add-feature-feed-video-embeds`).
 */
private data class VideoPayload(
    val posterUrl: String?,
    val playlistUrl: String,
    val aspectRatio: Float,
    val durationSeconds: Int?,
    val altText: String?,
)

private fun VideoView.toVideoPayload(): VideoPayload? {
    val playlistUrl = playlist.raw
    if (playlistUrl.isBlank()) return null
    val ratio =
        aspectRatio?.let { it.width.toFloat() / it.height.toFloat() }
            ?: VIDEO_FALLBACK_ASPECT_RATIO
    return VideoPayload(
        posterUrl = thumbnail?.raw,
        playlistUrl = playlistUrl,
        aspectRatio = ratio,
        durationSeconds = null,
        altText = alt?.takeIf { it.isNotBlank() },
    )
}

private const val VIDEO_FALLBACK_ASPECT_RATIO: Float = 16f / 9f

/**
 * Precomputes the user-readable display host for an external embed URI.
 * Strips a leading `www.` from the parsed host; falls back to the full
 * URI when the input is opaque or malformed (e.g. `mailto:foo@bar.com`)
 * so the render layer always has *something* to show.
 *
 * Uses `java.net.URI` rather than `android.net.Uri` so the mapper stays
 * JVM-pure and the unit-test path runs without a device runtime stub.
 */
private fun displayDomainOf(uri: String): String =
    runCatching { java.net.URI(uri).host }
        .getOrNull()
        ?.removePrefix("www.")
        ?: uri

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
 * Reads the `value` of an [AtField] for list-shaped fields, treating both
 * `Missing` (key absent) and `Null` (explicit JSON null) as an empty list.
 * The mapper doesn't need to distinguish "the author cleared this field"
 * from "the author never set it" — both produce an empty `PostUi.facets`.
 *
 * Earmarked for promotion upstream into `atproto-kotlin`'s runtime —
 * see [kikin81/atproto-kotlin#32](https://github.com/kikin81/atproto-kotlin/issues/32).
 * Delete this local extension once that lands and we bump the dep.
 */
private fun <T> AtField<List<T>>.valueOrEmpty(): List<T> = (this as? AtField.Defined)?.value ?: emptyList()
