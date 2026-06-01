package net.kikin.nubecita.bootstrap

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsInstanceIdProvider
import net.kikin.nubecita.core.analytics.IsPro
import net.kikin.nubecita.core.billing.EntitlementRepository
import net.kikin.nubecita.core.billing.RevenueCatInitializer
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Startup glue between Pro entitlement (`:core:billing`) and analytics
 * (`:core:analytics`) — the one place that needs both, so it lives in the
 * composition root rather than coupling either core module to the other.
 * Production-flavor only (bench has NoOp analytics + inert billing).
 *
 * [start] is invoked from [ProductionBootstrapModule]'s RevenueCat
 * `AppInitializer`, immediately after `Purchases.configure`, so the Firebase
 * link runs once the SDK is configured (the link seam also guards on
 * `isConfigured`, so the ordering is belt-and-suspenders).
 */
@Singleton
internal class ProAnalyticsCoordinator
    @Inject
    constructor(
        private val entitlementRepository: EntitlementRepository,
        private val analyticsClient: AnalyticsClient,
        private val instanceIdProvider: AnalyticsInstanceIdProvider,
        private val revenueCatInitializer: RevenueCatInitializer,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        fun start() {
            // Link this install's Firebase app-instance id to the RevenueCat
            // customer so RC's server-side GA4 events attribute to the same user
            // (launch checklist F4). Reads the id through :core:analytics and
            // hands it to :core:billing — neither core module sees the other's SDK.
            scope.launch {
                revenueCatInitializer.linkFirebaseAppInstanceId(instanceIdProvider.appInstanceId())
            }
            // Mirror the Pro entitlement into a GA4 user property so funnel /
            // engagement reports can segment by Pro status. Long-lived: tracks
            // every transition for the app's lifetime.
            entitlementRepository.isPro
                .onEach { analyticsClient.setUserProperty(IsPro(it)) }
                .launchIn(scope)
        }
    }
