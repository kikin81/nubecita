package net.kikin.nubecita.feature.profile.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class SettingsStubViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    /**
     * Default session + profile mocks for tests that only exercise the
     * sign-out flow. Session returns SignedOut so the VM's init block
     * skips the profile-fetch path; profile mock stays unused. Tests
     * that need to assert on header state can override either.
     */
    private fun createVm(
        auth: AuthRepository,
        session: SessionStateProvider =
            mockk(relaxed = true) {
                every { state } returns MutableStateFlow(SessionState.SignedOut)
            },
        profile: ProfileRepository = mockk(relaxed = true),
    ) = SettingsStubViewModel(
        authRepository = auth,
        sessionStateProvider = session,
        profileRepository = profile,
    )

    @Test
    fun `SignOutTapped opens the confirmation dialog`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = mockk<AuthRepository>(relaxed = true)
            val vm = createVm(auth = auth)

            vm.handleEvent(SettingsStubEvent.SignOutTapped)

            assertTrue(vm.uiState.value.confirmDialogOpen)
            assertEquals(SettingsStubStatus.Idle, vm.uiState.value.status)
        }

    @Test
    fun `DismissDialog closes the confirmation dialog`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = mockk<AuthRepository>(relaxed = true)
            val vm = createVm(auth = auth)
            vm.handleEvent(SettingsStubEvent.SignOutTapped)
            assertTrue(vm.uiState.value.confirmDialogOpen)

            vm.handleEvent(SettingsStubEvent.DismissDialog)

            assertFalse(vm.uiState.value.confirmDialogOpen)
        }

    @Test
    fun `ConfirmSignOut transitions to SigningOut and calls AuthRepository_signOut`() =
        runTest(mainDispatcher.dispatcher) {
            val auth =
                mockk<AuthRepository> {
                    coEvery { signOut() } returns Result.success(Unit)
                }
            val vm = createVm(auth = auth)
            vm.handleEvent(SettingsStubEvent.SignOutTapped)

            vm.handleEvent(SettingsStubEvent.ConfirmSignOut)
            advanceUntilIdle()

            coVerify(exactly = 1) { auth.signOut() }
            assertEquals(SettingsStubStatus.SigningOut, vm.uiState.value.status)
        }

    @Test
    fun `sign-out failure resets to Idle, closes dialog, emits ShowSignOutError`() =
        runTest(mainDispatcher.dispatcher) {
            val auth =
                mockk<AuthRepository> {
                    coEvery { signOut() } returns Result.failure(IOException("net down"))
                }
            val vm = createVm(auth = auth)
            vm.handleEvent(SettingsStubEvent.SignOutTapped)

            vm.effects.test {
                vm.handleEvent(SettingsStubEvent.ConfirmSignOut)
                advanceUntilIdle()
                assertEquals(SettingsStubEffect.ShowSignOutError, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(SettingsStubStatus.Idle, vm.uiState.value.status)
            assertFalse(vm.uiState.value.confirmDialogOpen)
        }

    @Test
    fun `double ConfirmSignOut is single-flight — only one signOut call`() =
        runTest(mainDispatcher.dispatcher) {
            val auth =
                mockk<AuthRepository> {
                    coEvery { signOut() } coAnswers {
                        kotlinx.coroutines.delay(1_000)
                        Result.success(Unit)
                    }
                }
            val vm = createVm(auth = auth)
            vm.handleEvent(SettingsStubEvent.SignOutTapped)

            vm.handleEvent(SettingsStubEvent.ConfirmSignOut)
            vm.handleEvent(SettingsStubEvent.ConfirmSignOut)
            advanceUntilIdle()

            coVerify(exactly = 1) { auth.signOut() }
        }
}
