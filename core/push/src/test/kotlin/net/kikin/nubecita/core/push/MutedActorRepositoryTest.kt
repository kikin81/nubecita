package net.kikin.nubecita.core.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MutedActorRepositoryTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `refresh populates the snapshot with DIDs from getMutes`() =
        runTest {
            val capture = RecordingEngine.respondingWith(mutesJson("did:plc:alice", "did:plc:bob"))
            val fixture = newFixture(this, capture.engine)

            val result = fixture.repository.refresh()

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            assertEquals(setOf("did:plc:alice", "did:plc:bob"), fixture.repository.snapshot.value)
            val request = capture.requests.single()
            assertEquals("/xrpc/app.bsky.graph.getMutes", request.url.encodedPath)
        }

    @Test
    fun `refresh skips the network when last successful refresh was within the 12-hour debounce window`() =
        runTest {
            val capture = RecordingEngine.respondingWith(mutesJson("did:plc:alice"))
            val clock = MutableClock(startMs = 1_700_000_000_000L)
            val fixture = newFixture(this, capture.engine, clock = clock::value)

            fixture.repository.refresh() // first call — fetches
            assertEquals(1, capture.requests.size)

            clock.advanceMs(10L * 60 * 60 * 1000) // 10 hours later, still inside the window
            fixture.repository.refresh() // second call — must be a no-op

            assertEquals(1, capture.requests.size, "second refresh inside debounce window must NOT hit the network")
        }

    @Test
    fun `refresh with force=true bypasses the debounce window`() =
        runTest {
            val capture = RecordingEngine.respondingWith(mutesJson("did:plc:alice"))
            val clock = MutableClock(startMs = 1_700_000_000_000L)
            val fixture = newFixture(this, capture.engine, clock = clock::value)

            fixture.repository.refresh()
            assertEquals(1, capture.requests.size)

            clock.advanceMs(60_000L) // 1 minute later — well inside debounce
            fixture.repository.refresh(force = true)

            assertEquals(2, capture.requests.size, "force=true must bypass the debounce")
        }

    @Test
    fun `refresh failure preserves the stale snapshot`() =
        runTest {
            // Successful refresh seeds the snapshot.
            val seedEngine = RecordingEngine.respondingWith(mutesJson("did:plc:alice", "did:plc:bob"))
            val fixture = newFixture(this, seedEngine.engine)
            fixture.repository.refresh()
            assertEquals(setOf("did:plc:alice", "did:plc:bob"), fixture.repository.snapshot.value)

            // Swap to a failing engine and force a refresh.
            val failingEngine =
                MockEngine { _ ->
                    respondError(
                        status = HttpStatusCode.InternalServerError,
                        content = "{\"error\":\"GatewayDown\"}",
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            fixture.swapEngine(failingEngine)
            val failedResult = fixture.repository.refresh(force = true)

            assertTrue(failedResult.isFailure, "expected failure for the 500 response")
            assertEquals(
                setOf("did:plc:alice", "did:plc:bob"),
                fixture.repository.snapshot.value,
                "the stale snapshot must NOT be cleared on refresh failure",
            )
        }

    private class Fixture(
        val repository: MutedActorRepository,
        private val provider: SwappableXrpcClientProvider,
    ) {
        fun swapEngine(engine: MockEngine) {
            provider.swap(engine)
        }
    }

    private fun newFixture(
        scope: TestScope,
        engine: MockEngine,
        fileName: String = "muted-${System.nanoTime()}.preferences_pb",
        clock: () -> Long = { System.currentTimeMillis() },
    ): Fixture {
        val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(scope.testScheduler + dispatcher),
                produceFile = { File(tempDir, fileName) },
            )
        val provider = SwappableXrpcClientProvider(engine)
        val repository =
            MutedActorRepository(
                xrpcClientProvider = provider,
                dataStore = dataStore,
                clock = clock,
            )
        return Fixture(repository, provider)
    }

    private class SwappableXrpcClientProvider(
        initialEngine: MockEngine,
    ) : XrpcClientProvider {
        private var current: XrpcClient = newClient(initialEngine)

        fun swap(engine: MockEngine) {
            current = newClient(engine)
        }

        override suspend fun authenticated(): XrpcClient = current

        private fun newClient(engine: MockEngine) =
            XrpcClient(
                baseUrl = "https://pds.example.test",
                httpClient = HttpClient(engine),
            )
    }

    private class MutableClock(
        startMs: Long,
    ) {
        var value: Long = startMs
            private set

        fun advanceMs(deltaMs: Long) {
            value += deltaMs
        }
    }

    private class RecordingEngine private constructor(
        val engine: MockEngine,
        val requests: List<HttpRequestData>,
    ) {
        companion object {
            fun respondingWith(body: String): RecordingEngine {
                val capturedRequests = mutableListOf<HttpRequestData>()
                val engine =
                    MockEngine { request ->
                        capturedRequests += request
                        respond(
                            content = ByteReadChannel(body),
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                return RecordingEngine(engine = engine, requests = capturedRequests)
            }
        }
    }

    private companion object {
        fun mutesJson(vararg dids: String): String {
            val mutes =
                dids.joinToString(",") { did ->
                    """{"did":"$did","handle":"${did.substringAfter("did:plc:")}.bsky.social"}"""
                }
            return """{"mutes":[$mutes]}"""
        }
    }
}
