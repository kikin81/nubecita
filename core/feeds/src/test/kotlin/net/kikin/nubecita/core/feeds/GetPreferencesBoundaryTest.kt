package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponse
import io.github.kikin81.atproto.app.bsky.actor.SavedFeed
import io.github.kikin81.atproto.runtime.AuthProvider
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Exercises the REAL `app.bsky.actor.getPreferences` deserialization boundary.
 *
 * The canonical response body is `{"preferences":[ … ]}` — `preferences` is an
 * ARRAY of `$type`-tagged objects. The generated SDK `GetPreferencesResponse`
 * mis-models that field as a `JsonObject`, so decoding the real body through
 * the SDK type throws; [`sdk GetPreferencesResponse type cannot decode the real array body`]
 * pins that regression. [`DefaultFeedsDataSource decodes the real array body and extracts pinned items`]
 * then drives the production data source end-to-end (HTTP body →
 * `XrpcClient.handle` → raw `JsonObject` decode → `extractSavedFeedItems`) and
 * asserts the pinned generator + Following are extracted while the unpinned feed
 * is dropped.
 */
internal class GetPreferencesBoundaryTest {
    // Mirrors the SDK's own response Json (explicitNulls + ignoreUnknownKeys).
    private val sdkJson =
        Json {
            explicitNulls = true
            ignoreUnknownKeys = true
        }

    private val realBody =
        """
        {"preferences":[{"${'$'}type":"app.bsky.actor.defs#savedFeedsPrefV2","items":[
        {"id":"a","type":"feed","value":"at://did:plc:x/app.bsky.feed.generator/art","pinned":true},
        {"id":"b","type":"timeline","value":"following","pinned":true},
        {"id":"c","type":"feed","value":"at://did:plc:y/app.bsky.feed.generator/news","pinned":false}]}]}
        """.trimIndent()

    @Test
    fun `sdk GetPreferencesResponse type cannot decode the real array body`() {
        // The generated type's `preferences` field is `JsonObject`-typed; the
        // real body is an array, so the SDK's typed decode path throws. This is
        // exactly the failure the production code now bypasses.
        assertThrows(Throwable::class.java) {
            sdkJson.decodeFromString(GetPreferencesResponse.serializer(), realBody)
        }
    }

    @Test
    fun `DefaultFeedsDataSource decodes the real array body and extracts pinned items`() =
        runTest {
            val dataSource = DefaultFeedsDataSource(stubProviderReturning(realBody))

            val items = dataSource.getSavedFeedItems()!!

            // All three saved feeds survive the decode (filtering on `pinned`
            // happens in the repository, not the data source).
            assertEquals(listOf("a", "b", "c"), items.map(SavedFeed::id))
            val pinned = items.filter(SavedFeed::pinned)
            assertEquals(
                listOf("at://did:plc:x/app.bsky.feed.generator/art", "following"),
                pinned.map(SavedFeed::value),
            )
            // The unpinned `news` generator is present but not pinned.
            assertEquals(
                listOf("at://did:plc:y/app.bsky.feed.generator/news"),
                items.filterNot(SavedFeed::pinned).map(SavedFeed::value),
            )
        }

    /**
     * Builds a real [XrpcClient] over a Ktor [MockEngine] that returns [body]
     * as the `getPreferences` response, wrapped in a stub [XrpcClientProvider].
     * This drives the same `query → handle → decode` path production uses.
     */
    private fun stubProviderReturning(body: String): XrpcClientProvider {
        val engine =
            MockEngine { _ ->
                respond(
                    content = body,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val noAuth =
            object : AuthProvider {
                override suspend fun authHeaders(
                    method: String,
                    url: String,
                ): Map<String, String> = emptyMap()
            }
        val client = XrpcClient("https://example.invalid", HttpClient(engine), sdkJson, noAuth)
        return mockk<XrpcClientProvider>().also {
            coEvery { it.authenticated() } returns client
        }
    }
}
