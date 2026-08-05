package net.kikin.nubecita.core.auth

import androidx.datastore.core.Serializer
import io.github.kikin81.atproto.oauth.PendingAuth
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore serializer for the in-flight OAuth login. Mirrors
 * [OAuthSessionSerializer] — the payload is wrapped in Tink's `AeadSerializer`
 * by the DI module, so this only handles the JSON layer.
 *
 * A malformed payload decodes to `null` rather than throwing: an unreadable
 * pending login is indistinguishable from no pending login, and both mean
 * "the user has to start sign-in again". That is the pre-existing behaviour
 * for a lost pending state, so degrading to it is safe.
 */
internal object PendingAuthSerializer : Serializer<PendingAuth?> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override val defaultValue: PendingAuth? = null

    override suspend fun readFrom(input: InputStream): PendingAuth? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return null
        return try {
            json.decodeFromString(PendingAuth.serializer(), bytes.decodeToString())
        } catch (_: SerializationException) {
            null
        }
    }

    override suspend fun writeTo(
        t: PendingAuth?,
        output: OutputStream,
    ) {
        if (t == null) return
        output.write(json.encodeToString(PendingAuth.serializer(), t).encodeToByteArray())
    }
}
