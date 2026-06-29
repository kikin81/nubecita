package net.kikin.nubecita.core.postinteractions.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import net.kikin.nubecita.core.postinteractions.internal.DefaultFollowRepository
import net.kikin.nubecita.core.postinteractions.internal.DefaultLikeRepostRepository
import javax.inject.Singleton

/**
 * Production-flavor Hilt bindings for the like/repost + follow write
 * repositories. Both are `@Singleton` to avoid re-wrapping the atproto service
 * per injection point.
 *
 * AGP source-set selection picks exactly one of the two write modules per
 * variant:
 * - `productionDebug` / `productionRelease` see **this** module (binds
 *   [DefaultLikeRepostRepository] → [LikeRepostRepository] and
 *   [DefaultFollowRepository] → [FollowRepository], real network).
 * - `benchDebug` / `benchRelease` see the bench counterpart at
 *   `core/post-interactions/src/bench/.../di/PostInteractionsWriteModule.kt`
 *   (binds `BenchFakeLikeRepostRepository` + `BenchFakeFollowRepository`,
 *   offline no-ops).
 *
 * The shared FQN (`net.kikin.nubecita.core.postinteractions.di.PostInteractionsWriteModule`)
 * is intentional: both modules cannot coexist on one variant's classpath.
 * Mirrors the `ActorsModule` production/bench split in `:core:actors`.
 *
 * The cache + `SessionClearable` + `PostInteractionHandler` bindings remain in
 * [PostInteractionsModule] in `src/main` — they are flavor-independent.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PostInteractionsWriteModule {
    @Binds
    @Singleton
    internal abstract fun bindLikeRepostRepository(
        impl: DefaultLikeRepostRepository,
    ): LikeRepostRepository

    @Binds
    @Singleton
    internal abstract fun bindFollowRepository(
        impl: DefaultFollowRepository,
    ): FollowRepository
}
