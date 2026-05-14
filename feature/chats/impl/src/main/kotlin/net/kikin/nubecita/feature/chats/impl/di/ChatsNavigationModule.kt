package net.kikin.nubecita.feature.chats.impl.di

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
            entry<Chat> { _ ->
                val navState = LocalMainShellNavState.current
                ChatScreen(onNavigateBack = { navState.removeLast() })
            }
        }
}
