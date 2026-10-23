package net.kikin.nubecita.feature.profile.impl.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class CardyBLinkFaviconFetcherTest {
    private fun fetcher(
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "image/png",
        body: ByteArray = byteArrayOf(1, 2, 3),
        contentLength: Long? = null,
        onRequest: (String) -> Unit = {},
    ): CardyBLinkFaviconFetcher {
        val engine =
            MockEngine { request ->
                onRequest(request.url.toString())
                val headers =
                    buildList {
                        add(HttpHeaders.ContentType to listOf(contentType))
                        contentLength?.let { add(HttpHeaders.ContentLength to listOf(it.toString())) }
                    }
                respond(body, status, headersOf(*headers.toTypedArray()))
            }
        return CardyBLinkFaviconFetcher(HttpClient(engine) { install(HttpTimeout) })
    }

    @Test
    fun `png within the cap is accepted and the request asks for a fresh icon`() =
        runTest {
            var requested = ""
            val bytes = fetcher(onRequest = { requested = it }).fetch("https://ko-fi.com/alice?x=1&y=2")
            assertArrayEquals(byteArrayOf(1, 2, 3), bytes)
            assertEquals(
                "https://cardyb.bsky.app/v1/favicon?url=https%3A%2F%2Fko-fi.com%2Falice%3Fx%3D1%26y%3D2&fresh=true",
                requested,
            )
        }

    @Test
    fun `non-png is no icon`() = runTest { assertNull(fetcher(contentType = "image/x-icon").fetch("https://a.example")) }

    @Test
    fun `non-2xx is no icon`() = runTest { assertNull(fetcher(status = HttpStatusCode.NotFound).fetch("https://a.example")) }

    @Test
    fun `declared oversize is no icon`() = runTest { assertNull(fetcher(contentLength = 200_000).fetch("https://a.example")) }

    @Test
    fun `undeclared oversize body is no icon`() = runTest { assertNull(fetcher(body = ByteArray(100_001)).fetch("https://a.example")) }

    @Test
    fun `body at exactly the cap is accepted`() = runTest { assertEquals(100_000, fetcher(body = ByteArray(100_000)).fetch("https://a.example")?.size) }

    @Test
    fun `empty body is no icon`() = runTest { assertNull(fetcher(body = ByteArray(0)).fetch("https://a.example")) }
}
