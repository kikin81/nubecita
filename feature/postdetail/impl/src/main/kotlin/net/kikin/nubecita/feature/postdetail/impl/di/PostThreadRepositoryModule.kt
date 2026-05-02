package net.kikin.nubecita.feature.postdetail.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.postdetail.impl.data.DefaultPostThreadRepository
import net.kikin.nubecita.feature.postdetail.impl.data.PostThreadRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface PostThreadRepositoryModule {
    @Binds
    fun bindPostThreadRepository(impl: DefaultPostThreadRepository): PostThreadRepository
}
