package net.kikin.nubecita.core.moderation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.moderation.DefaultModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModerationModule {
    @Binds
    @Singleton
    abstract fun bindModerationPreferencesRepository(
        impl: DefaultModerationPreferencesRepository,
    ): ModerationPreferencesRepository
}
