package net.kikin.nubecita.feature.login.impl

import io.github.kikin81.atproto.oauth.OAuthDiscoveryException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.Login
import net.kikin.nubecita.core.analytics.NoOpAnalyticsClient
import net.kikin.nubecita.core.analytics.UserProperty
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
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
internal class LoginViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `initial state is empty handle, not loading, no error`() {
        val vm = newViewModel()
        val state = vm.uiState.value
        assertEquals("", state.handle)
        assertEquals(false, state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `HandleChanged updates handle and clears errorMessage`() {
        val vm = newViewModel()

        vm.handleEvent(LoginEvent.SubmitLogin)
        assertEquals(LoginError.BlankHandle, vm.uiState.value.errorMessage)

        vm.handleEvent(LoginEvent.HandleChanged("alice"))
        assertEquals("alice", vm.uiState.value.handle)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `blank-handle SubmitLogin emits BlankHandle error without calling repository`() {
        val fake = FakeAuthRepository(beginLoginResult = Result.success("never returned"))
        val vm = newViewModel(authRepository = fake)

        vm.handleEvent(LoginEvent.SubmitLogin)

        val state = vm.uiState.value
        assertEquals(LoginError.BlankHandle, state.errorMessage)
        assertEquals(false, state.isLoading)
        assertEquals(0, fake.beginLoginInvocations)
    }

    @Test
    fun `whitespace-only handle is treated as blank`() {
        val vm = newViewModel()
        vm.handleEvent(LoginEvent.HandleChanged("   "))
        vm.handleEvent(LoginEvent.SubmitLogin)
        assertEquals(LoginError.BlankHandle, vm.uiState.value.errorMessage)
    }

    @Test
    fun `successful beginLogin emits LaunchCustomTab and clears loading`() =
        runTest(mainDispatcher.dispatcher) {
            val url = "https://bsky.social/oauth/authorize?req=uri:abc"
            val vm = newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.success(url)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            val effect = vm.effects.first()
            assertTrue(effect is LoginEffect.LaunchCustomTab)
            assertEquals(url, (effect as LoginEffect.LaunchCustomTab).url)

            val state = vm.uiState.value
            assertEquals(false, state.isLoading)
            assertNull(state.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException 'Failed to resolve handle' maps to HandleNotFound carrying the handle`() =
        runTest(mainDispatcher.dispatcher) {
            // Pin the upstream message: this prefix is what DiscoveryChain.resolveHandle
            // throws when neither DNS-over-HTTPS nor the HTTP fallback returns a DID.
            // If atproto-kotlin grows a typed HandleNotFoundException, replace both the
            // VM mapping and this test together.
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(
                            beginLoginResult =
                                Result.failure(
                                    OAuthDiscoveryException(
                                        "Failed to resolve handle 'alise.bsky.social': " +
                                            "neither DNS TXT record (_atproto.alise.bsky.social) " +
                                            "nor HTTP (https://alise.bsky.social/.well-known/atproto-did) " +
                                            "returned a valid DID",
                                    ),
                                ),
                        ),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alise.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(false, state.isLoading)
            assertEquals(LoginError.HandleNotFound("alise.bsky.social"), state.errorMessage)

            val effect = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(effect)
        }

    @Test
    fun `direct IOException maps to Network`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(beginLoginResult = Result.failure(IOException("no network"))),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `UnknownHostException maps to Network`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(beginLoginResult = Result.failure(UnknownHostException("no such host"))),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `SocketTimeoutException maps to Network`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(beginLoginResult = Result.failure(SocketTimeoutException("timed out"))),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `OAuthDiscoveryException wrapping IOException maps to Network via cause-walk`() =
        runTest(mainDispatcher.dispatcher) {
            val wrapped =
                OAuthDiscoveryException(
                    "Failed to fetch resource server metadata from https://example.com",
                    IOException("connect timed out"),
                )
            val vm =
                newViewModel(authRepository = FakeAuthRepository(beginLoginResult = Result.failure(wrapped)))
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }

    @Test
    fun `unknown Throwable maps to Generic and does not leak the library message`() =
        runTest(mainDispatcher.dispatcher) {
            val leakySubstring = "authorization_endpoint missing from auth server metadata"
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(beginLoginResult = Result.failure(IllegalStateException(leakySubstring))),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            val errorMessage = vm.uiState.value.errorMessage
            assertEquals(LoginError.Generic, errorMessage)
            // LoginError.Generic carries no payload, so the library message can't
            // round-trip through the sum; this assertion is belt-and-braces against
            // a future regression that adds a payload field.
            assertFalse(errorMessage.toString().contains(leakySubstring))
        }

    @Test
    fun `OpenSignup emits LaunchCustomTab(bsky_app_signup) without mutating state`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newViewModel()
            vm.handleEvent(LoginEvent.HandleChanged("alice.bsky.social"))
            val before = vm.uiState.value

            vm.handleEvent(LoginEvent.OpenSignup)
            advanceUntilIdle()

            val effect = vm.effects.first()
            assertTrue(effect is LoginEffect.LaunchCustomTab)
            assertEquals("https://bsky.app/", (effect as LoginEffect.LaunchCustomTab).url)

            // state is untouched: same handle, not loading, no error.
            assertEquals(before, vm.uiState.value)
        }

    @Test
    fun `ClearError nulls errorMessage`() {
        val vm = newViewModel()
        vm.handleEvent(LoginEvent.SubmitLogin)
        assertEquals(LoginError.BlankHandle, vm.uiState.value.errorMessage)

        vm.handleEvent(LoginEvent.ClearError)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `broker emission with successful completeLogin emits LoginSucceeded`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.success(Unit))
            val vm = newViewModel(authRepository = auth, broker = broker)
            advanceUntilIdle() // let init-time collector subscribe

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            val effect = vm.effects.first()
            // The fixture's default decider returns shouldPrompt = false (sdkInt = 0),
            // so the LoginSucceeded effect carries requestPostNotificationsPermission = false.
            // The true-branch is exercised separately in LoginPostNotificationsPromptTest.
            assertEquals(LoginEffect.LoginSucceeded(requestPostNotificationsPermission = false), effect)
            assertEquals(1, auth.completeLoginInvocations)
            assertNull(vm.uiState.value.errorMessage)
        }

    @Test
    fun `successful completeLogin logs the login event once`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.success(Unit))
            val analytics = RecordingAnalyticsClient()
            newViewModel(authRepository = auth, broker = broker, analytics = analytics)
            advanceUntilIdle() // let init-time collector subscribe

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            assertEquals(listOf(Login()), analytics.events)
        }

    @Test
    fun `failed completeLogin logs no login event`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.failure(java.io.IOException("net")))
            val analytics = RecordingAnalyticsClient()
            newViewModel(authRepository = auth, broker = broker, analytics = analytics)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
        }

    @Test
    fun `broker emission with generic failure populates errorMessage as Generic and emits no effect`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth =
                FakeAuthRepository(
                    completeLoginResult = Result.failure(IllegalStateException("invalid code")),
                )
            val vm = newViewModel(authRepository = auth, broker = broker)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=bad")
            advanceUntilIdle()

            assertEquals(LoginError.Generic, vm.uiState.value.errorMessage)
            assertEquals(false, vm.uiState.value.isLoading)

            val effect = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(effect)
        }

    @Test
    fun `broker emission with network failure populates errorMessage as Network`() =
        runTest(mainDispatcher.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.failure(IOException("connect failed")))
            val vm = newViewModel(authRepository = auth, broker = broker)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=bad")
            advanceUntilIdle()

            assertEquals(LoginError.Network, vm.uiState.value.errorMessage)
        }
}

private fun newViewModel(
    authRepository: AuthRepository = FakeAuthRepository(),
    broker: OAuthRedirectBroker = FakeOAuthRedirectBroker(),
    notificationsPromptDecider: NotificationsPromptDecider =
        NotificationsPromptDecider(NoopPromptStore, sdkInt = 0),
    analytics: AnalyticsClient = NoOpAnalyticsClient(),
): LoginViewModel =
    LoginViewModel(
        authRepository = authRepository,
        notificationsPromptDecider = notificationsPromptDecider,
        analytics = analytics,
        broker = broker,
    )

// The base LoginViewModel tests don't exercise the POST_NOTIFICATIONS prompt
// gating — the dedicated [LoginPostNotificationsPromptTest] covers that. Use
// a decider built on top of this no-op store + `sdkInt = 0` so `shouldPrompt`
// returns false (below TIRAMISU) and the broker-success test sees exactly one
// effect (LoginSucceeded) instead of two.
private object NoopPromptStore : NotificationsPromptShownStore {
    override suspend fun read(): Boolean = false

    override suspend fun markShown() = Unit
}

private class FakeAuthRepository(
    private val beginLoginResult: Result<String> = Result.success("ignored"),
    private val completeLoginResult: Result<Unit> = Result.success(Unit),
) : AuthRepository {
    var beginLoginInvocations: Int = 0
        private set
    var completeLoginInvocations: Int = 0
        private set

    override suspend fun beginLogin(handle: String): Result<String> {
        beginLoginInvocations++
        return beginLoginResult
    }

    override suspend fun completeLogin(redirectUri: String): Result<Unit> {
        completeLoginInvocations++
        return completeLoginResult
    }

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}

private class FakeOAuthRedirectBroker : OAuthRedirectBroker {
    private val channel = Channel<String>(Channel.BUFFERED)
    override val redirects: Flow<String> = channel.receiveAsFlow()

    override suspend fun publish(redirectUri: String) {
        channel.send(redirectUri)
    }

    suspend fun emit(redirectUri: String) {
        channel.send(redirectUri)
    }
}

private class RecordingAnalyticsClient : AnalyticsClient {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(property: UserProperty) = Unit

    override fun logScreen(screen: AnalyticsScreen) = Unit
}
