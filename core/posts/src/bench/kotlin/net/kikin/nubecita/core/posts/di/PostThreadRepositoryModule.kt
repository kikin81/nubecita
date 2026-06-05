package net.kikin.nubecita.core.posts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posts.PostThreadRepository
import net.kikin.nubecita.core.posts.internal.BenchFakePostThreadRepository

/**
 * Bench-flavor counterpart to the production [PostThreadRepositoryModule]
 * (`core/posts/src/production/.../di/PostThreadRepositoryModule.kt`). AGP
 * source-set selection picks exactly one of the two per variant — see the
 * sibling [PostRepositoryModule] split for the full rationale.
 *
 * Binds the offline [BenchFakePostThreadRepository] so the post-detail screen
 * (and the tablet list-detail pane) render under the `bench` flavor without an
 * authenticated XRPC client.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface PostThreadRepositoryModule {
    @Binds
    fun bindPostThreadRepository(impl: BenchFakePostThreadRepository): PostThreadRepository
}
