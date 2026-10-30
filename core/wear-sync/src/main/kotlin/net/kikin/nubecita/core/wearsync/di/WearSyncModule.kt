package net.kikin.nubecita.core.wearsync.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineScope
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.preferences.WatchFeedsPreference
import net.kikin.nubecita.core.wearsync.DataLayerWearDmPublisher
import net.kikin.nubecita.core.wearsync.DefaultWearFeedSync
import net.kikin.nubecita.core.wearsync.PlayServicesWearAppPresence
import net.kikin.nubecita.core.wearsync.WearActionDispatcher
import net.kikin.nubecita.core.wearsync.WearActionHandler
import net.kikin.nubecita.core.wearsync.WearAppPresence
import net.kikin.nubecita.core.wearsync.WearDmPublisher
import net.kikin.nubecita.core.wearsync.WearFeedRefreshTrigger
import net.kikin.nubecita.core.wearsync.WearFeedSync
import net.kikin.nubecita.core.wearsync.WearSessionPublisher
import net.kikin.nubecita.core.wearsync.WearSyncRequestHandler
import net.kikin.nubecita.core.wearsync.internal.CoilWearImageEncoder
import net.kikin.nubecita.core.wearsync.internal.PlayServicesWearDataLayer
import net.kikin.nubecita.core.wearsync.internal.PostActionHandler
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

    /** Watch action handlers, contributed `@IntoSet` by the modules that own each action. */
    @Multibinds
    fun wearActionHandlers(): Set<WearActionHandler>

    @Binds
    @IntoSet
    fun bindPostActionHandler(impl: PostActionHandler): WearActionHandler
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

    @Provides
    @Singleton
    fun provideWearSyncRequestHandler(
        sessionPublisher: WearSessionPublisher,
        sessionStateProvider: SessionStateProvider,
        feedSync: WearFeedSync,
        refreshTrigger: WearFeedRefreshTrigger,
        analytics: AnalyticsClient,
        clock: Clock,
    ): WearSyncRequestHandler = WearSyncRequestHandler(sessionPublisher, sessionStateProvider, feedSync, refreshTrigger, analytics, clock)

    @Provides
    @Singleton
    fun provideWearActionDispatcher(
        dataLayer: WearDataLayer,
        sessionStateProvider: SessionStateProvider,
        handlers: Set<@JvmSuppressWildcards WearActionHandler>,
        analytics: AnalyticsClient,
        clock: Clock,
    ): WearActionDispatcher = WearActionDispatcher(dataLayer, sessionStateProvider, handlers, analytics, clock)

    @Provides
    @Singleton
    fun provideWearDmPublisher(dataLayer: WearDataLayer): WearDmPublisher = DataLayerWearDmPublisher(dataLayer)
}
