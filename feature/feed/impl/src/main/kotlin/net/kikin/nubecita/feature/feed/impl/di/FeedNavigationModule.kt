package net.kikin.nubecita.feature.feed.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalComposerLauncher
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.impl.FeedScreen
import net.kikin.nubecita.feature.feed.impl.ui.FeedDetailPlaceholder
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute

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
                        detailPlaceholder = { FeedDetailPlaceholder() },
                    ),
            ) {
                val navState = LocalMainShellNavState.current
                val launchComposer = LocalComposerLauncher.current
                FeedScreen(
                    onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                    // Profile screen does not exist yet — wired as a no-op until
                    // the profile epic lands. Replace with `navState.add(Profile(handle = …))`
                    // once the profile :impl module surfaces a handle-from-DID resolver.
                    onNavigateToAuthor = {},
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
