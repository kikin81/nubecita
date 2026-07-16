package net.kikin.nubecita.feature.profile.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.profile.impl.PinnedPostRef
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship
import timber.log.Timber
import kotlin.time.Clock
import kotlin.time.Instant

internal object BenchProfileMapper {
    private const val TAG = "BenchProfileMapper"
    private const val DEFAULT_ASPECT_RATIO_16_9 = 16f / 9f

    fun toProfileHeaderWithViewer(dto: BenchProfileHeaderDto): ProfileHeaderWithViewer {
        val header =
            ProfileHeaderUi(
                did = dto.did,
                handle = dto.handle,
                displayName = dto.displayName,
                avatarUrl = dto.avatarUrl,
                bannerUrl = dto.bannerUrl,
                bio = dto.bio,
                location = dto.location,
                website = dto.website,
                joinedDisplay = dto.joinedDisplay,
                postsCount = dto.postsCount,
                followersCount = dto.followersCount,
                followsCount = dto.followsCount,
                // Bench: pin the self-authored benchPinnedPost so the Posts tab's
                // pinned slot renders offline. uri/cid must match core:posts'
                // benchPinnedPost (resolved via BenchFakePostRepository.getPost).
                pinnedPost =
                    PinnedPostRef(
                        uri = "at://did:plc:benchnubecita0000000000000/app.bsky.feed.post/pinned0000001",
                        cid = "bafyreibenchpinned00000000000000000000000000000000000001",
                    ),
                verifiedBadge = dto.verifiedBadge.toVerifiedBadge(),
            )
        return ProfileHeaderWithViewer(header = header, viewerRelationship = ViewerRelationship.None)
    }

    // Bench fixtures spell the badge as a string; map to the UI enum (unknown/null → None).
    private fun String?.toVerifiedBadge(): VerifiedBadge =
        when (this) {
            "trustedVerifier" -> VerifiedBadge.TrustedVerifier
            "verified" -> VerifiedBadge.Verified
            else -> VerifiedBadge.None
        }

    fun toPostUiList(dtos: List<BenchPostDto>): List<PostUi> = dtos.map { it.toPostUi() }

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
        }

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

    private fun parseInstantOrNow(raw: String): Instant =
        runCatching { Instant.parse(raw) }
            .getOrElse {
                Timber.tag(TAG).w("Unparseable createdAt %s; falling back to Clock.System.now()", raw)
                Clock.System.now()
            }

    fun toTabItemPosts(dtos: List<BenchPostDto>): List<TabItemUi.Post> = toPostUiList(dtos).map { TabItemUi.Post(it) }

    fun toTabItemMediaCells(dtos: List<BenchPostDto>): List<TabItemUi.MediaCell> =
        dtos.mapNotNull { post ->
            val isVideo = post.embed.type == BenchEmbedDto.Type.Video
            val thumbUrl =
                if (isVideo) {
                    post.embed.posterUrl
                } else {
                    post.embed.items
                        ?.firstOrNull()
                        ?.let { it.thumbUrl ?: it.fullsizeUrl }
                }
            if (thumbUrl != null) {
                TabItemUi.MediaCell(
                    postUri = post.id,
                    thumbUrl = thumbUrl,
                    isVideo = isVideo,
                )
            } else {
                null
            }
        }
}
