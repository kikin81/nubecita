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
                CompositionLocalProvider(
                    LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
                ) {
                    MediaViewerScreen(
                        onDismiss = { navigator.goBack() },
                        viewModel = viewModel,
                    )
                }
            }
        }
}
