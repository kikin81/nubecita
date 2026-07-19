package net.kikin.nubecita.feature.videos.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.videos.api.VideoFeed
import net.kikin.nubecita.feature.videos.impl.VideoFeedScreen

/**
 * `@MainShell` provider for the [VideoFeed] full-screen vertical video feed.
 * Pushed onto `MainShell`'s inner back stack (e.g. by the Trending Videos
 * carousel in a later slice) via `navState.add(VideoFeed(index))`; the back
 * gesture / arrow pops the same stack.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object VideosNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideVideosEntries(): EntryProviderInstaller =
        {
            entry<VideoFeed> {
                val navState = LocalMainShellNavState.current
                VideoFeedScreen(onBack = { navState.removeLast() })
            }
        }
}
