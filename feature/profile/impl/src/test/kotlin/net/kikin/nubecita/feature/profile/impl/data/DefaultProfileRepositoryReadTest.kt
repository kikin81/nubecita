package net.kikin.nubecita.feature.profile.impl.data

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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
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
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.VerifierRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Unit tests for [DefaultProfileRepository]'s read / pagination surface —
 * `fetchHeader`, `resolveVerifiers`, and `fetchTab`. (`updateProfile` is
 * covered by [DefaultProfileRepositoryUpdateProfileTest].)
 *
 * Reuses that test's strategy: a real [XrpcClient] over a Ktor [MockEngine] so
 * the SDK's `getProfile` / `getProfiles` / `getAuthorFeed` codepaths run
 * end-to-end against deterministic wire JSON (atproto:models 9.7.4 shapes).
 * The wire→UI mappers are unit-tested separately (AuthorProfileMapperTest,
 * AuthorFeedMapperTest, VerifierRefsMappingTest), so this suite targets the
 * repository's orchestration: endpoint wiring, the per-tab `filter` and cursor
 * pass-through, `resolveVerifiers` chunking + order-preservation +
 * skip-missing, and the failure / cancellation branches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultProfileRepositoryReadTest {
    private val testDid = "did:plc:self"

    // -------------------------------------------------------------------------
    // fetchHeader
    // -------------------------------------------------------------------------

    @Test
    fun fetchHeader_success_hitsGetProfileWithActorAndMapsHeader() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = true) { okJson(profileDetailedJson(did = "did:plc:alice", handle = "alice.test")) }

            val result = repo.fetchHeader("alice.test")

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val header = result.getOrThrow().header
            assertEquals("did:plc:alice", header.did)
            assertEquals("alice.test", header.handle)
            val request = engine.requestHistory.single()
            assertTrue(request.url.encodedPath.endsWith("app.bsky.actor.getProfile"))
            assertEquals("alice.test", request.url.parameters["actor"])
        }

    @Test
    fun fetchHeader_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.fetchHeader("alice.test").isFailure)
        }

    // -------------------------------------------------------------------------
    // resolveVerifiers
    // -------------------------------------------------------------------------

    @Test
    fun resolveVerifiers_empty_returnsEmptyWithoutNetwork() =
        runTest {
            val (engine, repo) = newRepo(signedIn = true) { error("must not hit the network for empty refs") }

            val result = repo.resolveVerifiers(persistentListOf())

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun resolveVerifiers_success_preservesRefOrderAndSkipsUnresolvedDids() =
        runTest {
            // getProfiles returns b and a (out of order) and NOT c; the result
            // must follow the refs' order (a, b) and drop the unresolved c.
            val (_, repo) =
                newRepo(signedIn = true) {
                    // getProfiles returns b before a — the result must still be a, b.
                    okJson(
                        getProfilesJson(
                            profileBasicDetailedJson("did:plc:b", "b.test", "Bob"),
                            profileBasicDetailedJson("did:plc:a", "a.test", "Alice"),
                        ),
                    )
                }

            val refs =
                persistentListOf(
                    VerifierRef(did = "did:plc:a", verifiedAt = Instant.fromEpochMilliseconds(1)),
                    VerifierRef(did = "did:plc:b", verifiedAt = Instant.fromEpochMilliseconds(2)),
                    VerifierRef(did = "did:plc:c", verifiedAt = Instant.fromEpochMilliseconds(3)),
                )

            val resolved = repo.resolveVerifiers(refs).getOrThrow()

            assertEquals(listOf("did:plc:a", "did:plc:b"), resolved.map { it.did })
            assertEquals(listOf("a.test", "b.test"), resolved.map { it.handle })
            assertEquals(listOf("Alice", "Bob"), resolved.map { it.displayName })
            // verifiedAt is carried from the ref, not the profile.
            assertEquals(Instant.fromEpochMilliseconds(1), resolved.first().verifiedAt)
        }

    @Test
    fun resolveVerifiers_over25Refs_chunksIntoTwoGetProfilesCalls() =
        runTest {
            // 26 refs → chunked at 25 → two getProfiles calls. Each responds with
            // no matching profiles, so the result is empty but exactly 2 calls
            // were made — the chunking behaviour under test.
            val (engine, repo) = newRepo(signedIn = true) { okJson("""{"profiles":[]}""") }

            val refs =
                (1..26)
                    .map { VerifierRef(did = "did:plc:v$it", verifiedAt = Instant.fromEpochMilliseconds(it.toLong())) }
                    .toImmutableList()

            val result = repo.resolveVerifiers(refs)

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
            assertEquals(2, engine.requestHistory.size, "26 refs should page into 2 getProfiles calls")
        }

    @Test
    fun resolveVerifiers_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }

            val refs = persistentListOf(VerifierRef(did = "did:plc:a", verifiedAt = Instant.fromEpochMilliseconds(1)))

            assertTrue(repo.resolveVerifiers(refs).isFailure)
        }

    @Test
    fun resolveVerifiers_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        val refs = persistentListOf(VerifierRef(did = "did:plc:a", verifiedAt = Instant.fromEpochMilliseconds(1)))
        assertThrows(CancellationException::class.java) { runBlocking { repo.resolveVerifiers(refs) } }
    }

    // -------------------------------------------------------------------------
    // fetchTab
    // -------------------------------------------------------------------------

    @Test
    fun fetchTab_success_mapsEmptyPageAndForwardsCursor() =
        runTest {
            val (engine, repo) = newRepo(signedIn = true) { okJson("""{"cursor":"nextpage","feed":[]}""") }

            val page = repo.fetchTab("alice.test", ProfileTab.Posts, cursor = "cur", limit = 30).getOrThrow()

            assertTrue(page.items.isEmpty())
            assertEquals("nextpage", page.nextCursor)
            val request = engine.requestHistory.single()
            assertTrue(request.url.encodedPath.endsWith("app.bsky.feed.getAuthorFeed"))
            assertEquals("alice.test", request.url.parameters["actor"])
            assertEquals("cur", request.url.parameters["cursor"])
            assertEquals("30", request.url.parameters["limit"])
        }

    @Test
    fun fetchTab_postsTab_sendsPostsNoRepliesFilter() = assertTabFilter(ProfileTab.Posts, "posts_no_replies")

    @Test
    fun fetchTab_repliesTab_sendsPostsWithRepliesFilter() = assertTabFilter(ProfileTab.Replies, "posts_with_replies")

    @Test
    fun fetchTab_mediaTab_sendsPostsWithMediaFilter() = assertTabFilter(ProfileTab.Media, "posts_with_media")

    @Test
    fun fetchTab_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.fetchTab("alice.test", ProfileTab.Posts, cursor = null, limit = 30).isFailure)
        }

    private fun assertTabFilter(
        tab: ProfileTab,
        expectedFilter: String,
    ) = runTest {
        val (engine, repo) = newRepo(signedIn = true) { okJson("""{"feed":[]}""") }

        repo.fetchTab("alice.test", tab, cursor = null, limit = 20)

        assertEquals(expectedFilter, engine.requestHistory.single().url.parameters["filter"])
    }

    // -------------------------------------------------------------------------
    // Harness
    // -------------------------------------------------------------------------

    private fun profileDetailedJson(
        did: String,
        handle: String,
    ): String = """{"${'$'}type":"app.bsky.actor.defs#profileViewDetailed","did":"$did","handle":"$handle"}"""

    private fun profileBasicDetailedJson(
        did: String,
        handle: String,
        displayName: String,
    ): String =
        """{"${'$'}type":"app.bsky.actor.defs#profileViewDetailed","did":"$did",""" +
            """"handle":"$handle","displayName":"$displayName"}"""

    private fun getProfilesJson(vararg profiles: String): String = """{"profiles":[${profiles.joinToString(",")}]}"""

    private fun MockRequestHandleScope.okJson(json: String): HttpResponseData =
        respond(
            ByteReadChannel(json),
            HttpStatusCode.OK,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.errorJson(): HttpResponseData =
        respond(
            ByteReadChannel("""{"error":"InternalServerError","message":"boom"}"""),
            HttpStatusCode.InternalServerError,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun newRepo(
        signedIn: Boolean,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultProfileRepository> {
        val engine = MockEngine(handler)
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val repo =
            DefaultProfileRepository(
                xrpcClientProvider =
                    object : XrpcClientProvider {
                        override suspend fun authenticated(): XrpcClient = xrpcClient
                    },
                sessionStateProvider =
                    object : SessionStateProvider {
                        override val state: StateFlow<SessionState> =
                            MutableStateFlow(
                                if (signedIn) SessionState.SignedIn(handle = "self.test", did = testDid) else SessionState.SignedOut,
                            )

                        override suspend fun refresh() = Unit
                    },
                moderationPreferences = inertModerationPrefs(),
                encoder = passthroughEncoder(),
                followRepository = inertFollowRepository(),
                dispatcher = UnconfinedTestDispatcher(),
            )
        return engine to repo
    }

    // The read paths never write follows; an inert delegate satisfies the ctor.
    private fun inertFollowRepository(): FollowRepository =
        object : FollowRepository {
            override suspend fun follow(subjectDid: String): Result<String> = error("follow not exercised in read tests")

            override suspend fun unfollow(followUri: String): Result<Unit> = error("unfollow not exercised in read tests")
        }

    // fetchTab reads prefs.value (DEFAULT) to filter; the mapper's own moderation
    // handling is covered by AuthorFeedMapperTest.
    private fun inertModerationPrefs(): ModerationPreferencesRepository =
        object : ModerationPreferencesRepository {
            override val prefs = MutableStateFlow(ModerationPrefs.DEFAULT)

            override suspend fun refresh() = Unit

            override fun resetToDefault() = Unit

            override suspend fun setAdultContentEnabled(enabled: Boolean) = Unit

            override suspend fun setVisibility(
                label: ContentLabel,
                visibility: LabelVisibility,
            ) = Unit
        }

    // The read paths never encode images; a passthrough satisfies the ctor.
    private fun passthroughEncoder(): ImageEncoder =
        object : ImageEncoder {
            override suspend fun encodeForUpload(
                bytes: ByteArray,
                sourceMimeType: String,
                maxBytes: Long,
            ): EncodedImage = EncodedImage(bytes = bytes, mimeType = sourceMimeType)
        }
}
