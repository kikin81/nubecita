package net.kikin.nubecita.core.push.testing

import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.MutableStateFlow
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.push.FcmAutoInit
import net.kikin.nubecita.core.push.FcmTokenProvider
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// Shared in-memory fakes for `:core:push` instrumented tests. These match
// the pure-JVM test fakes (`PushRegistrationCoordinatorTest`'s nested
// `FakeSessionStateProvider` / `StaticFcmTokenProvider` / `NoopFcmAutoInit`)
// but live in `androidTest` so they can also be wired through Hilt for
// `@HiltAndroidTest`-style tests that exercise the Android runtime
// (Service lifecycle, NotificationManager). Kept here (rather than
// re-derived per test class) so both §9.1 and §9.3 use the same
// recording / signaling primitives.

/**
 * Hands out a pre-built [XrpcClient] backed by [recordingEngine]. Tests
 * inspect [recordingEngine.requests] / [recordingEngine.bodies] to assert
 * wire shape (URL, headers, JSON body).
 */
class FakeXrpcClientProvider(
    val recordingEngine: RecordingMockEngine = RecordingMockEngine.respondingWithEmpty(),
    private val baseUrl: String = "https://pds.example.test",
) : XrpcClientProvider {
    private val client: XrpcClient by lazy {
        XrpcClient(
            baseUrl = baseUrl,
            httpClient = HttpClient(recordingEngine.engine),
        )
    }

    override suspend fun authenticated(): XrpcClient = client
}

/**
 * Ktor [MockEngine] wrapper that captures every outgoing request and the
 * JSON body (if any) into thread-safe lists. Ktor's HttpClient can invoke
 * the engine handler from concurrent dispatchers when the calling code
 * uses async/await, so the captured-request / captured-body lists are
 * wrapped in [Collections.synchronizedList] — adds are atomic; iteration
 * for assertions is single-threaded by convention (tests `await` before
 * asserting).
 *
 * Default response is HTTP 200 with body `{}` so the generated
 * `NotificationService.registerPush` deserializer (kotlinx UnitSerializer
 * accepts `{}`) doesn't choke; tests that want a failure path build
 * their own [MockEngine].
 */
class RecordingMockEngine private constructor(
    val engine: MockEngine,
    val requests: List<HttpRequestData>,
    val bodies: List<String>,
) {
    companion object {
        fun respondingWithEmpty(): RecordingMockEngine {
            val capturedRequests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())
            val capturedBodies: MutableList<String> = Collections.synchronizedList(mutableListOf())
            val engine =
                MockEngine { request ->
                    capturedRequests += request
                    capturedBodies += (request.body as? TextContent)?.text.orEmpty()
                    respond(
                        content = ByteReadChannel("{}"),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            return RecordingMockEngine(engine, capturedRequests, capturedBodies)
        }
    }
}

/**
 * [SessionStateProvider] backed by a [MutableStateFlow] tests can pump.
 * `refresh()` is a no-op — drive [state] directly.
 */
class FakeSessionStateProvider(
    initialState: SessionState = SessionState.Loading,
) : SessionStateProvider {
    override val state: MutableStateFlow<SessionState> = MutableStateFlow(initialState)

    override suspend fun refresh() {
        // No-op: tests drive the StateFlow directly via state.value = ...
    }
}

/**
 * Returns a fixed FCM token. Counts `current()` invocations so tests can
 * assert the coordinator queried the token exactly once per attempt.
 */
class FakeFcmTokenProvider(
    private val token: String = "fcm-token-instr",
) : FcmTokenProvider {
    private val callCount = AtomicInteger(0)
    val invocations: Int get() = callCount.get()

    override suspend fun current(): String {
        callCount.incrementAndGet()
        return token
    }
}

/**
 * No-op for tests — the production [FcmAutoInit] would flip
 * `FirebaseMessaging.isAutoInitEnabled` back on, which would race the
 * `HiltTestApplication` swap during the test runner's startup. Tests that
 * explicitly want auto-init can wire their own implementation per the
 * canonical Hilt-testing doc.
 */
class NoopFcmAutoInit : FcmAutoInit {
    private val enableCalls = AtomicInteger(0)
    val invocations: Int get() = enableCalls.get()

    override fun enable() {
        enableCalls.incrementAndGet()
    }
}

/**
 * Records the [Boolean] returned by [isAppForeground] so the
 * `NubecitaFcmService` foreground-drop branch can be driven without an
 * actual `ProcessLifecycleOwner`. Mutate via [setForeground] from the test
 * thread before invoking the service entry point.
 */
class ForegroundProbe(
    initiallyForeground: Boolean = false,
) {
    private val foreground = AtomicReference(initiallyForeground)
    val isAppForeground: Boolean get() = foreground.get()

    fun setForeground(value: Boolean) {
        foreground.set(value)
    }
}
