package net.kikin.nubecita.bootstrap

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsInstanceIdProvider
import net.kikin.nubecita.core.analytics.IsPro
import net.kikin.nubecita.core.analytics.NotificationsEnabled
import net.kikin.nubecita.core.analytics.SelfHosted
import net.kikin.nubecita.core.analytics.Theme
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.billing.EntitlementRepository
import net.kikin.nubecita.core.billing.RevenueCatInitializer
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.core.push.NotificationsEnabledSource
import javax.inject.Inject
import javax.inject.Singleton
import net.kikin.nubecita.core.analytics.ThemePreference as AnalyticsThemePreference
import net.kikin.nubecita.core.preferences.ThemePreference as PrefsThemePreference

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
        private val userPreferencesRepository: UserPreferencesRepository,
        private val sessionStateProvider: SessionStateProvider,
        private val notificationsEnabledSource: NotificationsEnabledSource,
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
            // Mirror the persisted theme choice. Reads SYSTEM for everyone until a
            // theme picker lands (see ThemePreference KDoc), but the observer is
            // wired now so the property tracks the moment a picker writes a value.
            userPreferencesRepository.themePreference
                .onEach { analyticsClient.setUserProperty(Theme(it.toAnalyticsPreference())) }
                .launchIn(scope)
            // Whether the signed-in account lives on a non-Bluesky PDS. Derived
            // from the session host inside :core:auth; only the boolean crosses
            // the boundary (the host string never leaves the auth layer).
            sessionStateProvider.isSelfHosted
                .onEach { analyticsClient.setUserProperty(SelfHosted(it)) }
                .launchIn(scope)
            // Whether push notifications are effectively on: system permission AND
            // a successful registration. Re-evaluated on every app foreground, so
            // an OS-Settings toggle outside the app is reflected on next resume.
            notificationsEnabledSource.notificationsEnabled
                .onEach { analyticsClient.setUserProperty(NotificationsEnabled(it)) }
                .launchIn(scope)
        }
    }

private fun PrefsThemePreference.toAnalyticsPreference(): AnalyticsThemePreference =
    when (this) {
        PrefsThemePreference.LIGHT -> AnalyticsThemePreference.Light
        PrefsThemePreference.DARK -> AnalyticsThemePreference.Dark
        PrefsThemePreference.SYSTEM -> AnalyticsThemePreference.System
    }
