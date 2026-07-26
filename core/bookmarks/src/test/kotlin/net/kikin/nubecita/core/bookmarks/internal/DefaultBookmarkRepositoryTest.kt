package net.kikin.nubecita.core.bookmarks.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultBookmarkRepositoryTest {
    private val postRef =
        StrongRef(
            cid = Cid("bafyreifakecid000000000000000000000000000000000"),
            uri = AtUri("at://did:plc:fake/app.bsky.feed.post/p1"),
        )

    @Test
    fun `bookmark returns success on an OK response`() =
        runTest {
            val repo = newRepo(okJson("{}"))
            val result = repo.bookmark(postRef)
            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
        }

    @Test
    fun `unbookmark returns success on an OK response`() =
        runTest {
            val repo = newRepo(okJson("{}"))
            val result = repo.unbookmark(postRef)
            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
        }

    @Test
    fun `getBookmarks maps a bookmarked postView to PostUi with bookmark state`() =
        runTest {
            val repo = newRepo(okJson(readFixture("getbookmarks_one_post.json")))

            val result = repo.getBookmarks(cursor = null)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val page = result.getOrThrow()
            assertEquals("next-page-cursor", page.cursor)
            assertEquals(1, page.posts.size)
            val post = page.posts.first()
            assertEquals("at://did:plc:fake/app.bsky.feed.post/p1", post.id)
            assertEquals(7, post.stats.bookmarkCount)
            assertTrue(post.viewer.isBookmarked)
        }

    @Test
    fun `network failure surfaces as Result failure`() =
        runTest {
            val repo = newRepo(MockEngine { throw IOException("simulated network failure") })
            val result = repo.bookmark(postRef)
            assertTrue(result.isFailure)
        }

    @Test
    fun `no authenticated session surfaces NoSessionException`() =
        runTest {
            val repo =
                DefaultBookmarkRepository(
                    FakeXrpcClientProvider { throw NoSessionException() },
                    fakeSessionStateProvider(),
                    UnconfinedTestDispatcher(testScheduler),
                )
            val result = repo.getBookmarks(cursor = null)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NoSessionException)
        }

    private fun kotlinx.coroutines.test.TestScope.newRepo(engine: MockEngine): DefaultBookmarkRepository =
        DefaultBookmarkRepository(
            FakeXrpcClientProvider {
                XrpcClient(baseUrl = "https://example.test", httpClient = HttpClient(engine))
            },
            fakeSessionStateProvider(),
            UnconfinedTestDispatcher(testScheduler),
        )

    private fun okJson(body: String): MockEngine =
        MockEngine { _ ->
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }

    private fun readFixture(name: String): String {
        val classLoader = checkNotNull(this::class.java.classLoader) { "test class loader missing" }
        return requireNotNull(classLoader.getResourceAsStream("fixtures/$name")) {
            "fixture $name not found on test classpath"
        }.bufferedReader().use { it.readText() }
    }
}

private class FakeXrpcClientProvider(
    private val factory: () -> XrpcClient,
) : XrpcClientProvider {
    override suspend fun authenticated(): XrpcClient = factory()
}

/**
 * Minimal [SessionStateProvider] for projection tests: only `state.value` is
 * read (via `viewerDidOrNull`), and `isSelfHosted` is interface-derived.
 *
 * @param did the signed-in DID, or null for a signed-out session.
 */
private fun fakeSessionStateProvider(did: String? = null): SessionStateProvider =
    object : SessionStateProvider {
        override val state: StateFlow<SessionState> =
            MutableStateFlow(
                did?.let { SessionState.SignedIn(handle = "tester.bsky.social", did = it) }
                    ?: SessionState.SignedOut,
            )

        // No-op: these tests never re-drive the session, they only read
        // state.value through viewerDidOrNull.
        override suspend fun refresh() = Unit
    }
