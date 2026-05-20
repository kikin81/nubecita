package net.kikin.nubecita.feature.onboarding.impl.testing

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.core.preferences.di.UserPreferencesBindingsModule

/**
 * Replaces the production [UserPreferencesBindingsModule] in
 * `:feature:onboarding:impl`'s androidTest graph. The fake binding
 * lets tests control persist outcomes (success / IOException) without
 * touching DataStore on disk.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [UserPreferencesBindingsModule::class],
)
internal interface TestUserPreferencesBindingsModule {
    @Binds
    fun bindFakeUserPreferencesRepository(
        impl: FakeUserPreferencesRepository,
    ): UserPreferencesRepository
}
