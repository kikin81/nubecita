package net.kikin.nubecita.feature.chats.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.DefaultChatRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface ChatsRepositoryModule {
    @Binds
    fun bindChatRepository(impl: DefaultChatRepository): ChatRepository
}
