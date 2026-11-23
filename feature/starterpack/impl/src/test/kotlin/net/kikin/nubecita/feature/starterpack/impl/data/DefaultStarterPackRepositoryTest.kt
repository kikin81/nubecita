package net.kikin.nubecita.feature.starterpack.impl.data

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
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * MockEngine-driven tests for [DefaultStarterPackRepository], decoding the shapes of real
 * `app.bsky.graph.getStarterPack` and `app.bsky.graph.getList` responses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStarterPackRepositoryTest {
    private val packUri = "at://did:plc:m7h633sxyqlzwlhdubecv4uy/app.bsky.graph.starterpack/3lggcx32mgu23"
    private val listUri = "at://did:plc:m7h633sxyqlzwlhdubecv4uy/app.bsky.graph.list/3lggcx2qvwj2m"

    private val packJson =
        """
        {"starterPack": {
          "uri": "$packUri",
          "cid": "bafyreigbqzmnwqk6larzrtakh4r2d7ojjw46a6yzfrgmzpr3af5c2hvbhu",
          "record": {"${'$'}type": "app.bsky.graph.starterpack", "name": "Gaming : Studios", "description": "Everything gaming.", "list": "$listUri", "createdAt": "2025-01-23T16:37:32.408Z"},
          "creator": {"did": "did:plc:m7h633sxyqlzwlhdubecv4uy", "handle": "x3nu.bsky.social", "displayName": ""},
          "list": {"uri": "$listUri", "cid": "bafyreidpaxaukmaxnx5vb5q5kd6nllfyouxhw4jquy7jjk4msxrvfm5fgm", "name": "Gaming & Leaks", "purpose": "app.bsky.graph.defs#referencelist", "listItemCount": 72, "indexedAt": "2025-01-23T16:37:32.457Z"},
          "feeds": [{
            "uri": "at://did:plc:f/app.bsky.feed.generator/games",
            "cid": "bafyreibzgmcyqfewleuqd2jg5bk5fq6tiupxusexftkdo2rcrfhusmppcm",
            "did": "did:web:skyfeed.me",
            "displayName": "Games",
            "avatar": "https://cdn.example/games.jpg",
            "likeCount": 4,
            "indexedAt": "2024-08-25T12:29:34.236Z",
            "creator": {"did": "did:plc:feeder", "handle": "feeder.bsky.social"}
          }],
          "indexedAt": "2025-02-26T06:24:41.554Z"
        }}
        """.trimIndent()

    private val membersJson =
        """
        {"cursor": "next-page",
         "list": {"uri": "$listUri", "cid": "bafyreidpaxaukmaxnx5vb5q5kd6nllfyouxhw4jquy7jjk4msxrvfm5fgm", "name": "Gaming & Leaks", "purpose": "app.bsky.graph.defs#referencelist", "indexedAt": "2025-01-23T16:37:32.457Z", "creator": {"did": "did:plc:m7h", "handle": "x3nu.bsky.social"}},
         "items": [
           {"uri": "at://did:plc:m7h/app.bsky.graph.listitem/1",
            "subject": {"did": "did:plc:ign", "handle": "ign.com", "displayName": "IGN", "avatar": "https://cdn.example/ign.jpg", "description": "We're all about games.",
                        "viewer": {"following": "at://did:plc:me/app.bsky.graph.follow/9"}}},
           {"uri": "at://did:plc:m7h/app.bsky.graph.listitem/2",
            "subject": {"did": "did:plc:blank", "handle": "blank.bsky.social", "displayName": "  ", "description": ""}}
         ]}
        """.trimIndent()

    @Test
    fun `getStarterPack maps the header, the list and the feeds`() =
        runTest {
            val (_, repo) = newRepo { okJson(packJson) }

            val detail = repo.getStarterPack(packUri).getOrThrow()

            assertEquals(packUri, detail.uri)
            assertEquals("Gaming : Studios", detail.name)
            assertEquals("x3nu.bsky.social", detail.creatorHandle)
            assertEquals("Everything gaming.", detail.description)
            assertEquals(72, detail.memberCount)
            assertEquals(listUri, detail.listUri)
            assertEquals(
                listOf(PackFeedUi("at://did:plc:f/app.bsky.feed.generator/games", "Games", "feeder.bsky.social", "https://cdn.example/games.jpg")),
                detail.feeds.toList(),
            )
        }

    @Test
    fun `getStarterPack sends the pack uri`() =
        runTest {
            val (engine, repo) = newRepo { okJson(packJson) }

            repo.getStarterPack(packUri)

            val url = engine.requestHistory.single().url
            assertTrue(url.encodedPath.endsWith("/xrpc/app.bsky.graph.getStarterPack"), url.toString())
            assertEquals(packUri, url.parameters["starterPack"])
        }

    @Test
    fun `getStarterPack without a description or list has none`() =
        runTest {
            val (_, repo) =
                newRepo {
                    okJson(
                        """{"starterPack":{"uri":"$packUri","cid":"bafyreigbqzmnwqk6larzrtakh4r2d7ojjw46a6yzfrgmzpr3af5c2hvbhu","record":{"name":"Bare"},"creator":{"did":"did:plc:c","handle":"c.test"},"indexedAt":"2025-02-26T06:24:41.554Z"}}""",
                    )
                }

            val detail = repo.getStarterPack(packUri).getOrThrow()

            assertNull(detail.description)
            assertNull(detail.listUri)
            assertNull(detail.memberCount)
            assertTrue(detail.feeds.isEmpty())
        }

    @Test
    fun `getStarterPack fails when the response has no starterPack`() =
        runTest {
            val (_, repo) = newRepo { okJson("""{}""") }

            assertTrue(repo.getStarterPack(packUri).isFailure)
        }

    @Test
    fun `getStarterPack fails for a pack with no usable name`() =
        runTest {
            val (_, repo) =
                newRepo {
                    okJson(
                        """{"starterPack":{"uri":"$packUri","cid":"bafyreigbqzmnwqk6larzrtakh4r2d7ojjw46a6yzfrgmzpr3af5c2hvbhu","record":{},"creator":{"did":"did:plc:c","handle":"c.test"},"indexedAt":"2025-02-26T06:24:41.554Z"}}""",
                    )
                }

            assertTrue(repo.getStarterPack(packUri).isFailure)
        }

    @Test
    fun `a pack uri the server rejects is a failure`() =
        runTest {
            val (_, repo) =
                newRepo {
                    respond(
                        content = ByteReadChannel("""{"error":"InvalidRequest","message":"Invalid uri"}"""),
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }

            assertTrue(repo.getStarterPack("not an at uri").isFailure)
        }

    @Test
    fun `getStarterPack surfaces an IOException as a failure`() =
        runTest {
            val (_, repo) = newRepo { throw IOException("down") }

            assertTrue(repo.getStarterPack(packUri).exceptionOrNull() is IOException)
        }

    @Test
    fun `getStarterPack rethrows cancellation`() =
        runTest {
            val (_, repo) = newRepo { throw CancellationException("cancelled") }

            assertThrows(CancellationException::class.java) { kotlinx.coroutines.runBlocking { repo.getStarterPack(packUri) } }
        }

    @Test
    fun `getMembers maps people rows and the cursor`() =
        runTest {
            val (engine, repo) = newRepo { okJson(membersJson) }

            val page = repo.getMembers(listUri, cursor = "c1").getOrThrow()

            assertEquals("next-page", page.nextCursor)
            val ign = page.members[0]
            assertEquals("did:plc:ign", ign.did)
            assertEquals("ign.com", ign.handle)
            assertEquals("IGN", ign.displayName)
            assertEquals("https://cdn.example/ign.jpg", ign.avatarUrl)
            assertEquals("We're all about games.", ign.bio)
            assertTrue(ign.isFollowing)
            assertEquals("at://did:plc:me/app.bsky.graph.follow/9", ign.followUri)
            val blank = page.members[1]
            assertNull(blank.displayName)
            assertNull(blank.bio)
            assertFalse(blank.isFollowing)

            val url = engine.requestHistory.single().url
            assertTrue(url.encodedPath.endsWith("/xrpc/app.bsky.graph.getList"), url.toString())
            assertEquals(listUri, url.parameters["list"])
            assertEquals("c1", url.parameters["cursor"])
            assertEquals("50", url.parameters["limit"])
        }

    @Test
    fun `getMembers without a cursor has no next page`() =
        runTest {
            val (_, repo) =
                newRepo {
                    okJson(
                        """{"list":{"uri":"$listUri","cid":"bafyreidpaxaukmaxnx5vb5q5kd6nllfyouxhw4jquy7jjk4msxrvfm5fgm","name":"L","purpose":"app.bsky.graph.defs#referencelist","indexedAt":"2025-01-23T16:37:32.457Z","creator":{"did":"did:plc:m7h","handle":"x3nu.bsky.social"}},"items":[]}""",
                    )
                }

            val page = repo.getMembers(listUri, cursor = null).getOrThrow()

            assertTrue(page.members.isEmpty())
            assertNull(page.nextCursor)
        }

    @Test
    fun `getMembers fails when the response has no items`() =
        runTest {
            val (_, repo) =
                newRepo {
                    okJson(
                        """{"list":{"uri":"$listUri","cid":"bafyreidpaxaukmaxnx5vb5q5kd6nllfyouxhw4jquy7jjk4msxrvfm5fgm","name":"L","purpose":"app.bsky.graph.defs#referencelist","indexedAt":"2025-01-23T16:37:32.457Z","creator":{"did":"did:plc:m7h","handle":"x3nu.bsky.social"}}}""",
                    )
                }

            assertTrue(repo.getMembers(listUri, cursor = null).isFailure)
        }

    @Test
    fun `getMembers rejects a limit outside 1 to 100`() {
        val (_, repo) = newRepo { okJson(membersJson) }

        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.getMembers(listUri, null, limit = 0) } }
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.getMembers(listUri, null, limit = 101) } }
    }

    private fun newRepo(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<MockEngine, DefaultStarterPackRepository> {
        val engine = MockEngine(handler)
        val client = XrpcClient(baseUrl = "https://test.invalid", httpClient = HttpClient(engine), authProvider = NoAuth)
        val provider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient = client
            }
        return engine to DefaultStarterPackRepository(provider, Dispatchers.Unconfined)
    }

    private fun MockRequestHandleScope.okJson(body: String): HttpResponseData =
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )
}
