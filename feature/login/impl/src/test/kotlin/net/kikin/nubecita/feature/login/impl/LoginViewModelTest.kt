package net.kikin.nubecita.feature.login.impl

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import net.kikin.nubecita.core.auth.AuthRepository
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
        val vm = LoginViewModel(FakeAuthRepository(Result.success("ignored")))
        val state = vm.uiState.value
        assertEquals("", state.handle)
        assertEquals(false, state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `HandleChanged updates handle and clears errorMessage`() {
        val vm = LoginViewModel(FakeAuthRepository(Result.success("ignored")))

        // Seed an error first via blank-handle submission, then verify HandleChanged clears it.
        vm.handleEvent(LoginEvent.SubmitLogin)
        assertEquals(LoginError.BlankHandle, vm.uiState.value.errorMessage)

        vm.handleEvent(LoginEvent.HandleChanged("alice"))
        assertEquals("alice", vm.uiState.value.handle)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `blank-handle SubmitLogin emits BlankHandle error without calling repository`() {
        val fake = FakeAuthRepository(Result.success("never returned"))
        val vm = LoginViewModel(fake)

        vm.handleEvent(LoginEvent.SubmitLogin)

        val state = vm.uiState.value
        assertEquals(LoginError.BlankHandle, state.errorMessage)
        assertEquals(false, state.isLoading)
        assertEquals(0, fake.beginLoginInvocations)
    }

    @Test
    fun `whitespace-only handle is treated as blank`() {
        val vm = LoginViewModel(FakeAuthRepository(Result.success("ignored")))
        vm.handleEvent(LoginEvent.HandleChanged("   "))
        vm.handleEvent(LoginEvent.SubmitLogin)
        assertEquals(LoginError.BlankHandle, vm.uiState.value.errorMessage)
    }

    @Test
    fun `successful beginLogin emits LaunchCustomTab and clears loading`() =
        runTest(mainDispatcherRule.dispatcher) {
            val url = "https://bsky.social/oauth/authorize?req=uri:abc"
            val vm = LoginViewModel(FakeAuthRepository(Result.success(url)))
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
                LoginViewModel(
                    FakeAuthRepository(Result.failure(IllegalStateException("Handle could not be resolved"))),
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
    fun `failure with blank message produces Failure carrying null cause`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                LoginViewModel(
                    FakeAuthRepository(Result.failure(RuntimeException(""))),
                )
            vm.handleEvent(LoginEvent.HandleChanged("alice"))
            vm.handleEvent(LoginEvent.SubmitLogin)
            advanceUntilIdle()

            // Empty exception messages flow through verbatim — the screen treats blank as "use the
            // generic-fallback resource string." Putting the string substitution here would couple
            // the VM to Android resources.
            assertEquals(LoginError.Failure(""), vm.uiState.value.errorMessage)
        }

    @Test
    fun `ClearError nulls errorMessage`() {
        val vm = LoginViewModel(FakeAuthRepository(Result.success("ignored")))
        vm.handleEvent(LoginEvent.SubmitLogin) // blank-handle error
        assertEquals(LoginError.BlankHandle, vm.uiState.value.errorMessage)

        vm.handleEvent(LoginEvent.ClearError)
        assertNull(vm.uiState.value.errorMessage)
    }
}

private class FakeAuthRepository(
    private val result: Result<String>,
) : AuthRepository {
    var beginLoginInvocations: Int = 0
        private set

    override suspend fun beginLogin(handle: String): Result<String> {
        beginLoginInvocations++
        return result
    }
}
