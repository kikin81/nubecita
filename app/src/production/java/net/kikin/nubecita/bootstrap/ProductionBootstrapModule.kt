package net.kikin.nubecita.bootstrap

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
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
}
