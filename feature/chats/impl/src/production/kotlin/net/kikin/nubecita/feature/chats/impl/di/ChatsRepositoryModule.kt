package net.kikin.nubecita.feature.chats.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ChatSettingsRepository
import net.kikin.nubecita.feature.chats.impl.data.DefaultChatRepository
import net.kikin.nubecita.feature.chats.impl.data.DefaultChatSettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ChatsRepositoryModule {
    // @Singleton so both the inbox (ChatsViewModel) and a thread (ChatViewModel)
    // share ONE DefaultChatRepository — and therefore one convo cache, the single
    // source of truth that makes the inbox update live when a thread sends.
    @Binds
    @Singleton
    fun bindChatRepository(impl: DefaultChatRepository): ChatRepository

    @Binds
    fun bindChatSettingsRepository(impl: DefaultChatSettingsRepository): ChatSettingsRepository
}
