package net.kikin.nubecita.core.database.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.database.NubecitaDatabase
import net.kikin.nubecita.core.database.dao.ActorDao
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPartitionStateDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.dao.RecentSearchDao
import net.kikin.nubecita.core.database.dao.SavedFeedDao

/**
 * Provides each DAO from the singleton [NubecitaDatabase]. DAOs are not
 * separately scoped — Room caches them on the database instance, which
 * is `@Singleton`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DaosModule {
    @Provides
    fun providesRecentSearchDao(database: NubecitaDatabase): RecentSearchDao = database.recentSearchDao()

    @Provides
    fun providesActorDao(database: NubecitaDatabase): ActorDao = database.actorDao()

    @Provides
    fun providesFeedPostDao(database: NubecitaDatabase): FeedPostDao = database.feedPostDao()

    @Provides
    fun providesCachedPostDao(database: NubecitaDatabase): CachedPostDao = database.cachedPostDao()

    @Provides
    fun providesFeedItemDao(database: NubecitaDatabase): FeedItemDao = database.feedItemDao()

    @Provides
    fun providesFeedPartitionStateDao(database: NubecitaDatabase): FeedPartitionStateDao = database.feedPartitionStateDao()

    @Provides
    fun providesFeedRemoteKeyDao(database: NubecitaDatabase): FeedRemoteKeyDao = database.feedRemoteKeyDao()

    @Provides
    fun providesSavedFeedDao(database: NubecitaDatabase): SavedFeedDao = database.savedFeedDao()
}
