package net.kikin.nubecita.core.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.data.models.ActiveSubscription

/**
 * In-memory [EntitlementRepository] test double. Production binds the RevenueCat
 * implementation (see `BillingModule`), so this is not part of the Hilt graph —
 * it lives in `src/test` and is constructed directly by `:core:billing`'s unit
 * tests. A Hilt test that needs it can bind it via
 * `@TestInstallIn(replaces = [BillingModule::class])`.
 */
internal class FakeEntitlementRepository : EntitlementRepository {
    private val _isPro = MutableStateFlow(false)
    override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _activeSubscription = MutableStateFlow<ActiveSubscription?>(null)
    override val activeSubscription: StateFlow<ActiveSubscription?> = _activeSubscription.asStateFlow()

    /** Nothing to sync for an in-memory entitlement; state only changes via [setPro]. */
    override suspend fun refresh() = Unit

    /** Test hook to drive the entitlement state (also used to simulate a purchase grant). */
    fun setPro(value: Boolean) {
        _isPro.value = value
    }

    /** Test hook to drive the active-subscription identity independently of [setPro]. */
    fun setActiveSubscription(value: ActiveSubscription?) {
        _activeSubscription.value = value
    }
}
