package net.kikin.nubecita.core.profile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.profile.DefaultPinnedPostRepository
import net.kikin.nubecita.core.profile.PinnedPostRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class PinnedPostModule {
    @Binds
    internal abstract fun bindPinnedPostRepository(impl: DefaultPinnedPostRepository): PinnedPostRepository
}
