package net.kikin.nubecita.feature.settings.impl

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
import net.kikin.nubecita.feature.settings.impl.data.SettingsAccountHeader
import net.kikin.nubecita.feature.settings.impl.data.SettingsAccountRepository
import net.kikin.nubecita.feature.settings.impl.data.avatarHueFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class SettingsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    /**
     * Default session + account-repository mocks for tests that only
     * exercise the sign-out flow. Session returns SignedOut so the
     * VM's init block skips the profile-fetch path; account-repo mock
     * stays unused. Tests that need to assert on header state can
     * override either.
     */
    private fun createVm(
        auth: AuthRepository,
        session: SessionStateProvider =
            mockk(relaxed = true) {
                every { state } returns MutableStateFlow(SessionState.SignedOut)
            },
        account: SettingsAccountRepository = mockk(relaxed = true),
    ) = SettingsViewModel(
        authRepository = auth,
        sessionStateProvider = session,
        accountRepository = account,
    )

    @Test
    fun `SignOutTapped opens the confirmation dialog`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = mockk<AuthRepository>(relaxed = true)
            val vm = createVm(auth = auth)

            vm.handleEvent(SettingsEvent.SignOutTapped)

            assertTrue(vm.uiState.value.confirmDialogOpen)
            assertEquals(SettingsStatus.Idle, vm.uiState.value.status)
        }

    @Test
    fun `DismissDialog closes the confirmation dialog`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = mockk<AuthRepository>(relaxed = true)
            val vm = createVm(auth = auth)
            vm.handleEvent(SettingsEvent.SignOutTapped)
            assertTrue(vm.uiState.value.confirmDialogOpen)

            vm.handleEvent(SettingsEvent.DismissDialog)

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
            vm.handleEvent(SettingsEvent.SignOutTapped)

            vm.handleEvent(SettingsEvent.ConfirmSignOut)
            advanceUntilIdle()

            coVerify(exactly = 1) { auth.signOut() }
            assertEquals(SettingsStatus.SigningOut, vm.uiState.value.status)
        }

    @Test
    fun `sign-out failure resets to Idle, closes dialog, emits ShowSignOutError`() =
        runTest(mainDispatcher.dispatcher) {
            val auth =
                mockk<AuthRepository> {
                    coEvery { signOut() } returns Result.failure(IOException("net down"))
                }
            val vm = createVm(auth = auth)
            vm.handleEvent(SettingsEvent.SignOutTapped)

            vm.effects.test {
                vm.handleEvent(SettingsEvent.ConfirmSignOut)
                advanceUntilIdle()
                assertEquals(SettingsEffect.ShowSignOutError, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(SettingsStatus.Idle, vm.uiState.value.status)
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
            vm.handleEvent(SettingsEvent.SignOutTapped)

            vm.handleEvent(SettingsEvent.ConfirmSignOut)
            vm.handleEvent(SettingsEvent.ConfirmSignOut)
            advanceUntilIdle()

            coVerify(exactly = 1) { auth.signOut() }
        }

    @Test
    fun `ManageAccountTapped emits LaunchUri pointing at the hosted web settings`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = createVm(auth = mockk(relaxed = true))

            vm.effects.test {
                vm.handleEvent(SettingsEvent.ManageAccountTapped)
                advanceUntilIdle()
                assertEquals(
                    SettingsEffect.LaunchUri(uri = "https://bsky.app/settings"),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SwitchAccountTapped emits ShowSwitchAccountComingSoon`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = createVm(auth = mockk(relaxed = true))

            vm.effects.test {
                vm.handleEvent(SettingsEvent.SwitchAccountTapped)
                advanceUntilIdle()
                assertEquals(SettingsEffect.ShowSwitchAccountComingSoon, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `init observes session flow and populates handle plus header on SignedIn`() =
        runTest(mainDispatcher.dispatcher) {
            val signedIn = SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice")
            val session =
                mockk<SessionStateProvider>(relaxed = true) {
                    every { state } returns MutableStateFlow(signedIn)
                }
            val account =
                mockk<SettingsAccountRepository> {
                    coEvery { fetchHeader("did:plc:alice") } returns
                        Result.success(
                            SettingsAccountHeader(
                                displayName = "Alice Anderson",
                                avatarUrl = "https://cdn.example/alice.jpg",
                            ),
                        )
                }
            val vm = createVm(auth = mockk(relaxed = true), session = session, account = account)

            // Handle + avatarHue (computed via avatarHueFor from did + handle)
            // lands first from the flow's emission. displayName + avatarUrl
            // arrive after fetchHeader resolves. advanceUntilIdle drains
            // both turns.
            advanceUntilIdle()
            assertEquals("alice.bsky.social", vm.uiState.value.handle)
            // avatarHue is the deterministic 0–359 value for this (did, handle)
            // pair — same helper used by AuthorProfileMapper/ConvoMapper, so
            // the same user paints identically across Settings/Profile/Chats.
            assertEquals(
                avatarHueFor(did = "did:plc:alice", handle = "alice.bsky.social"),
                vm.uiState.value.avatarHue,
            )
            assertEquals("Alice Anderson", vm.uiState.value.displayName)
            assertEquals("https://cdn.example/alice.jpg", vm.uiState.value.avatarUrl)
            coVerify(exactly = 1) { account.fetchHeader("did:plc:alice") }
        }

    @Test
    fun `init with SignedIn keeps displayName and avatarUrl null on fetch failure`() =
        runTest(mainDispatcher.dispatcher) {
            val signedIn = SessionState.SignedIn(handle = "bob.bsky.social", did = "did:plc:bob")
            val session =
                mockk<SessionStateProvider>(relaxed = true) {
                    every { state } returns MutableStateFlow(signedIn)
                }
            val account =
                mockk<SettingsAccountRepository> {
                    coEvery { fetchHeader("did:plc:bob") } returns
                        Result.failure(IOException("net down"))
                }
            val vm = createVm(auth = mockk(relaxed = true), session = session, account = account)

            advanceUntilIdle()
            // Handle is populated by the flow's first emission.
            assertEquals("bob.bsky.social", vm.uiState.value.handle)
            // Silent failure on the header fetch: displayName / avatarUrl
            // stay null and no effect is emitted (the header still
            // renders, with "Hi!" + initials disc).
            assertNull(vm.uiState.value.displayName)
            assertNull(vm.uiState.value.avatarUrl)
        }

    @Test
    fun `init with SignedOut skips fetchHeader entirely`() =
        runTest(mainDispatcher.dispatcher) {
            // The default createVm helper provides a SessionState.SignedOut mock.
            val account = mockk<SettingsAccountRepository>(relaxed = true)
            val vm = createVm(auth = mockk(relaxed = true), account = account)
            advanceUntilIdle()

            assertNull(vm.uiState.value.handle)
            assertNull(vm.uiState.value.displayName)
            assertNull(vm.uiState.value.avatarUrl)
            coVerify(exactly = 0) { account.fetchHeader(any()) }
        }
}
