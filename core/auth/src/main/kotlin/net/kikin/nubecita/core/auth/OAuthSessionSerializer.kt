package net.kikin.nubecita.core.auth

import androidx.datastore.core.Serializer
import io.github.kikin81.atproto.oauth.OAuthSession
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

internal object OAuthSessionSerializer : Serializer<OAuthSession?> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override val defaultValue: OAuthSession? = null

    override suspend fun readFrom(input: InputStream): OAuthSession? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return null
        return try {
            json.decodeFromString(OAuthSession.serializer(), bytes.decodeToString())
        } catch (_: SerializationException) {
            null
        }
    }

    override suspend fun writeTo(
        t: OAuthSession?,
        output: OutputStream,
    ) {
        if (t == null) return
        output.write(json.encodeToString(OAuthSession.serializer(), t).encodeToByteArray())
    }
}
