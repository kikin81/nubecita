package net.kikin.nubecita.core.review.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.review.BenchFakeReviewManager
import net.kikin.nubecita.core.review.ReviewManager
import javax.inject.Singleton

/**
 * Bench-flavor counterpart to the production `ReviewModule` at
 * `core/review/src/production/.../di/ReviewModule.kt`. AGP source-set selection
 * picks exactly one of the two per variant; this one binds the no-op
 * [BenchFakeReviewManager], so the bench build issues zero Play calls and pulls
 * in none of the DataStore / Play / clock providers. Shared FQN, mirrors
 * `:core:actors`' bench `ActorsModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReviewModule {
    @Binds
    @Singleton
    abstract fun bindReviewManager(impl: BenchFakeReviewManager): ReviewManager
}
