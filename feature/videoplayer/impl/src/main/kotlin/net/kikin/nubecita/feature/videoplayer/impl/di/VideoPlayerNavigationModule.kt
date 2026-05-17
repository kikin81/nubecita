package net.kikin.nubecita.feature.videoplayer.impl.di

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.core.common.navigation.OuterShell
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerScreen
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerViewModel

/**
 * Provides the `@OuterShell`-qualified `EntryProviderInstaller` that
 * registers [VideoPlayerRoute] inside the OUTER `NavDisplay`
 * (`MainNavigation` in `:app`), not inside `MainShell`'s inner
 * `NavDisplay`. The fullscreen video player is a true fullscreen
 * surface — sitting on the outer shell escapes `MainShell`'s
 * `NavigationSuiteScaffold` chrome (the bottom navigation bar / rail)
 * so the user gets an edge-to-edge canvas while video plays. Mirrors
 * the `MediaViewerNavigationModule` contract.
 *
 * The entry block reads a per-NavEntry [VideoPlayerViewModel] via
 * Hilt's assisted-inject bridge — the canonical Nav 3 pattern. The
 * `creationCallback` hands the route's NavKey to the assisted-inject
 * factory so the VM constructor sees a typed [VideoPlayerRoute] (not a
 * SavedStateHandle decode — see `ChatScreenInstrumentationTest.kt` for
 * the failure mode that motivated this pattern across the codebase).
 *
 * Pop semantics: the screen's `NavigateBack` effect resolves via the
 * outer [LocalAppNavigator] and calls `goBack()` — popping the player
 * off the outer back stack returns to `Main`, which preserves
 * `MainShell`'s inner back stack (the feed / profile / postdetail tab
 * the user was on).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object VideoPlayerNavigationModule {
    @Provides
    @IntoSet
    @OuterShell
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
