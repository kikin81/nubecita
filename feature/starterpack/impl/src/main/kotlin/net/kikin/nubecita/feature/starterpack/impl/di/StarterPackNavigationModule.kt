package net.kikin.nubecita.feature.starterpack.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.starterpack.api.StarterPack
import net.kikin.nubecita.feature.starterpack.impl.StarterPackScreen
import net.kikin.nubecita.feature.starterpack.impl.StarterPackViewModel

/**
 * Registers the [StarterPack] route on the `@MainShell` entry-provider multibinding.
 *
 * Like Hashtag and Profile, the pack is a sub-route pushed from any surface that lists packs,
 * so it stacks in the DETAIL pane rather than re-anchoring the list pane: beside the list on a
 * wide window, full-screen on a phone. The profiles and feeds it opens stack above it.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object StarterPackNavigationModule {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Provides
    @IntoSet
    @MainShell
    fun provideStarterPackEntries(): EntryProviderInstaller =
        {
            entry<StarterPack>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<StarterPackViewModel, StarterPackViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                StarterPackScreen(viewModel = viewModel, onBack = { navState.removeLast() })
            }
        }
}
