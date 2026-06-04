package net.kikin.nubecita.core.moderation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.moderation.FakeModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import javax.inject.Singleton

// Bench parallel of the production ModerationModule: binds the deterministic
// in-memory fake so offline bench / smoke builds get a working Content filters
// screen with no network or account.
@Module
@InstallIn(SingletonComponent::class)
abstract class ModerationModule {
    @Binds
    @Singleton
    internal abstract fun bindModerationPreferencesRepository(
        impl: FakeModerationPreferencesRepository,
    ): ModerationPreferencesRepository
}
