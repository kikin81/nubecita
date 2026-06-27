package net.kikin.nubecita.core.posting.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.posting.ExternalLinkMetadataRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class CardyBExternalLinkMetadataRepositoryTest {
    private fun repo(handler: MockRequestHandler): ExternalLinkMetadataRepository {
        val client = HttpClient(MockEngine(handler)) { install(HttpTimeout) }
        return CardyBExternalLinkMetadataRepository(client)
    }

    private fun json(
        error: String = "",
        title: String? = "Example Title",
        description: String? = "An example page.",
        image: String? = "https://cardyb.bsky.app/v1/image?url=x",
        url: String? = "https://example.com/article",
    ) = buildString {
        append("{")
        append("\"error\":\"$error\"")
        title?.let { append(",\"title\":\"$it\"") }
        description?.let { append(",\"description\":\"$it\"") }
        image?.let { append(",\"image\":\"$it\"") }
        url?.let { append(",\"url\":\"$it\"") }
        append("}")
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun fetch_success_mapsAllFields_andEncodesUrlParam() =
        runTest {
            var seenUrlParam: String? = null
            val repo =
                repo { request ->
                    seenUrlParam = request.url.parameters["url"]
                    respond(json(), HttpStatusCode.OK, jsonHeaders)
                }
            val preview = repo.fetch("https://example.com/article?a=1&b=2")
            // The pasted URL (with query params) round-trips as a single decoded param.
            assertEquals("https://example.com/article?a=1&b=2", seenUrlParam)
            requireNotNull(preview)
            assertEquals("Example Title", preview.title)
            assertEquals("An example page.", preview.description)
            assertEquals("https://cardyb.bsky.app/v1/image?url=x", preview.imageUrl)
        }

    @Test
    fun fetch_resolvedUrl_isUsedForUri_overTypedUrl() =
        runTest {
            val repo = repo { respond(json(url = "https://example.com/final-destination"), HttpStatusCode.OK, jsonHeaders) }
            val preview = repo.fetch("https://bit.ly/short")
            // CardyB's redirect-resolved `url`, not the typed shortener.
            assertEquals("https://example.com/final-destination", preview?.uri)
        }

    @Test
    fun fetch_missingUrl_fallsBackToTypedUrl() =
        runTest {
            val repo = repo { respond(json(url = null), HttpStatusCode.OK, jsonHeaders) }
            val preview = repo.fetch("https://example.com/typed")
            assertEquals("https://example.com/typed", preview?.uri)
        }

    @Test
    fun fetch_blankDescription_stillProducesCardWithEmptyDescription() =
        runTest {
            val repo = repo { respond(json(description = null), HttpStatusCode.OK, jsonHeaders) }
            val preview = repo.fetch("https://example.com")
            requireNotNull(preview)
            assertEquals("", preview.description)
        }

    @Test
    fun fetch_noTitle_returnsNull() =
        runTest {
            val repo = repo { respond(json(title = null), HttpStatusCode.OK, jsonHeaders) }
            assertNull(repo.fetch("https://example.com"))
        }

    @Test
    fun fetch_nonEmptyError_returnsNull() =
        runTest {
            val repo = repo { respond(json(error = "could not resolve"), HttpStatusCode.OK, jsonHeaders) }
            assertNull(repo.fetch("https://example.com"))
        }

    @Test
    fun fetch_httpError_returnsNull() =
        runTest {
            val repo = repo { respondError(HttpStatusCode.InternalServerError) }
            assertNull(repo.fetch("https://example.com"))
        }

    @Test
    fun fetch_networkException_returnsNull() =
        runTest {
            val repo = repo { throw IOException("offline") }
            assertNull(repo.fetch("https://example.com"))
        }

    @Test
    fun downloadThumb_success_returnsBytesAndMime() =
        runTest {
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            val repo =
                repo {
                    respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/webp"))
                }
            val encoded = repo.downloadThumb("https://cardyb.bsky.app/v1/image?url=x")
            requireNotNull(encoded)
            assertTrue(bytes.contentEquals(encoded.bytes))
            assertEquals("image/webp", encoded.mimeType)
        }

    @Test
    fun downloadThumb_oversizeContentLength_returnsNull() =
        runTest {
            val repo =
                repo {
                    respond(
                        byteArrayOf(0),
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.ContentType to listOf("image/jpeg"),
                            HttpHeaders.ContentLength to listOf("5000000"),
                        ),
                    )
                }
            assertNull(repo.downloadThumb("https://cardyb.bsky.app/v1/image?url=big"))
        }

    @Test
    fun downloadThumb_httpError_returnsNull() =
        runTest {
            val repo = repo { respondError(HttpStatusCode.NotFound) }
            assertNull(repo.downloadThumb("https://cardyb.bsky.app/v1/image?url=missing"))
        }

    @Test
    fun downloadThumb_oversizeWithoutContentLength_isBounded_returnsNull() =
        runTest {
            // A channel body (no Content-Length) larger than the cap — the bounded
            // read must reject it without buffering the whole thing.
            val big = ByteArray(1_000_100)
            val repo =
                repo {
                    respond(
                        ByteReadChannel(big),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "image/jpeg"),
                    )
                }
            assertNull(repo.downloadThumb("https://cardyb.bsky.app/v1/image?url=huge"))
        }
}
