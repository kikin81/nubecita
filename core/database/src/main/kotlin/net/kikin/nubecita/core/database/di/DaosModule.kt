package net.kikin.nubecita.core.database.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provides each DAO from the singleton [NubecitaDatabase]. DAOs are not
 * separately scoped — Room caches them on the database instance, which
 * is `@Singleton`.
 *
 * Empty at v1; each `@Provides fun providesFooDao(db: NubecitaDatabase): FooDao
 * = db.fooDao()` lands alongside the entity/DAO it exposes.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DaosModule
