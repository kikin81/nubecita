package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.image.EncodedImage
import net.kikin.nubecita.core.image.ImageEncoder
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.LabelVisibility
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.core.profile.DefaultProfileRecordWriter
import net.kikin.nubecita.core.profile.ProfileCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `fetchHeader` writes every successful `getProfile` through to the
 * [ProfileCache] and drops the cached copy only when the server says the
 * profile is definitively gone; `cachedHeader` maps a cached profile with the
 * viewer's current moderation prefs.
 */
internal class DefaultProfileRepositoryHeaderCacheTest {
    private val cache = FakeProfileCache()

    @Test
    fun `fetchHeader success writes the response through to the cache`() =
        runTest {
            val repo = newRepo { okJson(PROFILE_JSON) }

            assertTrue(repo.fetchHeader("alice.test").isSuccess)

            assertEquals("he/him", cache.stored["did:plc:alice"]?.pronouns)
        }

    @Test
    fun `fetchHeader of a deactivated account removes the cached copy`() =
        runTest {
            cache.stored["did:plc:alice"] = cachedAlice
            val repo = newRepo { error400("AccountDeactivated", "Account is deactivated") }

            assertTrue(repo.fetchHeader("alice.test").isFailure)

            assertEquals(listOf("alice.test"), cache.removed)
        }

    @Test
    fun `fetchHeader network failure keeps the cached copy`() =
        runTest {
            cache.stored["did:plc:alice"] = cachedAlice
            val repo = newRepo { error400("InternalServerError", "boom", HttpStatusCode.BadGateway) }

            assertTrue(repo.fetchHeader("alice.test").isFailure)

            assertTrue(cache.removed.isEmpty())
            assertNotNull(cache.stored["did:plc:alice"])
        }

    @Test
    fun `cachedHeader maps the cached profile without a network call`() =
        runTest {
            cache.stored["did:plc:alice"] = cachedAlice
            var requests = 0
            val repo =
                newRepo {
                    requests++
                    okJson(PROFILE_JSON)
                }

            val header = repo.cachedHeader("alice.test")

            assertEquals("Alice", header?.header?.displayName)
            assertEquals("she/her", header?.header?.pronouns)
            assertEquals(0, requests)
        }

    @Test
    fun `cachedHeader with nothing cached is null`() =
        runTest {
            assertNull(newRepo { okJson(PROFILE_JSON) }.cachedHeader("nobody.test"))
        }

    // ---------- harness ----------

    private val cachedAlice =
        ProfileViewDetailed(
            did = Did("did:plc:alice"),
            handle = Handle("alice.test"),
            displayName = "Alice",
            pronouns = "she/her",
        )

    private class FakeProfileCache : ProfileCache {
        val stored = mutableMapOf<String, ProfileViewDetailed>()
        val removed = mutableListOf<String>()

        override suspend fun read(actor: String): ProfileViewDetailed? = stored.values.firstOrNull { it.did.raw == actor || it.handle.raw == actor }

        override suspend fun write(profile: ProfileViewDetailed) {
            stored[profile.did.raw] = profile
        }

        override suspend fun remove(actor: String) {
            removed += actor
            stored.values.removeAll { it.did.raw == actor || it.handle.raw == actor }
        }
    }

    private fun MockRequestHandleScope.okJson(json: String): HttpResponseData =
        respond(
            ByteReadChannel(json),
            HttpStatusCode.OK,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.error400(
        name: String,
        message: String,
        status: HttpStatusCode = HttpStatusCode.BadRequest,
    ): HttpResponseData =
        respond(
            ByteReadChannel("""{"error":"$name","message":"$message"}"""),
            status,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun newRepo(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): DefaultProfileRepository {
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(MockEngine(handler)),
                authProvider = NoAuth,
            )
        val xrpcClientProvider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient = xrpcClient
            }
        val sessionStateProvider =
            object : SessionStateProvider {
                override val state: StateFlow<SessionState> =
                    MutableStateFlow(SessionState.SignedIn(handle = "me.test", did = "did:plc:me"))

                override suspend fun refresh() = Unit
            }
        val dispatcher = UnconfinedTestDispatcher()
        return DefaultProfileRepository(
            xrpcClientProvider = xrpcClientProvider,
            sessionStateProvider = sessionStateProvider,
            moderationPreferences =
                object : ModerationPreferencesRepository {
                    override val prefs = MutableStateFlow(ModerationPrefs.DEFAULT)

                    override suspend fun refresh() = Unit

                    override fun resetToDefault() = Unit

                    override suspend fun setAdultContentEnabled(enabled: Boolean) = Unit

                    override suspend fun setVisibility(
                        label: ContentLabel,
                        visibility: LabelVisibility,
                    ) = Unit
                },
            encoder =
                object : ImageEncoder {
                    override suspend fun encodeForUpload(
                        bytes: ByteArray,
                        sourceMimeType: String,
                        maxBytes: Long,
                    ): EncodedImage = EncodedImage(bytes = bytes, mimeType = sourceMimeType)
                },
            followRepository =
                object : FollowRepository {
                    override suspend fun follow(subjectDid: String): Result<String> = error("unused")

                    override suspend fun unfollow(followUri: String): Result<Unit> = error("unused")
                },
            profileRecordWriter = DefaultProfileRecordWriter(xrpcClientProvider, sessionStateProvider, dispatcher),
            profileCache = cache,
            dispatcher = dispatcher,
        )
    }

    private companion object {
        const val PROFILE_JSON =
            """{"did":"did:plc:alice","handle":"alice.test","displayName":"Alice","pronouns":"he/him"}"""
    }
}
