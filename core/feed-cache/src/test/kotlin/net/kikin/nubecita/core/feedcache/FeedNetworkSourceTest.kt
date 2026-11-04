package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedNetworkSourceTest {
    private val following = FeedKey.following("did:plc:me")
    private val discover = FeedKey("did:plc:me", FeedType.DISCOVER, "at://did:plc:gen/app.bsky.feed.generator/whats-hot")
    private val list = FeedKey("did:plc:me", FeedType.LIST, "at://did:plc:owner/app.bsky.graph.list/friends")

    @Test
    fun `FOLLOWING fetch returns wire posts and forwarded cursor`() =
        runTest {
            val source = sourceFor(readFixture("timeline_typical.json"))

            val (posts, cursor) = source.fetchPage(following, cursor = null).getOrThrow()

            assertEquals(3, posts.size)
            assertEquals("next-page-cursor-typical", cursor)
        }

    @Test
    fun `DISCOVER fetch maps the generator response through getFeed`() =
        runTest {
            val source = sourceFor(readFixture("timeline_typical.json"))

            val (posts, cursor) = source.fetchPage(discover, cursor = null).getOrThrow()

            assertEquals(3, posts.size)
            assertEquals("next-page-cursor-typical", cursor)
        }

    @Test
    fun `LIST fetch maps the list response through getListFeed`() =
        runTest {
            val source = sourceFor(readFixture("timeline_typical.json"))

            val result = source.fetchPage(list, cursor = null)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            assertEquals(3, result.getOrThrow().posts.size)
        }

    @Test
    fun `null cursor in response means end-of-feed`() =
        runTest {
            val source = sourceFor(readFixture("timeline_with_repost.json"))

            // The repost fixture carries no cursor.
            assertNull(source.fetchPage(following, cursor = null).getOrThrow().nextCursor)
        }

    @Test
    fun `network failure surfaces as Result_failure`() =
        runTest {
            val provider =
                FakeXrpcClientProvider {
                    XrpcClient(
                        baseUrl = "https://example.test",
                        httpClient = HttpClient(MockEngine { throw IOException("simulated network failure") }),
                    )
                }
            val source = FeedNetworkSource(provider, UnconfinedTestDispatcher(testScheduler))

            val result = source.fetchPage(following, cursor = null)

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertTrue(cause is IOException || cause?.cause is IOException, "expected IOException in cause chain, got $cause")
        }

    @Test
    fun `5xx server response surfaces as Result_failure`() =
        runTest {
            val provider =
                FakeXrpcClientProvider {
                    XrpcClient(
                        baseUrl = "https://example.test",
                        httpClient = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }),
                    )
                }
            val source = FeedNetworkSource(provider, UnconfinedTestDispatcher(testScheduler))

            assertTrue(source.fetchPage(discover, cursor = null).isFailure)
        }

    @Test
    fun `NoSessionException propagates via Result_failure`() =
        runTest {
            val provider = FakeXrpcClientProvider { throw NoSessionException() }
            val source = FeedNetworkSource(provider, UnconfinedTestDispatcher(testScheduler))

            val result = source.fetchPage(following, cursor = null)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NoSessionException)
        }

    @Test
    fun `CancellationException propagates rather than collapsing into a failure`() =
        runTest {
            val provider = FakeXrpcClientProvider { throw CancellationException("cancelled") }
            val source = FeedNetworkSource(provider, UnconfinedTestDispatcher(testScheduler))

            // Cooperative cancellation must propagate, not become Result.failure
            // (which would log a false error and swallow the cancel).
            val propagated =
                try {
                    source.fetchPage(following, cursor = null)
                    false
                } catch (_: CancellationException) {
                    true
                }
            assertTrue(propagated, "CancellationException must propagate for cooperative cancellation")
        }

    // --- `since`-bounded catch-up (task 3.1) ------------------------------
    //
    // Asserted against the URL that actually goes on the wire, not against the
    // request object: a `since` that is built but not serialized would turn a
    // bounded catch-up into an unbounded walk backwards through the feed, and
    // nothing else would notice.

    @Test
    fun `FOLLOWING forwards since and captures startCursor`() =
        runTest {
            val urls = mutableListOf<String>()
            val source = recordingSource(urls, BODY_WITH_START_CURSOR)

            val page = source.fetchPage(following, cursor = null, since = "boundary-1").getOrThrow()

            assertTrue(urls.single().contains("since=boundary-1"), "since missing from ${urls.single()}")
            assertEquals("newest-item-cursor", page.startCursor)
        }

    @Test
    fun `LIST forwards since`() =
        runTest {
            val urls = mutableListOf<String>()
            val source = recordingSource(urls, BODY_WITH_START_CURSOR)

            source.fetchPage(list, cursor = null, since = "boundary-1").getOrThrow()

            assertTrue(urls.single().contains("since=boundary-1"), "since missing from ${urls.single()}")
        }

    @Test
    fun `a generator feed never receives since`() =
        runTest {
            // getFeed has no `since` parameter. Sending one would be silently
            // ignored by the server, so it is dropped here and logged instead.
            val urls = mutableListOf<String>()
            val source = recordingSource(urls, BODY_WITH_START_CURSOR)

            val page = source.fetchPage(discover, cursor = null, since = "boundary-1").getOrThrow()

            assertTrue(!urls.single().contains("since="), "since leaked into ${urls.single()}")
            // And no startCursor comes back, which is why a generator feed
            // cannot bound a catch-up range at all.
            assertNull(page.startCursor)
        }

    @Test
    fun `no since means no since parameter`() =
        runTest {
            val urls = mutableListOf<String>()
            val source = recordingSource(urls, BODY_WITH_START_CURSOR)

            source.fetchPage(following, cursor = null).getOrThrow()

            assertTrue(!urls.single().contains("since="), "unexpected since in ${urls.single()}")
        }

    @Test
    fun `supportsSince matches the protocol, exhaustively`() {
        assertTrue(FeedType.FOLLOWING.supportsSince)
        assertTrue(FeedType.LIST.supportsSince)
        assertEquals(false, FeedType.DISCOVER.supportsSince)
        assertEquals(false, FeedType.CUSTOM.supportsSince)
    }

    /** A source whose engine records every request URL it serves. */
    private fun kotlinx.coroutines.test.TestScope.recordingSource(
        urls: MutableList<String>,
        body: String,
    ): FeedNetworkSource {
        val engine =
            MockEngine { request ->
                urls += request.url.toString()
                respond(
                    content = ByteReadChannel(body),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        val provider =
            FakeXrpcClientProvider {
                XrpcClient(baseUrl = "https://example.test", httpClient = HttpClient(engine))
            }
        return FeedNetworkSource(provider, UnconfinedTestDispatcher(testScheduler))
    }

    private fun kotlinx.coroutines.test.TestScope.sourceFor(fixture: String): FeedNetworkSource {
        val provider =
            FakeXrpcClientProvider {
                XrpcClient(
                    baseUrl = "https://example.test",
                    httpClient = HttpClient(jsonMockEngine(fixture)),
                )
            }
        return FeedNetworkSource(provider, UnconfinedTestDispatcher(testScheduler))
    }

    private fun readFixture(name: String): String {
        val classLoader = checkNotNull(this::class.java.classLoader) { "test class loader missing" }
        return requireNotNull(classLoader.getResourceAsStream("fixtures/$name")) {
            "fixture $name not found on test classpath"
        }.bufferedReader().use { it.readText() }
    }

    private companion object {
        /** An empty page that still carries a `startCursor`. */
        const val BODY_WITH_START_CURSOR = """{"feed":[],"cursor":"next-1","startCursor":"newest-item-cursor"}"""
    }

    private fun jsonMockEngine(body: String): MockEngine =
        MockEngine { _ ->
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
}

private class FakeXrpcClientProvider(
    private val factory: () -> XrpcClient,
) : XrpcClientProvider {
    override suspend fun authenticated(): XrpcClient = factory()
}
