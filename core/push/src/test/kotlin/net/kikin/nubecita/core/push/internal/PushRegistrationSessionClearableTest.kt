package net.kikin.nubecita.core.push.internal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.push.FcmAutoInit
import net.kikin.nubecita.core.push.FcmTokenProvider
import net.kikin.nubecita.core.push.PushRegistrationCoordinator
import net.kikin.nubecita.core.push.PushRegistrationRepository
import net.kikin.nubecita.core.push.PushRegistrationState
import net.kikin.nubecita.core.push.PushRegistrationStateStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Verifies the SessionClearable contributed by :core:push routes through to
 * [PushRegistrationCoordinator.signOut] — the entry point that performs the
 * unregisterPush call BEFORE atproto-kotlin's `atOAuth.logout()` revokes the
 * OAuth tokens. Regression coverage for nubecita-1fy.8.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrationSessionClearableTest {
    @TempDir
    lateinit var tempDir: File

    private val viewerDid = "did:plc:viewer123"
    private val fcmToken = "fcm-token-abc"

    @Test
    fun `clearSession unregisters and clears the store when a session was registered`() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val scope = CoroutineScope(testScheduler + dispatcher)
            val dataStore: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { File(tempDir, "clearable-${System.nanoTime()}.preferences_pb") },
                )
            val store = PushRegistrationStateStore(dataStore)
            val sessionFlow =
                MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))
            val repository = FakeRepository()
            val coordinator =
                PushRegistrationCoordinator(
                    sessionStateProvider = FakeSessionStateProvider(sessionFlow),
                    repository = repository,
                    stateStore = store,
                    tokenProvider = StaticFcmTokenProvider(fcmToken),
                    fcmAutoInit = NoopFcmAutoInit,
                    scope = scope,
                )
            coordinator.start()
            advanceUntilIdle()
            // Sanity: the register call landed.
            assertEquals(1, repository.registerCalls.size)

            val clearable = PushRegistrationSessionClearable(coordinator)
            clearable.clearSession()
            advanceUntilIdle()

            assertEquals(listOf(viewerDid to fcmToken), repository.unregisterCalls)
            assertEquals(PushRegistrationState.Default, store.read())
        }

    @Test
    fun `clearSession on an empty store is a no-op`() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val scope = CoroutineScope(testScheduler + dispatcher)
            val dataStore: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { File(tempDir, "clearable-empty-${System.nanoTime()}.preferences_pb") },
                )
            val store = PushRegistrationStateStore(dataStore)
            val sessionFlow = MutableStateFlow<SessionState>(SessionState.Loading)
            val repository = FakeRepository()
            val coordinator =
                PushRegistrationCoordinator(
                    sessionStateProvider = FakeSessionStateProvider(sessionFlow),
                    repository = repository,
                    stateStore = store,
                    tokenProvider = StaticFcmTokenProvider(fcmToken),
                    fcmAutoInit = NoopFcmAutoInit,
                    scope = scope,
                )

            val clearable = PushRegistrationSessionClearable(coordinator)
            clearable.clearSession()
            advanceUntilIdle()

            assertEquals(0, repository.unregisterCalls.size)
            assertEquals(PushRegistrationState.Default, store.read())
        }

    private class FakeSessionStateProvider(
        override val state: MutableStateFlow<SessionState>,
    ) : SessionStateProvider {
        override suspend fun refresh() = Unit
    }

    private class StaticFcmTokenProvider(
        private val token: String,
    ) : FcmTokenProvider {
        override suspend fun current(): String = token
    }

    private object NoopFcmAutoInit : FcmAutoInit {
        override fun enable() = Unit
    }

    private class FakeRepository : PushRegistrationRepository {
        val registerCalls = mutableListOf<Pair<String, String>>()
        val unregisterCalls = mutableListOf<Pair<String, String>>()

        override suspend fun register(
            did: String,
            fcmToken: String,
        ): Result<Unit> {
            registerCalls += did to fcmToken
            return Result.success(Unit)
        }

        override suspend fun unregister(
            did: String,
            fcmToken: String,
        ): Result<Unit> {
            unregisterCalls += did to fcmToken
            return Result.success(Unit)
        }
    }
}
