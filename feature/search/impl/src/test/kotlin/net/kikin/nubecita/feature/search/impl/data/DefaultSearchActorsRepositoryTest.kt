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
import net.kikin.nubecita.data.models.ActorUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Unit tests for [DefaultSearchActorsRepository].
 *
 * Strategy mirrors [DefaultSearchPostsRepositoryTest] (and its ancestor
 * [net.kikin.nubecita.core.posting.internal.DefaultActorTypeaheadRepositoryTest]):
 * stand up a real [XrpcClient] backed by a Ktor [MockEngine] so the
 * SDK's full `ActorService.searchActors(...)` codepath runs end-to-end
 * against deterministic responses. Tests assert on the
 * `engine.requestHistory.single().url` for wire-format guarantees and
 * on the parsed `Result<SearchActorsPage>` for mapping + cursor
 * guarantees.
 *
 * Coverage:
 *  - Happy path: well-formed actor + cursor map through correctly,
 *    including all four ActorUi fields.
 *  - Blank displayName upstream → null in [ActorUi] (boundary contract).
 *  - Empty result: empty page + null cursor.
 *  - IOException: surfaces as Result.failure(throwable) without
 *    propagating.
 *  - CancellationException: re-thrown (NOT trapped in Result.failure)
 *    so structured concurrency works.
 *  - limit out of range (0 / 101): synchronous IllegalArgumentException
 *    from the `require(limit in 1..100)` boundary check.
 *  - Wire format: `q` + `limit` + `cursor` parameters present on the
 *    outgoing GET.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSearchActorsRepositoryTest {
    @Test
    fun searchActors_happyPath_mapsAllFieldsAndCursor() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "cursor": "c2",
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileView",
                              "did": "did:plc:abc123",
                              "handle": "alice.bsky.social",
                              "displayName": "Alice",
                              "avatar": "https://cdn.example/alice.jpg"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val page = repo.searchActors(query = "ali", cursor = "c1", limit = 25).getOrThrow()

            assertEquals(1, page.items.size)
            assertEquals(
                ActorUi(
                    did = "did:plc:abc123",
                    handle = "alice.bsky.social",
                    displayName = "Alice",
                    avatarUrl = "https://cdn.example/alice.jpg",
                ),
                page.items[0],
            )
            assertEquals("c2", page.nextCursor)
        }

    @Test
    fun searchActors_blankDisplayName_normalizedToNull() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileView",
                              "did": "did:plc:xyz",
                              "handle": "bob.bsky.social",
                              "displayName": "   "
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val page = repo.searchActors(query = "bob", cursor = null, limit = 25).getOrThrow()

            assertNull(page.items[0].displayName)
            assertNull(page.items[0].avatarUrl)
        }

    @Test
    fun searchActors_emptyResult_returnsEmptyPage() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson("""{"actors": []}""")
                }

            val page = repo.searchActors(query = "noone", cursor = null, limit = 25).getOrThrow()

            assertTrue(page.items.isEmpty())
            assertNull(page.nextCursor)
        }

    @Test
    fun searchActors_throws_surfacesFailure() =
        runTest {
            val (_, repo) = newRepo { _ -> throw IOException("network down") }

            val result = repo.searchActors(query = "x", cursor = null, limit = 25)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun searchActors_cancellation_propagates() {
        // Don't wrap the outer test in `runTest`: kotlinx-coroutines-test
        // forbids nested `runTest` invocations (TestScopeImpl.enter), so
        // `assertThrows` needs a plain `runBlocking` driver to reach into
        // the suspending repo call. The contract under test — the repo's
        // explicit `catch (cancellation: CancellationException) { throw }`
        // before the Timber log — is dispatcher-agnostic. Same shape as
        // `searchPosts_cancellation_propagates`.
        val (_, repo) =
            newRepo { _ ->
                throw CancellationException("scope cancelled")
            }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                repo.searchActors(query = "x", cursor = null, limit = 25)
            }
        }
    }

    @Test
    fun searchActors_limitOutOfRange_throwsIllegalArgument() {
        // No `runTest` wrapper: the `require(limit in 1..100)` check throws
        // synchronously *before* the suspending body runs, so a plain
        // `runBlocking` is sufficient. Nesting `runBlocking` inside
        // `runTest` is the anti-pattern that kotlinx-coroutines-test's
        // docs warn against — same shape as
        // `searchActors_cancellation_propagates`.
        val (_, repo) =
            newRepo { _ -> okJson("""{"actors": []}""") }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.searchActors(query = "x", cursor = null, limit = 0) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.searchActors(query = "x", cursor = null, limit = 101) }
        }
    }

    @Test
    fun searchActors_passesQueryCursorAndLimitThrough() =
        runTest {
            val (engine, repo) = newRepo { _ -> okJson("""{"actors": []}""") }

            repo.searchActors(query = "kotlin lang", cursor = "abc", limit = 13)

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

    // --- helpers (same shape as DefaultSearchPostsRepositoryTest) ---

    private fun newRepo(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultSearchActorsRepository> {
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
            DefaultSearchActorsRepository(
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
