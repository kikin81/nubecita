package net.kikin.nubecita.feature.chats.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.feature.chats.impl.store.ChatsUnreadCountStore
import net.kikin.nubecita.feature.chats.impl.store.ChatsUnreadPollingObserver
import javax.inject.Singleton

/**
 * Hilt bindings for the chats unread-count store's `ProcessLifecycleOwner`-
 * scoped polling observer. Mirrors `NotificationsStoreModule`.
 *
 * [ChatsUnreadCountStore] is wired by its own `@Inject constructor` +
 * `@Singleton`, so it isn't provided here. The observer needs an
 * application-scoped [CoroutineScope] and is started at the `:app` layer
 * (contributed to the `AppInitializer` multibinding in the production
 * flavor's `ProductionBootstrapModule`), so it's plain-constructed here
 * rather than `@Inject`-annotated.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ChatsStoreModule {
    @Provides
    @Singleton
    internal fun provideChatsUnreadPollingObserver(
        store: ChatsUnreadCountStore,
        sessionStateProvider: SessionStateProvider,
        @ApplicationScope scope: CoroutineScope,
    ): ChatsUnreadPollingObserver =
        ChatsUnreadPollingObserver(
            store = store,
            sessionStateProvider = sessionStateProvider,
            scope = scope,
        )
}
