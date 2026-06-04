package net.kikin.nubecita.feature.feed.impl.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bench-only intermediate DTOs for the timeline fixture at
 * `feature/feed/impl/src/bench/assets/timeline.json` — loaded by
 * [BenchFakeFeedRepository] via `AssetManager` on the asset path
 * `timeline.json`.
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
 * Two-layer safety net for discriminator robustness:
 *
 * 1. **Missing-field protection**: each owning property carries a
 *    `= Type.Single` / `= Type.Empty` default, so a fixture entry
 *    that omits `"type"` entirely deserializes to the most-permissive
 *    variant rather than throwing.
 * 2. **Unknown-value protection**: [BenchFakeFeedRepository.JSON]
 *    sets `coerceInputValues = true`. With this flag, a JSON value
 *    that doesn't match any known enum case (a future
 *    `"type": "ReplyCluster"` ahead of the enum extension, or a
 *    fixture-author typo like `"video"` lowercase) is coerced to the
 *    property's declared default rather than throwing
 *    `SerializationException`.
 *
 * Both nets are needed: defaults handle the missing-field case;
 * coerceInputValues handles the unknown-value case. Without
 * coerceInputValues, an unknown discriminator throws and the bench
 * journey lands on InitialError until the fixture is fixed (per
 * [BenchFakeFeedRepository]'s retryable-cache, Retry would re-parse
 * but would re-hit the same throw).
 *
 * Known trade-off of coerceInputValues: an explicit JSON `null` on a
 * non-nullable Kotlin field that HAS a default is silently substituted
 * with the default rather than throwing. For a checked-in test
 * fixture this is acceptable — explicit `null` isn't an intentional
 * encoding here. The alternative (drop the flag and add
 * `@JsonEnumDefaultValue` on each enum case) requires
 * kotlinx-serialization-json 1.6+'s experimental API and complicates
 * the type definitions; not worth it for the bench scope.
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
    val gifUrl: String? = null,
    // Optional NSFW content-warning cover for this media embed (mirrors the
    // moderation decision production computes). One of the
    // ContentWarningCategory names — ADULT_CONTENT / SEXUALLY_SUGGESTIVE /
    // GRAPHIC_MEDIA / NON_SEXUAL_NUDITY — or null for no cover. The bench feed
    // builds PostUi directly (no moderation pass), so this sets the cover
    // explicitly to demo the feature; keep such posts toward the END of
    // timeline.json so the Play-listing marketing screenshots (captured from the
    // top of the bench feed) never include covered content.
    val contentWarning: String? = null,
    val contentWarningOverridable: Boolean = true,
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

        @SerialName("Gif")
        Gif,
    }
}

@Serializable
internal data class BenchImageDto(
    val fullsizeUrl: String,
    val thumbUrl: String? = null,
    val altText: String? = null,
    val aspectRatio: Double? = null,
)
