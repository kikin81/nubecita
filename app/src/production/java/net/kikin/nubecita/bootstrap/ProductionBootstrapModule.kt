package net.kikin.nubecita.bootstrap

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.BuildConfig
import net.kikin.nubecita.core.billing.RevenueCatInitializer
import net.kikin.nubecita.core.moderation.ModerationPreferencesCoordinator
import net.kikin.nubecita.core.push.AppLifecycleObserver
import net.kikin.nubecita.core.push.PushRegistrationCoordinator
import net.kikin.nubecita.feature.notifications.impl.store.NotificationsPollingObserver

/**
 * Production-flavor contributions to the [AppInitializer] multibinding.
 * Lives under `app/src/production/` so the bench flavor's empty initializer
 * set (declared via `BootstrapModule.@Multibinds` in `src/main/`) leaves
 * Firebase messaging dormant, push registration silent, and notification
 * polling idle for the duration of a Macrobench measurement window.
 *
 * The three coordinators were previously injected and started directly
 * from `NubecitaApplication.onCreate`. Routing them through this
 * multibinding lets per-flavor source-set selection decide whether they
 * run, without extracting interfaces in `:core:push` or
 * `:feature:notifications:impl`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ProductionBootstrapModule {
    @Provides
    @IntoSet
    fun provideAppLifecycleInitializer(
        observer: AppLifecycleObserver,
    ): AppInitializer = AppInitializer { observer.start() }

    @Provides
    @IntoSet
    fun providePushRegistrationInitializer(
        coordinator: PushRegistrationCoordinator,
    ): AppInitializer = AppInitializer { coordinator.start() }

    @Provides
    @IntoSet
    fun provideNotificationsPollingInitializer(
        observer: NotificationsPollingObserver,
    ): AppInitializer = AppInitializer { observer.start() }

    // Refresh the viewer's content-filter preferences once the session is
    // signed in (cold start + re-login). Until this runs, ModerationPrefs sits
    // at the fail-safe DEFAULT (adult off). Production-only: the bench flavor's
    // empty initializer set keeps moderation dormant during Macrobench windows.
    @Provides
    @IntoSet
    fun provideModerationPreferencesInitializer(
        coordinator: ModerationPreferencesCoordinator,
    ): AppInitializer = AppInitializer { coordinator.start() }

    // RevenueCat lives only in the production flavor: configure runs here, so the
    // bench flavor (empty initializer set) never touches the SDK or the network.
    // The API key is :app's BuildConfig field, passed into :core:billing so the
    // RevenueCat SDK itself stays confined to that module.
    @Provides
    @IntoSet
    fun provideRevenueCatInitializer(
        initializer: RevenueCatInitializer,
        proAnalytics: ProAnalyticsCoordinator,
    ): AppInitializer =
        AppInitializer {
            initializer.initialize(apiKey = BuildConfig.REVENUECAT_API_KEY, verboseLogging = BuildConfig.DEBUG)
            // After configure: link the Firebase app-instance id to the RC
            // customer (F4) and start mirroring isPro into the GA4 user property.
            proAnalytics.start()
        }
}
