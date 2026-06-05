package net.kikin.nubecita.core.posts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posts.PostThreadRepository
import net.kikin.nubecita.core.posts.internal.DefaultPostThreadRepository

/**
 * Production-flavor binding for [PostThreadRepository] → the live
 * `getPostThread` XRPC implementation.
 *
 * Flavor-split (production / bench) mirrors [PostRepositoryModule]: AGP
 * source-set merging picks exactly one module of this FQN per variant. The
 * bench counterpart at `core/posts/src/bench/.../di/PostThreadRepositoryModule.kt`
 * binds the offline [net.kikin.nubecita.core.posts.internal.BenchFakePostThreadRepository]
 * so post-detail renders without an authenticated client.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface PostThreadRepositoryModule {
    @Binds
    fun bindPostThreadRepository(impl: DefaultPostThreadRepository): PostThreadRepository
}
