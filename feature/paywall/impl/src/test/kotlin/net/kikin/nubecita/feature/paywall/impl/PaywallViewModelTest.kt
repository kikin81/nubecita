package net.kikin.nubecita.feature.paywall.impl

import android.app.Activity
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.PaywallCheckoutStarted
import net.kikin.nubecita.core.analytics.PaywallPlan
import net.kikin.nubecita.core.analytics.PaywallPlanSelected
import net.kikin.nubecita.core.analytics.PaywallPurchaseCancelled
import net.kikin.nubecita.core.analytics.PaywallPurchaseError
import net.kikin.nubecita.core.analytics.PaywallRestore
import net.kikin.nubecita.core.analytics.PaywallViewed
import net.kikin.nubecita.core.analytics.RestoreOutcome
import net.kikin.nubecita.core.analytics.UserProperty
import net.kikin.nubecita.core.billing.BillingRepository
import net.kikin.nubecita.core.billing.PurchaseResult
import net.kikin.nubecita.core.billing.RestoreResult
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.SubscriptionOfferingFixtures
import net.kikin.nubecita.data.models.SubscriptionPlanId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class PaywallViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val offering = SubscriptionOfferingFixtures.proOffering()

    /**
     * Billing mock that loads the offering successfully by default;
     * purchase/restore are stubbed per-test. [loadPlans] is a `coEvery`
     * so the init-time load drives the VM to [PaywallStatus.Ready].
     */
    private fun createVm(
        billing: BillingRepository =
            mockk {
                coEvery { loadPlans() } returns Result.success(offering)
            },
        analytics: AnalyticsClient = RecordingAnalyticsClient(),
    ) = PaywallViewModel(billingRepository = billing, analytics = analytics)

    @Test
    fun `init loads the offering and transitions to Ready`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = createVm()
            advanceUntilIdle()

            assertEquals(PaywallStatus.Ready(offering), vm.uiState.value.status)
            // Annual is the default selection (design D9).
            assertEquals(SubscriptionPlanId.Annual, vm.uiState.value.selectedPlan)
        }

    @Test
    fun `init load failure transitions to Error`() =
        runTest(mainDispatcher.dispatcher) {
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.failure(IOException("offline"))
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            assertEquals(PaywallStatus.Error, vm.uiState.value.status)
        }

    @Test
    fun `Retry re-runs the load and recovers to Ready`() =
        runTest(mainDispatcher.dispatcher) {
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returnsMany
                        listOf(
                            Result.failure(IOException("offline")),
                            Result.success(offering),
                        )
                }
            val vm = createVm(billing)
            advanceUntilIdle()
            assertEquals(PaywallStatus.Error, vm.uiState.value.status)

            vm.handleEvent(PaywallEvent.Retry)
            advanceUntilIdle()

            assertEquals(PaywallStatus.Ready(offering), vm.uiState.value.status)
            coVerify(exactly = 2) { billing.loadPlans() }
        }

    @Test
    fun `PlanSelected updates the selected plan`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = createVm()
            advanceUntilIdle()

            vm.handleEvent(PaywallEvent.PlanSelected(SubscriptionPlanId.Monthly))

            assertEquals(SubscriptionPlanId.Monthly, vm.uiState.value.selectedPlan)
        }

    @Test
    fun `purchase success buys the selected plan and emits PurchaseSucceeded`() =
        runTest(mainDispatcher.dispatcher) {
            val activity = mockk<Activity>(relaxed = true)
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { purchase(activity, offering.annual) } returns PurchaseResult.Success
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PaywallEvent.PurchaseClicked(activity))
                advanceUntilIdle()
                // A fresh purchase routes to the thank-you screen, NOT a plain
                // Dismiss (which is restore-only) — nubecita-ykpc.
                assertEquals(PaywallEffect.PurchaseSucceeded, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            // Default selection is annual, so the annual plan is purchased.
            coVerify(exactly = 1) { billing.purchase(activity, offering.annual) }
            assertFalse(vm.uiState.value.isPurchasing)
        }

    @Test
    fun `purchase cancel leaves the paywall in place with no effect`() =
        runTest(mainDispatcher.dispatcher) {
            val activity = mockk<Activity>(relaxed = true)
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { purchase(activity, offering.annual) } returns PurchaseResult.Cancelled
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PaywallEvent.PurchaseClicked(activity))
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.isPurchasing)
            // Still on the (Ready) paywall.
            assertInstanceOf(PaywallStatus.Ready::class.java, vm.uiState.value.status)
        }

    @Test
    fun `purchase error clears the spinner and emits ShowPurchaseError`() =
        runTest(mainDispatcher.dispatcher) {
            val activity = mockk<Activity>(relaxed = true)
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { purchase(activity, offering.annual) } returns
                        PurchaseResult.Error("BILLING_UNAVAILABLE (dev-facing)")
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PaywallEvent.PurchaseClicked(activity))
                advanceUntilIdle()
                assertEquals(PaywallEffect.ShowPurchaseError, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.isPurchasing)
        }

    @Test
    fun `purchase is ignored while the offering is not Ready`() =
        runTest(mainDispatcher.dispatcher) {
            val activity = mockk<Activity>(relaxed = true)
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.failure(IOException("offline"))
                }
            val vm = createVm(billing)
            advanceUntilIdle()
            assertEquals(PaywallStatus.Error, vm.uiState.value.status)

            vm.handleEvent(PaywallEvent.PurchaseClicked(activity))
            advanceUntilIdle()

            coVerify(exactly = 0) { billing.purchase(any(), any()) }
        }

    @Test
    fun `double purchase tap is single-flight — only one purchase call`() =
        runTest(mainDispatcher.dispatcher) {
            val activity = mockk<Activity>(relaxed = true)
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { purchase(activity, offering.annual) } coAnswers {
                        delay(1_000)
                        PurchaseResult.Success
                    }
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            vm.handleEvent(PaywallEvent.PurchaseClicked(activity))
            vm.handleEvent(PaywallEvent.PurchaseClicked(activity))
            advanceUntilIdle()

            coVerify(exactly = 1) { billing.purchase(activity, offering.annual) }
        }

    @Test
    fun `restore that finds Pro emits Dismiss`() =
        runTest(mainDispatcher.dispatcher) {
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { restorePurchases() } returns RestoreResult.Completed(isPro = true)
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PaywallEvent.RestoreClicked)
                advanceUntilIdle()
                assertEquals(PaywallEffect.Dismiss, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.isRestoring)
        }

    @Test
    fun `restore that finds nothing emits ShowNothingToRestore`() =
        runTest(mainDispatcher.dispatcher) {
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { restorePurchases() } returns RestoreResult.Completed(isPro = false)
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PaywallEvent.RestoreClicked)
                advanceUntilIdle()
                assertEquals(PaywallEffect.ShowNothingToRestore, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `restore failure emits ShowRestoreError`() =
        runTest(mainDispatcher.dispatcher) {
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { restorePurchases() } returns RestoreResult.Error("network (dev-facing)")
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PaywallEvent.RestoreClicked)
                advanceUntilIdle()
                assertEquals(PaywallEffect.ShowRestoreError, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.isRestoring)
        }

    @Test
    fun `load that throws unexpectedly transitions to Error, not stuck Loading`() =
        runTest(mainDispatcher.dispatcher) {
            // loadPlans is contractually a Result, but guard against the impl
            // throwing (a provider-SDK bug) leaving the UI stuck in Loading.
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } throws RuntimeException("provider blew up")
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            assertEquals(PaywallStatus.Error, vm.uiState.value.status)
        }

    @Test
    fun `purchase that throws unexpectedly clears the spinner and emits ShowPurchaseError`() =
        runTest(mainDispatcher.dispatcher) {
            val activity = mockk<Activity>(relaxed = true)
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { purchase(activity, offering.annual) } throws RuntimeException("billing blew up")
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PaywallEvent.PurchaseClicked(activity))
                advanceUntilIdle()
                assertEquals(PaywallEffect.ShowPurchaseError, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            // Spinner must clear so the CTA isn't permanently disabled.
            assertFalse(vm.uiState.value.isPurchasing)
        }

    @Test
    fun `restore that throws unexpectedly clears the spinner and emits ShowRestoreError`() =
        runTest(mainDispatcher.dispatcher) {
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { restorePurchases() } throws RuntimeException("restore blew up")
                }
            val vm = createVm(billing)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PaywallEvent.RestoreClicked)
                advanceUntilIdle()
                assertEquals(PaywallEffect.ShowRestoreError, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.uiState.value.isRestoring)
        }

    @Test
    fun `Terms and Privacy taps emit LaunchUri`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = createVm()
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PaywallEvent.TermsClicked)
                advanceUntilIdle()
                assertEquals(
                    PaywallEffect.LaunchUri("https://nubecita.app/terms/"),
                    awaitItem(),
                )

                vm.handleEvent(PaywallEvent.PrivacyClicked)
                advanceUntilIdle()
                assertEquals(
                    PaywallEffect.LaunchUri("https://nubecita.app/privacy-policy/"),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -- Analytics (q5ge.13) -----------------------------------------------

    @Test
    fun `init logs PaywallViewed`() =
        runTest(mainDispatcher.dispatcher) {
            val analytics = RecordingAnalyticsClient()
            createVm(analytics = analytics)
            advanceUntilIdle()

            assertTrue(analytics.events.contains(PaywallViewed))
        }

    @Test
    fun `PlanSelected logs PaywallPlanSelected with the plan`() =
        runTest(mainDispatcher.dispatcher) {
            val analytics = RecordingAnalyticsClient()
            val vm = createVm(analytics = analytics)
            advanceUntilIdle()

            vm.handleEvent(PaywallEvent.PlanSelected(SubscriptionPlanId.Monthly))

            assertTrue(analytics.events.contains(PaywallPlanSelected(PaywallPlan.Monthly)))
        }

    @Test
    fun `successful purchase logs checkout started and NO client success event`() =
        runTest(mainDispatcher.dispatcher) {
            val analytics = RecordingAnalyticsClient()
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { purchase(any(), any()) } returns PurchaseResult.Success
                }
            val vm = createVm(billing = billing, analytics = analytics)
            advanceUntilIdle()

            vm.handleEvent(PaywallEvent.PurchaseClicked(mockk<Activity>()))
            advanceUntilIdle()

            // Annual is the default selection (D9).
            assertTrue(analytics.events.contains(PaywallCheckoutStarted(PaywallPlan.Annual)))
            // The terminal purchase/revenue event is owned by RevenueCat server-side.
            assertFalse(analytics.events.any { it.name == "paywall_purchase_success" })
            assertFalse(analytics.events.contains(PaywallPurchaseError))
            assertFalse(analytics.events.contains(PaywallPurchaseCancelled))
        }

    @Test
    fun `cancelled purchase logs checkout started then purchase cancelled`() =
        runTest(mainDispatcher.dispatcher) {
            val analytics = RecordingAnalyticsClient()
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { purchase(any(), any()) } returns PurchaseResult.Cancelled
                }
            val vm = createVm(billing = billing, analytics = analytics)
            advanceUntilIdle()

            vm.handleEvent(PaywallEvent.PurchaseClicked(mockk<Activity>()))
            advanceUntilIdle()

            assertTrue(analytics.events.contains(PaywallCheckoutStarted(PaywallPlan.Annual)))
            assertTrue(analytics.events.contains(PaywallPurchaseCancelled))
        }

    @Test
    fun `failed purchase logs checkout started then purchase error`() =
        runTest(mainDispatcher.dispatcher) {
            val analytics = RecordingAnalyticsClient()
            val billing =
                mockk<BillingRepository> {
                    coEvery { loadPlans() } returns Result.success(offering)
                    coEvery { purchase(any(), any()) } returns PurchaseResult.Error("boom")
                }
            val vm = createVm(billing = billing, analytics = analytics)
            advanceUntilIdle()

            vm.handleEvent(PaywallEvent.PurchaseClicked(mockk<Activity>()))
            advanceUntilIdle()

            assertTrue(analytics.events.contains(PaywallCheckoutStarted(PaywallPlan.Annual)))
            assertTrue(analytics.events.contains(PaywallPurchaseError))
        }

    @Test
    fun `restore logs PaywallRestore with the matching outcome`() =
        runTest(mainDispatcher.dispatcher) {
            suspend fun outcomeFor(result: RestoreResult): RestoreOutcome {
                val analytics = RecordingAnalyticsClient()
                val billing =
                    mockk<BillingRepository> {
                        coEvery { loadPlans() } returns Result.success(offering)
                        coEvery { restorePurchases() } returns result
                    }
                val vm = createVm(billing = billing, analytics = analytics)
                advanceUntilIdle()
                vm.handleEvent(PaywallEvent.RestoreClicked)
                advanceUntilIdle()
                return analytics.events
                    .filterIsInstance<PaywallRestore>()
                    .single()
                    .outcome
            }

            assertEquals(RestoreOutcome.Restored, outcomeFor(RestoreResult.Completed(isPro = true)))
            assertEquals(RestoreOutcome.Nothing, outcomeFor(RestoreResult.Completed(isPro = false)))
            assertEquals(RestoreOutcome.Error, outcomeFor(RestoreResult.Error("net")))
        }
}

/** Records logged events for assertions; user-property / screen calls are dropped. */
private class RecordingAnalyticsClient : AnalyticsClient {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(property: UserProperty) = Unit

    override fun logScreen(screen: AnalyticsScreen) = Unit
}
