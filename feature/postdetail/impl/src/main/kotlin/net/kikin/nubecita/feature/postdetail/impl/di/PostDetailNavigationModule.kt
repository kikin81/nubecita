package net.kikin.nubecita.feature.postdetail.impl.di

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
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.postdetail.impl.PostDetailScreen
import net.kikin.nubecita.feature.postdetail.impl.PostDetailViewModel

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
    @Provides
    @IntoSet
    @MainShell
    fun providePostDetailEntries(): EntryProviderInstaller =
        {
            entry<PostDetailRoute> { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<PostDetailViewModel, PostDetailViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                PostDetailScreen(
                    onBack = { navState.removeLast() },
                    onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                    // Profile screen does not exist yet — wired as a no-op
                    // until the profile epic lands. Replace with
                    // `navState.add(Profile(handle = …))` once the profile
                    // :impl module surfaces a handle-from-DID resolver.
                    onNavigateToAuthor = {},
                    onNavigateToMediaViewer = { uri, index ->
                        navState.add(MediaViewerRoute(postUri = uri, imageIndex = index))
                    },
                    viewModel = viewModel,
                )
            }
        }
}
