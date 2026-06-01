package net.kikin.nubecita.core.billing

import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.EntitlementInfo
import com.revenuecat.purchases.EntitlementInfos
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.StoreProduct
import io.mockk.every
import io.mockk.mockk
import net.kikin.nubecita.data.models.BillingPeriod
import net.kikin.nubecita.data.models.SubscriptionPlanId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RevenueCatMappersTest {
    private fun pkg(
        formatted: String,
        micros: Long,
        currency: String = "USD",
    ): Package {
        val price = mockk<Price>()
        every { price.formatted } returns formatted
        every { price.amountMicros } returns micros
        every { price.currencyCode } returns currency
        val storeProduct = mockk<StoreProduct>()
        every { storeProduct.price } returns price
        val billingPackage = mockk<Package>()
        every { billingPackage.product } returns storeProduct
        return billingPackage
    }

    private fun offerings(
        monthly: Package?,
        annual: Package?,
        hasCurrent: Boolean = true,
    ): Offerings {
        val offering =
            mockk<Offering> {
                every { this@mockk.monthly } returns monthly
                every { this@mockk.annual } returns annual
            }
        return mockk<Offerings> { every { current } returns (if (hasCurrent) offering else null) }
    }

    @Test
    fun `toSubscriptionOffering maps the current offering's monthly and annual packages`() {
        val result =
            offerings(
                monthly = pkg("$1.99", 1_990_000),
                annual = pkg("$19.99", 19_990_000),
            ).toSubscriptionOfferingResult()

        val offering = result.getOrThrow()
        assertEquals(SubscriptionPlanId.Monthly, offering.monthly.id)
        assertEquals(BillingPeriod.Monthly, offering.monthly.period)
        assertEquals("$1.99", offering.monthly.formattedPrice)
        assertEquals(1_990_000L, offering.monthly.priceAmountMicros)
        assertEquals("USD", offering.monthly.priceCurrencyCode)

        assertEquals(SubscriptionPlanId.Annual, offering.annual.id)
        assertEquals(BillingPeriod.Annual, offering.annual.period)
        assertEquals("$19.99", offering.annual.formattedPrice)
        assertEquals(19_990_000L, offering.annual.priceAmountMicros)
    }

    @Test
    fun `toSubscriptionOffering fails when there is no current offering`() {
        val result =
            offerings(monthly = pkg("$1.99", 1_990_000), annual = pkg("$19.99", 19_990_000), hasCurrent = false)
                .toSubscriptionOfferingResult()
        assertTrue(result.isFailure)
    }

    @Test
    fun `toSubscriptionOffering fails when the annual package is missing`() {
        val result = offerings(monthly = pkg("$1.99", 1_990_000), annual = null).toSubscriptionOfferingResult()
        assertTrue(result.isFailure)
    }

    @Test
    fun `hasProEntitlement is true when the pro entitlement is active`() {
        val info =
            mockk<CustomerInfo> {
                every { entitlements } returns
                    mockk<EntitlementInfos> { every { active } returns mapOf("pro" to mockk<EntitlementInfo>()) }
            }
        assertTrue(info.hasProEntitlement())
    }

    @Test
    fun `hasProEntitlement is false when no entitlement is active`() {
        val info =
            mockk<CustomerInfo> {
                every { entitlements } returns mockk<EntitlementInfos> { every { active } returns emptyMap() }
            }
        assertFalse(info.hasProEntitlement())
    }

    @Test
    fun `activeProSubscription maps the pro entitlement's base plan and product id`() {
        val info = customerInfoWithPro(productPlanIdentifier = "annual", productIdentifier = "pro_sub:annual")

        val active = info.activeProSubscription()
        assertEquals(SubscriptionPlanId.Annual, active?.planId)
        assertEquals("pro_sub:annual", active?.productId)
    }

    @Test
    fun `activeProSubscription maps a monthly base plan case-insensitively`() {
        val info = customerInfoWithPro(productPlanIdentifier = "MONTHLY", productIdentifier = "pro_sub:monthly")
        assertEquals(SubscriptionPlanId.Monthly, info.activeProSubscription()?.planId)
    }

    @Test
    fun `activeProSubscription yields a null plan for an unrecognized base plan`() {
        // A future / renamed base plan must degrade to a neutral label, not crash
        // or mis-map. productId still flows through for the deep link.
        val info = customerInfoWithPro(productPlanIdentifier = "weekly", productIdentifier = "pro_sub:weekly")
        val active = info.activeProSubscription()
        assertNull(active?.planId)
        assertEquals("pro_sub:weekly", active?.productId)
    }

    @Test
    fun `activeProSubscription tolerates a null base plan identifier`() {
        val info = customerInfoWithPro(productPlanIdentifier = null, productIdentifier = "pro_legacy")
        assertNull(info.activeProSubscription()?.planId)
        assertEquals("pro_legacy", info.activeProSubscription()?.productId)
    }

    @Test
    fun `activeProSubscription is null when pro is not active`() {
        val info =
            mockk<CustomerInfo> {
                every { entitlements } returns mockk<EntitlementInfos> { every { active } returns emptyMap() }
            }
        assertNull(info.activeProSubscription())
    }

    private fun customerInfoWithPro(
        productPlanIdentifier: String?,
        productIdentifier: String,
    ): CustomerInfo {
        val pro =
            mockk<EntitlementInfo> {
                every { this@mockk.productPlanIdentifier } returns productPlanIdentifier
                every { this@mockk.productIdentifier } returns productIdentifier
            }
        return mockk<CustomerInfo> {
            every { entitlements } returns mockk<EntitlementInfos> { every { active } returns mapOf("pro" to pro) }
        }
    }

    @Test
    fun `purchase exception maps user cancellation to Cancelled`() {
        val ex = mockk<PurchasesTransactionException>()
        every { ex.userCancelled } returns true

        assertEquals(PurchaseResult.Cancelled, ex.toPurchaseResult())
    }

    @Test
    fun `purchase exception maps a real failure to Error with its message`() {
        val ex = mockk<PurchasesTransactionException>()
        every { ex.userCancelled } returns false
        every { ex.message } returns "Item unavailable"

        assertEquals(PurchaseResult.Error("Item unavailable"), ex.toPurchaseResult())
    }
}
