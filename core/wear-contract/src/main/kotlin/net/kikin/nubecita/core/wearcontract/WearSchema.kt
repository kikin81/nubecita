package net.kikin.nubecita.core.wearcontract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

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
     * Parses once into a JSON tree and reads `schemaVersion` from it before
     * decoding, so a payload from a newer major version is reported as
     * [WearDecodeResult.UnsupportedVersion] even when its shape no longer
     * parses as [serializer]'s type.
     */
    fun <T : WearPayload> decode(
        serializer: KSerializer<T>,
        json: String,
    ): WearDecodeResult<T> =
        try {
            val tree = WearJson.parseToJsonElement(json)
            val version =
                (tree as? JsonObject)?.get(SCHEMA_VERSION_KEY)?.let { (it as? JsonPrimitive)?.intOrNull }
                    ?: return WearDecodeResult.Malformed(SerializationException("missing or non-integer $SCHEMA_VERSION_KEY"))
            if (version > WearSchema.CURRENT) {
                WearDecodeResult.UnsupportedVersion(version)
            } else {
                WearDecodeResult.Decoded(WearJson.decodeFromJsonElement(serializer, tree))
            }
        } catch (e: SerializationException) {
            WearDecodeResult.Malformed(e)
        } catch (e: IllegalArgumentException) {
            WearDecodeResult.Malformed(e)
        }

    private const val SCHEMA_VERSION_KEY = "schemaVersion"
}
