package net.kikin.nubecita.core.feeds.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.feeds.DefaultFeedsDataSource
import net.kikin.nubecita.core.feeds.DefaultPinnedFeedsRepository
import net.kikin.nubecita.core.feeds.FeedsDataSource
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import javax.inject.Singleton

// NOT `internal`: downstream instrumentation tests reach this module via
// `@TestInstallIn(replaces = [FeedsModule::class])`, which requires the
// module type to be addressable from outside `:core:feeds`. The `@Binds`
// methods stay `internal` (Hilt's generated factory lives in this module).
// Mirrors `core/profile/.../ActorProfileModule`.
@Module
@InstallIn(SingletonComponent::class)
abstract class FeedsModule {
    @Binds
    @Singleton
    internal abstract fun bindPinnedFeedsRepository(impl: DefaultPinnedFeedsRepository): PinnedFeedsRepository

    @Binds
    @Singleton
    internal abstract fun bindFeedsDataSource(impl: DefaultFeedsDataSource): FeedsDataSource
}
