package net.kikin.nubecita.core.posting.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posting.ExternalLinkMetadataRepository
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.internal.CardyBExternalLinkMetadataRepository
import net.kikin.nubecita.core.posting.internal.DefaultPostingRepository
import javax.inject.Singleton

/**
 * Production bindings for `:core:posting`'s network-backed repositories:
 * [PostingRepository] → the real [DefaultPostingRepository] (`createRecord`
 * XRPC), and [ExternalLinkMetadataRepository] → [CardyBExternalLinkMetadataRepository]
 * (CardyB HTTP).
 *
 * Flavor-split counterpart at `core/posting/src/bench/.../di/PostingRepositoryModule.kt`
 * binds the network-free `BenchFake*` doubles, keeping the bench flavor offline.
 * The shared FQN (`net.kikin.nubecita.core.posting.di.PostingRepositoryModule`)
 * means AGP source-set merging picks exactly one per `environment` flavor — they
 * cannot coexist on a single variant's classpath. Mirrors `:core:posts`'
 * production/bench `PostRepositoryModule` split.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface PostingRepositoryModule {
    @Binds
    @Singleton
    fun bindPostingRepository(impl: DefaultPostingRepository): PostingRepository

    @Binds
    @Singleton
    fun bindExternalLinkMetadataRepository(
        impl: CardyBExternalLinkMetadataRepository,
    ): ExternalLinkMetadataRepository
}
