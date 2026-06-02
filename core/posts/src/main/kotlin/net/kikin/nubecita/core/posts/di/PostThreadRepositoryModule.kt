package net.kikin.nubecita.core.posts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posts.PostThreadRepository
import net.kikin.nubecita.core.posts.internal.DefaultPostThreadRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface PostThreadRepositoryModule {
    @Binds
    fun bindPostThreadRepository(impl: DefaultPostThreadRepository): PostThreadRepository
}
