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
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.ProfileScreen
import net.kikin.nubecita.feature.profile.impl.ProfileViewModel

/**
 * Real Profile entry. Bead D wires the screen for `Profile(handle = null)`
 * (own profile) and `Profile(handle = "...")` (other-user navigation
 * reaches the same screen; the actions-row branch for the latter is
 * Bead F territory).
 *
 * `ListDetailSceneStrategy.listPane{}` metadata is NOT applied here —
 * Bead F adds it so Medium-width post-taps land in the right pane.
 * Without the metadata, post-taps push PostDetailRoute onto the
 * back stack and replace the profile screen full-screen on all
 * widths in Bead D — the same behavior the previous :app placeholder
 * exhibited.
 *
 * The Settings entry stays inert in Bead D — Bead F wires the
 * Settings stub Composable + its overflow-menu entry point on the
 * profile screen.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ProfileNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideProfileEntries(): EntryProviderInstaller =
        {
            entry<Profile> { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                    onNavigateToProfile = { handle -> navState.add(Profile(handle = handle)) },
                )
            }
        }

    @Provides
    @IntoSet
    @MainShell
    fun provideSettingsEntries(): EntryProviderInstaller =
        {
            // Inert in Bead D. Bead F wires the Settings stub Composable
            // here and removes the :app-side Settings placeholder.
        }
}
