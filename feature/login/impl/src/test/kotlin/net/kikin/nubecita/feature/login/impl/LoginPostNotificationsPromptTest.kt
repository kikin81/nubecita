package net.kikin.nubecita.feature.login.impl

import android.os.Build
import io.github.kikin81.atproto.oauth.OAuthDiscoveryException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.push.NotificationsPromptDecider
import net.kikin.nubecita.core.push.NotificationsPromptShownStore
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class LoginPostNotificationsPromptTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `first login on API-33 plus emits LoginSucceeded with prompt flag true and marks the gate`() =
        runTest(mainDispatcher.dispatcher) {
            val store = PromptTestNotificationsPromptShownStore(initialShown = false)
            val decider = NotificationsPromptDecider(store, sdkInt = Build.VERSION_CODES.TIRAMISU)
            val broker = PromptTestOAuthRedirectBroker()
            val auth = PromptTestAuthRepository(completeLoginResult = Result.success(Unit))
            val vm = newViewModel(authRepository = auth, broker = broker, decider = decider)
            advanceUntilIdle() // let init-time collector subscribe

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            // Single atomic effect: LoginSucceeded carries the prompt decision in
            // its payload. The screen reads `requestPostNotificationsPermission`
            // and launches the system dialog in the same `when` branch — no
            // dependency on dispatcher-specific ordering of two emissions.
            val effect = vm.effects.first()
            assertEquals(
                LoginEffect.LoginSucceeded(requestPostNotificationsPermission = true),
                effect,
                "first login must signal the screen to launch the system permission dialog",
            )

            // Gate flipped — the next login on this install must NOT re-prompt
            // even if the user denied the system dialog. Re-prompting would
            // make this look like an OS bug to the user.
            assertTrue(store.shown, "markShown must have flipped the persisted gate")
            assertEquals(1, store.markShownCalls)
        }

    @Test
    fun `second login on API-33 plus emits LoginSucceeded with prompt flag false`() =
        runTest(mainDispatcher.dispatcher) {
            val store = PromptTestNotificationsPromptShownStore(initialShown = true)
            val decider = NotificationsPromptDecider(store, sdkInt = Build.VERSION_CODES.TIRAMISU)
            val broker = PromptTestOAuthRedirectBroker()
            val auth = PromptTestAuthRepository(completeLoginResult = Result.success(Unit))
            val vm = newViewModel(authRepository = auth, broker = broker, decider = decider)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            val effect = vm.effects.first()
            assertEquals(
                LoginEffect.LoginSucceeded(requestPostNotificationsPermission = false),
                effect,
            )

            // Defensive: confirm there is no buffered second effect lurking.
            val extra = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(extra, "second login must not buffer a second effect")

            assertEquals(0, store.markShownCalls, "markShown must NOT be called when the gate was already shown")
            assertTrue(store.shown, "the pre-existing flag must remain true")
        }

    @Test
    fun `login on pre-API-33 device emits LoginSucceeded with prompt flag false and does not flip the gate`() =
        runTest(mainDispatcher.dispatcher) {
            // Android 12 (API 32) and earlier auto-grant POST_NOTIFICATIONS at
            // install time. Triggering the runtime prompt is impossible (and a
            // launch() call would no-op anyway). Skip the flag entirely so
            // the gate stays unset — if the user later upgrades to Android 13,
            // they'll get prompted on first post-upgrade login.
            val store = PromptTestNotificationsPromptShownStore(initialShown = false)
            val decider = NotificationsPromptDecider(store, sdkInt = Build.VERSION_CODES.S_V2)
            val broker = PromptTestOAuthRedirectBroker()
            val auth = PromptTestAuthRepository(completeLoginResult = Result.success(Unit))
            val vm = newViewModel(authRepository = auth, broker = broker, decider = decider)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            val effect = vm.effects.first()
            assertEquals(
                LoginEffect.LoginSucceeded(requestPostNotificationsPermission = false),
                effect,
            )

            assertEquals(0, store.markShownCalls)
            assertFalse(store.shown, "the gate must stay false so a future Android upgrade still gets a prompt")
        }

    @Test
    fun `DataStore failure on the prompt gate must not cancel the login coroutine`() =
        runTest(mainDispatcher.dispatcher) {
            // Best-effort guard for the markPrompted() call. A DataStore IO
            // error (transient disk pressure, file lock contention, etc.)
            // arriving on the prompt-gate path must NOT cancel the
            // login-success coroutine. The session is already persisted by
            // the time we reach this branch; worst case the user can re-
            // enable notifications from system settings later.
            val store =
                PromptTestNotificationsPromptShownStore(
                    initialShown = false,
                    failOnMarkShown = true,
                )
            val decider = NotificationsPromptDecider(store, sdkInt = Build.VERSION_CODES.TIRAMISU)
            val broker = PromptTestOAuthRedirectBroker()
            val auth = PromptTestAuthRepository(completeLoginResult = Result.success(Unit))
            val vm = newViewModel(authRepository = auth, broker = broker, decider = decider)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            // VM must STILL emit LoginSucceeded — the screen can't be left
            // stranded waiting on an effect that never arrives just because
            // a persistence write failed.
            val effect = vm.effects.first()
            assertEquals(
                LoginEffect.LoginSucceeded(requestPostNotificationsPermission = false),
                effect,
                "DataStore failure on the prompt gate must degrade to 'no prompt this login', not crash",
            )

            // markShown was attempted exactly once; the failure means the
            // gate stays unset, so the next login (after whatever transient
            // condition cleared) will get a fresh chance at the prompt.
            assertEquals(1, store.markShownCalls)
            assertFalse(store.shown)
        }

    @Test
    fun `failed completeLogin does NOT emit any LoginSucceeded effect`() =
        runTest(mainDispatcher.dispatcher) {
            // Defensive: the prompt belongs only on the success branch — emitting
            // a LoginSucceeded (with or without the prompt flag) on failed login
            // would falsely signal a session to the screen.
            val store = PromptTestNotificationsPromptShownStore(initialShown = false)
            val decider = NotificationsPromptDecider(store, sdkInt = Build.VERSION_CODES.TIRAMISU)
            val broker = PromptTestOAuthRedirectBroker()
            val auth =
                PromptTestAuthRepository(
                    completeLoginResult = Result.failure(OAuthDiscoveryException("simulated failure")),
                )
            val vm = newViewModel(authRepository = auth, broker = broker, decider = decider)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            val extra = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(extra, "failed completeLogin must not emit any effect")
            assertEquals(0, store.markShownCalls)
            assertFalse(store.shown)
        }
}

