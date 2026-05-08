package net.kikin.nubecita.core.posts.internal

import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.data.models.EmbedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultPostRepositoryTest {
    @Test
    fun `success returns PostUi with three-image embed`() =
        runTest {
            val fixture = readFixture("getposts_with_three_images.json")
            val provider =
                FakeXrpcClientProvider {
                    XrpcClient(
                        baseUrl = "https://example.test",
                        httpClient = HttpClient(jsonMockEngine(fixture)),
                    )
                }
            val repo = DefaultPostRepository(provider, UnconfinedTestDispatcher(testScheduler))

            val result = repo.getPost("at://did:plc:oky5czdrnfjpqslsw2a5iclo/app.bsky.feed.post/3mjko6vtdps2b")

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val post = result.getOrThrow()
            val embed = post.embed
            assertTrue(embed is EmbedUi.Images, "expected Images embed, got ${embed::class.simpleName}")
            assertEquals(3, (embed as EmbedUi.Images).items.size)
        }

    @Test
    fun `empty posts list surfaces as PostNotFoundException`() =
        runTest {
            val fixture = readFixture("getposts_empty.json")
            val provider =
                FakeXrpcClientProvider {
                    XrpcClient(
                        baseUrl = "https://example.test",
                        httpClient = HttpClient(jsonMockEngine(fixture)),
                    )
                }
            val repo = DefaultPostRepository(provider, UnconfinedTestDispatcher(testScheduler))

            val result = repo.getPost("at://did:plc:test/app.bsky.feed.post/missing")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is PostNotFoundException)
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
            val repo = DefaultPostRepository(provider, UnconfinedTestDispatcher(testScheduler))

            val result = repo.getPost("at://did:plc:test/app.bsky.feed.post/anything")

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }

    @Test
    fun `no authenticated session surfaces NoSessionException`() =
        runTest {
            val provider =
                FakeXrpcClientProvider { throw NoSessionException() }
            val repo = DefaultPostRepository(provider, UnconfinedTestDispatcher(testScheduler))

            val result = repo.getPost("at://did:plc:test/app.bsky.feed.post/anything")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NoSessionException)
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
