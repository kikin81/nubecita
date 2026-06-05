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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Drives [DefaultModerationPreferencesRepository] end-to-end over a Ktor
 * [MockEngine]: the real `getPreferences` GET → raw decode → parse path, and
 * the real merge → `putPreferences` POST path (asserting the captured request
 * body is the canonical `{"preferences":[…]}` array, never the SDK's mis-modeled
 * object).
 */
internal class ModerationPreferencesBoundaryTest {
    private val sdkJson =
        Json {
            explicitNulls = true
            ignoreUnknownKeys = true
        }

    @Test
    fun `refresh reads adult gate and visibilities from the live array`() =
        runTest {
            val body =
                """
                {"preferences":[
                  {"${'$'}type":"app.bsky.actor.defs#adultContentPref","enabled":true},
                  {"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"porn","visibility":"warn"}
                ]}
                """.trimIndent()
            val repo = DefaultModerationPreferencesRepository(provider(getBody = body))

            repo.refresh()

            assertTrue(repo.prefs.value.adultContentEnabled)
            assertEquals(LabelVisibility.WARN, repo.prefs.value.visibilityFor(ContentLabel.PORN))
        }

    @Test
    fun `prefs default to adult-off before any refresh`() =
        runTest {
            val repo = DefaultModerationPreferencesRepository(provider(getBody = """{"preferences":[]}"""))
            // Fail-safe: nothing fetched yet, adult content stays hidden.
            assertEquals(ModerationPrefs.DEFAULT, repo.prefs.value)
        }

    @Test
    fun `setAdultContentEnabled writes the whole array back as a preferences array`() =
        runTest {
            val captured = mutableListOf<HttpRequestData>()
            val getBody =
                """
                {"preferences":[
                  {"${'$'}type":"app.bsky.actor.defs#savedFeedsPrefV2","items":[]},
                  {"${'$'}type":"app.bsky.actor.defs#adultContentPref","enabled":false}
                ]}
                """.trimIndent()
            val repo = DefaultModerationPreferencesRepository(provider(getBody = getBody, onRequest = captured::add))

            repo.setAdultContentEnabled(true)

            // Published optimistically.
            assertTrue(repo.prefs.value.adultContentEnabled)

            val put = captured.single { it.url.encodedPath.endsWith("app.bsky.actor.putPreferences") }
            val sent = (put.body as TextContent).text
            val prefsArray = sdkJson.decodeFromString(JsonObject.serializer(), sent)["preferences"]
            assertNotNull(prefsArray)
            assertTrue(prefsArray is JsonArray, "preferences must be an ARRAY on the wire")

            val entries = (prefsArray as JsonArray).map { it.jsonObject }
            // Foreign saved-feeds entry preserved.
            assertTrue(entries.any { it["\$type"]?.jsonPrimitive?.content == "app.bsky.actor.defs#savedFeedsPrefV2" })
            // Adult gate now enabled.
            val adult = entries.single { it["\$type"]?.jsonPrimitive?.content == "app.bsky.actor.defs#adultContentPref" }
            assertEquals("true", adult["enabled"]!!.jsonPrimitive.content)
            // All four content-label prefs present.
            val labels =
                entries
                    .filter { it["\$type"]?.jsonPrimitive?.content == "app.bsky.actor.defs#contentLabelPref" }
                    .map { it["label"]!!.jsonPrimitive.content }
                    .toSet()
            assertEquals(setOf("porn", "sexual", "graphic-media", "nudity"), labels)
        }

    // --- optimistic update (twmt.6) ---

    @Test
    fun `setVisibility publishes optimistically before the network write resolves`() =
        runTest {
            // graphic-media defaults to WARN; we set HIDE. The PUT is gated so it
            // can't complete — yet the cache must already reflect HIDE.
            val putGate = CompletableDeferred<Unit>()
            val repo =
                DefaultModerationPreferencesRepository(
                    gatedPutProvider(getBody = """{"preferences":[]}""", putGate = putGate),
                )
            val job = launch { repo.setVisibility(ContentLabel.GRAPHIC_MEDIA, LabelVisibility.HIDE) }
            testScheduler.runCurrent()

            assertEquals(
                LabelVisibility.HIDE,
                repo.prefs.value.visibilityFor(ContentLabel.GRAPHIC_MEDIA),
                "the cache must update before the putPreferences round-trip completes",
            )
            putGate.complete(Unit)
            job.join()
        }

    @Test
    fun `a failed write reverts the optimistic cache update`() =
        runTest {
            val putGate = CompletableDeferred<Unit>()
            val repo =
                DefaultModerationPreferencesRepository(
                    gatedPutProvider(getBody = """{"preferences":[]}""", putGate = putGate, failAfterGate = true),
                )
            val before = repo.prefs.value
            val job = launch { runCatching { repo.setVisibility(ContentLabel.GRAPHIC_MEDIA, LabelVisibility.HIDE) } }
            testScheduler.runCurrent()
            // Optimistically applied while the write is in flight…
            assertEquals(LabelVisibility.HIDE, repo.prefs.value.visibilityFor(ContentLabel.GRAPHIC_MEDIA))

            putGate.complete(Unit) // …then the gated write fails →
            job.join()

            assertEquals(before, repo.prefs.value, "a failed write must roll the cache back to the prior value")
        }

    /**
     * Builds a stub [XrpcClientProvider] over a Ktor [MockEngine]. GET requests
     * (getPreferences) return [getBody]; POST requests (putPreferences) return an
     * empty 200. Each request is forwarded to [onRequest] for assertions.
     */
    private fun provider(
        getBody: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ): XrpcClientProvider {
        val engine =
            MockEngine { request ->
                onRequest(request)
                if (request.url.encodedPath.endsWith("getPreferences")) {
                    respond(
                        content = getBody,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond(content = "", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
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

    /**
     * Like [provider] but the PUT (putPreferences) suspends on [putGate] before
     * responding — so a test can observe the optimistic cache update while the
     * write is in flight. When [failAfterGate] is true the gated PUT throws once
     * released, to exercise the revert path.
     */
    private fun gatedPutProvider(
        getBody: String,
        putGate: CompletableDeferred<Unit>,
        failAfterGate: Boolean = false,
    ): XrpcClientProvider {
        val engine =
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("getPreferences")) {
                    respond(content = getBody, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    putGate.await()
                    if (failAfterGate) error("putPreferences failed")
                    respond(content = "", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
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
