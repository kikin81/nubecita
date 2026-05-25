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
    fun `first login on API-33 plus emits the permission-prompt effect, then LoginSucceeded, and marks the gate`() =
        runTest(mainDispatcher.dispatcher) {
            val store = PromptTestNotificationsPromptShownStore(initialShown = false)
            val decider = NotificationsPromptDecider(store, sdkInt = Build.VERSION_CODES.TIRAMISU)
            val broker = PromptTestOAuthRedirectBroker()
            val auth = PromptTestAuthRepository(completeLoginResult = Result.success(Unit))
            val vm = newViewModel(authRepository = auth, broker = broker, decider = decider)
            advanceUntilIdle() // let init-time collector subscribe

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            // Effect order: the prompt fires FIRST so the system dialog is requested
            // before MainActivity's SignedIn observer tears down LoginScreen on the
            // subsequent recomposition. LoginSucceeded follows so the existing
            // exhaustive-when branch in LoginScreen.kt keeps working.
            val first = vm.effects.first()
            assertEquals(LoginEffect.RequestPostNotificationsPermission, first)

            val second = vm.effects.first()
            assertEquals(LoginEffect.LoginSucceeded, second)

            // Gate flipped — the next login on this install must NOT re-prompt
            // even if the user denied the system dialog. Re-prompting would
            // make this look like an OS bug to the user.
            assertTrue(store.shown, "markShown must have flipped the persisted gate")
            assertEquals(1, store.markShownCalls)
        }

    @Test
    fun `second login on API-33 plus skips the prompt and still emits LoginSucceeded`() =
        runTest(mainDispatcher.dispatcher) {
            val store = PromptTestNotificationsPromptShownStore(initialShown = true)
            val decider = NotificationsPromptDecider(store, sdkInt = Build.VERSION_CODES.TIRAMISU)
            val broker = PromptTestOAuthRedirectBroker()
            val auth = PromptTestAuthRepository(completeLoginResult = Result.success(Unit))
            val vm = newViewModel(authRepository = auth, broker = broker, decider = decider)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            // Only LoginSucceeded — no permission-prompt effect this time.
            val effect = vm.effects.first()
            assertEquals(LoginEffect.LoginSucceeded, effect)

            // Defensive: confirm there is no buffered second effect lurking.
            val extra = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(extra, "second login must not buffer a second effect")

            assertEquals(0, store.markShownCalls, "markShown must NOT be called when the gate was already shown")
            assertTrue(store.shown, "the pre-existing flag must remain true")
        }

    @Test
    fun `login on pre-API-33 device skips the prompt and does not flip the gate`() =
        runTest(mainDispatcher.dispatcher) {
            // Android 12 (API 32) and earlier auto-grant POST_NOTIFICATIONS at
            // install time. Triggering the runtime prompt is impossible (and a
            // launch() call would no-op anyway). Skip the effect entirely so
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
            assertEquals(LoginEffect.LoginSucceeded, effect)

            val extra = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(extra, "pre-API-33 must not buffer a prompt effect")

            assertEquals(0, store.markShownCalls)
            assertFalse(store.shown, "the gate must stay false so a future Android upgrade still gets a prompt")
        }

    @Test
    fun `failed completeLogin does NOT emit the permission-prompt effect`() =
        runTest(mainDispatcher.dispatcher) {
            // Defensive: the prompt belongs only on the success branch — emitting
            // it on a failed login would look like an OS misfire to the user
            // (they declined to give us a session, why are we asking for
            // notifications now?).
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
            assertNull(extra, "failed completeLogin must not emit any effect (including the prompt)")
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
) : NotificationsPromptShownStore {
    var shown: Boolean = initialShown
        private set
    var markShownCalls: Int = 0
        private set

    override suspend fun read(): Boolean = shown

    override suspend fun markShown() {
        markShownCalls++
        shown = true
    }
}