private fun newViewModel(
    authRepository: AuthRepository,
    broker: OAuthRedirectBroker,
    decider: NotificationsPromptDecider,
): LoginViewModel =
    LoginViewModel(
        authRepository = authRepository,
        notificationsPromptDecider = decider,
        broker = broker,
    )

private class PromptTestAuthRepository(
    private val beginLoginResult: Result<String> = Result.success("ignored"),
    private val completeLoginResult: Result<Unit> = Result.success(Unit),
) : AuthRepository {
    override suspend fun beginLogin(handle: String): Result<String> = beginLoginResult

    override suspend fun completeLogin(redirectUri: String): Result<Unit> = completeLoginResult

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}

private class PromptTestOAuthRedirectBroker : OAuthRedirectBroker {
    private val channel = Channel<String>(Channel.BUFFERED)
    override val redirects: Flow<String> = channel.receiveAsFlow()

    override suspend fun publish(redirectUri: String) {
        channel.send(redirectUri)
    }

    suspend fun emit(redirectUri: String) {
        channel.send(redirectUri)
    }
}

private class PromptTestNotificationsPromptShownStore(
    initialShown: Boolean,
    private val failOnMarkShown: Boolean = false,
) : NotificationsPromptShownStore {
    var shown: Boolean = initialShown
        private set
    var markShownCalls: Int = 0
        private set

    override suspend fun read(): Boolean = shown

    override suspend fun markShown() {
        markShownCalls++
        if (failOnMarkShown) {
            // Mirror the kind of failure DataStore surfaces in production:
            // a runtime IOException wrapped by Preferences serialization.
            throw java.io.IOException("simulated DataStore write failure")
        }
        shown = true
    }
}
