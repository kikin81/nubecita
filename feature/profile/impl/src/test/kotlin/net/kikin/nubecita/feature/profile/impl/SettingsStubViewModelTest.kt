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
import net.kikin.nubecita.feature.profile.impl.data.ProfileHeaderWithViewer
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `ManageAccountTapped emits LaunchUri pointing at the hosted web settings`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = createVm(auth = mockk(relaxed = true))

            vm.effects.test {
                vm.handleEvent(SettingsStubEvent.ManageAccountTapped)
                advanceUntilIdle()
                assertEquals(
                    SettingsStubEffect.LaunchUri(uri = "https://bsky.app/settings"),
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
                vm.handleEvent(SettingsStubEvent.SwitchAccountTapped)
                advanceUntilIdle()
                assertEquals(SettingsStubEffect.ShowSwitchAccountComingSoon, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `init with SignedIn populates handle synchronously and fetches header`() =
        runTest(mainDispatcher.dispatcher) {
            val signedIn = SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice")
            val session =
                mockk<SessionStateProvider>(relaxed = true) {
                    every { state } returns MutableStateFlow(signedIn)
                }
            val profile =
                mockk<ProfileRepository> {
                    coEvery { fetchHeader("did:plc:alice") } returns
                        Result.success(
                            ProfileHeaderWithViewer(
                                header =
                                    ProfileHeaderUi(
                                        did = "did:plc:alice",
                                        handle = "alice.bsky.social",
                                        displayName = "Alice Anderson",
                                        avatarUrl = "https://cdn.example/alice.jpg",
                                        bannerUrl = null,
                                        avatarHue = 217,
                                        bio = null,
                                        location = null,
                                        website = null,
                                        joinedDisplay = null,
                                        postsCount = 0L,
                                        followersCount = 0L,
                                        followsCount = 0L,
                                    ),
                                viewerRelationship = ViewerRelationship.Self,
                            ),
                        )
                }
            val vm = createVm(auth = mockk(relaxed = true), session = session, profile = profile)

            // Handle lands synchronously from the SignedIn read in init.
            assertEquals("alice.bsky.social", vm.uiState.value.handle)
            // displayName + avatarUrl arrive once the fetch coroutine resolves.
            advanceUntilIdle()
            assertEquals("Alice Anderson", vm.uiState.value.displayName)
            assertEquals("https://cdn.example/alice.jpg", vm.uiState.value.avatarUrl)
            coVerify(exactly = 1) { profile.fetchHeader("did:plc:alice") }
        }

    @Test
    fun `init with SignedIn keeps displayName and avatarUrl null on fetch failure`() =
        runTest(mainDispatcher.dispatcher) {
            val signedIn = SessionState.SignedIn(handle = "bob.bsky.social", did = "did:plc:bob")
            val session =
                mockk<SessionStateProvider>(relaxed = true) {
                    every { state } returns MutableStateFlow(signedIn)
                }
            val profile =
                mockk<ProfileRepository> {
                    coEvery { fetchHeader("did:plc:bob") } returns
                        Result.failure(IOException("net down"))
                }
            val vm = createVm(auth = mockk(relaxed = true), session = session, profile = profile)

            // Handle still lands synchronously.
            assertEquals("bob.bsky.social", vm.uiState.value.handle)
            advanceUntilIdle()
            // Silent failure: header renders without displayName / avatar; no effect emitted.
            assertNull(vm.uiState.value.displayName)
            assertNull(vm.uiState.value.avatarUrl)
        }

    @Test
    fun `init with SignedOut skips fetchHeader entirely`() =
        runTest(mainDispatcher.dispatcher) {
            // The default createVm helper provides a SessionState.SignedOut mock.
            val profile = mockk<ProfileRepository>(relaxed = true)
            val vm = createVm(auth = mockk(relaxed = true), profile = profile)
            advanceUntilIdle()

            assertNull(vm.uiState.value.handle)
            assertNull(vm.uiState.value.displayName)
            assertNull(vm.uiState.value.avatarUrl)
            coVerify(exactly = 0) { profile.fetchHeader(any()) }
        }
}
