package net.kikin.nubecita.core.wearcontract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Schema versioning for every Wear Data Layer payload.
 *
 * Each payload carries an integer `schemaVersion` — the contract's **major**
 * version. Additive changes (a new optional field) keep the major version:
 * [WearJson] ignores unknown keys, so an older watch reads a newer phone's
 * payload. A breaking change bumps [CURRENT]; an older reader then refuses the
 * payload via [WearCodec.decode] instead of mis-parsing it, keeps its last data,
 * and asks the user to update.
 */
object WearSchema {
    const val CURRENT = 1
}

/** Implemented by every top-level payload so the codec can stamp and check it. */
interface WearPayload {
    val schemaVersion: Int
}

/** JSON configuration shared by both ends of the contract. */
val WearJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/** Outcome of [WearCodec.decode]. */
sealed interface WearDecodeResult<out T> {
    data class Decoded<T>(
        val value: T,
    ) : WearDecodeResult<T>

    /** The payload's major version is newer than this build understands. */
    data class UnsupportedVersion(
        val found: Int,
    ) : WearDecodeResult<Nothing>

    /** Not valid JSON for the expected type (or missing a version). */
    data class Malformed(
        val cause: Throwable,
    ) : WearDecodeResult<Nothing>
}

/** Encodes and decodes payloads as the JSON string stored in a Data Layer item. */
object WearCodec {
    fun <T : WearPayload> encode(
        serializer: KSerializer<T>,
        value: T,
    ): String = WearJson.encodeToString(serializer, value)

    /**
     * Reads `schemaVersion` first, so a payload from a newer major version is
     * reported as [WearDecodeResult.UnsupportedVersion] even when its shape no
     * longer parses as [serializer]'s type.
     */
    fun <T : WearPayload> decode(
        serializer: KSerializer<T>,
        json: String,
    ): WearDecodeResult<T> {
        val version =
            try {
                WearJson.decodeFromString(VersionProbe.serializer(), json).schemaVersion
            } catch (e: SerializationException) {
                return WearDecodeResult.Malformed(e)
            } catch (e: IllegalArgumentException) {
                return WearDecodeResult.Malformed(e)
            }
        if (version > WearSchema.CURRENT) return WearDecodeResult.UnsupportedVersion(version)
        return try {
            WearDecodeResult.Decoded(WearJson.decodeFromString(serializer, json))
        } catch (e: SerializationException) {
            WearDecodeResult.Malformed(e)
        } catch (e: IllegalArgumentException) {
            WearDecodeResult.Malformed(e)
        }
    }

    @Serializable
    private data class VersionProbe(
        val schemaVersion: Int,
    )
}
