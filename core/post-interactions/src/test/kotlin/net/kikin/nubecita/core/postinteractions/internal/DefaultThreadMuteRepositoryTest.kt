package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.runtime.NoAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `app.bsky.graph.muteThread` / `unmuteThread` wiring (nubecita-iwjz.4): the
 * thread is identified by its ROOT post URI, sent as the procedure's `root`.
 */
class DefaultThreadMuteRepositoryTest {
    private val root = "at://did:plc:a/app.bsky.feed.post/root"

    @Test
    fun mute_sendsMuteThreadWithRootUri() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val bodies = mutableListOf<String>()
            val repo = newRepo(okEngine(requests, bodies))

            val result = repo.mute(root)

            assertTrue(result.isSuccess)
            assertTrue(
                requests
                    .single()
                    .url
                    .toString()
                    .contains("graph.muteThread"),
            )
            assertTrue(bodies.single().contains(root), "body should carry the root uri: ${bodies.single()}")
        }

    @Test
    fun unmute_sendsUnmuteThreadWithRootUri() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val bodies = mutableListOf<String>()
            val repo = newRepo(okEngine(requests, bodies))

            val result = repo.unmute(root)

            assertTrue(result.isSuccess)
            assertTrue(
                requests
                    .single()
                    .url
                    .toString()
                    .contains("graph.unmuteThread"),
            )
            assertTrue(bodies.single().contains(root))
        }

    @Test
    fun failure_isResultFailure() =
        runTest {
            val repo = newRepo(MockEngine { respondError(HttpStatusCode.BadRequest) })

            assertTrue(repo.mute(root).isFailure)
        }

    @Test
    fun cancellation_propagates() {
        val repo = newRepo(MockEngine { throw CancellationException("scope cancelled") })

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.unmute(root) }
        }
    }

    private fun okEngine(
        requests: MutableList<HttpRequestData>,
        bodies: MutableList<String>,
    ): MockEngine =
        MockEngine { request ->
            requests += request
            bodies += (request.body as? TextContent)?.text.orEmpty()
            respond(
                content = ByteReadChannel(ByteArray(0)),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }

    private fun newRepo(engine: MockEngine): DefaultThreadMuteRepository {
        val client = XrpcClient(baseUrl = "https://test.invalid", httpClient = HttpClient(engine), authProvider = NoAuth)
        val provider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient = client
            }
        return DefaultThreadMuteRepository(xrpcClientProvider = provider, dispatcher = Dispatchers.Unconfined)
    }
}
