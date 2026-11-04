package net.kikin.nubecita.feature.feed.impl.testing

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feedcache.di.FeedRepositoryModule

/**
 * Serves the feed from an in-memory fake instead of Room + the network.
 *
 * It replaces `:core:feed-cache`'s production module now, not a
 * feature-local one: the screen reads its pages from the cache (D1), so the
 * seam a UI test has to fake moved there with it. Faking at the repository
 * rather than at the `PagingSource` keeps these tests about rendering and tap
 * routing, which is what they assert.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [FeedRepositoryModule::class],
)
internal interface TestFeedRepositoryModule {
    @Binds
    fun bindFakeFeedRepository(impl: FakeFeedRepository): FeedRepository
}
