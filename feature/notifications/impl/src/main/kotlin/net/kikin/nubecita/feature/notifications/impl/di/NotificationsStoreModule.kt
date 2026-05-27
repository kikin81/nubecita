package net.kikin.nubecita.feature.notifications.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.feature.notifications.impl.store.NotificationsPollingObserver
import net.kikin.nubecita.feature.notifications.impl.store.NotificationsUnreadCountStore

/**
 * Hilt bindings for the unread-count store and its `ProcessLifecycleOwner`-
 * scoped polling observer. Both are process singletons.
 *
 * The store itself is wired by Hilt via [NotificationsUnreadCountStore]'s
 * `@Inject constructor` + class-level `@Singleton` annotation, so it isn't
 * provided here. This module exists to provide the [NotificationsPollingObserver],
 * which needs an application-scoped [CoroutineScope] and isn't `@Inject`-
 * constructable from Hilt itself (it doesn't carry the `@Inject` annotation —
 * the observer is wired only at the `:app` layer in `NubecitaApplication.onCreate`).
 *
 * Mirrors the `provideAppLifecycleObserver` shape in
 * `core/push/.../di/PushModule.kt`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object NotificationsStoreModule {
    @Provides
    internal fun provideNotificationsPollingObserver(
        store: NotificationsUnreadCountStore,
        sessionStateProvider: SessionStateProvider,
        @ApplicationScope scope: CoroutineScope,
    ): NotificationsPollingObserver =
        NotificationsPollingObserver(
            store = store,
            sessionStateProvider = sessionStateProvider,
            scope = scope,
        )
}
