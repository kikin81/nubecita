package net.kikin.nubecita.feature.feed.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.feed.impl.data.DefaultLikeRepostRepository
import net.kikin.nubecita.feature.feed.impl.data.LikeRepostRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface LikeRepostRepositoryModule {
    @Binds
    fun bindLikeRepostRepository(impl: DefaultLikeRepostRepository): LikeRepostRepository
}
