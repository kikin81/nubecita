package net.kikin.nubecita.core.postinteractions.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.internal.DefaultLikeRepostRepository
import net.kikin.nubecita.core.postinteractions.internal.DefaultPostInteractionsCache
import javax.inject.Singleton

/**
 * Hilt bindings for [PostInteractionsCache] and [LikeRepostRepository].
 *
 * The cache is `@Singleton`-scoped (matches the class-level annotation on
 * [DefaultPostInteractionsCache]) so all VMs across the app share one
 * canonical state map. The repository is also `@Singleton` to avoid
 * re-wrapping the atproto service per injection point.
 *
 * The class is publicly addressable (not `internal`) so future test
 * modules in any feature module can replace it via
 * `@TestInstallIn(replaces = [PostInteractionsModule::class])`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PostInteractionsModule {
    @Binds
    @Singleton
    internal abstract fun bindPostInteractionsCache(
        impl: DefaultPostInteractionsCache,
    ): PostInteractionsCache

    @Binds
    @Singleton
    internal abstract fun bindLikeRepostRepository(
        impl: DefaultLikeRepostRepository,
    ): LikeRepostRepository
}
