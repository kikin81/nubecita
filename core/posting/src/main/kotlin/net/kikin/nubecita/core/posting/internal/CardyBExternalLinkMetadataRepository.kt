package net.kikin.nubecita.core.posting.internal

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.image.EncodedImage
import net.kikin.nubecita.core.posting.ExternalLinkMetadataRepository
import net.kikin.nubecita.core.posting.LinkPreview
import timber.log.Timber
import javax.inject.Inject

/**
 * [ExternalLinkMetadataRepository] backed by Bluesky's CardyB service
 * (`cardyb.bsky.app/v1/extract`) — the same service the official app uses, so
 * cards render consistently across clients. Reuses the singleton Ktor
 * [HttpClient] from `:core:auth` (the `HttpTimeout` plugin is installed there, so
 * per-request `timeout {}` works).
 *
 * Everything degrades to `null` (no card / no thumb): the composer surfaces no
 * error and the post still goes through. Nothing CardyB-specific escapes this
 * class — callers see only [LinkPreview] / [EncodedImage].
 */
internal class CardyBExternalLinkMetadataRepository
    @Inject
    constructor(
        private val httpClient: HttpClient,
    ) : ExternalLinkMetadataRepository {
        override suspend fun fetch(url: String): LinkPreview? =
            try {
                val response =
                    httpClient.get(CARDYB_EXTRACT) {
                        // URL-encoded as a query param — a pasted URL with `?`/`&`
                        // would otherwise be parsed as CardyB's own params.
                        parameter("url", url)
                        timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
                    }
                if (!response.status.isSuccess()) {
                    null
                } else {
                    val dto = JSON.decodeFromString(CardyBResponse.serializer(), response.bodyAsText())
                    val title = dto.title?.trim().orEmpty()
                    when {
                        // Non-empty `error` = CardyB couldn't resolve a card.
                        dto.error.isNotBlank() -> null
                        // No usable title → no card (a description-only card is useless).
                        title.isBlank() -> null
                        else ->
                            LinkPreview(
                                // CardyB follows redirects; its `url` is the final
                                // destination. Fall back to the typed URL when absent.
                                uri = dto.url?.takeIf { it.isNotBlank() } ?: url,
                                title = title,
                                description = dto.description?.trim().orEmpty(),
                                imageUrl = dto.image?.takeIf { it.isNotBlank() },
                            )
                    }
                }
            } catch (cancellation: CancellationException) {
                // Don't swallow cooperative cancellation (the composer cancels an
                // in-flight fetch when images are added / the card is cleared).
                throw cancellation
            } catch (e: Exception) {
                Timber.tag(TAG).d(e, "fetch(%s) failed → no card", url)
                null
            }

        override suspend fun downloadThumb(imageUrl: String): EncodedImage? =
            try {
                val response =
                    httpClient.get(imageUrl) {
                        timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
                    }
                val declaredLength = response.contentLength()
                when {
                    !response.status.isSuccess() -> null
                    // OOM/bandwidth guard: reject up-front via Content-Length when present.
                    declaredLength != null && declaredLength > MAX_THUMB_BYTES -> null
                    else -> {
                        // Bounded read — never buffer more than the cap (+1 to detect
                        // overflow), so a missing/chunked Content-Length can't OOM.
                        val bytes =
                            response.bodyAsChannel().readRemaining(MAX_THUMB_BYTES + 1L).readByteArray()
                        if (bytes.size > MAX_THUMB_BYTES) {
                            null
                        } else {
                            val mimeType =
                                response
                                    .contentType()
                                    ?.let { "${it.contentType}/${it.contentSubtype}" }
                                    ?: DEFAULT_THUMB_MIME
                            EncodedImage(bytes = bytes, mimeType = mimeType)
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Timber.tag(TAG).d(e, "downloadThumb(%s) failed → no thumb", imageUrl)
                null
            }

        private companion object {
            const val TAG = "CardyB"
            const val CARDYB_EXTRACT = "https://cardyb.bsky.app/v1/extract"
            const val REQUEST_TIMEOUT_MS = 10_000L

            // Bluesky's per-blob byte cap. Oversize thumbnails are skipped rather
            // than posted (the card still posts with title/description/uri).
            const val MAX_THUMB_BYTES = 1_000_000

            const val DEFAULT_THUMB_MIME = "image/jpeg"

            val JSON = Json { ignoreUnknownKeys = true }
        }
    }

/**
 * CardyB `/v1/extract` response. All metadata fields are nullable — CardyB returns
 * `null`/absent for pages lacking them. `error` is empty on success, non-empty
 * with a message on failure. `url` is the redirect-resolved final destination.
 */
@Serializable
internal data class CardyBResponse(
    val error: String = "",
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val url: String? = null,
    @SerialName("likely_type") val likelyType: String? = null,
)
