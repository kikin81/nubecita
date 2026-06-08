package net.kikin.nubecita.feature.chats.impl.worker

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.preferences.MessageCheckingPreference
import javax.inject.Singleton

/**
 * Provides [DmPollScheduler]. Plain-constructed (not `@Inject`) because it needs
 * the application-scoped [CoroutineScope] and is `start()`ed at the `:app` layer
 * via the production-flavor `AppInitializer` multibinding — mirrors
 * `ChatsStoreModule.provideChatsUnreadPollingObserver`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DmWorkerProvidesModule {
    @Provides
    @Singleton
    fun provideDmPollScheduler(
        @ApplicationScope scope: CoroutineScope,
        sessionStateProvider: SessionStateProvider,
        messageChecking: MessageCheckingPreference,
        scheduler: DmWorkScheduler,
    ): DmPollScheduler =
        DmPollScheduler(
            scope = scope,
            sessionStateProvider = sessionStateProvider,
            messageChecking = messageChecking,
            scheduler = scheduler,
        )
}
