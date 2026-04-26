package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultFeedRepositoryTest {
    @Test
    fun `success returns TimelinePage with mapped PostUis and forwarded cursor`() =
        runTest {
            val fixture = readFixture("timeline_typical.json")
            val provider =
                FakeXrpcClientProvider {
                    XrpcClient(
                        baseUrl = "https://example.test",
                        httpClient = HttpClient(jsonMockEngine(fixture)),
                    )
                }
            val repo = DefaultFeedRepository(provider, UnconfinedTestDispatcher(testScheduler))

            val result = repo.getTimeline(cursor = null)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val page = result.getOrThrow()
            assertEquals(3, page.posts.size)
            assertEquals("next-page-cursor-typical", page.nextCursor)
        }

    @Test
    fun `malformed posts in the response are dropped from the page`() =
        runTest {
            val fixture = readFixture("timeline_malformed_record.json")
            val provider =
                FakeXrpcClientProvider {
                    XrpcClient(
                        baseUrl = "https://example.test",
                        httpClient = HttpClient(jsonMockEngine(fixture)),
                    )
                }
            val repo = DefaultFeedRepository(provider, UnconfinedTestDispatcher(testScheduler))

            val page = repo.getTimeline(cursor = null).getOrThrow()
            // Fixture has 2 entries (1 well-formed, 1 stripped of `text`).
            // The mapper drops the malformed one, so the page has exactly 1 post.
            assertEquals(1, page.posts.size)
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
            val repo = DefaultFeedRepository(provider, UnconfinedTestDispatcher(testScheduler))

            val result = repo.getTimeline(cursor = null)

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertNotNull(cause)
            // Ktor wraps the engine exception, but the underlying IOException is reachable
            // via the cause chain (or it IS the thrown exception when the engine's
            // throw bubbles up unwrapped). Either way, the result is a failure.
            assertTrue(cause is IOException || cause?.cause is IOException, "expected IOException in cause chain, got $cause")
        }

    @Test
    fun `5xx server response surfaces as Result_failure`() =
        runTest {
            val provider =
                FakeXrpcClientProvider {
                    XrpcClient(
                        baseUrl = "https://example.test",
                        httpClient =
                            HttpClient(
                                MockEngine { respondError(HttpStatusCode.InternalServerError) },
                            ),
                    )
                }
            val repo = DefaultFeedRepository(provider, UnconfinedTestDispatcher(testScheduler))

            val result = repo.getTimeline(cursor = null)
            assertTrue(result.isFailure)
        }

    @Test
    fun `NoSessionException propagates via Result_failure`() =
        runTest {
            val provider =
                FakeXrpcClientProvider { throw NoSessionException() }
            val repo = DefaultFeedRepository(provider, UnconfinedTestDispatcher(testScheduler))

            val result = repo.getTimeline(cursor = null)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NoSessionException)
        }

    @Test
    fun `null cursor in response means end-of-feed`() =
        runTest {
            val fixture = readFixture("timeline_with_video_embed.json")
            val provider =
                FakeXrpcClientProvider {
                    XrpcClient(
                        baseUrl = "https://example.test",
                        httpClient = HttpClient(jsonMockEngine(fixture)),
                    )
                }
            val repo = DefaultFeedRepository(provider, UnconfinedTestDispatcher(testScheduler))

            val page = repo.getTimeline(cursor = null).getOrThrow()
            assertNull(page.nextCursor)
        }

    private fun readFixture(name: String): String {
        val classLoader = checkNotNull(this::class.java.classLoader) { "test class loader missing" }
        return requireNotNull(classLoader.getResourceAsStream("fixtures/$name")) {
            "fixture $name not found on test classpath"
        }.bufferedReader().use { it.readText() }
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
