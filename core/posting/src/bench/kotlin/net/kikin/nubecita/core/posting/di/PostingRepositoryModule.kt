package net.kikin.nubecita.core.posting.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.internal.BenchFakePostingRepository
import javax.inject.Singleton

/**
 * Bench-flavor counterpart to the production [PostingRepositoryModule] at
 * `core/posting/src/production/.../di/PostingRepositoryModule.kt`.
 *
 * AGP source-set selection picks exactly one of the two per variant:
 * - `productionDebug` / `productionRelease` → production module
 *   (binds `DefaultPostingRepository`, real `createRecord` XRPC).
 * - `benchDebug` / `benchRelease` → this module (binds
 *   [BenchFakePostingRepository], a network-free synthetic success).
 *
 * Without this, the bench build's `FakeXrpcClientProvider.authenticated()`
 * throws, so any post/quote submit fails with `ComposerError.Unauthorized`.
 * Binding the fake lets the offline bench app exercise the full compose →
 * submit flow (post, reply, quote) on a device with no network.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface PostingRepositoryModule {
    @Binds
    @Singleton
    fun bindPostingRepository(impl: BenchFakePostingRepository): PostingRepository
}
