package net.kikin.nubecita.feature.mediaviewer.impl.di

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.mediaviewer.impl.MediaViewerScreen
import net.kikin.nubecita.feature.mediaviewer.impl.MediaViewerViewModel

/**
 * Provides the `@MainShell`-qualified `EntryProviderInstaller` that
 * registers [MediaViewerRoute] inside `MainShell`'s inner `NavDisplay`.
 *
 * Mirrors `PostDetailNavigationModule`'s shape: the entry block resolves
 * the per-NavEntry [MediaViewerViewModel] via Hilt's assisted-inject
 * bridge (`hiltViewModel<VM, Factory>(creationCallback)`) so the
 * `MediaViewerRoute` flows from the back-stack key into the VM
 * constructor without a `SavedStateHandle` decode step.
 *
 * The screen's `onDismiss` callback wires to `LocalMainShellNavState.removeLast()`
 * — keeping the ViewModel free of any `CompositionLocal` reach-in per
 * the `CLAUDE.md` MVI rule.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object MediaViewerNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideMediaViewerEntries(): EntryProviderInstaller =
        {
            entry<MediaViewerRoute> { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<MediaViewerViewModel, MediaViewerViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                MediaViewerScreen(
                    onDismiss = { navState.removeLast() },
                    viewModel = viewModel,
                )
            }
        }
}
