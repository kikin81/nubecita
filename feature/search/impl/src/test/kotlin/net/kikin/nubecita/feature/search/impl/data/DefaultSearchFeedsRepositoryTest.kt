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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Unit tests for [DefaultSearchFeedsRepository].
 *
 * Same MockEngine-driven shape as
 * [DefaultSearchActorsRepositoryTest] / [DefaultSearchPostsRepositoryTest]
 * — stand up a real [XrpcClient] backed by Ktor [MockEngine] and assert
 * on `engine.requestHistory.single().url` (wire format) and the parsed
 * `Result<SearchFeedsPage>` (mapping + cursor + error path).
 *
 * The repo bypasses any generated service class and calls
 * `XrpcClient.query()` directly with hand-written `@Serializable` request
 * + response, so wire-format and decode behavior are NOT covered by
 * upstream atproto-kotlin tests — these tests are the only thing
 * between `app.bsky.unspecced.getPopularFeedGenerators` schema drift
 * and a user-facing rendering bug.
 *
 * Coverage:
 *  - Happy path: well-formed GeneratorView + cursor map through; all
 *    `FeedGeneratorUi` fields populate correctly.
 *  - Blank `displayName` on creator → null in [FeedGeneratorUi] (boundary
 *    contract, same as DefaultSearchActorsRepository).
 *  - Empty result: empty page + null cursor (the `feeds: []` shape).
 *  - Missing required `feeds` field: decode throws MissingFieldException;
 *    repo surfaces `Result.failure`. Pins the no-default-on-feeds
 *    invariant.
 *  - IOException: surfaces as Result.failure(throwable).
 *  - CancellationException: re-thrown (structured-concurrency contract).
 *  - limit out of range (0 / 101): synchronous IllegalArgumentException.
 *  - Wire format: `query` + `limit` + `cursor` parameters on the GET.
 *  - Blank query: omitted from the GET (the repo translates "" → null
 *    so the AppView's "popular feeds" mode kicks in).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class DefaultSearchFeedsRepositoryTest {
    @Test
    fun searchFeeds_happyPath_mapsAllFieldsAndCursor() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "cursor": "c2",
                          "feeds": [
                            {
                              "${'$'}type": "app.bsky.feed.defs#generatorView",
                              "uri": "at://did:plc:fc/app.bsky.feed.generator/discover",
                              "cid": "bafyreitest1",
                              "did": "did:plc:fc",
                              "displayName": "Discover",
                              "description": "Trending posts",
                              "avatar": "https://cdn.example/d.jpg",
                              "likeCount": 1234,
                              "indexedAt": "2026-01-01T00:00:00Z",
                              "creator": {
                                "${'$'}type": "app.bsky.actor.defs#profileView",
                                "did": "did:plc:creator",
                                "handle": "skyfeed.bsky.social",
                                "displayName": "skyfeed"
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val page = repo.searchFeeds(query = "art", cursor = "c1", limit = 25).getOrThrow()

            assertEquals(1, page.items.size)
            assertEquals(
                FeedGeneratorUi(
                    uri = "at://did:plc:fc/app.bsky.feed.generator/discover",
                    displayName = "Discover",
                    creatorHandle = "skyfeed.bsky.social",
                    creatorDisplayName = "skyfeed",
                    description = "Trending posts",
                    avatarUrl = "https://cdn.example/d.jpg",
                    likeCount = 1234L,
                ),
                page.items[0],
            )
            assertEquals("c2", page.nextCursor)
        }

    @Test
    fun searchFeeds_blankCreatorDisplayName_normalizedToNull() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "feeds": [
                            {
                              "${'$'}type": "app.bsky.feed.defs#generatorView",
                              "uri": "at://did:plc:fc/app.bsky.feed.generator/x",
                              "cid": "bafyreitest1",
                              "did": "did:plc:fc",
                              "displayName": "Untitled",
                              "indexedAt": "2026-01-01T00:00:00Z",
                              "creator": {
                                "${'$'}type": "app.bsky.actor.defs#profileView",
                                "did": "did:plc:creator",
                                "handle": "anon.bsky.social",
                                "displayName": "   "
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val page = repo.searchFeeds(query = "x", cursor = null, limit = 25).getOrThrow()

            assertNull(page.items[0].creatorDisplayName)
            assertNull(page.items[0].description)
            assertNull(page.items[0].avatarUrl)
            // Missing upstream likeCount must default to 0 per FeedGeneratorUi's contract.
            assertEquals(0L, page.items[0].likeCount)
        }

    @Test
    fun searchFeeds_emptyResult_returnsEmptyPage() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson("""{"feeds": []}""")
                }

            val page = repo.searchFeeds(query = "noone", cursor = null, limit = 25).getOrThrow()

            assertTrue(page.items.isEmpty())
            assertNull(page.nextCursor)
        }

    @Test
    fun searchFeeds_missingRequiredFeedsField_surfacesFailure() =
        runTest {
            // Pins the no-default invariant: dropping `val feeds: List<…> = emptyList()`
            // makes a response that omits the required field fail-fast at decode time
            // rather than silently render as an empty "No feeds" state. The decode
            // throws MissingFieldException which the repo catches and wraps as
            // Result.failure — `result.isFailure` is the visible contract here.
            val (_, repo) =
                newRepo { _ ->
                    okJson("""{"cursor": "c2"}""")
                }

            val result = repo.searchFeeds(query = "x", cursor = null, limit = 25)

            assertTrue(result.isFailure, "missing required 'feeds' must surface as Result.failure")
            assertTrue(
                result.exceptionOrNull() is MissingFieldException,
                "expected MissingFieldException, got ${result.exceptionOrNull()?.javaClass?.name}",
            )
        }

    @Test
    fun searchFeeds_throws_surfacesFailure() =
        runTest {
            val (_, repo) = newRepo { _ -> throw IOException("network down") }

            val result = repo.searchFeeds(query = "x", cursor = null, limit = 25)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun searchFeeds_cancellation_propagates() {
        // Same shape as DefaultSearchActorsRepositoryTest's cancellation test —
        // a plain runBlocking outside of runTest because nested runTest is
        // forbidden by kotlinx-coroutines-test.
        val (_, repo) =
            newRepo { _ ->
                throw CancellationException("scope cancelled")
            }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                repo.searchFeeds(query = "x", cursor = null, limit = 25)
            }
        }
    }

    @Test
    fun searchFeeds_limitOutOfRange_throwsIllegalArgument() {
        val (_, repo) =
            newRepo { _ -> okJson("""{"feeds": []}""") }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.searchFeeds(query = "x", cursor = null, limit = 0) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.searchFeeds(query = "x", cursor = null, limit = 101) }
        }
    }

    @Test
    fun searchFeeds_passesQueryCursorAndLimitThrough() =
        runTest {
            val (engine, repo) = newRepo { _ -> okJson("""{"feeds": []}""") }

            repo.searchFeeds(query = "kotlin lang", cursor = "abc", limit = 13)

            val url =
                engine.requestHistory
                    .single()
                    .url
                    .toString()
            // Param keys live in the upstream lexicon's params block: `query`,
            // `cursor`, `limit`. Note the lexicon uses `query` (not `q` as in
            // searchPosts / searchActors) — verified against
            // lexicons/app/bsky/unspecced/getPopularFeedGenerators.json.
            assertTrue(url.contains("query=kotlin"), "expected query= present in $url")
            assertTrue(url.contains("cursor=abc"), "expected cursor=abc present in $url")
            assertTrue(url.contains("limit=13"), "expected limit=13 present in $url")
        }

    @Test
    fun searchFeeds_blankQuery_omittedFromUrl() =
        runTest {
            // A blank query collapses to `null` at the repo boundary so the
            // AppView's "popular feeds" mode kicks in (the lexicon param is
            // optional). Without this translation the AppView would receive
            // `query=` and may treat it as an empty-substring filter rather
            // than as "no query".
            val (engine, repo) = newRepo { _ -> okJson("""{"feeds": []}""") }

            repo.searchFeeds(query = "", cursor = null, limit = 25)

            val url =
                engine.requestHistory
                    .single()
                    .url
                    .toString()
            assertTrue(!url.contains("query="), "expected no query= param in $url")
        }

    // --- helpers (same shape as DefaultSearchActorsRepositoryTest) ---

    private fun newRepo(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultSearchFeedsRepository> {
        val engine = MockEngine(handler)
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val provider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient = xrpcClient
            }
        val repo =
            DefaultSearchFeedsRepository(
                xrpcClientProvider = provider,
                dispatcher = Dispatchers.Unconfined,
            )
        return engine to repo
    }

    private fun MockRequestHandleScope.okJson(body: String): HttpResponseData =
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )
}
