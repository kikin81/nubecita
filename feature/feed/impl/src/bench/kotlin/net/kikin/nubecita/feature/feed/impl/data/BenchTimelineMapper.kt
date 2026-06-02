package net.kikin.nubecita.feature.feed.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import timber.log.Timber
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Pure-function conversions from the bench JSON DTOs ([BenchTimelineDto]
 * and friends) into the production-shaped `:data:models` UI types —
 * [FeedItemUi], [PostUi], [EmbedUi], etc. Keeping the conversion
 * separate from the loader (in [BenchFakeFeedRepository]) means the
 * loader's caching policy and the DTO→UI shape mapping are
 * independently swappable.
 *
 * Error containment:
 *
 * - Per-item failures (malformed Video embed, missing External `uri`
 *   etc.) are caught inside [toFeedItemUi] and the offending item is
 *   skipped with a [Timber.w]. One bad fixture entry does NOT poison
 *   the entire timeline parse — the remaining 18-of-19 items still
 *   render and the bench journey can proceed against a deliberately-
 *   degraded feed.
 * - Bad timestamps fall back to [Clock.System.now] (NOT
 *   [Instant.DISTANT_PAST] — that produced ~58,000-year-ago strings
 *   that overran [PostCard]'s author/timestamp row and corrupted
 *   layout/measure metrics in the bench journey). The post stays at
 *   the visible top of the feed instead of being silently buried.
 * - Required-field-missing embed errors (Video without `playlistUrl`,
 *   External without `uri`/`domain`) demote the post's embed to
 *   [EmbedUi.Unsupported] rather than throwing — the post itself
 *   still renders as text-only.
 *
 * Float precision: [BenchEmbedDto.aspectRatio] and
 * [BenchImageDto.aspectRatio] are typed `Double?` in the DTO layer to
 * preserve JSON precision. The Float narrowing happens exactly once
 * here at the mapper boundary, keeping the narrowing site visible
 * for future fixture comparisons.
 */
internal object BenchTimelineMapper {
    private const val TAG = "BenchTimelineMapper"
    private const val DEFAULT_ASPECT_RATIO_16_9 = 16f / 9f

    fun toFeedItems(dto: BenchTimelineDto): List<FeedItemUi> = dto.items.mapNotNull { it.toFeedItemUi() }

    private fun BenchFeedItemDto.toFeedItemUi(): FeedItemUi? =
        runCatching {
            when (type) {
                BenchFeedItemDto.Type.Single ->
                    post?.let { FeedItemUi.Single(post = it.toPostUi()) }
                        ?: run {
                            Timber.tag(TAG).w("Single feed item with null post; skipping")
                            null
                        }
            }
        }.getOrElse { throwable ->
            // Per-item failure containment: a malformed embed or any
            // other unexpected mapper failure logs + skips this entry so
            // the rest of the timeline still renders. Without this, a
            // single bad fixture post takes down the whole bench feed
            // via the loader's runCatching → cached Result.failure.
            //
            // Log only the AT-URI's rkey (path segment after the last
            // `/`) rather than the full `at://did:plc:.../app.bsky.feed.post/<rkey>`
            // form — the codebase convention (see
            // `:core:auth/.../RedactDid`) is to avoid emitting full
            // DIDs to logcat surfaces that could be captured by a
            // future Crashlytics/Sentry tree. The rkey alone is
            // enough to identify which fixture post failed during
            // editing.
            Timber.tag(TAG).w(
                throwable,
                "Skipping malformed feed item: rkey=%s",
                post?.id?.substringAfterLast('/'),
            )
            null
        }

    private fun BenchPostDto.toPostUi(): PostUi =
        PostUi(
            id = id,
            cid = cid,
            author = author.toAuthorUi(),
            createdAt = parseInstantOrNow(createdAt),
            text = text,
            facets = persistentListOf(),
            embed = embed.toEmbedUi(),
            stats = stats.toPostStatsUi(),
            viewer = viewer.toViewerStateUi(),
            repostedBy = repostedBy,
        )

    private fun BenchAuthorDto.toAuthorUi(): AuthorUi =
        AuthorUi(
            did = did,
            handle = handle,
            displayName = displayName,
            avatarUrl = avatarUrl,
        )

