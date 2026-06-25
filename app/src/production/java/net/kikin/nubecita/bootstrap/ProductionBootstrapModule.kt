package net.kikin.nubecita.bootstrap

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.BuildConfig
import net.kikin.nubecita.core.billing.RevenueCatInitializer
import net.kikin.nubecita.core.moderation.ModerationPreferencesCoordinator
import net.kikin.nubecita.core.moderation.PostAudienceDefaultCoordinator
import net.kikin.nubecita.core.push.AppLifecycleObserver
import net.kikin.nubecita.core.push.PushRegistrationCoordinator
import net.kikin.nubecita.core.widgetsync.worker.WidgetRefreshScheduler
import net.kikin.nubecita.feature.chats.impl.store.ChatsUnreadPollingObserver
import net.kikin.nubecita.feature.chats.impl.worker.DmPollScheduler
import net.kikin.nubecita.feature.chats.impl.worker.MessagesNotificationChannelInstaller
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

    // Foreground-only unread-DM polling for the Chats tab badge. Mirrors the
    // notifications poller; bench's empty set keeps it idle during Macrobench.
    @Provides
    @IntoSet
    fun provideChatsUnreadPollingInitializer(
        observer: ChatsUnreadPollingObserver,
    ): AppInitializer = AppInitializer { observer.start() }

    // Reactively schedule/cancel the background DM-poll worker on
    // (signed-in ∧ message-checking enabled) — nubecita-1fy.15 §7. Production-
    // only: bench's empty set means no periodic work is ever enqueued there.
    @Provides
    @IntoSet
    fun provideDmPollSchedulerInitializer(
        scheduler: DmPollScheduler,
    ): AppInitializer = AppInitializer { scheduler.start() }

    // Install the "Messages" DM notification channel eagerly at startup (like
    // the push channels) so it shows in system notification settings on first
    // launch, not only after the first DM posts (nubecita-29rw). Unconditional —
    // independent of sign-in / the message-checking toggle — so the user can
    // pre-tune or mute it. Production-only; bench's empty set keeps it idle.
    @Provides
    @IntoSet
    fun provideMessagesNotificationChannelInitializer(
        @ApplicationContext context: Context,
    ): AppInitializer = AppInitializer { MessagesNotificationChannelInstaller().install(context) }

    // Reactively schedule/cancel the background widget-feed-refresh worker on
    // sign-in / sign-out (sub-project B, nubecita-lgoo.2 §7). Production-only:
    // bench's empty set means no periodic widget-refresh work is ever enqueued
    // there (keeps Macrobench windows free of background work).
    @Provides
    @IntoSet
    fun provideWidgetRefreshSchedulerInitializer(
        scheduler: WidgetRefreshScheduler,
    ): AppInitializer = AppInitializer { scheduler.start() }

    // Refresh the viewer's content-filter preferences once the session is
    // signed in (cold start + re-login). Until this runs, ModerationPrefs sits
    // at the fail-safe DEFAULT (adult off). Production-only: the bench flavor's
    // empty initializer set keeps moderation dormant during Macrobench windows.
    @Provides
    @IntoSet
    fun provideModerationPreferencesInitializer(
        coordinator: ModerationPreferencesCoordinator,
    ): AppInitializer = AppInitializer { coordinator.start() }

    // Refresh the viewer's post-audience default on sign-in (so the composer
    // pre-fills with the synced value) and reset it on sign-out. Production-only,
    // like the moderation coordinator above.
    @Provides
    @IntoSet
    fun providePostAudienceDefaultInitializer(
        coordinator: PostAudienceDefaultCoordinator,
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
