package net.kikin.nubecita.feature.feed.impl.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bench-only intermediate DTOs for `app/src/bench/assets/timeline.json`.
 *
 * These types live in the bench source set deliberately — `:data:models`
 * stays clean of `kotlinx.serialization.runtime` per the
 * [data-models capability spec][_models_policy] (compose-runtime only).
 * The mapper at [BenchTimelineMapper] converts these DTOs into the
 * existing `:data:models` UI types ([net.kikin.nubecita.data.models.FeedItemUi],
 * [net.kikin.nubecita.data.models.PostUi], etc.) so the bench `Feed`
 * renders against the same shape production does.
 *
 * Sealed types in the JSON use a flat `type` enum discriminator rather
 * than kotlinx.serialization polymorphism — the mapper does an explicit
 * `when` on the discriminator. Two reasons:
 *
 * - Polymorphic deserialization configuration adds boilerplate
 *   (`SerializersModule { polymorphic { ... } }`) the bench scope
 *   doesn't justify.
 * - The flat shape lets the JSON fixture stay forgiving: adding a new
 *   variant means extending the enum plus the mapper `when`.
 *
 * Both discriminator enums ([BenchFeedItemDto.Type] and
 * [BenchEmbedDto.Type]) carry a `= Type.Single` / `= Type.Empty`
 * default on the owning property so an unknown discriminator string
 * coerces to a safe fallback rather than throwing
 * `SerializationException` and bricking the bench journey via the
 * [BenchFakeFeedRepository] cache. The `Single`/`Empty` defaults are
 * the most-permissive variants and intentionally make a fixture-author
 * typo silent-but-renderable — failure is loud in dev (Timber-tag
 * warning) but the feed still loads.
 *
 * Field names mirror the Kotlin property names of the target types
 * verbatim (`fullsizeUrl`, `thumbUrl`, `displayName`, etc.) so the JSON
 * fixture is human-editable without a serial-name lookup table.
 *
 * [_models_policy]: openspec/specs/data-models/spec.md
 */

@Serializable
internal data class BenchTimelineDto(
    val cursor: String? = null,
    val items: List<BenchFeedItemDto> = emptyList(),
)

/**
 * Sealed-type variant for a top-level feed item. Section A2's fixture
 * only uses [Type.Single]; the other [net.kikin.nubecita.data.models.FeedItemUi]
 * variants ([ReplyCluster][net.kikin.nubecita.data.models.FeedItemUi.ReplyCluster],
 * [SelfThreadChain][net.kikin.nubecita.data.models.FeedItemUi.SelfThreadChain],
 * [Blocked][net.kikin.nubecita.data.models.FeedItemUi.Blocked],
 * [NotFound][net.kikin.nubecita.data.models.FeedItemUi.NotFound]) land
 * when the fixture grows to exercise those shapes.
 */
@Serializable
internal data class BenchFeedItemDto(
    // Default ensures an unknown discriminator string (future-fixture-
    // typo) coerces to Single rather than throwing — see the type-level
    // KDoc above.
    val type: Type = Type.Single,
    val post: BenchPostDto? = null,
) {
    @Serializable
    internal enum class Type {
        @SerialName("Single")
        Single,
    }
}

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

/**
 * Flat DTO covering every embed variant the bench fixture uses today.
 * The mapper dispatches on [type] and reads only the fields that
 * variant populates — null/default fields for unused slots are
 * harmless because every variant-specific field is optional.
 *
 * Variants currently shipped by the fixture: `Empty`, `Images`,
 * `Video`, `External`. The wider [net.kikin.nubecita.data.models.EmbedUi]
 * hierarchy ([Record][net.kikin.nubecita.data.models.EmbedUi.Record],
 * [RecordWithMedia][net.kikin.nubecita.data.models.EmbedUi.RecordWithMedia],
 * etc.) lands here once the fixture grows.
 *
 * [aspectRatio] is typed `Double?` (not `Float?`) so JSON values like
 * `1.7777778` round-trip without the silent Double-to-Float narrowing
 * the kotlinx.serialization deserializer would otherwise apply at the
 * DTO boundary. The mapper narrows to `Float` exactly once when
 * constructing [net.kikin.nubecita.data.models.EmbedUi.Video], keeping
 * the precision-loss site explicit.
 */
@Serializable
internal data class BenchEmbedDto(
    // Default makes an unknown discriminator coerce to Empty — see the
    // type-level KDoc on BenchTimelineDto.
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
