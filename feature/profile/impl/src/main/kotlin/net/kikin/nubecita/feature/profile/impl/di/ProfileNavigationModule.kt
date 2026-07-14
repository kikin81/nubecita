package net.kikin.nubecita.feature.profile.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.res.stringResource
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
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.component.DetailPaneEmptyState
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.bookmarks.api.Bookmarks
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.ProfileScreen
import net.kikin.nubecita.feature.profile.impl.ProfileViewModel
import net.kikin.nubecita.feature.settings.api.Settings
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute

/**
 * Pane role for a [Profile] entry in the list-detail scene — instance-dependent
 * (nubecita-xqp7):
 *  - `Profile(handle = null)` is the own-profile **tab root** — the list-pane
 *    anchor on Medium/Expanded, so its post-taps land in the detail pane.
 *  - `Profile(handle = "...")` is a **sub-route** opened from elsewhere (e.g.
 *    tapping a post author). It must stack in the **detail** pane over the
 *    current detail, NOT re-anchor the list pane — so it is tagged
 *    `detailPane()`. Without this, a `listPane`-tagged Profile pushed onto
 *    `[Feed, PostDetail]` evicted Feed from the left pane.
 *
 * The role is keyed on `handle == null` (own profile is *only ever* the You-tab
 * root today). If own-profile ever becomes reachable as a non-root sub-route,
 * this would wrongly re-anchor the list and the discriminator must change to a
 * real "is this the tab root?" test. Cross-tab pane isolation is handled
 * separately by `ActiveTabScopedSceneStrategy` (`:app`) — these two pieces are
 * coupled: this assigns list-vs-detail *within* a tab segment, that scopes the
 * segment *to* the active tab.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun profilePaneMetadata(route: Profile): Map<String, Any> =
    if (route.handle == null) {
        ListDetailSceneStrategy.listPane(
            detailPlaceholder = {
                DetailPaneEmptyState(
                    icon = NubecitaIconName.Article,
                    message = stringResource(R.string.nubecita_detail_pane_select_post),
                )
            },
        )
    } else {
        ListDetailSceneStrategy.detailPane()
    }

/**
 * Real Profile entry. Bead D wires the screen for `Profile(handle = null)`
 * (own profile) and `Profile(handle = "...")` (other-user navigation
 * reaches the same screen; the actions-row branch for the latter is
 * Bead F territory).
 *
 * Pane-role metadata is supplied per route instance via [profilePaneMetadata]
 * so that Medium-width post-taps land in the right pane and author sub-routes
 * stack in the detail pane (nubecita-xqp7). The Settings sub-route graduated to
 * `:feature:settings:impl` in nubecita-77l — Profile still imports the
 * [Settings] NavKey from `:feature:settings:api` to push it onto the inner
 * stack, but the screen itself is provided by `SettingsNavigationModule` over
 * there.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ProfileNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideProfileEntries(): EntryProviderInstaller =
        {
            entry<Profile>(metadata = ::profilePaneMetadata) { route ->
                val navState = LocalMainShellNavState.current
                // MediaViewer is registered on the OUTER NavDisplay
                // (@OuterShell), so push it via LocalAppNavigator — pushing
                // onto MainShell's inner back stack crashes with
                // `IllegalStateException: Unknown screen MediaViewerRoute(...)`
                // because the inner NavDisplay has no handler for that key.
                // Same contract PostDetailNavigationModule uses.
                val appNavigator = LocalAppNavigator.current
                val viewModel =
                    hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                    onNavigateToProfile = { handle -> navState.add(Profile(handle = handle)) },
                    onNavigateToSettings = { navState.add(Settings) },
                    onNavigateToBookmarks = { navState.add(Bookmarks) },
                    // Cross-tab nav: switch to Chats, then push the per-conversation
                    // thread for the other user. ChatScreen resolves DID→convoId
                    // itself via `chat.bsky.convo.getConvoForMembers`.
                    onNavigateToMessage = { did ->
                        navState.addTopLevel(Chats)
                        navState.add(Chat(otherUserDid = did))
                    },
                    // Direct gallery launch: image-in-PostCard (Posts/Replies)
                    // and image media-grid cell taps skip PostDetail and
                    // open the MediaViewer carousel at the right start index.
                    onNavigateToMediaViewer = { uri, idx ->
                        appNavigator.goTo(MediaViewerRoute(postUri = uri, imageIndex = idx))
                    },
                    // Video media-grid cell taps land here instead of
                    // MediaViewer (the viewer dead-ends on "post has no
                    // images" for video embeds). VideoPlayerRoute is
                    // @OuterShell-registered so it presents fullscreen
                    // over MainShell's bottom-nav chrome.
                    onNavigateToVideoPlayer = { uri ->
                        appNavigator.goTo(VideoPlayerRoute(postUri = uri))
                    },
                    // Generic tab-internal sub-route push — pushed onto
                    // MainShell's inner back stack so the @MainShell
                    // entry provider for the key resolves it (e.g. the
                    // Report dialog provider from :feature:moderation:impl).
                    // The host-side callback shape ((NavKey) -> Unit) keeps
                    // sub-route pushes out of the screen body: the screen
                    // doesn't need to read `LocalMainShellNavState` to
                    // push (it still reads it to wire the back-handler,
                    // which is fine — back handling is host-policy that
                    // legitimately lives at the shell seam). Mirrors the
                    // canonical Nav3 modular-hilt recipe and matches
                    // FeedScreen / FeedNavigationModule (PR3 of oftc.3).
                    onNavigateTo = { key -> navState.add(key) },
                )
            }
        }
}
