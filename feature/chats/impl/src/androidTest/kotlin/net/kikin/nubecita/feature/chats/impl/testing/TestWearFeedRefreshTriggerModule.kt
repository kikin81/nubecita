package net.kikin.nubecita.feature.chats.impl.testing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.wearsync.WearFeedRefreshTrigger

/**
 * Test-only no-op [WearFeedRefreshTrigger]. `:core:wear-sync` declares the
 * interface and `:core:widget-sync` binds it, but this module depends only on
 * wear-sync, so its androidTest Hilt graph fails with
 * `[Dagger/MissingBinding] WearFeedRefreshTrigger` without this shim. Pulling in
 * `:core:widget-sync` instead would cascade into further app-only bindings
 * (`WidgetUpdater`, `WidgetImagePrefetcher`).
 *
 * A graph-completion shim, not a swap, so `@InstallIn` rather than
 * `@TestInstallIn`. No test here reaches the watch refresh path.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestWearFeedRefreshTriggerModule {
    @Provides
    fun provideWearFeedRefreshTrigger(): WearFeedRefreshTrigger =
        object : WearFeedRefreshTrigger {
            override suspend fun refreshFeedsNow() = Unit
        }
}
