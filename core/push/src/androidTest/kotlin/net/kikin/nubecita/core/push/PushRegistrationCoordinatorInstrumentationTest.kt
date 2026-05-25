package net.kikin.nubecita.core.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.http.encodedPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.push.testing.FakeFcmTokenProvider
import net.kikin.nubecita.core.push.testing.FakeSessionStateProvider
import net.kikin.nubecita.core.push.testing.FakeXrpcClientProvider
import net.kikin.nubecita.core.push.testing.NoopFcmAutoInit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * §9.3 of the push proposal — end-to-end coordinator coverage on a real
 * Android runtime. The pure-JVM
 * [PushRegistrationCoordinatorTest][PushRegistrationCoordinatorTest in
 * src/test] uses TestScope's virtual time to exercise the full
 * backoff schedule (5s / 30s / 2m / 8m) in milliseconds; this
 * instrumentation suite focuses on "real Android coroutines + real
 * DataStore + real Ktor through MockEngine produce the canonical wire
 * shape on the canonical state transitions" — not re-running the unit
 * test at wall-clock speed.
 *
 * No Hilt scaffolding is needed here — the coordinator's collaborators
 * are all interfaces we can fake by hand. §9.1's service-lifecycle test
 * is what actually needs `@HiltAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class PushRegistrationCoordinatorInstrumentationTest {
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var coordinatorScope: CoroutineScope
    private lateinit var dataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: PushRegistrationStateStore

    private val viewerDid = "did:plc:viewer-instr"
    private val viewerHandle = "alice.bsky.social"
    private val fcmToken = "fcm-token-instr"
    private val appId = "net.kikin.nubecita"

    @Before
    fun setUp() {
        // Each test gets a fresh DataStore file in the test's private
        // cache directory. The two scopes are separated so we can cancel
        // the coordinator's scope mid-test without tearing down DataStore
        // before the test's verification reads complete.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dataStoreScope = CoroutineScope(Dispatchers.IO)
        coordinatorScope = CoroutineScope(Dispatchers.Main)
        dataStoreFile = File(context.cacheDir, "test-${System.nanoTime()}.preferences_pb")
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile },
            )
        store = PushRegistrationStateStore(dataStore)
    }

    @After
    fun tearDown() {
        coordinatorScope.cancel()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun signedIn_triggers_register_with_the_gateway_proxy_header_and_writes_Succeeded() =
        runBlocking {
            val xrpcProvider = FakeXrpcClientProvider()
            val sessionFlow = FakeSessionStateProvider(SessionState.SignedIn(handle = viewerHandle, did = viewerDid))
            val coordinator = newCoordinator(xrpcProvider, sessionFlow)

            coordinator.start()
            waitForStatus(PushRegistrationState.Status.Succeeded)

            val request = xrpcProvider.recordingEngine.requests.single()
            assertEquals(
                "wire path must match the registerPush NSID",
                "/xrpc/app.bsky.notification.registerPush",
                request.url.encodedPath,
            )
            assertEquals(
                "atproto-proxy header must carry the gateway DID — without this the PDS routes to bsky's own notification service",
                "did:web:push.nubecita.app#bsky_notif",
                request.headers["atproto-proxy"],
            )

            val body =
                xrpcProvider.recordingEngine.bodies
                    .single()
                    .asJsonObject()
            assertEquals("did:web:push.nubecita.app", body["serviceDid"]!!.jsonPrimitive.content)
            assertEquals(fcmToken, body["token"]!!.jsonPrimitive.content)
            assertEquals("android", body["platform"]!!.jsonPrimitive.content)
            assertEquals(appId, body["appId"]!!.jsonPrimitive.content)

            val stored = store.read()
            assertEquals(viewerDid, stored.accountDid)
            assertEquals(fcmToken, stored.fcmToken)
            assertEquals(PushRegistrationState.Status.Succeeded, stored.status)
        }

    @Test
    fun signedOut_after_a_successful_registration_calls_unregister_and_clears_the_store() =
        runBlocking {
            val xrpcProvider = FakeXrpcClientProvider()
            val sessionFlow = FakeSessionStateProvider(SessionState.SignedIn(handle = viewerHandle, did = viewerDid))
            val coordinator = newCoordinator(xrpcProvider, sessionFlow)

            coordinator.start()
            waitForStatus(PushRegistrationState.Status.Succeeded)
            // Sanity: registerPush has fired exactly once.
            assertEquals(1, xrpcProvider.recordingEngine.requests.size)

            sessionFlow.state.value = SessionState.SignedOut
            waitForCondition("unregisterPush request to arrive") {
                xrpcProvider.recordingEngine.requests.any {
                    it.url.encodedPath == "/xrpc/app.bsky.notification.unregisterPush"
                }
            }
            waitForStoreClear()

            val unregister = xrpcProvider.recordingEngine.requests.last()
            assertEquals(
                "/xrpc/app.bsky.notification.unregisterPush",
                unregister.url.encodedPath,
            )
            assertEquals(
                "did:web:push.nubecita.app#bsky_notif",
                unregister.headers["atproto-proxy"],
            )
            assertEquals(PushRegistrationState.Default, store.read())
        }

    @Test
    fun onTokenRotated_while_signed_in_triggers_a_fresh_register_with_the_new_token() =
        runBlocking {
            val xrpcProvider = FakeXrpcClientProvider()
            val sessionFlow = FakeSessionStateProvider(SessionState.SignedIn(handle = viewerHandle, did = viewerDid))
            val coordinator = newCoordinator(xrpcProvider, sessionFlow)

            coordinator.start()
            waitForStatus(PushRegistrationState.Status.Succeeded)
            val beforeRotation = xrpcProvider.recordingEngine.requests.size

            coordinator.onTokenRotated("fcm-token-rotated")
            waitForCondition("rotated-token register to land in the store") {
                store.read().fcmToken == "fcm-token-rotated" &&
                    store.read().status == PushRegistrationState.Status.Succeeded
            }

            val newRequests = xrpcProvider.recordingEngine.requests.drop(beforeRotation)
            assertTrue(
                "expected at least one new register call carrying the rotated token; got $newRequests",
                newRequests.any { it.url.encodedPath == "/xrpc/app.bsky.notification.registerPush" },
            )
            val rotatedBody =
                xrpcProvider.recordingEngine.bodies
                    .last()
                    .asJsonObject()
            assertEquals("fcm-token-rotated", rotatedBody["token"]!!.jsonPrimitive.content)
        }

    private fun newCoordinator(
        xrpcProvider: FakeXrpcClientProvider,
        sessionFlow: FakeSessionStateProvider,
        tokenProvider: FakeFcmTokenProvider = FakeFcmTokenProvider(fcmToken),
    ): PushRegistrationCoordinator =
        PushRegistrationCoordinator(
            sessionStateProvider = sessionFlow,
            repository =
                DefaultPushRegistrationRepository(
                    xrpcClientProvider = xrpcProvider,
                    appId = appId,
                    gateway = PushGatewayConfig.Nubecita,
                ),
            stateStore = store,
            tokenProvider = tokenProvider,
            fcmAutoInit = NoopFcmAutoInit(),
            scope = coordinatorScope,
        )

    /** Spin-poll the store until [target] appears, with a hard timeout. */
    private suspend fun waitForStatus(target: PushRegistrationState.Status) = waitForCondition("store status == $target") { store.read().status == target }

    private suspend fun waitForStoreClear() = waitForCondition("store cleared") { store.read() == PushRegistrationState.Default }

    /**
     * Real-time polling helper. Instrumentation tests must NOT use
     * TestScope's virtual time — the coordinator's `scope` is the real
     * [Dispatchers.Main], so its `launch` / `delay` calls run on
     * wall-clock time. `runTest` + virtual `delay` would never advance
     * Main's clock and the coordinator would appear hung. Use
     * `runBlocking` (above) with a real [delay] here. 50ms × 100 iters
     * = 5s timeout, well above the immediate-attempt window we exercise
     * (the unit test covers the long backoff schedule with virtual time).
     */
    private suspend fun waitForCondition(
        description: String,
        condition: suspend () -> Boolean,
    ) {
        repeat(100) {
            if (condition()) return
            delay(50)
        }
        error("timed out waiting for: $description")
    }

    private fun String.asJsonObject() = Json.parseToJsonElement(this).jsonObject
}
