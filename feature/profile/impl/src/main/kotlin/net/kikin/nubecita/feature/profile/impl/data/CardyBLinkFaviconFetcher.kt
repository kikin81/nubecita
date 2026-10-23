package net.kikin.nubecita.feature.profile.impl.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import timber.log.Timber
import javax.inject.Inject

/**
 * [LinkFaviconFetcher] backed by Bluesky's CardyB favicon proxy — the same
 * service and rules the official client uses (`useUploadLinkIcon`): always ask
 * for the site's *current* icon (`fresh=true`, no cache), give up after 8 s,
 * and accept only a 2xx `image/png` within the lexicon's 100 KB icon cap.
 * Anything else is "no icon", never an error.
 */
internal class CardyBLinkFaviconFetcher
    @Inject
    constructor(
        private val httpClient: HttpClient,
    ) : LinkFaviconFetcher {
        override suspend fun fetch(url: String): ByteArray? =
            try {
                val response =
                    httpClient.get(CARDYB_FAVICON) {
                        parameter("url", url)
                        parameter("fresh", "true")
                        header(HttpHeaders.CacheControl, "no-store")
                        timeout { requestTimeoutMillis = TIMEOUT_MS }
                    }
                val type = response.contentType()
                val declaredLength = response.contentLength()
                when {
                    !response.status.isSuccess() -> null
                    type == null || type.contentType != "image" || type.contentSubtype != "png" -> null
                    declaredLength != null && declaredLength > MAX_ICON_BYTES -> null
                    else -> {
                        // Bounded read (+1 to detect overflow) so a missing or
                        // lying Content-Length can't make us buffer a huge body.
                        val bytes = response.bodyAsChannel().readRemaining(MAX_ICON_BYTES + 1L).readByteArray()
                        bytes.takeIf { it.isNotEmpty() && it.size <= MAX_ICON_BYTES }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                // The URL is user content; log only the failure type.
                Timber.tag(TAG).d("favicon fetch failed: %s", e.javaClass.name)
                null
            }

        internal companion object {
            private const val TAG = "LinkFavicon"
            const val CARDYB_FAVICON = "https://cardyb.bsky.app/v1/favicon"
            const val TIMEOUT_MS = 8_000L

            /** `app.bsky.actor.link#icon` maxSize. */
            const val MAX_ICON_BYTES = 100_000
        }
    }
