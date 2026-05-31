package net.kikin.nubecita.core.billing.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.core.billing.api.EntitlementRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory [EntitlementRepository] that lets the rest of the Pro epic build
 * and run against the billing boundary before the RevenueCat implementation
 * lands (`nubecita-q5ge.2` swaps the Hilt binding). Also the basis for
 * previews and tests of any Pro-gated surface.
 *
 * `@Singleton` so a single entitlement state is shared process-wide — the
 * same instance [FakeBillingRepository] flips on a simulated purchase is the
 * one features observe via [isPro].
 *
 * `internal` like the peer `Default*` repository impls (e.g.
 * `core/profile/DefaultActorProfileRepository`): consumers reach it only
 * through the [EntitlementRepository] interface bound by `BillingModule`, so
 * nothing outside this module can call [setPro] and bypass the abstraction.
 */
@Singleton
internal class FakeEntitlementRepository
    @Inject
    constructor() : EntitlementRepository {
        private val _isPro = MutableStateFlow(false)
        override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

        /** Nothing to sync for an in-memory entitlement; state only changes via [setPro]. */
        override suspend fun refresh() = Unit

        /** Simulated-purchase / test hook to drive the entitlement state. */
        fun setPro(value: Boolean) {
            _isPro.value = value
        }
    }
