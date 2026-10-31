package net.kikin.nubecita.feature.composer.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.composer.impl.data.DefaultParentFetchSource
import net.kikin.nubecita.feature.composer.impl.data.ParentFetchSource

/**
 * Production counterpart to the bench `ParentFetchSourceModule`: reply targets
 * resolve through `app.bsky.feed.getPostThread`. AGP source-set selection picks
 * exactly one of the two per variant (shared FQN).
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface ParentFetchSourceModule {
    @Binds
    fun bindParentFetchSource(impl: DefaultParentFetchSource): ParentFetchSource
}
