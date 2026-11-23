package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.runtime.NoAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * MockEngine-driven tests for [DefaultSearchStarterPacksRepository]. The payload shapes are
 * trimmed from a real `app.bsky.graph.searchStarterPacksV2` response: the pack name is in the
 * record, the member count on the list, the avatars in the member sample.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSearchStarterPacksRepositoryTest {
    private val listUri = "at://did:plc:c/app.bsky.graph.list/l1"

    private fun member(
        n: Int,
        avatar: Boolean = true,
    ) = """{"uri":"at://did:plc:c/app.bsky.graph.listitem/$n","subject":{"did":"did:plc:m$n","handle":"m$n.test"${if (avatar) ""","avatar":"https://cdn.example/m$n.jpg"""" else ""}}}"""

    private fun pack(
        slug: String,
        name: String?,
        memberCount: Int?,
        members: Int,
    ) = """
        {"uri":"at://did:plc:c/app.bsky.graph.starterpack/$slug",
         "cid":"bafyreie6lwqlqayknghcimbpc5hec7npn5otdlgds7ugauyiwjktc423ne",
         "record":{"${'$'}type":"app.bsky.graph.starterpack"${name?.let { ""","name":"$it"""" } ?: ""},"list":"$listUri"},
         "creator":{"did":"did:plc:c","handle":"creator.bsky.social","displayName":""},
         "joinedAllTimeCount":3,"joinedWeekCount":1,"labels":[],"indexedAt":"2025-02-26T06:24:41.554Z",
         "feeds":[],
         "list":{"uri":"$listUri","cid":"bafyreidpaxaukmaxnx5vb5q5kd6nllfyouxhw4jquy7jjk4msxrvfm5fgm","name":"l","purpose":"app.bsky.graph.defs#referencelist",${memberCount?.let { """"listItemCount":$it,""" } ?: ""}"indexedAt":"2025-01-23T16:37:32.457Z"},
         "listItemsSample":[${(1..members).joinToString(",") { member(it) }}]}
        """.trimIndent()

    @Test
    fun `searchStarterPacks maps name, creator, member count, avatars and the cursor`() =
        runTest {
            val (_, repo) =
                newRepo {
                    okJson("""{"cursor":"c2","hitsTotal":40,"starterPacks":[${pack("a", "Android", 19, 6)},${pack("b", "Kotlin", null, 0)}]}""")
                }

            val page = repo.searchStarterPacks(query = "android", cursor = "c1", limit = 25).getOrThrow()

            assertEquals("c2", page.nextCursor)
            val first = page.items[0]
            assertEquals("at://did:plc:c/app.bsky.graph.starterpack/a", first.uri)
            assertEquals("Android", first.name)
            assertEquals("creator.bsky.social", first.creatorHandle)
            assertEquals(19, first.memberCount)
            assertEquals((1..4).map { "https://cdn.example/m$it.jpg" }, first.avatarUrls)
            val second = page.items[1]
            assertNull(second.memberCount)
            assertTrue(second.avatarUrls.isEmpty())
        }

    @Test
    fun `a pack without a name is dropped from the page`() =
        runTest {
            val (_, repo) = newRepo { okJson("""{"starterPacks":[${pack("a", null, 2, 0).replace("\"name\":\"l\"", "\"name\":\" \"")},${pack("b", "Kotlin", 2, 0)}]}""") }

            val page = repo.searchStarterPacks("x", cursor = null).getOrThrow()

            assertEquals(listOf("Kotlin"), page.items.map { it.name })
            assertNull(page.nextCursor)
        }

    @Test
    fun `an empty result is an empty page`() =
        runTest {
            val (_, repo) = newRepo { okJson("""{"starterPacks":[]}""") }

            val page = repo.searchStarterPacks("x", cursor = null).getOrThrow()

            assertTrue(page.items.isEmpty())
            assertNull(page.nextCursor)
        }

    @Test
    fun `a response without the required starterPacks field is a failure`() =
        runTest {
            val (_, repo) = newRepo { okJson("""{"cursor":"c2"}""") }

            assertTrue(repo.searchStarterPacks("x", cursor = null).isFailure)
        }

    @Test
    fun `an IOException is a failure and cancellation is rethrown`() =
        runTest {
            val (_, down) = newRepo { throw IOException("down") }
            assertTrue(down.searchStarterPacks("x", cursor = null).exceptionOrNull() is IOException)

            val (_, cancelled) = newRepo { throw CancellationException("cancelled") }
            assertThrows(CancellationException::class.java) { runBlocking { cancelled.searchStarterPacks("x", cursor = null) } }
        }

    @Test
    fun `a limit outside 1 to 100 is rejected`() {
        val (_, repo) = newRepo { okJson("""{"starterPacks":[]}""") }

        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.searchStarterPacks("x", null, limit = 0) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.searchStarterPacks("x", null, limit = 101) } }
    }

    @Test
    fun `the request carries q, limit and cursor`() =
        runTest {
            val (engine, repo) = newRepo { okJson("""{"starterPacks":[]}""") }

            repo.searchStarterPacks(query = "android dev", cursor = "c1", limit = 25)

            val url = engine.requestHistory.single().url
            assertTrue(url.encodedPath.endsWith("/xrpc/app.bsky.graph.searchStarterPacksV2"), url.toString())
            assertEquals("android dev", url.parameters["q"])
            assertEquals("25", url.parameters["limit"])
            assertEquals("c1", url.parameters["cursor"])
        }

    private fun newRepo(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<MockEngine, DefaultSearchStarterPacksRepository> {
        val engine = MockEngine(handler)
        val client = XrpcClient(baseUrl = "https://test.invalid", httpClient = HttpClient(engine), authProvider = NoAuth)
        val provider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient = client
            }
        return engine to DefaultSearchStarterPacksRepository(provider, Dispatchers.Unconfined)
    }

    private fun MockRequestHandleScope.okJson(body: String): HttpResponseData =
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )
}
