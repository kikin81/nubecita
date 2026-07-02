package net.kikin.nubecita.core.klipy.internal

import io.ktor.client.plugins.logging.Logger
import timber.log.Timber

/**
 * Ktor [Logger] that masks the KLIPY API key before anything reaches logcat.
 *
 * KLIPY embeds the key as a path segment of every request URL
 * (`…/api/v1/<key>/gifs/search`), so the default request-line logging would
 * leak the whole secret. This wrapper replaces every occurrence of [apiKey]
 * with a fixed placeholder first. A blank key (keyless/bench builds) is left
 * untouched — replacing the empty string would corrupt every message.
 */
internal class KlipyKeyRedactingLogger(
    private val apiKey: String,
    private val delegate: (String) -> Unit = { Timber.tag(TAG).d(it) },
) : Logger {
    override fun log(message: String) {
        delegate(redact(message))
    }

    private fun redact(message: String): String = if (apiKey.isEmpty()) message else message.replace(apiKey, REDACTED)

    private companion object {
        const val TAG = "Klipy"
        const val REDACTED = "***"
    }
}
