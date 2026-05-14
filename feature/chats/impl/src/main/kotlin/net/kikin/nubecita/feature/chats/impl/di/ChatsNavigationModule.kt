package net.kikin.nubecita.feature.chats.impl.di

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.chats.impl.ChatScreen
import net.kikin.nubecita.feature.chats.impl.ChatViewModel
import net.kikin.nubecita.feature.chats.impl.ChatsScreen

@Module
@InstallIn(SingletonComponent::class)
internal object ChatsNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideChatsEntries(): EntryProviderInstaller =
        {
            entry<Chats> {
                val navState = LocalMainShellNavState.current
                ChatsScreen(
                    onNavigateToChat = { did -> navState.add(Chat(otherUserDid = did)) },
                )
            }
            entry<Chat> { chat ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<ChatViewModel, ChatViewModel.Factory>(
                        creationCallback = { factory -> factory.create(chat) },
                    )
                ChatScreen(viewModel = viewModel, onNavigateBack = { navState.removeLast() })
            }
        }
}
