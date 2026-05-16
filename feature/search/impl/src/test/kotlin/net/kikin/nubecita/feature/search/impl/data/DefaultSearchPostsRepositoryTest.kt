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
 * Unit tests for [DefaultSearchPostsRepository].
 *
 * Strategy mirrors [net.kikin.nubecita.core.posting.internal.DefaultActorTypeaheadRepositoryTest]:
 * stand up a real [XrpcClient] backed by a Ktor [MockEngine] so the
 * SDK's full `FeedService.searchPosts(...)` codepath runs end-to-end
 * against deterministic responses. Tests assert on the
 * `engine.requestHistory.single().url` for wire-format guarantees and
 * on the parsed `Result<SearchPostsPage>` for mapping + cursor
 * guarantees.
 *
 * Coverage:
 *  - Happy path: posts list + cursor map through correctly.
 *  - Empty result: empty page + null cursor.
 *  - IOException: surfaces as Result.failure(throwable) without
 *    propagating.
 *  - CancellationException: re-thrown (NOT trapped in Result.failure)
 *    so structured concurrency works.
 *  - Wire format: `q` + `limit` + `cursor` parameters present on the
 *    outgoing GET.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSearchPostsRepositoryTest {
    @Test
    fun searchPosts_happyPath_mapsPostsAndCursor() =
        runTest {
            val (engine, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "cursor": "c2",
                          "hitsTotal": 42,
                          "posts": []
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchPosts(query = "kotlin", cursor = "c1", limit = 25)

            assertTrue(result.isSuccess)
            val page = result.getOrThrow()
            assertEquals(0, page.items.size)
            assertEquals("c2", page.nextCursor)
            assertEquals(1, engine.requestHistory.size)
        }

    @Test
    fun searchPosts_emptyResultNullCursor_returnsEmptyPage() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "posts": []
                        }
                        """.trimIndent(),
                    )
                }

            val page = repo.searchPosts(query = "kotlin", cursor = null, limit = 25).getOrThrow()

            assertTrue(page.items.isEmpty())
            assertNull(page.nextCursor)
        }

    @Test
    fun searchPosts_throws_surfacesFailure() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    throw IOException("network down")
                }

            val result = repo.searchPosts(query = "kotlin", cursor = null, limit = 25)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun searchPosts_cancellation_propagates() {
        // Don't wrap the outer test in `runTest`: kotlinx-coroutines-test
        // forbids nested `runTest` invocations (TestScopeImpl.enter), so
        // `assertThrows` needs a plain `runBlocking` driver to reach into
        // the suspending repo call. The contract under test — the repo's
        // explicit `catch (cancellation: CancellationException) { throw }`
        // before the Timber log — is dispatcher-agnostic.
        val (_, repo) =
            newRepo { _ ->
                throw CancellationException("scope cancelled")
            }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                repo.searchPosts(query = "kotlin", cursor = null, limit = 25)
            }
        }
    }

    @Test
    fun searchPosts_passesQueryCursorAndLimitThrough() =
        runTest {
            val (engine, repo) =
                newRepo { _ ->
                    okJson("""{"posts": []}""")
                }

            repo.searchPosts(query = "kotlin lang", cursor = "abc", limit = 13)

            val url =
                engine.requestHistory
                    .single()
                    .url
                    .toString()
            // Order of query params is not contractual, just presence:
            assertTrue(url.contains("q=kotlin")) // kotlin lang URL-encoded → kotlin+lang or kotlin%20lang
            assertTrue(url.contains("cursor=abc"))
            assertTrue(url.contains("limit=13"))
        }

    // --- helpers ---

    private fun newRepo(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultSearchPostsRepository> {
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
            DefaultSearchPostsRepository(
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
