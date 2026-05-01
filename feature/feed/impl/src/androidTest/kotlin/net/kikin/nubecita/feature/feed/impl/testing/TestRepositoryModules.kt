package net.kikin.nubecita.feature.feed.impl.testing

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository
import net.kikin.nubecita.feature.feed.impl.data.LikeRepostRepository
import net.kikin.nubecita.feature.feed.impl.di.FeedRepositoryModule
import net.kikin.nubecita.feature.feed.impl.di.LikeRepostRepositoryModule

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [FeedRepositoryModule::class],
)
internal interface TestFeedRepositoryModule {
    @Binds
    fun bindFakeFeedRepository(impl: FakeFeedRepository): FeedRepository
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [LikeRepostRepositoryModule::class],
)
internal interface TestLikeRepostRepositoryModule {
    @Binds
    fun bindFakeLikeRepostRepository(impl: FakeLikeRepostRepository): LikeRepostRepository
}
