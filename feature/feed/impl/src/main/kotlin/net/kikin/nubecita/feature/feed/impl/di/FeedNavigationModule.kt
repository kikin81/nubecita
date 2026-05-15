package net.kikin.nubecita.feature.feed.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.core.common.navigation.LocalComposerLauncher
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.designsystem.component.PostDetailPaneEmptyState
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.impl.FeedScreen
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile

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
                // Marks the Feed entry as the list pane in `MainShell`'s inner
                // NavDisplay's ListDetailSceneStrategy. On compact widths the
                // strategy collapses to single-pane and the placeholder is not
                // composed; on medium/expanded widths the placeholder fills the
                // detail pane until a `detailPane()`-tagged entry is pushed.
                metadata =
                    ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { PostDetailPaneEmptyState() },
                    ),
            ) {
                val navState = LocalMainShellNavState.current
                val launchComposer = LocalComposerLauncher.current
                // MediaViewer is registered on the OUTER NavDisplay
                // (@OuterShell), so push it via LocalAppNavigator — pushing
                // onto MainShell's inner back stack crashes with
                // `IllegalStateException: Unknown screen MediaViewerRoute(...)`
                // because the inner NavDisplay has no handler for that key.
                // Same contract PostDetailNavigationModule uses.
                val appNavigator = LocalAppNavigator.current
                FeedScreen(
                    onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                    onNavigateToAuthor = { handle -> navState.add(Profile(handle = handle)) },
                    // Image-in-PostCard tap skips PostDetail — open the
                    // MediaViewer directly with the carousel's start index.
                    onNavigateToMediaViewer = { uri, idx ->
                        appNavigator.goTo(MediaViewerRoute(postUri = uri, imageIndex = idx))
                    },
                    // Width-class-conditional composer launch. At Compact width
                    // the launcher pushes ComposerRoute onto the tab stack; at
                    // Medium / Expanded widths it toggles MainShell's overlay
                    // state to Open, rendering the composer in a centered
                    // Dialog. This callsite doesn't need to know which path
                    // fires — that's MainShell's call.
                    onComposeClick = { launchComposer(null) },
                    // Per-post reply tap target on every PostCard. The path
                    // does NOT pass through FeedViewModel — the screen-
                    // level handler invokes `launchComposer(replyToUri)`
                    // directly. Same width-conditional dispatch as
                    // `onComposeClick`: full-screen route at Compact,
                    // centered Dialog overlay at Medium/Expanded.
                    onReplyClick = { uri -> launchComposer(uri) },
                )
            }
        }
}
