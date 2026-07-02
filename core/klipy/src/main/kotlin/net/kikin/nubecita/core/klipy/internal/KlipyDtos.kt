package net.kikin.nubecita.core.klipy.internal

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * KLIPY wire DTOs. Every field is nullable with a default so a partial or
 * evolving payload deserializes instead of throwing (KLIPY omits fields freely,
 * and `Json { ignoreUnknownKeys = true }` handles the extras). These never
 * escape `:core:klipy` — [KlipyMediaMapper] turns them into `:data:models`.
 */
@Serializable
internal data class KlipyMediaResponseDto(
    val result: Boolean? = null,
    val data: KlipyDataDto? = null,
)

@Serializable
internal data class KlipyDataDto(
    val data: List<KlipyMediaItemDto>? = null,
    @SerialName("has_next") val hasNext: Boolean? = null,
)

@Serializable
internal data class KlipyCategoriesResponseDto(
    val result: Boolean? = null,
    val data: List<String>? = null,
)

/**
 * A KLIPY feed item. The wire uses a `type` discriminator whose values
 * (`clip`, `ad`, or a media type like `gif`/`sticker`) are NOT serial names, so
 * a [JsonContentPolymorphicSerializer] selects the concrete DTO by inspecting
 * `type`. Clips and ads are modelled minimally — [KlipyMediaMapper] drops them
 * for v1 — but they must still deserialize so a mixed page doesn't fail whole.
 */
@Serializable(with = KlipyMediaItemDtoSerializer::class)
internal sealed interface KlipyMediaItemDto {
    @Serializable
    data class General(
        val slug: String? = null,
        val title: String? = null,
        @SerialName("blur_preview") val blurPreview: String? = null,
        val file: KlipyDimensionsDto? = null,
        val type: String? = null,
    ) : KlipyMediaItemDto

    @Serializable
    data class Clip(
        val slug: String? = null,
        val type: String? = null,
    ) : KlipyMediaItemDto

    @Serializable
    data class Ad(
        val type: String? = null,
    ) : KlipyMediaItemDto
}

internal object KlipyMediaItemDtoSerializer :
    JsonContentPolymorphicSerializer<KlipyMediaItemDto>(KlipyMediaItemDto::class) {
    override fun selectDeserializer(
        element: JsonElement,
    ): DeserializationStrategy<KlipyMediaItemDto> {
        val type =
            (element as? JsonObject)
                ?.get("type")
                ?.jsonPrimitive
                ?.contentOrNull
        return when (type) {
            "clip" -> KlipyMediaItemDto.Clip.serializer()
            "ad" -> KlipyMediaItemDto.Ad.serializer()
            else -> KlipyMediaItemDto.General.serializer()
        }
    }
}

@Serializable
internal data class KlipyDimensionsDto(
    val hd: KlipyFileTypesDto? = null,
    val md: KlipyFileTypesDto? = null,
    val sm: KlipyFileTypesDto? = null,
    val xs: KlipyFileTypesDto? = null,
)

@Serializable
internal data class KlipyFileTypesDto(
    val gif: KlipyFileDto? = null,
    val webp: KlipyFileDto? = null,
    val mp4: KlipyFileDto? = null,
)

@Serializable
internal data class KlipyFileDto(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)
