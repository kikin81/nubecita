package net.kikin.nubecita.core.widgetsync.worker

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import javax.inject.Singleton

/**
 * Provides [WidgetRefreshScheduler]. Plain-constructed (not `@Inject`) because it
 * needs the application-scoped [CoroutineScope] and is `start()`ed at the `:app`
 * layer via the production-flavor `AppInitializer` multibinding — mirrors
 * `:feature:chats:impl`'s `DmWorkerProvidesModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object WidgetWorkerProvidesModule {
    @Provides
    @Singleton
    fun provideWidgetRefreshScheduler(
        @ApplicationScope scope: CoroutineScope,
        sessionStateProvider: SessionStateProvider,
        scheduler: WidgetWorkScheduler,
    ): WidgetRefreshScheduler =
        WidgetRefreshScheduler(
            scope = scope,
            sessionStateProvider = sessionStateProvider,
            scheduler = scheduler,
        )
}
