package net.kikin.nubecita.feature.composer.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.composer.impl.data.BenchParentFetchSource
import net.kikin.nubecita.feature.composer.impl.data.ParentFetchSource

/**
 * Bench counterpart to the production `ParentFetchSourceModule`: reply targets
 * resolve offline through the bench `PostRepository` fake. AGP source-set
 * selection picks exactly one of the two per variant (shared FQN).
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface ParentFetchSourceModule {
    @Binds
    fun bindParentFetchSource(impl: BenchParentFetchSource): ParentFetchSource
}
