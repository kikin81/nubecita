package net.kikin.nubecita.core.posts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.core.posts.internal.BenchFakePostRepository

/**
 * Bench-flavor counterpart to the production [PostRepositoryModule] at
 * `core/posts/src/production/.../di/PostRepositoryModule.kt`.
 *
 * AGP source-set selection picks exactly one of the two per variant:
 * - `productionDebug` / `productionRelease` see the production module
 *   (binds `DefaultPostRepository` → `PostRepository`).
 * - `benchDebug` / `benchRelease` see this module (binds
 *   [BenchFakePostRepository] → `PostRepository`).
 *
 * The shared FQN matters: both modules sit at
 * `net.kikin.nubecita.core.posts.di.PostRepositoryModule`, so they cannot
 * coexist on one variant's classpath. Source-set merging takes care of
 * the variant pick automatically — see `:feature:feed:impl`'s parallel
 * production/bench `FeedRepositoryModule` split for the established
 * precedent.
 *
 * `PostThreadRepositoryModule` stays in `src/main` (it isn't flavor-split);
 * only the single-post read surface needs a deterministic bench fake.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface PostRepositoryModule {
    @Binds
    fun bindPostRepository(impl: BenchFakePostRepository): PostRepository
}
