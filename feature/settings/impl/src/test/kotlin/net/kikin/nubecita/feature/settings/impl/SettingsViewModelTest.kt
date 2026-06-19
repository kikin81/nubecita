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
import net.kikin.nubecita.core.billing.BillingRepository
import net.kikin.nubecita.core.billing.EntitlementRepository
import net.kikin.nubecita.core.billing.RestoreResult
import net.kikin.nubecita.core.common.avatar.avatarHueFor
import net.kikin.nubecita.core.preferences.MessageCheckingPreference
import net.kikin.nubecita.core.profile.ActorProfile
import net.kikin.nubecita.core.profile.ActorProfileRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.ActiveSubscription
import net.kikin.nubecita.data.models.BillingPeriod
import net.kikin.nubecita.data.models.SubscriptionOfferingFixtures
import net.kikin.nubecita.data.models.SubscriptionPlanId
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
     * Default session + actor-profile-repository mocks for tests that
     * only exercise the sign-out flow. Session returns SignedOut so the
     * VM's init block skips the profile-fetch path; the actor-profile
     * mock stays unused. Tests that need to assert on header state can
     * override either.
     */
    private fun createVm(
        auth: AuthRepository,
        session: SessionStateProvider =
            mockk(relaxed = true) {
                every { state } returns MutableStateFlow(SessionState.SignedOut)
            },
        actorProfile: ActorProfileRepository = mockk(relaxed = true),
        isPro: Boolean = false,
        activeSub: ActiveSubscription? = null,
        billing: BillingRepository =
            mockk(relaxed = true) {
                coEvery { loadPlans() } returns Result.success(SubscriptionOfferingFixtures.proOffering())
                coEvery { restorePurchases() } returns RestoreResult.Completed(isPro = false)
            },
        entitlement: EntitlementRepository =
            mockk(relaxed = true) {
                every { this@mockk.isPro } returns MutableStateFlow(isPro)
                every { activeSubscription } returns MutableStateFlow(activeSub)
            },
        messageChecking: MessageCheckingPreference =
            mockk(relaxed = true) {
                every { enabled } returns MutableStateFlow(true)
            },
    ) = SettingsViewModel(
        authRepository = auth,
        sessionStateProvider = session,
        actorProfileRepository = actorProfile,
        entitlementRepository = entitlement,
        billingRepository = billing,
        messageCheckingPreference = messageChecking,
    )

    @Test
    fun `message-checking enabled is mirrored from the preference`() =
        runTest(mainDispatcher.dispatcher) {
            val pref =
                mockk<MessageCheckingPreference>(relaxed = true) {
                    every { enabled } returns MutableStateFlow(false)
                }
            val vm = createVm(auth = mockk(relaxed = true), messageChecking = pref)
            advanceUntilIdle()

            assertEquals(false, vm.uiState.value.messageCheckingEnabled)
        }

    @Test
    fun `MessageCheckingToggled persists to the preference`() =
        runTest(mainDispatcher.dispatcher) {
            val pref = mockk<MessageCheckingPreference>(relaxed = true) { every { enabled } returns MutableStateFlow(true) }
            val vm = createVm(auth = mockk(relaxed = true), messageChecking = pref)

            vm.handleEvent(SettingsEvent.MessageCheckingToggled(enabled = false))
            advanceUntilIdle()

            coVerify { pref.setEnabled(false) }
        }

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
    fun `NotificationsTapped emits OpenSystemNotificationSettings`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = createVm(auth = mockk(relaxed = true))

            vm.effects.test {
                vm.handleEvent(SettingsEvent.NotificationsTapped)
                advanceUntilIdle()
                assertEquals(SettingsEffect.OpenSystemNotificationSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `FollowDeveloperTapped emits NavigateToDeveloperProfile`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = createVm(auth = mockk(relaxed = true))

            vm.effects.test {
                vm.handleEvent(SettingsEvent.FollowDeveloperTapped)
                advanceUntilIdle()
                assertEquals(SettingsEffect.NavigateToDeveloperProfile, awaitItem())
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
            val actorProfile =
                mockk<ActorProfileRepository> {
                    coEvery { fetchProfile("did:plc:alice") } returns
                        Result.success(
                            ActorProfile(
                                did = "did:plc:alice",
                                handle = "alice.bsky.social",
                                displayName = "Alice Anderson",
                                avatarUrl = "https://cdn.example/alice.jpg",
                            ),
                        )
                }
            val vm =
                createVm(auth = mockk(relaxed = true), session = session, actorProfile = actorProfile)

            // Handle + avatarHue (computed via avatarHueFor from did + handle)
            // lands first from the flow's emission. displayName + avatarUrl
            // arrive after fetchProfile resolves. advanceUntilIdle drains
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
            coVerify(exactly = 1) { actorProfile.fetchProfile("did:plc:alice") }
        }

    @Test
    fun `init with SignedIn keeps displayName and avatarUrl null on fetch failure`() =
        runTest(mainDispatcher.dispatcher) {
            val signedIn = SessionState.SignedIn(handle = "bob.bsky.social", did = "did:plc:bob")
            val session =
                mockk<SessionStateProvider>(relaxed = true) {
                    every { state } returns MutableStateFlow(signedIn)
                }
            val actorProfile =
                mockk<ActorProfileRepository> {
                    coEvery { fetchProfile("did:plc:bob") } returns
                        Result.failure(IOException("net down"))
                }
            val vm =
                createVm(auth = mockk(relaxed = true), session = session, actorProfile = actorProfile)

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
    fun `non-Pro state mirrors isPro false and ProUpsellTapped emits OpenPaywall`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = createVm(auth = mockk(relaxed = true), isPro = false)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isPro)

            vm.effects.test {
                vm.handleEvent(SettingsEvent.ProUpsellTapped)
                advanceUntilIdle()
                assertEquals(SettingsEffect.OpenPaywall, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Pro state resolves the annual plan caption and manage sku`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                createVm(
                    auth = mockk(relaxed = true),
                    isPro = true,
                    activeSub = ActiveSubscription(planId = SubscriptionPlanId.Annual, productId = "pro_sub:annual"),
                )
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.isPro)
            // Period + store-localized price resolved via the loadPlans() cross-reference
            // (the fixture offering's annual plan is "$19.99").
            assertEquals(BillingPeriod.Annual, state.currentPlanPeriod)
            assertEquals("$19.99", state.currentPlanFormattedPrice)
            assertEquals("pro_sub:annual", state.manageSku)
        }

    @Test
    fun `an unrecognized plan id leaves the period null but keeps the manage sku`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                createVm(
                    auth = mockk(relaxed = true),
                    isPro = true,
                    activeSub = ActiveSubscription(planId = null, productId = "pro_sub:weekly"),
                )
            advanceUntilIdle()

            assertNull(vm.uiState.value.currentPlanPeriod)
            assertNull(vm.uiState.value.currentPlanFormattedPrice)
            assertEquals("pro_sub:weekly", vm.uiState.value.manageSku)
        }

    @Test
    fun `ManageSubscriptionTapped emits OpenManageSubscription carrying the active sku`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                createVm(
                    auth = mockk(relaxed = true),
                    isPro = true,
                    activeSub = ActiveSubscription(planId = SubscriptionPlanId.Monthly, productId = "pro_sub:monthly"),
                )
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(SettingsEvent.ManageSubscriptionTapped)
                advanceUntilIdle()
                assertEquals(SettingsEffect.OpenManageSubscription(sku = "pro_sub:monthly"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `RestorePurchasesTapped that finds Pro emits ShowRestoreSuccess`() =
        runTest(mainDispatcher.dispatcher) {
            val billing =
                mockk<BillingRepository>(relaxed = true) {
                    coEvery { restorePurchases() } returns RestoreResult.Completed(isPro = true)
                }
            val vm = createVm(auth = mockk(relaxed = true), billing = billing)

            vm.effects.test {
                vm.handleEvent(SettingsEvent.RestorePurchasesTapped)
                advanceUntilIdle()
                assertEquals(SettingsEffect.ShowRestoreSuccess, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.isRestoring)
        }

    @Test
    fun `RestorePurchasesTapped that finds nothing emits ShowNothingToRestore`() =
        runTest(mainDispatcher.dispatcher) {
            val billing =
                mockk<BillingRepository>(relaxed = true) {
                    coEvery { restorePurchases() } returns RestoreResult.Completed(isPro = false)
                }
            val vm = createVm(auth = mockk(relaxed = true), billing = billing)

            vm.effects.test {
                vm.handleEvent(SettingsEvent.RestorePurchasesTapped)
                advanceUntilIdle()
                assertEquals(SettingsEffect.ShowNothingToRestore, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `RestorePurchasesTapped error emits ShowRestoreError and clears the in-flight flag`() =
        runTest(mainDispatcher.dispatcher) {
            val billing =
                mockk<BillingRepository>(relaxed = true) {
                    coEvery { restorePurchases() } returns RestoreResult.Error("billing unavailable")
                }
            val vm = createVm(auth = mockk(relaxed = true), billing = billing)

            vm.effects.test {
                vm.handleEvent(SettingsEvent.RestorePurchasesTapped)
                advanceUntilIdle()
                assertEquals(SettingsEffect.ShowRestoreError, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.isRestoring)
        }

    @Test
    fun `init with SignedOut skips fetchProfile entirely`() =
        runTest(mainDispatcher.dispatcher) {
            // The default createVm helper provides a SessionState.SignedOut mock.
            val actorProfile = mockk<ActorProfileRepository>(relaxed = true)
            val vm = createVm(auth = mockk(relaxed = true), actorProfile = actorProfile)
            advanceUntilIdle()

            assertNull(vm.uiState.value.handle)
            assertNull(vm.uiState.value.displayName)
            assertNull(vm.uiState.value.avatarUrl)
            coVerify(exactly = 0) { actorProfile.fetchProfile(any()) }
        }
}
