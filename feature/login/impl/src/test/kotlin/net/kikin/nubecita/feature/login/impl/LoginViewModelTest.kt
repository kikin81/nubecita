package net.kikin.nubecita.feature.login.impl

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
        runTest(mainDispatcherRule.dispatcher) {
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
    fun `failed beginLogin emits Failure error carrying the cause message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                newViewModel(
                    authRepository =
                        FakeAuthRepository(
                            beginLoginResult = Result.failure(IllegalStateException("Handle could not be resolved")),
                        ),
                )
            vm.handleEvent(LoginEvent.HandleChanged("nope.bsky.social"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(false, state.isLoading)
            assertEquals(LoginError.Failure("Handle could not be resolved"), state.errorMessage)

            val effect = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(effect)
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
        runTest(mainDispatcherRule.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth = FakeAuthRepository(completeLoginResult = Result.success(Unit))
            val vm = newViewModel(authRepository = auth, broker = broker)
            advanceUntilIdle() // let init-time collector subscribe

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=abc")
            advanceUntilIdle()

            val effect = vm.effects.first()
            assertEquals(LoginEffect.LoginSucceeded, effect)
            assertEquals(1, auth.completeLoginInvocations)
            assertNull(vm.uiState.value.errorMessage)
        }

    @Test
    fun `broker emission with failure populates errorMessage and emits no effect`() =
        runTest(mainDispatcherRule.dispatcher) {
            val broker = FakeOAuthRedirectBroker()
            val auth =
                FakeAuthRepository(
                    completeLoginResult = Result.failure(IllegalStateException("invalid code")),
                )
            val vm = newViewModel(authRepository = auth, broker = broker)
            advanceUntilIdle()

            broker.emit("net.kikin.nubecita:/oauth-redirect?code=bad")
            advanceUntilIdle()

            assertEquals(LoginError.Failure("invalid code"), vm.uiState.value.errorMessage)
            assertEquals(false, vm.uiState.value.isLoading)

            val effect = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(effect)
        }
}

private fun newViewModel(
    authRepository: AuthRepository = FakeAuthRepository(),
    broker: OAuthRedirectBroker = FakeOAuthRedirectBroker(),
): LoginViewModel = LoginViewModel(authRepository = authRepository, broker = broker)

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
