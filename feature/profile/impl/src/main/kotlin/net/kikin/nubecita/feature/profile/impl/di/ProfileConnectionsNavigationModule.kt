package net.kikin.nubecita.feature.profile.impl.di

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
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.api.ProfileConnections
import net.kikin.nubecita.feature.profile.impl.ProfileConnectionsScreen
import net.kikin.nubecita.feature.profile.impl.ProfileConnectionsViewModel

/**
 * The followers and following route. Stacks in the detail pane, as a pushed
 * profile does, so opening it from the You tab's list pane keeps that pane.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ProfileConnectionsNavigationModule {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Provides
    @IntoSet
    @MainShell
    fun provideProfileConnectionsEntries(): EntryProviderInstaller =
        {
            entry<ProfileConnections>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<ProfileConnectionsViewModel, ProfileConnectionsViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                ProfileConnectionsScreen(
                    viewModel = viewModel,
                    onNavigateToProfile = { handle -> navState.add(Profile(handle = handle)) },
                    onNavigateBack = { navState.removeLast() },
                )
            }
        }
}
