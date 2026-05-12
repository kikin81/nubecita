package net.kikin.nubecita.feature.profile.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
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

    @Test
    fun `SignOutTapped opens the confirmation dialog`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = mockk<AuthRepository>(relaxed = true)
            val vm = SettingsStubViewModel(authRepository = auth)

            vm.handleEvent(SettingsStubEvent.SignOutTapped)

            assertTrue(vm.uiState.value.confirmDialogOpen)
            assertEquals(SettingsStubStatus.Idle, vm.uiState.value.status)
        }

    @Test
    fun `DismissDialog closes the confirmation dialog`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = mockk<AuthRepository>(relaxed = true)
            val vm = SettingsStubViewModel(authRepository = auth)
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
            val vm = SettingsStubViewModel(authRepository = auth)
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
            val vm = SettingsStubViewModel(authRepository = auth)
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
            val vm = SettingsStubViewModel(authRepository = auth)
            vm.handleEvent(SettingsStubEvent.SignOutTapped)

            vm.handleEvent(SettingsStubEvent.ConfirmSignOut)
            vm.handleEvent(SettingsStubEvent.ConfirmSignOut)
            advanceUntilIdle()

            coVerify(exactly = 1) { auth.signOut() }
        }
}
