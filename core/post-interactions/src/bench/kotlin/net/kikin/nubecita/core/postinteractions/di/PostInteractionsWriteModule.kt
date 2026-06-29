package net.kikin.nubecita.core.postinteractions.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import net.kikin.nubecita.core.postinteractions.internal.BenchFakeFollowRepository
import net.kikin.nubecita.core.postinteractions.internal.BenchFakeLikeRepostRepository
import javax.inject.Singleton

/**
 * Bench-flavor counterpart to the production [PostInteractionsWriteModule] at
 * `core/post-interactions/src/production/.../di/PostInteractionsWriteModule.kt`.
 *
 * AGP source-set selection picks exactly one of the two write modules per
 * variant:
 * - `productionDebug` / `productionRelease` see the production module
 *   (binds `DefaultLikeRepostRepository` + `DefaultFollowRepository`, real
 *   network).
 * - `benchDebug` / `benchRelease` see **this** module (binds
 *   [BenchFakeLikeRepostRepository] → [LikeRepostRepository] and
 *   [BenchFakeFollowRepository] → [FollowRepository], offline success no-ops).
 *
 * The shared FQN (`net.kikin.nubecita.core.postinteractions.di.PostInteractionsWriteModule`)
 * is intentional: both modules cannot coexist on one variant's classpath.
 * Mirrors the `ActorsModule` production/bench split in `:core:actors`.
 *
 * The bench fakes return a synthetic non-blank at-uri for like/repost so the
 * cache's optimistic viewer-record uri is set and the toggle does not roll back.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PostInteractionsWriteModule {
    @Binds
    @Singleton
    internal abstract fun bindLikeRepostRepository(
        impl: BenchFakeLikeRepostRepository,
    ): LikeRepostRepository

    @Binds
    @Singleton
    internal abstract fun bindFollowRepository(
        impl: BenchFakeFollowRepository,
    ): FollowRepository
}
