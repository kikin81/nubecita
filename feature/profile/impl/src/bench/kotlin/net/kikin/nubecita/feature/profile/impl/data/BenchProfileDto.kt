package net.kikin.nubecita.feature.profile.impl.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bench-only intermediate DTOs for the profile fixture at
 * `feature/profile/impl/src/bench/assets/profile.json` — loaded by
 * [BenchFakeProfileRepository] via `AssetManager`.
 */
@Serializable
internal data class BenchProfileDto(
    val header: BenchProfileHeaderDto,
    val posts: List<BenchPostDto> = emptyList(),
    val replies: List<BenchPostDto> = emptyList(),
    val media: List<BenchPostDto> = emptyList(),
)

@Serializable
internal data class BenchProfileHeaderDto(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val website: String? = null,
    val joinedDisplay: String? = null,
    val postsCount: Long = 0,
    val followersCount: Long = 0,
    val followsCount: Long = 0,
    /** `"verified"`, `"trustedVerifier"`, or null/omitted for no badge. */
    val verifiedBadge: String? = null,
)

@Serializable
internal data class BenchPostDto(
    val id: String,
    val cid: String,
    val author: BenchAuthorDto,
    val createdAt: String,
    val text: String,
    val embed: BenchEmbedDto = BenchEmbedDto(type = BenchEmbedDto.Type.Empty),
    val stats: BenchStatsDto = BenchStatsDto(),
    val viewer: BenchViewerDto = BenchViewerDto(),
    val repostedBy: String? = null,
)

@Serializable
internal data class BenchAuthorDto(
    val did: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

@Serializable
internal data class BenchStatsDto(
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val likeCount: Int = 0,
    val quoteCount: Int = 0,
)

@Serializable
internal data class BenchViewerDto(
    val isLikedByViewer: Boolean = false,
    val isRepostedByViewer: Boolean = false,
    val isFollowingAuthor: Boolean = false,
    val likeUri: String? = null,
    val repostUri: String? = null,
    val isAuthorMutedByViewer: Boolean = false,
    val isAuthorBlockedByViewer: Boolean = false,
    val isAuthorBlockingViewer: Boolean = false,
)

@Serializable
internal data class BenchEmbedDto(
    val type: Type = Type.Empty,
    val items: List<BenchImageDto>? = null,
    val posterUrl: String? = null,
    val playlistUrl: String? = null,
    val aspectRatio: Double? = null,
    val durationSeconds: Int? = null,
    val altText: String? = null,
    val uri: String? = null,
    val domain: String? = null,
    val title: String? = null,
    val description: String? = null,
    val thumbUrl: String? = null,
) {
    @Serializable
    internal enum class Type {
        @SerialName("Empty")
        Empty,

        @SerialName("Images")
        Images,

        @SerialName("Video")
        Video,

        @SerialName("External")
        External,
    }
}

@Serializable
internal data class BenchImageDto(
    val fullsizeUrl: String,
    val thumbUrl: String? = null,
    val altText: String? = null,
    val aspectRatio: Double? = null,
)
