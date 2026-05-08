package net.kikin.nubecita.core.posts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.core.posts.internal.DefaultPostRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface PostRepositoryModule {
    @Binds
    fun bindPostRepository(impl: DefaultPostRepository): PostRepository
}
