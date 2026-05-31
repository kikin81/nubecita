package net.kikin.nubecita.feature.profile.impl.di

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.impl.EditProfileScreen
import net.kikin.nubecita.feature.profile.impl.EditProfileViewModel

/**
 * Registers the full-screen `EditProfile` sub-route on `MainShell`'s inner
 * `NavDisplay`. Mirrors `SettingsNavigationModule` but threads the
 * [EditProfile] route's pre-fill values into the ViewModel via its assisted
 * factory (same pattern as `ProfileNavigationModule`'s `entry<Profile>`).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object EditProfileNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideEditProfileEntries(): EntryProviderInstaller =
        {
            entry<EditProfile> { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<EditProfileViewModel, EditProfileViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                EditProfileScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navState.removeLast() },
                )
            }
        }
}