    private fun BenchStatsDto.toPostStatsUi(): PostStatsUi =
        PostStatsUi(
            replyCount = replyCount,
            repostCount = repostCount,
            likeCount = likeCount,
            quoteCount = quoteCount,
        )

    private fun BenchViewerDto.toViewerStateUi(): ViewerStateUi =
        ViewerStateUi(
            isLikedByViewer = isLikedByViewer,
            isRepostedByViewer = isRepostedByViewer,
            isFollowingAuthor = isFollowingAuthor,
            likeUri = likeUri,
            repostUri = repostUri,
            isAuthorMutedByViewer = isAuthorMutedByViewer,
            isAuthorBlockedByViewer = isAuthorBlockedByViewer,
            isAuthorBlockingViewer = isAuthorBlockingViewer,
        )

    private fun BenchEmbedDto.toEmbedUi(): EmbedUi =
        when (type) {
            BenchEmbedDto.Type.Empty -> EmbedUi.Empty
            BenchEmbedDto.Type.Images ->
                EmbedUi.Images(
                    items = (items ?: emptyList()).map { it.toImageUi() }.toPersistentList(),
                )
            BenchEmbedDto.Type.Video -> toEmbedUiVideoOrUnsupported()
            BenchEmbedDto.Type.External -> toEmbedUiExternalOrUnsupported()
            BenchEmbedDto.Type.Gif ->
                gifUrl?.let { url ->
                    EmbedUi.Gif(
                        gifUrl = url,
                        thumbUrl = thumbUrl,
                        aspectRatio = aspectRatio?.toFloat(),
                        alt = altText,
                    )
                } ?: EmbedUi.Unsupported(typeUri = "app.bsky.embed.external")
        }

    /**
     * Required-field-missing Video → [EmbedUi.Unsupported] rather than
     * a hard throw. A fixture-author typo on a single Video embed
     * surfaces as a per-card "Unsupported embed" chip instead of
     * killing the whole timeline parse.
     */
    private fun BenchEmbedDto.toEmbedUiVideoOrUnsupported(): EmbedUi {
        val resolvedPlaylist = playlistUrl
        if (resolvedPlaylist == null) {
            Timber.tag(TAG).w("Video embed missing playlistUrl; rendering as Unsupported")
            return EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")
        }
        return EmbedUi.Video(
            posterUrl = posterUrl,
            playlistUrl = resolvedPlaylist,
            aspectRatio = aspectRatio?.toFloat() ?: DEFAULT_ASPECT_RATIO_16_9,
            durationSeconds = durationSeconds,
            altText = altText,
        )
    }

    /**
     * Required-field-missing External → [EmbedUi.Unsupported] rather
     * than a hard throw. See [toEmbedUiVideoOrUnsupported] for the
     * rationale.
     */
    private fun BenchEmbedDto.toEmbedUiExternalOrUnsupported(): EmbedUi {
        val resolvedUri = uri
        val resolvedDomain = domain
        if (resolvedUri == null || resolvedDomain == null) {
            Timber.tag(TAG).w(
                "External embed missing uri=%b / domain=%b; rendering as Unsupported",
                resolvedUri == null,
                resolvedDomain == null,
            )
            return EmbedUi.Unsupported(typeUri = "app.bsky.embed.external")
        }
        return EmbedUi.External(
            uri = resolvedUri,
            domain = resolvedDomain,
            title = title.orEmpty(),
            description = description.orEmpty(),
            thumbUrl = thumbUrl,
        )
    }

    private fun BenchImageDto.toImageUi(): ImageUi =
        ImageUi(
            fullsizeUrl = fullsizeUrl,
            thumbUrl = thumbUrl,
            altText = altText,
            aspectRatio = aspectRatio?.toFloat(),
        )

    /**
     * Falls back to [Clock.System.now] (NOT [Instant.DISTANT_PAST]) so a
     * fixture-author typo produces a "just now" timestamp instead of a
     * layout-breaking "57,000 years ago" string. The Timber.w surfaces
     * the bad input for debugging during fixture edits.
     */
    private fun parseInstantOrNow(raw: String): Instant =
        runCatching { Instant.parse(raw) }
            .getOrElse {
                Timber.tag(TAG).w("Unparseable createdAt %s; falling back to Clock.System.now()", raw)
                Clock.System.now()
            }
}
