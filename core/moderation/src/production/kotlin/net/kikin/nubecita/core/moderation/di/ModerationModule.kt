package net.kikin.nubecita.core.moderation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.moderation.DefaultModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import javax.inject.Singleton

// The module class stays public so a downstream `androidTest` can reference it
// in `@TestInstallIn(replaces = [ModerationModule::class])`; only the @Binds
// method is `internal` (Kotlin `internal` is module-scoped).
@Module
@InstallIn(SingletonComponent::class)
abstract class ModerationModule {
    @Binds
    @Singleton
    internal abstract fun bindModerationPreferencesRepository(
        impl: DefaultModerationPreferencesRepository,
    ): ModerationPreferencesRepository
}
