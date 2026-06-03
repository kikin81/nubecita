package net.kikin.nubecita.feature.chats.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ChatSettingsRepository
import net.kikin.nubecita.feature.chats.impl.data.DefaultChatRepository
import net.kikin.nubecita.feature.chats.impl.data.DefaultChatSettingsRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface ChatsRepositoryModule {
    @Binds
    fun bindChatRepository(impl: DefaultChatRepository): ChatRepository

    @Binds
    fun bindChatSettingsRepository(impl: DefaultChatSettingsRepository): ChatSettingsRepository
}
