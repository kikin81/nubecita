package net.kikin.nubecita.core.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrationCoordinatorTest {
    @TempDir
    lateinit var tempDir: File

    private val viewerDid = "did:plc:viewer123"
    private val fcmToken = "fcm-token-abc"

    @Test
    fun `Loading state is a no-op`() =
        runTest {
            val fixture = newFixture(initialState = SessionState.Loading)

            fixture.coordinator.start()
            advanceUntilIdle()

            assertEquals(0, fixture.repository.registerCalls.size)
            assertEquals(0, fixture.repository.unregisterCalls.size)
            assertEquals(PushRegistrationState.Default, fixture.store.read())
        }

    @Test
    fun `SignedIn from initial state triggers register and writes Succeeded to the store`() =
        runTest {
            val fixture = newFixture(initialState = SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))

            fixture.coordinator.start()
            advanceUntilIdle()

            assertEquals(listOf(viewerDid to fcmToken), fixture.repository.registerCalls)
            assertEquals(
                PushRegistrationState(
                    accountDid = viewerDid,
                    fcmToken = fcmToken,
                    status = PushRegistrationState.Status.Succeeded,
                ),
                fixture.store.read(),
            )
        }

    @Test
    fun `SignedIn whose did and token already match a Succeeded store entry is a no-op`() =
        runTest {
            val fixture = newFixture(initialState = SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            fixture.store.write(
                PushRegistrationState(
                    accountDid = viewerDid,
                    fcmToken = fcmToken,
                    status = PushRegistrationState.Status.Succeeded,
                ),
            )

            fixture.coordinator.start()
            advanceUntilIdle()

            assertEquals(0, fixture.repository.registerCalls.size)
        }

    @Test
    fun `SignedIn whose stored status is Failed triggers register again`() =
        runTest {
            val fixture = newFixture(initialState = SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            fixture.store.write(
                PushRegistrationState(
                    accountDid = viewerDid,
                    fcmToken = fcmToken,
                    status = PushRegistrationState.Status.Failed,
                ),
            )

            fixture.coordinator.start()
            advanceUntilIdle()

            assertEquals(listOf(viewerDid to fcmToken), fixture.repository.registerCalls)
        }

    @Test
    fun `SignedIn whose stored did differs from current session triggers register`() =
        runTest {
            val fixture = newFixture(initialState = SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            fixture.store.write(
                PushRegistrationState(
                    accountDid = "did:plc:someone-else",
                    fcmToken = fcmToken,
                    status = PushRegistrationState.Status.Succeeded,
                ),
            )

            fixture.coordinator.start()
            advanceUntilIdle()

            assertEquals(listOf(viewerDid to fcmToken), fixture.repository.registerCalls)
        }

    @Test
    fun `SignedOut after a successful registration calls unregister and clears the store`() =
        runTest {
            val sessionFlow = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            val fixture = newFixture(sessionFlow = sessionFlow)

            fixture.coordinator.start()
            advanceUntilIdle()
            // Sanity: we registered first.
            assertEquals(1, fixture.repository.registerCalls.size)

            sessionFlow.value = SessionState.SignedOut
            advanceUntilIdle()

            assertEquals(listOf(viewerDid to fcmToken), fixture.repository.unregisterCalls)
            assertEquals(PushRegistrationState.Default, fixture.store.read())
        }

    @Test
    fun `SignedOut still clears the store when unregister fails (best-effort)`() =
        runTest {
            val sessionFlow = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            val fixture = newFixture(sessionFlow = sessionFlow)

            fixture.coordinator.start()
            advanceUntilIdle()
            fixture.repository.unregisterFailures = Int.MAX_VALUE // every unregister attempt fails

            sessionFlow.value = SessionState.SignedOut
            advanceUntilIdle()

            assertEquals(1, fixture.repository.unregisterCalls.size, "unregister must be called exactly once (best-effort, no retry)")
            assertEquals(PushRegistrationState.Default, fixture.store.read())
        }

    @Test
    fun `onTokenRotated while signed in triggers a fresh register with the new token`() =
        runTest {
            val fixture = newFixture(initialState = SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            fixture.coordinator.start()
            advanceUntilIdle()
            // The initial register fires; reset counters before the rotated-token call so the assertion is unambiguous.
            fixture.repository.registerCalls.clear()

            fixture.coordinator.onTokenRotated("fcm-token-rotated")
            advanceUntilIdle()

            assertEquals(listOf(viewerDid to "fcm-token-rotated"), fixture.repository.registerCalls)
            assertEquals(
                PushRegistrationState(
                    accountDid = viewerDid,
                    fcmToken = "fcm-token-rotated",
                    status = PushRegistrationState.Status.Succeeded,
                ),
                fixture.store.read(),
            )
        }

    @Test
    fun `onTokenRotated while signed out is a no-op (nothing to register against)`() =
        runTest {
            val fixture = newFixture(initialState = SessionState.SignedOut)
            fixture.coordinator.start()
            advanceUntilIdle()

            fixture.coordinator.onTokenRotated("fcm-token-rotated")
            advanceUntilIdle()

            assertEquals(0, fixture.repository.registerCalls.size)
        }

    @Test
    fun `register retries with exponential backoff 5s 30s 2m 8m after failure`() =
        runTest {
            val fixture = newFixture(initialState = SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            fixture.repository.registerFailures = 4 // first four attempts fail, fifth succeeds

            fixture.coordinator.start()
            runCurrent()

            // Attempt 1 (immediate)
            assertEquals(1, fixture.repository.registerCalls.size)

            // Inter-attempt delays: 5s, 30s, 2m, 8m
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(2, fixture.repository.registerCalls.size)

            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(3, fixture.repository.registerCalls.size)

            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(4, fixture.repository.registerCalls.size)

            advanceTimeBy(480_000)
            runCurrent()
            assertEquals(5, fixture.repository.registerCalls.size)

            // Attempt 5 succeeds → store is Succeeded.
            assertEquals(PushRegistrationState.Status.Succeeded, fixture.store.read().status)
        }

    @Test
    fun `register stops retrying after five attempts and writes Failed to the store`() =
        runTest {
            val fixture = newFixture(initialState = SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            fixture.repository.registerFailures = Int.MAX_VALUE // every attempt fails

            fixture.coordinator.start()
            advanceUntilIdle()

            assertEquals(5, fixture.repository.registerCalls.size, "must cap at five attempts (one immediate + four delayed)")
            assertEquals(PushRegistrationState.Status.Failed, fixture.store.read().status)
        }

    @Test
    fun `register failure marks store Pending mid-flight so an out-of-band reader sees the in-flight attempt`() =
        runTest {
            val fixture = newFixture(initialState = SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            fixture.repository.registerFailures = Int.MAX_VALUE

            fixture.coordinator.start()
            // After the first call but before the 5s delay elapses, the store should
            // reflect an in-flight attempt with status Pending so cold readers
            // (e.g. a debug surface) don't misread "no register has ever been tried."
            runCurrent()
            assertEquals(
                PushRegistrationState(
                    accountDid = viewerDid,
                    fcmToken = fcmToken,
                    status = PushRegistrationState.Status.Pending,
                ),
                fixture.store.read(),
            )
            // Drain the backoff schedule.
            advanceUntilIdle()
        }

    @Test
    fun `SignedOut mid-backoff preempts the register loop and runs unregister immediately`() =
        runTest {
            // Without collectLatest + cancellable registerJob, SignedOut would queue behind
            // the in-flight delay(5_000) / delay(30_000) / etc. and the unregister + store
            // clear wouldn't run until the backoff schedule completed (or hit the cap).
            val sessionFlow = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            val fixture = newFixture(sessionFlow = sessionFlow)
            fixture.repository.registerFailures = Int.MAX_VALUE // every attempt fails

            fixture.coordinator.start()
            runCurrent()
            // First attempt has fired; the loop is now suspended in delay(5_000).
            assertEquals(1, fixture.repository.registerCalls.size)
            assertEquals(PushRegistrationState.Status.Pending, fixture.store.read().status)

            // Sign out partway through the first delay — virtual time hasn't advanced past it.
            sessionFlow.value = SessionState.SignedOut
            runCurrent()

            // Register loop must have been cancelled (no second register call) and
            // the unregister + store clear must have run on the SignedOut path.
            assertEquals(1, fixture.repository.registerCalls.size, "register loop must be cancelled mid-backoff")
            assertEquals(1, fixture.repository.unregisterCalls.size)
            assertEquals(PushRegistrationState.Default, fixture.store.read())
        }

    @Test
    fun `onTokenRotated mid-backoff cancels the in-flight register and starts a fresh one with the new token`() =
        runTest {
            // Without the single-flight registerJob serialization, the old-token register
            // would keep running in the background and race with the new-token register
            // for PushRegistrationStateStore writes.
            val fixture = newFixture(initialState = SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            fixture.repository.registerFailures = Int.MAX_VALUE // old-token attempts all fail; we'll flip this before the new-token call

            fixture.coordinator.start()
            runCurrent()
            // First old-token attempt fired; loop now suspended in delay(5_000).
            assertEquals(listOf(viewerDid to fcmToken), fixture.repository.registerCalls)

            // Rotate the token. The new-token register should succeed immediately.
            fixture.repository.registerFailures = 0
            fixture.coordinator.onTokenRotated("fcm-token-rotated")
            advanceUntilIdle()

            // The old-token loop was cancelled before any further attempts; exactly one
            // new-token attempt fires and succeeds. No interleaved writes mean the store
            // settles cleanly on the new token.
            val newTokenCalls = fixture.repository.registerCalls.count { it == viewerDid to "fcm-token-rotated" }
            val oldTokenCalls = fixture.repository.registerCalls.count { it == viewerDid to fcmToken }
            assertEquals(1, oldTokenCalls, "old-token register loop must have been cancelled after the first attempt")
            assertEquals(1, newTokenCalls, "new-token register should fire exactly once")
            assertEquals(
                PushRegistrationState(
                    accountDid = viewerDid,
                    fcmToken = "fcm-token-rotated",
                    status = PushRegistrationState.Status.Succeeded,
                ),
                fixture.store.read(),
            )
        }

    private data class Fixture(
        val coordinator: PushRegistrationCoordinator,
        val repository: FakePushRegistrationRepository,
        val store: PushRegistrationStateStore,
    )

    private fun TestScope.newFixture(
        initialState: SessionState = SessionState.Loading,
        sessionFlow: MutableStateFlow<SessionState> = MutableStateFlow(initialState),
    ): Fixture {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(testScheduler + dispatcher),
                produceFile = { File(tempDir, "coordinator-${System.nanoTime()}.preferences_pb") },
            )
        val store = PushRegistrationStateStore(dataStore)
        val repository = FakePushRegistrationRepository()
        val coordinator =
            PushRegistrationCoordinator(
                sessionStateProvider = FakeSessionStateProvider(sessionFlow),
                repository = repository,
                stateStore = store,
                tokenProvider = StaticFcmTokenProvider(fcmToken),
                fcmAutoInit = NoopFcmAutoInit,
                scope = CoroutineScope(testScheduler + dispatcher),
            )
        return Fixture(coordinator, repository, store)
    }

    private class FakeSessionStateProvider(
        override val state: MutableStateFlow<SessionState>,
    ) : SessionStateProvider {
        override suspend fun refresh() {
            // no-op: tests drive the StateFlow directly
        }
    }

    private class StaticFcmTokenProvider(
        private val token: String,
    ) : FcmTokenProvider {
        override suspend fun current(): String = token
    }

    private object NoopFcmAutoInit : FcmAutoInit {
        // Production FirebaseFcmAutoInit calls FirebaseMessaging.getInstance()
        // which hits android.os.Process.myPid — unavailable under the AGP
        // unit-test stub framework. Tests substitute this no-op since the
        // coordinator's contract under test is the session-state collection
        // + register/unregister behavior, not the manifest-disabled-auto-init
        // re-enable side effect.
        override fun enable() = Unit
    }

    private class FakePushRegistrationRepository : PushRegistrationRepository {
        val registerCalls = mutableListOf<Pair<String, String>>()
        val unregisterCalls = mutableListOf<Pair<String, String>>()
        var registerFailures = 0
        var unregisterFailures = 0

        override suspend fun register(
            did: String,
            fcmToken: String,
        ): Result<Unit> {
            registerCalls += did to fcmToken
            return if (registerFailures > 0) {
                registerFailures--
                Result.failure(RuntimeException("simulated register failure"))
            } else {
                Result.success(Unit)
            }
        }

        override suspend fun unregister(
            did: String,
            fcmToken: String,
        ): Result<Unit> {
            unregisterCalls += did to fcmToken
            return if (unregisterFailures > 0) {
                unregisterFailures--
                Result.failure(RuntimeException("simulated unregister failure"))
            } else {
                Result.success(Unit)
            }
        }
    }
}
