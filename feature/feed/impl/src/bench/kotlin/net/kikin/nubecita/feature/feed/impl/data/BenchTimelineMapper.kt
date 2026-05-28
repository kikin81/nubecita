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
import kotlin.time.Instant

/**
 * Pure-function conversions from the bench JSON DTOs ([BenchTimelineDto]
 * and friends) into the production-shaped `:data:models` UI types —
 * [FeedItemUi], [PostUi], [EmbedUi], etc. Keeping the conversion
 * separate from the loader (in [FakeFeedRepository]) means the loader's
 * `@Singleton` caching policy and the DTO→UI shape mapping are
 * independently swappable.
 *
 * Conventions:
 *
 * - Field aspect ratios fall back to a 16:9 (`1.7777f`) default when the
 *   fixture omits them — matches the production mapper's behaviour
 *   documented on [EmbedUi.Video.aspectRatio].
 * - Unparseable timestamps fall back to [Instant.DISTANT_PAST] rather
 *   than throwing — the bench fixture is checked-in test data; if a
 *   timestamp is wrong, the better signal is a visually-broken card in
 *   the bench journey than a `FakeFeedRepository` that fails its
 *   `getTimeline` call entirely.
 * - Facets are hard-coded empty; see [BenchPostDto.facets] for why.
 */
internal object BenchTimelineMapper {
    private const val TAG = "BenchTimelineMapper"
    private const val DEFAULT_ASPECT_RATIO_16_9 = 16f / 9f

    fun toFeedItems(dto: BenchTimelineDto): List<FeedItemUi> = dto.items.mapNotNull { it.toFeedItemUi() }

    private fun BenchFeedItemDto.toFeedItemUi(): FeedItemUi? =
        when (type) {
            BenchFeedItemDto.Type.Single ->
                post
                    ?.let { FeedItemUi.Single(post = it.toPostUi()) }
                    .also {
                        if (it == null) Timber.tag(TAG).w("Single feed item with null post; skipping")
                    }
        }

    private fun BenchPostDto.toPostUi(): PostUi =
        PostUi(
            id = id,
            cid = cid,
            author = author.toAuthorUi(),
            createdAt = parseInstantOrEpoch(createdAt),
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
            BenchEmbedDto.Type.Video ->
                EmbedUi.Video(
                    posterUrl = posterUrl,
                    // playlistUrl is non-null in the lexicon `view` form, and the
                    // bench fixture must populate it. Fall back to the empty
                    // string + Unsupported promotion would mask a fixture bug;
                    // the assertion form keeps the failure loud during fixture
                    // edits.
                    playlistUrl = playlistUrl ?: error("Video embed missing playlistUrl"),
                    aspectRatio = aspectRatio ?: DEFAULT_ASPECT_RATIO_16_9,
                    durationSeconds = durationSeconds,
                    altText = altText,
                )
            BenchEmbedDto.Type.External ->
                EmbedUi.External(
                    uri = uri ?: error("External embed missing uri"),
                    domain = domain ?: error("External embed missing domain"),
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
            aspectRatio = aspectRatio,
        )

    private fun parseInstantOrEpoch(raw: String): Instant =
        runCatching { Instant.parse(raw) }
            .getOrElse {
                Timber.tag(TAG).w("Unparseable createdAt %s; falling back to DISTANT_PAST", raw)
                Instant.DISTANT_PAST
            }
}
