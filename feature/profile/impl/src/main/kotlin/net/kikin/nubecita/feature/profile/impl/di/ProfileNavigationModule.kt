package net.kikin.nubecita.feature.profile.impl.di

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
import net.kikin.nubecita.designsystem.component.PostDetailPaneEmptyState
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.api.Settings
import net.kikin.nubecita.feature.profile.impl.ProfileScreen
import net.kikin.nubecita.feature.profile.impl.ProfileViewModel
import net.kikin.nubecita.feature.profile.impl.SettingsStubScreen

/**
 * Real Profile entry. Bead D wires the screen for `Profile(handle = null)`
 * (own profile) and `Profile(handle = "...")` (other-user navigation
 * reaches the same screen; the actions-row branch for the latter is
 * Bead F territory).
 *
 * `ListDetailSceneStrategy.listPane{}` metadata is applied here so that
 * Medium-width post-taps land in the right pane. The Settings entry now
 * resolves to [SettingsStubScreen] with a back-arrow that pops the inner
 * nav stack.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ProfileNavigationModule {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Provides
    @IntoSet
    @MainShell
    fun provideProfileEntries(): EntryProviderInstaller =
        {
            entry<Profile>(
                metadata =
                    ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { PostDetailPaneEmptyState() },
                    ),
            ) { route ->
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
                    // Cross-tab nav: switch to Chats, then push the per-conversation
                    // thread for the other user. ChatScreen resolves DID→convoId
                    // itself via `chat.bsky.convo.getConvoForMembers`.
                    onNavigateToMessage = { did ->
                        navState.addTopLevel(Chats)
                        navState.add(Chat(otherUserDid = did))
                    },
                    // Direct gallery launch: image-in-PostCard (Posts/Replies)
                    // and media-grid cell (Media tab) taps skip PostDetail and
                    // open the MediaViewer carousel at the right start index.
                    onNavigateToMediaViewer = { uri, idx ->
                        appNavigator.goTo(MediaViewerRoute(postUri = uri, imageIndex = idx))
                    },
                )
            }
        }

    @Provides
    @IntoSet
    @MainShell
    fun provideSettingsEntries(): EntryProviderInstaller =
        {
            entry<Settings> {
                val navState = LocalMainShellNavState.current
                SettingsStubScreen(onBack = { navState.removeLast() })
            }
        }
}
