package net.kikin.nubecita.feature.feed.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.feed.impl.data.DefaultFeedRepository
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface FeedRepositoryModule {
    @Binds
    fun bindFeedRepository(impl: DefaultFeedRepository): FeedRepository
}
