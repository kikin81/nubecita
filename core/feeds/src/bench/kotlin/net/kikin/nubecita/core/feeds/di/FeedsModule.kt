package net.kikin.nubecita.core.feeds.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.feeds.DefaultFeedsDataSource
import net.kikin.nubecita.core.feeds.FakePinnedFeedsRepository
import net.kikin.nubecita.core.feeds.FeedsDataSource
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FeedsModule {
    @Binds
    @Singleton
    internal abstract fun bindPinnedFeedsRepository(impl: FakePinnedFeedsRepository): PinnedFeedsRepository

    @Binds
    @Singleton
    internal abstract fun bindFeedsDataSource(impl: DefaultFeedsDataSource): FeedsDataSource
}
