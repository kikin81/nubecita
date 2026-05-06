package net.kikin.nubecita.core.posting.internal

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.posting.ActorTypeaheadUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Unit tests for [DefaultActorTypeaheadRepository].
 *
 * Strategy mirrors [DefaultPostingRepositoryTest]: stand up a real
 * [XrpcClient] backed by a Ktor [MockEngine] so the SDK's full
 * `ActorService.searchActorsTypeahead(...)` codepath runs end-to-end
 * against deterministic responses. The test asserts on the recorded
 * HTTP request URL (`engine.requestHistory.single().url`) for wire-
 * format guarantees and on the parsed `Result<List<ActorTypeaheadUi>>`
 * for mapping guarantees.
 *
 * Coverage:
 *  - Happy path: handle, displayName, avatar all present → ActorTypeaheadUi
 *    carries through unchanged.
 *  - Blank displayName upstream → null in ActorTypeaheadUi.
 *  - Missing displayName / avatar → null in ActorTypeaheadUi.
 *  - Empty actor list → Result.success(emptyList()) (NOT failure — the
 *    "no matches" outcome is distinct from the network-failure outcome
 *    per the repository's contract).
 *  - Wire format: `q=…` and `limit=8` query parameters present,
 *    `term=` absent.
 *  - IOException during the call → Result.failure(IOException) and the
 *    exception does NOT propagate to the caller.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultActorTypeaheadRepositoryTest {
    @Test
    fun searchTypeahead_singleActor_mapsAllFields() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                              "did": "did:plc:abc123",
                              "handle": "alice.bsky.social",
                              "displayName": "Alice",
                              "avatar": "https://cdn.example/avatar/alice.jpg"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchTypeahead("ali")

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    ActorTypeaheadUi(
                        did = "did:plc:abc123",
                        handle = "alice.bsky.social",
                        displayName = "Alice",
                        avatarUrl = "https://cdn.example/avatar/alice.jpg",
                    ),
                ),
                result.getOrNull(),
            )
        }

    @Test
    fun searchTypeahead_blankDisplayName_normalizesToNull() =
        runTest {
            // displayName = "" is on the wire (Bluesky does emit empty
            // strings for unset display names rather than omitting the
            // field). The boundary contract says null means "no display
            // name", so consumers don't have to re-check `.isBlank()`
            // to render the handle fallback.
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                              "did": "did:plc:bob",
                              "handle": "bob.bsky.social",
                              "displayName": ""
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchTypeahead("bob")

            assertTrue(result.isSuccess)
            val ui = result.getOrNull()!!.single()
            assertNull(ui.displayName)
            assertNull(ui.avatarUrl)
        }

    @Test
    fun searchTypeahead_missingDisplayNameAndAvatar_returnNull() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    okJson(
                        """
                        {
                          "actors": [
                            {
                              "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                              "did": "did:plc:carol",
                              "handle": "carol.bsky.social"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

            val result = repo.searchTypeahead("carol")

            assertTrue(result.isSuccess)
            val ui = result.getOrNull()!!.single()
            assertEquals("did:plc:carol", ui.did)
            assertEquals("carol.bsky.social", ui.handle)
            assertNull(ui.displayName)
            assertNull(ui.avatarUrl)
        }

    @Test
    fun searchTypeahead_emptyActors_returnsSuccessWithEmptyList() =
        runTest {
            // The repository contract distinguishes "the call worked,
            // no actors matched" (Result.success(emptyList())) from
            // "the call failed" (Result.failure). The composer's VM
            // turns the former into TypeaheadStatus.NoResults and the
            // latter into TypeaheadStatus.Idle (hidden). This test
            // pins the success-empty path.
            val (_, repo) =
                newRepo { _ ->
                    okJson("""{"actors": []}""")
                }

            val result = repo.searchTypeahead("zzznomatch")

            assertTrue(result.isSuccess)
            assertEquals(emptyList<ActorTypeaheadUi>(), result.getOrNull())
        }

    @Test
    fun searchTypeahead_wireFormat_usesQNotTerm_andLimit8() =
        runTest {
            val (engine, repo) =
                newRepo { _ ->
                    okJson("""{"actors": []}""")
                }

            repo.searchTypeahead("alice")

            val recorded = engine.requestHistory.single()
            val rawUrl = recorded.url.toString()
            assertTrue(
                recorded.url.encodedPath.endsWith("searchActorsTypeahead"),
                "Unexpected NSID path: $rawUrl",
            )
            assertEquals("alice", recorded.url.parameters["q"], "q parameter missing or wrong: $rawUrl")
            assertEquals("8", recorded.url.parameters["limit"], "limit parameter must be 8: $rawUrl")
            assertNull(recorded.url.parameters["term"], "deprecated term parameter must not be sent: $rawUrl")
        }

    @Test
    fun searchTypeahead_ioException_returnsFailureAndDoesNotPropagate() =
        runTest {
            val (_, repo) =
                newRepo { _ ->
                    throw IOException("simulated socket failure")
                }

            val result = repo.searchTypeahead("alice")

            assertTrue(result.isFailure, "IOException must surface as Result.failure, not propagate")
            // We don't pin the exact wrapping (Ktor may wrap in
            // ConnectException etc.) — the contract is "non-success
            // outcome surfaces as Result.failure". Pinning the cause
            // chain would couple this test to Ktor internals.
            assertTrue(result.exceptionOrNull() != null)
        }

    // ---------- harness ----------

    private fun MockRequestHandleScope.okJson(json: String): HttpResponseData =
        respond(
            ByteReadChannel(json),
            HttpStatusCode.OK,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun newRepo(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultActorTypeaheadRepository> {
        val engine = MockEngine(handler)
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val repo =
            DefaultActorTypeaheadRepository(
                xrpcClientProvider =
                    object : XrpcClientProvider {
                        override suspend fun authenticated(): XrpcClient = xrpcClient
                    },
            )
        return engine to repo
    }
}
