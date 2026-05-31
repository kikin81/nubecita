package net.kikin.nubecita.core.billing

import android.app.Activity
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.data.models.SubscriptionPlanId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FakeBillingRepositoryTest {
    private val activity = mockk<Activity>(relaxed = true)

    private fun newRepos(): Pair<FakeBillingRepository, FakeEntitlementRepository> {
        val entitlement = FakeEntitlementRepository()
        return FakeBillingRepository(entitlement) to entitlement
    }

    @Test
    fun `loadPlans returns the monthly and annual Pro plans`() =
        runTest {
            val (billing, _) = newRepos()

            val offering = billing.loadPlans().getOrThrow()

            assertEquals(SubscriptionPlanId.Monthly, offering.monthly.id)
            assertEquals(SubscriptionPlanId.Annual, offering.annual.id)
        }

    @Test
    fun `purchase succeeds and grants the entitlement`() =
        runTest {
            val (billing, entitlement) = newRepos()
            val plan = billing.loadPlans().getOrThrow().annual

            val result = billing.purchase(activity, plan)

            assertEquals(PurchaseResult.Success, result)
            assertTrue(entitlement.isPro.value)
        }

    @Test
    fun `restorePurchases reports no entitlement before any purchase`() =
        runTest {
            val (billing, _) = newRepos()

            assertEquals(RestoreResult.Completed(isPro = false), billing.restorePurchases())
        }

    @Test
    fun `restorePurchases reports the entitlement after a purchase`() =
        runTest {
            val (billing, _) = newRepos()
            val plan = billing.loadPlans().getOrThrow().monthly
            billing.purchase(activity, plan)

            assertEquals(RestoreResult.Completed(isPro = true), billing.restorePurchases())
        }
}
