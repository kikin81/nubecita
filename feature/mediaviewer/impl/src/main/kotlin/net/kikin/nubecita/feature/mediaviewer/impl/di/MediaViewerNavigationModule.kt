package net.kikin.nubecita.feature.mediaviewer.impl.di

import androidx.compose.runtime.CompositionLocalProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalAnimatedVisibilityScope
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.core.common.navigation.OuterShell
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.mediaviewer.impl.MediaViewerScreen
import net.kikin.nubecita.feature.mediaviewer.impl.MediaViewerViewModel

/**
 * Provides the `@OuterShell`-qualified `EntryProviderInstaller` that
 * registers [MediaViewerRoute] inside the OUTER `NavDisplay`
 * (`MainNavigation` in `:app`), not inside `MainShell`'s inner
 * `NavDisplay`. The viewer is a fullscreen modal — sitting on the outer
 * shell escapes `MainShell`'s `NavigationSuiteScaffold` chrome (the
 * bottom navigation bar / rail) so the user gets a true fullscreen
 * canvas while images are open.
 *
 * Pop semantics: the screen's `onDismiss` callback reads the outer
 * [LocalAppNavigator] and calls `goBack()` — popping the viewer off the
 * outer back stack returns to `Main`, which preserves `MainShell`'s
 * inner back stack including the post-detail screen the user was on.
 *
 * The push site (`PostDetailNavigationModule`) also routes through
 * [LocalAppNavigator] — see that module for the push contract.
 *
 * The entry block resolves the per-NavEntry [MediaViewerViewModel] via
 * Hilt's assisted-inject bridge (`hiltViewModel<VM, Factory>(creationCallback)`)
 * so the `MediaViewerRoute` flows from the back-stack key into the VM
 * constructor without a `SavedStateHandle` decode step.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object MediaViewerNavigationModule {
    @Provides
    @IntoSet
    @OuterShell
    fun provideMediaViewerEntries(): EntryProviderInstaller =
        {
            entry<MediaViewerRoute> { route ->
                val navigator = LocalAppNavigator.current
                val viewModel =
                    hiltViewModel<MediaViewerViewModel, MediaViewerViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                // The viewer's own half of the feed → viewer shared image
                // transition. This scope comes from the OUTER NavDisplay's
                // AnimatedContent — the same one `entry<Main>` in Navigation.kt
                // provides to the post cards underneath — so the two halves sit
                // in one transition and the shared element can match.
                CompositionLocalProvider(
                    LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
                ) {
                    MediaViewerScreen(
                        onDismiss = { navigator.goBack() },
                        onOpenInMainShell = navigator::closeAndOpenInMainShell,
                        // The composer is registered on both shells, so it can
                        // open above the viewer and return to it. Anything else
                        // only exists inside MainShell.
                        onNavigate = { key ->
                            if (key is ComposerRoute) navigator.goTo(key) else navigator.closeAndOpenInMainShell(key)
                        },
                        viewModel = viewModel,
                    )
                }
            }
        }
}
