package net.kikin.nubecita.core.billing

import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.data.models.ActiveSubscription
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RevenueCat-backed [EntitlementRepository]. Bridges the SDK's
 * `UpdatedCustomerInfoListener` (and an initial `awaitCustomerInfo`) onto the
 * [isPro] `StateFlow` for the `pro` entitlement.
 *
 * Deliberately **inert until [startObserving]** is called — the constructor
 * touches no `Purchases.sharedInstance`. `RevenueCatInitializer` calls
 * [startObserving] exactly once, right after `Purchases.configure`, from the
 * production-flavor `AppInitializer`. The bench flavor never registers that
 * initializer, so this singleton is constructed (Hilt-bound) but stays at
 * `isPro = false` and issues zero SDK / network calls — keeping the Macrobench
 * APK silent without a `:core:billing` flavor split.
 */
@Singleton
internal class RevenueCatEntitlementRepository
    @Inject
    constructor(
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : EntitlementRepository {
        private val _isPro = MutableStateFlow(false)
        override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

        private val _activeSubscription = MutableStateFlow<ActiveSubscription?>(null)
        override val activeSubscription: StateFlow<ActiveSubscription?> = _activeSubscription.asStateFlow()

        /**
         * Wire the live entitlement listener and seed the initial value. MUST be
         * called after `Purchases.configure`. Idempotent enough for app startup:
         * re-registering the listener simply replaces it.
         */
        fun startObserving() {
            Purchases.sharedInstance.updatedCustomerInfoListener =
                UpdatedCustomerInfoListener { customerInfo -> publish(customerInfo) }
            scope.launch { refresh() }
        }

        override suspend fun refresh() {
            // Inert until RevenueCatInitializer runs configure (bench / keyless builds):
            // a no-op here keeps the "inert, not crashing" promise and avoids logging a
            // spurious warning on every call, matching the BillingRepository guards.
            if (!Purchases.isConfigured) return
            runCatching { Purchases.sharedInstance.awaitCustomerInfo() }
                .onSuccess { publish(it) }
                .onFailure { Timber.tag(TAG).w(it, "entitlement refresh failed: %s", it.javaClass.name) }
        }

        /** Fan one CustomerInfo snapshot out to both derived flows so they never disagree. */
        private fun publish(customerInfo: CustomerInfo) {
            _isPro.value = customerInfo.hasProEntitlement()
            _activeSubscription.value = customerInfo.activeProSubscription()
        }

        private companion object {
            const val TAG = "RevenueCatEntitlement"
        }
    }
