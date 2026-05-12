package net.kikin.nubecita.feature.postdetail.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.postdetail.impl.PostDetailScreen
import net.kikin.nubecita.feature.postdetail.impl.PostDetailViewModel
import net.kikin.nubecita.feature.profile.api.Profile

/**
 * Provides the `@MainShell`-qualified `EntryProviderInstaller` that
 * registers [PostDetailRoute] inside `MainShell`'s inner `NavDisplay`.
 *
 * The entry block:
 *
 * 1. Reads the per-NavEntry [PostDetailViewModel] via the assisted-
 *    inject Hilt bridge — the canonical Nav 3 pattern documented at
 *    `developer.android.com`'s "Passing Arguments to ViewModels (Hilt)"
 *    recipe. The `creationCallback` hands the route's NavKey instance
 *    to the assisted-inject factory, so the VM constructor sees a
 *    typed [PostDetailRoute] (not a SavedStateHandle decode).
 *
 * 2. Wires the screen's nav callbacks to `LocalMainShellNavState`'s
 *    `add` / `removeLast` mutators per the MVI-effect convention —
 *    ViewModels MUST NOT inject the nav state holder, so the entry
 *    composable is the seam where the effect's payload gets translated
 *    into a NavDisplay-side mutation.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object PostDetailNavigationModule {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Provides
    @IntoSet
    @MainShell
    fun providePostDetailEntries(): EntryProviderInstaller =
        {
            entry<PostDetailRoute>(
                metadata = ListDetailSceneStrategy.detailPane(),
            ) { route ->
                val navState = LocalMainShellNavState.current
                // The media viewer is registered on the OUTER NavDisplay
                // (`@OuterShell` in MediaViewerNavigationModule) so it
                // escapes MainShell's NavigationSuiteScaffold chrome —
                // pushing it onto MainShell's inner back stack would leave
                // the bottom nav visible behind the fullscreen canvas.
                // Push via the outer Navigator instead.
                val appNavigator = LocalAppNavigator.current
                val viewModel =
                    hiltViewModel<PostDetailViewModel, PostDetailViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                PostDetailScreen(
                    onBack = { navState.removeLast() },
                    onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                    onNavigateToAuthor = { handle -> navState.add(Profile(handle = handle)) },
                    onNavigateToMediaViewer = { uri, index ->
                        appNavigator.goTo(MediaViewerRoute(postUri = uri, imageIndex = index))
                    },
                    viewModel = viewModel,
                )
            }
        }
}
