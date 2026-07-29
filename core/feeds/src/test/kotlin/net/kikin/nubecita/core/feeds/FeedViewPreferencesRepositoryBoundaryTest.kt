package net.kikin.nubecita.core.feeds

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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Drives [DefaultFeedViewPreferencesRepository] end-to-end over the REAL
 * `app.bsky.actor.getPreferences` decode path (HTTP body →
 * `ActorService.getPreferences` → [parseFeedViewPrefs] → published state),
 * mirroring [GetPreferencesBoundaryTest]'s data-source half.
 *
 * [parseFeedViewPrefs] is unit-tested against typed values in
 * [FeedViewPrefsParsingTest]; this file covers the parts that only exist in the
 * repository: that a real wire body reaches the parser at all, that the result
 * is published to [FeedViewPreferencesRepository.prefs], and that
 * [FeedViewPreferencesRepository.resetToDefault] clears it.
 *
 * The reset path matters more than a typical cache clear: the repository is an
 * app-scoped singleton, so without it account B's Following feed would be
 * filtered using account A's preferences until B's own refresh lands.
 */
internal class FeedViewPreferencesRepositoryBoundaryTest {
    // Mirrors the SDK's own response Json (explicitNulls + ignoreUnknownKeys).
    private val sdkJson =
        Json {
            explicitNulls = true
            ignoreUnknownKeys = true
        }

    private val allOffBody =
        """
        {"preferences":[{"${'$'}type":"app.bsky.actor.defs#feedViewPref","feed":"home",
        "hideReplies":false,"hideRepliesByUnfollowed":false,"hideReposts":false,"hideQuotePosts":false}]}
        """.trimIndent()

    private val allOnBody =
        """
        {"preferences":[{"${'$'}type":"app.bsky.actor.defs#feedViewPref","feed":"home",
        "hideReplies":true,"hideRepliesByUnfollowed":true,"hideReposts":true,"hideQuotePosts":true}]}
        """.trimIndent()

    @Test
    fun `prefs starts at the defaults before any refresh`() {
        val repo = DefaultFeedViewPreferencesRepository(stubProviderReturning(allOffBody))

        // Not refreshed yet — a reader observing this early must see the
        // official-client defaults, not an unfiltered feed.
        assertEquals(FeedViewPrefs.DEFAULT, repo.prefs.value)
        assertTrue(repo.prefs.value.hideRepliesByUnfollowed)
    }

    @Test
    fun `refresh decodes the real wire body and publishes it`() =
        runTest {
            val repo = DefaultFeedViewPreferencesRepository(stubProviderReturning(allOnBody))

            repo.refresh()

            val prefs = repo.prefs.value
            assertTrue(prefs.hideReplies)
            assertTrue(prefs.hideRepliesByUnfollowed)
            assertTrue(prefs.hideReposts)
            assertTrue(prefs.hideQuotePosts)
        }

    /**
     * The discriminating direction: the server saying "all off" must actually
     * turn filtering off. If `refresh` silently failed, or the body never
     * reached the parser, `prefs` would still read the defaults
     * (`hideRepliesByUnfollowed = true`) and this would fail.
     */
    @Test
    fun `refresh applies a server value that differs from the defaults`() =
        runTest {
            val repo = DefaultFeedViewPreferencesRepository(stubProviderReturning(allOffBody))

            repo.refresh()

            assertFalse(repo.prefs.value.hideRepliesByUnfollowed)
            assertEquals(
                FeedViewPrefs(
                    hideReplies = false,
                    hideRepliesByUnfollowed = false,
                    hideReposts = false,
                    hideQuotePosts = false,
                ),
                repo.prefs.value,
            )
        }

    @Test
    fun `resetToDefault clears a previously fetched value`() =
        runTest {
            val repo = DefaultFeedViewPreferencesRepository(stubProviderReturning(allOffBody))
            repo.refresh()
            assertFalse(repo.prefs.value.hideRepliesByUnfollowed)

            repo.resetToDefault()

            assertEquals(FeedViewPrefs.DEFAULT, repo.prefs.value)
            assertTrue(repo.prefs.value.hideRepliesByUnfollowed)
        }

    @Test
    fun `a body carrying no feedViewPref leaves the defaults in place`() =
        runTest {
            val repo =
                DefaultFeedViewPreferencesRepository(
                    stubProviderReturning("""{"preferences":[{"${'$'}type":"app.bsky.actor.defs#adultContentPref","enabled":true}]}"""),
                )

            repo.refresh()

            assertEquals(FeedViewPrefs.DEFAULT, repo.prefs.value)
        }

    /**
     * Builds a real [XrpcClient] over a Ktor [MockEngine] returning [body] as
     * the `getPreferences` response, so this drives the same
     * `query → handle → decode` path production uses.
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
