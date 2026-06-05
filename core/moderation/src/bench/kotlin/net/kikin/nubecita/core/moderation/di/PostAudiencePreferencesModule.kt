package net.kikin.nubecita.core.moderation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.moderation.FakePostAudienceDefaultRepository
import net.kikin.nubecita.core.moderation.PostAudienceDefaultRepository
import javax.inject.Singleton

// Bench flavor: bind the deterministic in-memory fake so bench / Macrobench
// builds never touch the network. Mirrors the bench `ModerationModule`.
@Module
@InstallIn(SingletonComponent::class)
abstract class PostAudiencePreferencesModule {
    @Binds
    @Singleton
    internal abstract fun bindPostAudienceDefaultRepository(
        impl: FakePostAudienceDefaultRepository,
    ): PostAudienceDefaultRepository
}
