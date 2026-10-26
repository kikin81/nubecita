package net.kikin.nubecita.core.wearsync.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.preferences.WatchFeedsPreference
import net.kikin.nubecita.core.wearsync.DefaultWearFeedSync
import net.kikin.nubecita.core.wearsync.PlayServicesWearAppPresence
import net.kikin.nubecita.core.wearsync.WearAppPresence
import net.kikin.nubecita.core.wearsync.WearFeedSync
import net.kikin.nubecita.core.wearsync.WearSessionPublisher
import net.kikin.nubecita.core.wearsync.internal.CoilWearImageEncoder
import net.kikin.nubecita.core.wearsync.internal.PlayServicesWearDataLayer
import net.kikin.nubecita.core.wearsync.internal.WearDataLayer
import net.kikin.nubecita.core.wearsync.internal.WearImageEncoder
import javax.inject.Singleton
import kotlin.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal interface WearSyncBindsModule {
    @Binds
    fun bindWearDataLayer(impl: PlayServicesWearDataLayer): WearDataLayer

    @Binds
    fun bindWearAppPresence(impl: PlayServicesWearAppPresence): WearAppPresence

    @Binds
    fun bindWearImageEncoder(impl: CoilWearImageEncoder): WearImageEncoder
}

/**
 * Provides [WearSessionPublisher]. Plain-constructed (not `@Inject`) so its
 * constructor can stay `internal` around the [WearDataLayer] seam, like
 * `WidgetRefreshScheduler`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object WearSyncProvidesModule {
    @Provides
    @Singleton
    fun provideWearSessionPublisher(
        @ApplicationScope scope: CoroutineScope,
        sessionStateProvider: SessionStateProvider,
        dataLayer: WearDataLayer,
        clock: Clock,
    ): WearSessionPublisher = WearSessionPublisher(scope, sessionStateProvider, dataLayer, clock)

    @Provides
    @Singleton
    fun provideWearFeedSync(
        dataLayer: WearDataLayer,
        watchFeedsPreference: WatchFeedsPreference,
        pinnedFeedsRepository: PinnedFeedsRepository,
        feedRepository: FeedRepository,
        imageEncoder: WearImageEncoder,
        clock: Clock,
    ): WearFeedSync = DefaultWearFeedSync(dataLayer, watchFeedsPreference, pinnedFeedsRepository, feedRepository, imageEncoder, clock)
}
