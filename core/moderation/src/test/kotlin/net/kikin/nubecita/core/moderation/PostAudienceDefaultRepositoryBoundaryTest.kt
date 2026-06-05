package net.kikin.nubecita.core.moderation

import io.github.kikin81.atproto.runtime.AuthProvider
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.ReplyAudience
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Drives [DefaultPostAudienceDefaultRepository] end-to-end over a Ktor
 * [MockEngine]: the real `getPreferences` GET → raw decode → parse path, and the
 * real merge → `putPreferences` POST path (asserting the captured body is the
 * canonical `{"preferences":[…]}` array). Also covers the optimistic publish and
 * the revert-on-failure of [DefaultPostAudienceDefaultRepository.setDefault].
 */
internal class PostAudienceDefaultRepositoryBoundaryTest {
    private val sdkJson = Json { ignoreUnknownKeys = true }
    private val combination =
        PostAudience(
            ReplyAudience.Combination(followers = true, following = false, mentioned = true),
            allowQuotes = false,
        )

    @Test
    fun `refresh reads the default from the live array`() =
        runTest {
            val body =
                """
                {"preferences":[
                  {"${'$'}type":"app.bsky.actor.defs#postInteractionSettingsPref",
                   "threadgateAllowRules":[{"${'$'}type":"app.bsky.feed.threadgate#followerRule"}],
                   "postgateEmbeddingRules":[{"${'$'}type":"app.bsky.feed.postgate#disableRule"}]}
                ]}
                """.trimIndent()
            val repo = DefaultPostAudienceDefaultRepository(provider(getBody = body))

            repo.refresh()

            assertEquals(
                PostAudience(ReplyAudience.Combination(true, false, false), allowQuotes = false),
                repo.default.value,
            )
        }

    @Test
    fun `default seeds to wide-open before any refresh`() =
        runTest {
            val repo = DefaultPostAudienceDefaultRepository(provider(getBody = """{"preferences":[]}"""))
            assertEquals(PostAudience.DEFAULT, repo.default.value)
        }

    @Test
    fun `setDefault writes a preferences array preserving foreign entries`() =
        runTest {
            val captured = mutableListOf<HttpRequestData>()
            val getBody =
                """
                {"preferences":[
                  {"${'$'}type":"app.bsky.actor.defs#savedFeedsPrefV2","items":[]},
                  {"${'$'}type":"app.bsky.actor.defs#postInteractionSettingsPref"}
                ]}
                """.trimIndent()
            val repo = DefaultPostAudienceDefaultRepository(provider(getBody = getBody, onRequest = captured::add))

            repo.setDefault(combination)

            // Published optimistically.
            assertEquals(combination, repo.default.value)

            val put = captured.single { it.url.encodedPath.endsWith("app.bsky.actor.putPreferences") }
            val prefs = sdkJson.decodeFromString(JsonObject.serializer(), (put.body as TextContent).text)["preferences"]
            assertTrue(prefs is JsonArray, "preferences must be an ARRAY on the wire")
            val entries = (prefs as JsonArray).map { it.jsonObject }
            // Foreign saved-feeds entry preserved, exactly one post-interaction entry.
            assertTrue(entries.any { it["\$type"]?.jsonPrimitive?.content == "app.bsky.actor.defs#savedFeedsPrefV2" })
            assertEquals(
                1,
                entries.count { it["\$type"]?.jsonPrimitive?.content == "app.bsky.actor.defs#postInteractionSettingsPref" },
            )
        }

    @Test
    fun `setDefault reverts the optimistic value when the put fails`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo =
                DefaultPostAudienceDefaultRepository(
                    provider(getBody = """{"preferences":[]}""", failPut = true),
                )

            val error = runCatching { repo.setDefault(combination) }.exceptionOrNull()

            assertNotNull(error, "a failed put must propagate")
            // Reverted to the pre-write value (the seeded DEFAULT).
            assertEquals(PostAudience.DEFAULT, repo.default.value)
        }

    @Test
    fun `setDefault publishes optimistically before the put resolves`() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val repo =
                DefaultPostAudienceDefaultRepository(
                    provider(getBody = """{"preferences":[]}""", putGate = gate),
                )

            val write = async { repo.setDefault(combination) }
            // The optimistic publish must already be visible while the PUT is gated.
            assertEquals(combination, repo.default.value)
            assertFalse(write.isCompleted)
            gate.complete(Unit)
            write.await()
            assertEquals(combination, repo.default.value)
        }

    /**
     * Stub [XrpcClientProvider] over a [MockEngine]. GET (getPreferences) returns
     * [getBody]; POST (putPreferences) returns 200 — unless [failPut] (throws 500)
     * or [putGate] (suspends until completed). Each request is forwarded to
     * [onRequest].
     */
    private fun provider(
        getBody: String,
        onRequest: (HttpRequestData) -> Unit = {},
        failPut: Boolean = false,
        putGate: CompletableDeferred<Unit>? = null,
    ): XrpcClientProvider {
        val engine =
            MockEngine { request ->
                onRequest(request)
                if (request.url.encodedPath.endsWith("getPreferences")) {
                    respond(content = getBody, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    putGate?.await()
                    if (failPut) {
                        respond(content = "boom", status = io.ktor.http.HttpStatusCode.InternalServerError)
                    } else {
                        respond(content = "", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            }
        return providerFromEngine(engine)
    }

    @Test
    fun `a failed write does not revert a newer successful write`() =
        runTest(UnconfinedTestDispatcher()) {
            // A's PUT is gated then fails; B's PUT (issued only after A releases the
            // write mutex) succeeds. A's revert must be SKIPPED because B's value
            // already superseded A's optimistic publish — exercises the guard.
            val gate = CompletableDeferred<Unit>()
            val putCount =
                java.util.concurrent.atomic
                    .AtomicInteger(0)
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("getPreferences")) {
                        respond(content = """{"preferences":[]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    } else if (putCount.incrementAndGet() == 1) {
                        gate.await()
                        respond(content = "boom", status = io.ktor.http.HttpStatusCode.InternalServerError)
                    } else {
                        respond(content = "", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val repo = DefaultPostAudienceDefaultRepository(providerFromEngine(engine))
            val b = PostAudience(ReplyAudience.Nobody, allowQuotes = true)

            val writeA = async { runCatching { repo.setDefault(combination) } }
            // A now holds the mutex, suspended at the gated PUT.
            val writeB = async { runCatching { repo.setDefault(b) } }
            // B published optimistically and is blocked on the mutex; its value wins.
            assertEquals(b, repo.default.value)

            gate.complete(Unit)
            val resultA = writeA.await()
            val resultB = writeB.await()

            assertTrue(resultA.isFailure, "A's gated write must fail")
            assertTrue(resultB.isSuccess, "B's write must succeed")
            // A's failure must NOT revert to DEFAULT — B's newer value stands.
            assertEquals(b, repo.default.value)
        }

    private fun providerFromEngine(engine: MockEngine): XrpcClientProvider {
        val noAuth =
            object : AuthProvider {
                override suspend fun authHeaders(
                    method: String,
                    url: String,
                ): Map<String, String> = emptyMap()
            }
        val client = XrpcClient("https://example.invalid", HttpClient(engine), sdkJson, noAuth)
        return mockk<XrpcClientProvider>().also { coEvery { it.authenticated() } returns client }
    }
}
