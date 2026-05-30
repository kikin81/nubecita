package net.kikin.nubecita.core.actors.internal

import app.cash.turbine.test
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.database.dao.ActorDao
import net.kikin.nubecita.core.database.model.ActorEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
    // Limit guard (synchronous, no runTest needed)
    // -------------------------------------------------------------------------

    @Test
    fun searchTypeahead_limitOutOfRange_throwsIllegalArgument() {
        // `require(limit in 1..100)` throws synchronously before any suspend body
        // runs, so a plain runBlocking suffices — same shape as
        // searchActors_cancellation_propagates in the sibling test.
        val actorDao = mockk<ActorDao>(relaxed = true)
        val (_, repo) = newRepo(actorDao) { _ -> okJson("""{"actors": []}""") }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.searchTypeahead(query = "x", limit = 0) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.searchTypeahead(query = "x", limit = 101) }
        }
    }

    @Test
    fun searchActors_limitOutOfRange_throwsIllegalArgument() {
        val actorDao = mockk<ActorDao>(relaxed = true)
        val (_, repo) = newRepo(actorDao) { _ -> okJson("""{"actors": []}""") }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.searchActors(query = "x", cursor = null, limit = 0) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.searchActors(query = "x", cursor = null, limit = 101) }
        }
    }

    // -------------------------------------------------------------------------
    // CancellationException propagates (not swallowed into Result.failure)
    // -------------------------------------------------------------------------

    @Test
    fun searchTypeahead_cancellation_propagates() {
        // Don't wrap the outer test in `runTest`: kotlinx-coroutines-test
        // forbids nested `runTest` invocations (TestScopeImpl.enter), so
        // `assertThrows` needs a plain `runBlocking` driver to reach into
        // the suspending repo call. The contract under test — the repo's
        // explicit `catch (cancellation: CancellationException) { throw }`
        // before the Timber log — is dispatcher-agnostic.
        val actorDao = mockk<ActorDao>(relaxed = true)
        val (_, repo) =
            newRepo(actorDao) { _ ->
                throw CancellationException("scope cancelled")
            }

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.searchTypeahead(query = "x", limit = 8) }
        }
    }

    @Test
    fun searchActors_cancellation_propagates() {
        val actorDao = mockk<ActorDao>(relaxed = true)
        val (_, repo) =
            newRepo(actorDao) { _ ->
                throw CancellationException("scope cancelled")
            }

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.searchActors(query = "x", cursor = null, limit = 25) }
        }
    }

    // -------------------------------------------------------------------------
    // Wire-format: outgoing request carries correct query parameters
    // -------------------------------------------------------------------------

    @Test
    fun searchTypeahead_passesQueryAndLimitThrough() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (engine, repo) = newRepo(actorDao) { _ -> okJson("""{"actors": []}""") }

            repo.searchTypeahead(query = "kotlin lang", limit = 7)

            val url =
                engine.requestHistory
                    .single()
                    .url
                    .toString()
            // Order of query params is not contractual, just presence:
            assertTrue(url.contains("q=kotlin"), "url should contain q=kotlin but was: $url")
            assertTrue(url.contains("limit=7"), "url should contain limit=7 but was: $url")
            // Guard against regression to the deprecated `term` query parameter:
            assertTrue(
                url.contains("searchActorsTypeahead"),
                "url path should contain searchActorsTypeahead but was: $url",
            )
            assertFalse(
                url.contains("term="),
                "url must NOT contain deprecated term= param but was: $url",
            )
        }

    @Test
    fun searchActors_passesQueryCursorAndLimitThrough() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (engine, repo) = newRepo(actorDao) { _ -> okJson("""{"actors": []}""") }

            repo.searchActors(query = "kotlin lang", cursor = "abc", limit = 13)

            val url =
                engine.requestHistory
                    .single()
                    .url
                    .toString()
            // Order of query params is not contractual, just presence:
            assertTrue(url.contains("q=kotlin"), "url should contain q=kotlin but was: $url")
            assertTrue(url.contains("cursor=abc"), "url should contain cursor=abc but was: $url")
            assertTrue(url.contains("limit=13"), "url should contain limit=13 but was: $url")
        }

    // -------------------------------------------------------------------------
    // Missing optional fields: displayName and avatar absent entirely
    // -------------------------------------------------------------------------

    @Test
    fun searchTypeahead_missingDisplayNameAndAvatar_normalizedToNull() =
        runTest {
            // Distinct from the blank-string case: these fields are not present
            // in the JSON at all (omitted), so the SDK will default them to null.
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
                              "handle": "carol.bsky.social"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchTypeahead(query = "carol", limit = 8)

            assertTrue(result.isSuccess)
            val actor = result.getOrThrow().single()
            assertNull(actor.displayName, "displayName should be null when absent from JSON")
            assertNull(actor.avatarUrl, "avatarUrl should be null when absent from JSON")
        }

    // -------------------------------------------------------------------------
    // recentActors
    // -------------------------------------------------------------------------

    @Test
    fun recentActors_mapsEntitiesAndPassesSelfDid() =
        runTest {
            val actorDao = mockk<ActorDao>(relaxed = true)
            val entity =
                ActorEntity(
                    did = "did:a",
                    handle = "a.bsky.social",
                    displayName = "A",
                    avatarUrl = null,
                    lastSeenAt = Instant.fromEpochMilliseconds(1),
                )
            every { actorDao.recentActors("did:self", 20) } returns flowOf(listOf(entity))
            val (_, repo) = newRepo(actorDao) { _ -> okJson("""{"actors": []}""") }
            repo.recentActors(selfDid = "did:self").test {
                assertEquals(listOf("did:a"), awaitItem().map { it.did })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun recentActors_limitOutOfRange_throwsIllegalArgument() {
        // recentActors is non-suspend; the require guard fires on the call, before any Flow is returned.
        val actorDao = mockk<ActorDao>(relaxed = true)
        val (_, repo) = newRepo(actorDao) { _ -> okJson("""{"actors": []}""") }
        assertThrows(IllegalArgumentException::class.java) { repo.recentActors(selfDid = null, limit = 0) }
        assertThrows(IllegalArgumentException::class.java) { repo.recentActors(selfDid = null, limit = 101) }
    }

    // -------------------------------------------------------------------------
    // canMessage mapping (DM availability hint)
    // -------------------------------------------------------------------------

    @Test
    fun searchTypeahead_allowIncomingNone_canMessageFalse() =
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
                              "did": "did:plc:none",
                              "handle": "none.bsky.social",
                              "associated": { "chat": { "allowIncoming": "none" } }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val actor = repo.searchTypeahead(query = "none", limit = 8).getOrThrow().single()
            assertFalse(actor.canMessage)
        }

    @Test
    fun searchTypeahead_allowIncomingFollowing_withoutFollowedBy_canMessageFalse() =
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
                              "did": "did:plc:foll",
                              "handle": "foll.bsky.social",
                              "associated": { "chat": { "allowIncoming": "following" } }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val actor = repo.searchTypeahead(query = "foll", limit = 8).getOrThrow().single()
            assertFalse(actor.canMessage)
        }

    @Test
    fun searchActors_allowIncomingFollowing_withFollowedBy_canMessageTrue() =
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
                              "did": "did:plc:foll",
                              "handle": "foll.bsky.social",
                              "associated": { "chat": { "allowIncoming": "following" } },
                              "viewer": { "followedBy": "at://did:plc:foll/app.bsky.graph.follow/x" }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val actor =
                repo
                    .searchActors(query = "foll", cursor = null, limit = 25)
                    .getOrThrow()
                    .items
                    .single()
            assertTrue(actor.canMessage)
        }

    @Test
    fun searchTypeahead_absentAssociated_withoutFollowedBy_canMessageFalse() =
        runTest {
            // No `associated.chat` block ⇒ Bluesky's "following" default; with no
            // `viewer.followedBy` the actor is NOT messageable (not fail-open).
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (_, repo) =
                newRepo(actorDao) { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                              "did": "did:plc:plain",
                              "handle": "plain.bsky.social"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val actor = repo.searchTypeahead(query = "plain", limit = 8).getOrThrow().single()
            assertFalse(actor.canMessage)
        }

    @Test
    fun searchTypeahead_absentAssociated_withFollowedBy_canMessageTrue() =
        runTest {
            // No `associated.chat` block but the actor follows the viewer ⇒ messageable
            // under the "following" default.
            val actorDao = mockk<ActorDao>(relaxed = true)
            val (_, repo) =
                newRepo(actorDao) { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                              "did": "did:plc:plain",
                              "handle": "plain.bsky.social",
                              "viewer": { "followedBy": "at://did:plc:plain/app.bsky.graph.follow/x" }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val actor = repo.searchTypeahead(query = "plain", limit = 8).getOrThrow().single()
            assertTrue(actor.canMessage)
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
