package net.kikin.nubecita.feature.videoplayer.impl.di

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerScreen
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerViewModel

/**
 * Registers the fullscreen player route on the `@MainShell`-qualified
 * `EntryProviderInstaller` multibinding so `MainShell`'s inner
 * `NavDisplay` can resolve [VideoPlayerRoute] to [VideoPlayerScreen].
 *
 * The entry block reads a per-NavEntry [VideoPlayerViewModel] via the
 * assisted-inject Hilt bridge — the canonical Nav 3 pattern documented
 * at `developer.android.com`'s "Passing Arguments to ViewModels (Hilt)"
 * recipe. The `creationCallback` hands the route's NavKey to the
 * assisted-inject factory so the VM constructor sees a typed
 * [VideoPlayerRoute] (not a SavedStateHandle decode — see
 * `ChatScreenInstrumentationTest.kt` for the failure mode that motivated
 * this pattern across the codebase).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object VideoPlayerNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideVideoPlayerEntries(): EntryProviderInstaller =
        {
            entry<VideoPlayerRoute> { route ->
                val viewModel =
                    hiltViewModel<VideoPlayerViewModel, VideoPlayerViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                VideoPlayerScreen(viewModel = viewModel)
            }
        }
}
