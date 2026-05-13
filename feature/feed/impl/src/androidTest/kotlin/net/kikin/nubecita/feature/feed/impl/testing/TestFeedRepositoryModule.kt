package net.kikin.nubecita.feature.feed.impl.testing

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository
import net.kikin.nubecita.feature.feed.impl.di.FeedRepositoryModule

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [FeedRepositoryModule::class],
)
internal interface TestFeedRepositoryModule {
    @Binds
    fun bindFakeFeedRepository(impl: FakeFeedRepository): FeedRepository
}
