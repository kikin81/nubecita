package net.kikin.nubecita.feature.chats.impl.di

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
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.core.common.navigation.adaptiveDialog
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.component.DetailPaneEmptyState
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.ChatSettings
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.chats.api.NewChat
import net.kikin.nubecita.feature.chats.impl.ChatScreen
import net.kikin.nubecita.feature.chats.impl.ChatSettingsScreen
import net.kikin.nubecita.feature.chats.impl.ChatSettingsViewModel
import net.kikin.nubecita.feature.chats.impl.ChatViewModel
import net.kikin.nubecita.feature.chats.impl.ChatsScreen
import net.kikin.nubecita.feature.chats.impl.NewChatScreen
import net.kikin.nubecita.feature.chats.impl.selectedConvoId
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute

@Module
@InstallIn(SingletonComponent::class)
internal object ChatsNavigationModule {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Provides
    @IntoSet
    @MainShell
    fun provideChatsEntries(): EntryProviderInstaller =
        {
            entry<Chats>(
                // List pane of MainShell's inner NavDisplay ListDetailSceneStrategy.
                // Compact widths collapse to single-pane (placeholder never
                // composed); Medium/Expanded show the convo list left with the
                // placeholder filling the detail pane until a Chat thread is
                // pushed. Same opt-in Feed/Profile use.
                metadata =
                    ListDetailSceneStrategy.listPane(
                        detailPlaceholder = {
                            DetailPaneEmptyState(
                                icon = NubecitaIconName.ChatBubble,
                                message = stringResource(R.string.nubecita_detail_pane_select_conversation),
                            )
                        },
                    ),
            ) {
                val navState = LocalMainShellNavState.current
                // The open thread's convoId, derived from the back stack — drives
                // the list pane's selected-row highlight on Medium/Expanded. Reading
                // `backStack` (a SnapshotStateList) here recomposes the list on
                // navigation. Null on Compact's list view (no thread on the
                // stack) and harmless when a thread covers the list full-screen.
                val selectedConvoId = selectedConvoId(navState.backStack)
                ChatsScreen(
                    // replaceTop (not add): selecting a conversation swaps the
                    // open detail pane instead of stacking threads, so Back from
                    // a thread returns to the list/placeholder. At the list home
                    // (`[Chats]`, e.g. on a phone) replaceTop degrades to a plain
                    // push, so Compact behavior is unchanged.
                    onNavigateToChat = { convoId -> navState.replaceTop(Chat(convoId = convoId)) },
                    onNewChat = { navState.add(NewChat) },
                    selectedConvoId = selectedConvoId,
                    onNavigateToChatSettings = { navState.add(ChatSettings) },
                    // Contextual-action sub-routes (Profile / Report / Block) —
                    // a plain push onto the inner back stack. Profile is a tab
                    // sub-route; Report / Block carry adaptiveDialog() metadata
                    // from :feature:moderation, so they render full-screen on
                    // Compact and as a centered Dialog on Medium / Expanded.
                    onNavigateTo = { key -> navState.add(key) },
                )
            }
            entry<NewChat> {
                val navState = LocalMainShellNavState.current
                NewChatScreen(onBack = { navState.removeLast() })
            }
            // adaptiveDialog(): full-screen on Compact, centered Dialog on
            // Medium / Expanded — the AdaptiveDialogSceneStrategy in :app reads
            // this metadata. Plain @HiltViewModel (no assisted factory): the
            // account-global declaration carries no per-route arguments.
            entry<ChatSettings>(metadata = adaptiveDialog()) {
                val navState = LocalMainShellNavState.current
                ChatSettingsScreen(
                    viewModel = hiltViewModel<ChatSettingsViewModel>(),
                    onNavigateBack = { navState.removeLast() },
                )
            }
            entry<Chat>(
                // Detail pane of the list-detail strategy — renders in the right
                // pane on Medium/Expanded, full-screen on Compact.
                metadata = ListDetailSceneStrategy.detailPane(),
            ) { chat ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<ChatViewModel, ChatViewModel.Factory>(
                        creationCallback = { factory -> factory.create(chat) },
                    )
                ChatScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navState.removeLast() },
                    onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                )
            }
        }
}
