package net.kikin.nubecita.core.actors.internal

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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.database.dao.ActorDao
import net.kikin.nubecita.core.database.model.ActorEntity
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.IOException

/**
 * Unit tests for [DefaultActorRepository].
 *
 * Stand up a real [XrpcClient] backed by a Ktor [MockEngine] so the SDK's
 * full `ActorService.searchActors` / `searchActorsTypeahead` codepaths run
 * end-to-end against deterministic responses. [ActorDao] is a MockK relaxed
 * mock so write-through upserts can be verified without a real Room database.
 *
 * Coverage:
 *  - searchTypeahead success → maps 2 actors (assert dids) + write-through upsert called.
 *  - searchActors success → correct ActorSearchPage with nextCursor + write-through upsert called.
 *  - Empty results → Result.success with empty list, upsert never called (early-return guard).
 *  - Network failure → Result.isFailure == true.
 *  - Blank displayName → normalized to null.
 *  - Cache write failure (upsert throws) → search still returns Result.success (best-effort).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class DefaultActorRepositoryTest {
    // -------------------------------------------------------------------------
    // searchTypeahead
    // -------------------------------------------------------------------------

    @Test
    fun searchTypeahead_success_mapsTwoActorsAndWritesThrough() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (_, repo) =
                newRepo(actorDao) { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                              "did": "did:plc:aaa",
                              "handle": "alice.bsky.social",
                              "displayName": "Alice",
                              "avatar": "https://cdn.example/alice.jpg"
                            },
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                              "did": "did:plc:bbb",
                              "handle": "bob.bsky.social",
                              "displayName": "Bob"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchTypeahead(query = "ali", limit = 8)

            assertTrue(result.isSuccess)
            val actors = result.getOrThrow()
            assertEquals(2, actors.size)
            assertEquals("did:plc:aaa", actors[0].did)
            assertEquals("did:plc:bbb", actors[1].did)
            coVerify { actorDao.upsert(match { it.size == 2 }) }
        }

    // -------------------------------------------------------------------------
    // searchActors
    // -------------------------------------------------------------------------

    @Test
    fun searchActors_success_returnsPageWithCursorAndWritesThrough() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (_, repo) =
                newRepo(actorDao) { _ ->
                    okJson(
                        """
                        {
                          "cursor": "next-page-token",
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileView",
                              "did": "did:plc:aaa",
                              "handle": "alice.bsky.social",
                              "displayName": "Alice",
                              "avatar": "https://cdn.example/alice.jpg"
                            },
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileView",
                              "did": "did:plc:bbb",
                              "handle": "bob.bsky.social",
                              "displayName": "Bob"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchActors(query = "ali", cursor = null, limit = 25)

            assertTrue(result.isSuccess)
            val page = result.getOrThrow()
            assertEquals(2, page.items.size)
            assertEquals("did:plc:aaa", page.items[0].did)
            assertEquals("did:plc:bbb", page.items[1].did)
            assertEquals("next-page-token", page.nextCursor)
            coVerify { actorDao.upsert(match { it.size == 2 }) }
        }

    // -------------------------------------------------------------------------
    // Empty results
    // -------------------------------------------------------------------------

    @Test
    fun searchTypeahead_emptyResult_returnsSuccessAndSkipsUpsert() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (_, repo) =
                newRepo(actorDao) { _ ->
                    okJson("""{"actors": []}""")
                }

            val result = repo.searchTypeahead(query = "zzz", limit = 8)

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
            coVerify(exactly = 0) { actorDao.upsert(any()) }
        }

    @Test
    fun searchActors_emptyResult_returnsSuccessAndSkipsUpsert() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (_, repo) =
                newRepo(actorDao) { _ ->
                    okJson("""{"actors": []}""")
                }

            val result = repo.searchActors(query = "zzz", cursor = null, limit = 25)

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().items.isEmpty())
            coVerify(exactly = 0) { actorDao.upsert(any()) }
        }

    // -------------------------------------------------------------------------
    // Network failure
    // -------------------------------------------------------------------------

    @Test
    fun searchTypeahead_networkFailure_returnsFailure() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (_, repo) =
                newRepo(actorDao) { _ ->
                    throw IOException("network down")
                }

            val result = repo.searchTypeahead(query = "x", limit = 8)

            assertTrue(result.isFailure)
        }

    @Test
    fun searchActors_networkFailure_returnsFailure() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (_, repo) =
                newRepo(actorDao) { _ ->
                    throw IOException("network down")
                }

            val result = repo.searchActors(query = "x", cursor = null, limit = 25)

            assertTrue(result.isFailure)
        }

    // -------------------------------------------------------------------------
    // Blank displayName normalization
    // -------------------------------------------------------------------------

    @Test
    fun searchTypeahead_blankDisplayName_normalizedToNull() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (_, repo) =
                newRepo(actorDao) { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                              "did": "did:plc:xyz",
                              "handle": "carol.bsky.social",
                              "displayName": "   "
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchTypeahead(query = "carol", limit = 8)

            assertTrue(result.isSuccess)
            assertNull(result.getOrThrow().single().displayName)
        }

    @Test
    fun searchActors_blankDisplayName_normalizedToNull() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (_, repo) =
                newRepo(actorDao) { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileView",
                              "did": "did:plc:xyz",
                              "handle": "carol.bsky.social",
                              "displayName": ""
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchActors(query = "carol", cursor = null, limit = 25)

            assertTrue(result.isSuccess)
            assertNull(
                result
                    .getOrThrow()
                    .items
                    .single()
                    .displayName,
            )
        }

    // -------------------------------------------------------------------------
    // Cache write failure is best-effort (does not fail the search)
    // -------------------------------------------------------------------------

    @Test
    fun searchTypeahead_cacheWriteFailure_searchStillSucceeds() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            coEvery { actorDao.upsert(any<List<ActorEntity>>()) } throws RuntimeException("disk full")

            val (_, repo) =
                newRepo(actorDao) { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                              "did": "did:plc:aaa",
                              "handle": "alice.bsky.social"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchTypeahead(query = "alice", limit = 8)

            assertTrue(result.isSuccess, "cache write failure must not fail the search")
            assertEquals(1, result.getOrThrow().size)
        }

    @Test
    fun searchActors_cacheWriteFailure_searchStillSucceeds() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            coEvery { actorDao.upsert(any<List<ActorEntity>>()) } throws RuntimeException("disk full")

            val (_, repo) =
                newRepo(actorDao) { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileView",
                              "did": "did:plc:aaa",
                              "handle": "alice.bsky.social"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchActors(query = "alice", cursor = null, limit = 25)

            assertTrue(result.isSuccess, "cache write failure must not fail the search")
            assertEquals(1, result.getOrThrow().items.size)
        }

    // -------------------------------------------------------------------------
    // Harness helpers
    // -------------------------------------------------------------------------

    private fun newRepo(
        actorDao: ActorDao,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultActorRepository> {
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
            DefaultActorRepository(
                xrpcClientProvider = provider,
                actorDao = actorDao,
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
