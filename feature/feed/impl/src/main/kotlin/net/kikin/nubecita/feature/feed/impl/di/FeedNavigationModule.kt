package net.kikin.nubecita.feature.feed.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.res.stringResource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.component.DetailPaneEmptyState
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.impl.FeedHost
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
                // Marks the Feed entry as the list pane in `MainShell`'s inner
                // NavDisplay's ListDetailSceneStrategy. On compact widths the
                // strategy collapses to single-pane and the placeholder is not
                // composed; on medium/expanded widths the placeholder fills the
                // detail pane until a `detailPane()`-tagged entry is pushed.
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
                // MediaViewer is registered on the OUTER NavDisplay
                // (@OuterShell), so push it via LocalAppNavigator — pushing
                // onto MainShell's inner back stack crashes with
                // `IllegalStateException: Unknown screen MediaViewerRoute(...)`
                // because the inner NavDisplay has no handler for that key.
                // Same contract PostDetailNavigationModule uses.
                val appNavigator = LocalAppNavigator.current
                FeedHost(
                    onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                    onNavigateToAuthor = { handle -> navState.add(Profile(handle = handle)) },
                    // Image-in-PostCard tap skips PostDetail — open the
                    // MediaViewer directly with the carousel's start index.
                    onNavigateToMediaViewer = { uri, idx ->
                        appNavigator.goTo(MediaViewerRoute(postUri = uri, imageIndex = idx))
                    },
                    // Video-in-PostCard tap skips PostDetail. The
                    // fullscreen player route is registered on the OUTER
                    // NavDisplay (@OuterShell, same as MediaViewer), so
                    // push via appNavigator — pushing onto MainShell's
                    // inner back stack would leave the bottom-nav chrome
                    // visible behind the supposedly fullscreen canvas.
                    // SharedVideoPlayer is process-scoped, so the
                    // feed → fullscreen instance-transfer holds across
                    // the shell boundary too.
                    onNavigateToVideoPlayer = { uri ->
                        appNavigator.goTo(VideoPlayerRoute(postUri = uri))
                    },
                    // Generic tab-internal sub-route push — pushed onto
                    // MainShell's inner back stack so the @MainShell
                    // entry provider for the key resolves it (e.g. the
                    // Report dialog provider from :feature:moderation:impl).
                    // Keeping the host-side callback shape ((NavKey) -> Unit)
                    // means the screen doesn't directly read
                    // LocalMainShellNavState — mirrors the canonical
                    // Nav3 modular-hilt recipe and matches the other
                    // nav callbacks above.
                    onNavigateTo = { key -> navState.add(key) },
                    // Just push ComposerRoute — its entry is tagged
                    // `adaptiveDialog()`, so AdaptiveDialogSceneStrategy renders
                    // it full-screen at Compact and as a centered Dialog at
                    // Medium/Expanded. No width branching here.
                    onComposeClick = { navState.add(ComposerRoute()) },
                    // Per-post reply tap target on every PostCard. Does NOT pass
                    // through FeedViewModel — the screen-level handler pushes
                    // ComposerRoute(replyToUri) directly; same adaptive dialog
                    // presentation as onComposeClick.
                    onReplyClick = { uri -> navState.add(ComposerRoute(replyToUri = uri)) },
                )
            }
        }
}
