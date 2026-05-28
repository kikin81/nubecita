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
 * Sealed types in the JSON use a flat `type: String` discriminator
 * rather than kotlinx.serialization polymorphism — the mapper does an
 * explicit `when` on the discriminator string. Two reasons:
 *
 * - Polymorphic deserialization configuration adds boilerplate
 *   (`SerializersModule { polymorphic { ... } }`) the bench scope
 *   doesn't justify.
 * - The flat shape lets the JSON fixture stay forgiving: adding a new
 *   variant means extending the `when` plus the DTO, with no central
 *   registration site to remember to update.
 *
 * Field names mirror the Kotlin property names of the target types
 * verbatim (`fullsizeUrl`, `thumbUrl`, `displayName`, etc.) so the JSON
 * fixture is human-editable without a serial-name lookup table.
 *
 * Every field is non-null with a sensible default where the JSON might
 * omit it (per kotlinx.serialization's
 * [Json.encodeDefaults]/[Json.ignoreUnknownKeys] settings used by the
 * loader) — this keeps the fixture easy to extend in Section A2+ when
 * the timeline grows from the current 15 items.
 *
 * [_models_policy]: openspec/specs/data-models/spec.md
 */

@Serializable
internal data class BenchTimelineDto(
    val cursor: String? = null,
    val items: List<BenchFeedItemDto> = emptyList(),
)

/**
 * Sealed-type variant for a top-level feed item. Section A1's fixture
 * only uses [Type.Single]; the other [net.kikin.nubecita.data.models.FeedItemUi]
 * variants ([ReplyCluster][net.kikin.nubecita.data.models.FeedItemUi.ReplyCluster],
 * [SelfThreadChain][net.kikin.nubecita.data.models.FeedItemUi.SelfThreadChain],
 * [Blocked][net.kikin.nubecita.data.models.FeedItemUi.Blocked],
 * [NotFound][net.kikin.nubecita.data.models.FeedItemUi.NotFound]) land
 * when the fixture grows to exercise those shapes.
 */
@Serializable
internal data class BenchFeedItemDto(
    val type: Type,
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
    /**
     * The fixture currently ships `"facets": []` for every post — bench
     * doesn't exercise rich-text affordances. The wire `Facet` type lives
     * in the atproto-kotlin runtime; if the fixture later needs links /
     * mentions, this field grows into `List<BenchFacetDto>` plus a
     * mapper branch that constructs typed [io.github.kikin81.atproto.app.bsky.richtext.Facet]
     * instances. Today the loader hard-codes empty.
     */
    val facets: List<JsonElementUnused> = emptyList(),
    val embed: BenchEmbedDto = BenchEmbedDto(type = BenchEmbedDto.Type.Empty),
    val stats: BenchStatsDto = BenchStatsDto(),
    val viewer: BenchViewerDto = BenchViewerDto(),
    val repostedBy: String? = null,
)

/**
 * Placeholder type so `facets: []` in the JSON parses without coupling
 * to atproto-runtime's [io.github.kikin81.atproto.app.bsky.richtext.Facet]
 * serializer. Replace with a real `BenchFacetDto` if the fixture starts
 * carrying populated facet arrays.
 */
@Serializable
internal class JsonElementUnused

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
 */
@Serializable
internal data class BenchEmbedDto(
    val type: Type,
    val items: List<BenchImageDto>? = null,
    val posterUrl: String? = null,
    val playlistUrl: String? = null,
    val aspectRatio: Float? = null,
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
    val aspectRatio: Float? = null,
)
