package net.kikin.nubecita.feature.feed.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
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
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.component.DetailPaneEmptyState
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.api.FeedView
import net.kikin.nubecita.feature.feed.impl.FeedHost
import net.kikin.nubecita.feature.feed.impl.FeedPinViewModel
import net.kikin.nubecita.feature.feed.impl.FeedViewModel
import net.kikin.nubecita.feature.feed.impl.FeedViewScreen
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute

@Module
@InstallIn(SingletonComponent::class)
internal object FeedNavigationModule {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Provides
    @IntoSet
    @MainShell
    fun provideFeedEntries(): EntryProviderInstaller =
        {
            entry<Feed>(
                metadata =
                    ListDetailSceneStrategy.listPane(
                        detailPlaceholder = {
                            DetailPaneEmptyState(
                                icon = NubecitaIconName.Article,
                                message = stringResource(R.string.nubecita_detail_pane_select_post),
                            )
                        },
                    ),
            ) {
                val navState = LocalMainShellNavState.current
                val isTopRoute = navState.backStack.lastOrNull() == Feed
                val appNavigator = LocalAppNavigator.current
                CompositionLocalProvider(
                    LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
                ) {
                    FeedHost(
                        isTopRoute = isTopRoute,
                        onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                        onNavigateToAuthor = { handle -> navState.add(Profile(handle = handle)) },
                        onNavigateToMediaViewer = { uri, idx ->
                            appNavigator.goTo(MediaViewerRoute(postUri = uri, imageIndex = idx))
                        },
                        onNavigateToVideoPlayer = { uri ->
                            appNavigator.goTo(VideoPlayerRoute(postUri = uri))
                        },
                        onNavigateTo = { key -> navState.add(key) },
                        onComposeClick = { navState.add(ComposerRoute()) },
                        onReplyClick = { uri -> navState.add(ComposerRoute(replyToUri = uri)) },
                        onQuoteClick = { uri -> navState.add(ComposerRoute(quotePostUri = uri)) },
                    )
                }
            }
        }

    @Provides
    @IntoSet
    @MainShell
    fun provideFeedViewEntries(): EntryProviderInstaller =
        {
            entry<FeedView> { route ->
                val navState = LocalMainShellNavState.current
                val appNavigator = LocalAppNavigator.current
                CompositionLocalProvider(
                    LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
                ) {
                    FeedViewScreen(
                        feedUri = route.feedUri,
                        displayName = route.displayName,
                        onBack = { navState.removeLast() },
                        onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                        onNavigateToAuthor = { handle -> navState.add(Profile(handle = handle)) },
                        onNavigateToMediaViewer = { uri, idx ->
                            appNavigator.goTo(MediaViewerRoute(postUri = uri, imageIndex = idx))
                        },
                        onNavigateToVideoPlayer = { uri ->
                            appNavigator.goTo(VideoPlayerRoute(postUri = uri))
                        },
                        onNavigateTo = { key -> navState.add(key) },
                        onReplyClick = { uri -> navState.add(ComposerRoute(replyToUri = uri)) },
                        onQuoteClick = { uri -> navState.add(ComposerRoute(quotePostUri = uri)) },
                        viewModel = hiltViewModel<FeedViewModel>(),
                        pinViewModel =
                            hiltViewModel<FeedPinViewModel, FeedPinViewModel.Factory>(
                                creationCallback = { factory -> factory.create(route.feedUri) },
                            ),
                    )
                }
            }
        }
}
