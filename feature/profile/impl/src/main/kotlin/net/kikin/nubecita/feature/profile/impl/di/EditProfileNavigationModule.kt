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
import net.kikin.nubecita.core.common.navigation.adaptiveDialog
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.impl.EditProfileScreen
import net.kikin.nubecita.feature.profile.impl.EditProfileViewModel

/**
 * Registers the `EditProfile` sub-route on `MainShell`'s inner `NavDisplay`.
 * Tagged with `adaptiveDialog()` metadata, so it renders full-screen on Compact
 * width and as a centered Dialog on Medium / Expanded (the
 * `AdaptiveDialogSceneStrategy` in `:app` handles the presentation). Threads the
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
            // adaptiveDialog(): full-screen on Compact, centered Dialog on
            // Medium / Expanded — the entire phone-vs-tablet opt-in. The
            // AdaptiveDialogSceneStrategy in :app reads this metadata.
            entry<EditProfile>(metadata = adaptiveDialog()) { route ->
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
