package net.kikin.nubecita.feature.videos.impl.di

import androidx.hilt.navigation.compose.hiltViewModel
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
import net.kikin.nubecita.feature.videos.impl.VideoFeedViewModel

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
            entry<VideoFeed> { route ->
                val navState = LocalMainShellNavState.current
                // NavKey args reach the VM via the assisted factory (NavKey types aren't
                // reachable through SavedStateHandle) — so the feed opens at route.startIndex.
                val viewModel =
                    hiltViewModel<VideoFeedViewModel, VideoFeedViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                VideoFeedScreen(onBack = { navState.removeLast() }, viewModel = viewModel)
            }
        }
}
