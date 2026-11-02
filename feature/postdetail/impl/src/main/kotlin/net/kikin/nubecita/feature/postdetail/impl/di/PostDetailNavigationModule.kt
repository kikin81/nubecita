package net.kikin.nubecita.feature.postdetail.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
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
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.postdetail.impl.PostDetailScreen
import net.kikin.nubecita.feature.postdetail.impl.PostDetailViewModel
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute

/**
 * Provides the `@MainShell`-qualified `EntryProviderInstaller` that
 * registers [PostDetailRoute] inside `MainShell`'s inner `NavDisplay`.
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
                val appNavigator = LocalAppNavigator.current
                val viewModel =
                    hiltViewModel<PostDetailViewModel, PostDetailViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                CompositionLocalProvider(
                    LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
                ) {
                    PostDetailScreen(
                        onBack = { navState.removeLast() },
                        onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                        onNavigateToAuthor = { handle -> navState.add(Profile(handle = handle)) },
                        onNavigateToMediaViewer = { uri, index ->
                            appNavigator.goTo(MediaViewerRoute(postUri = uri, imageIndex = index))
                        },
                        onNavigateToVideoPlayer = { uri ->
                            appNavigator.goTo(VideoPlayerRoute(postUri = uri))
                        },
                        onReplyClick = { uri -> navState.add(ComposerRoute(replyToUri = uri)) },
                        onQuoteClick = { uri -> navState.add(ComposerRoute(quotePostUri = uri)) },
                        onNavigateTo = { key -> navState.add(key) },
                        viewModel = viewModel,
                    )
                }
            }
        }
}
