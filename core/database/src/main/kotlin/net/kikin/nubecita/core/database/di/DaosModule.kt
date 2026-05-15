package net.kikin.nubecita.core.database.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.database.NubecitaDatabase
import net.kikin.nubecita.core.database.dao.RecentSearchDao

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
}
