package net.kikin.nubecita.core.preferences.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.preferences.FakeUserPreferencesRepository
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import javax.inject.Singleton

/**
 * Benchmark-flavor parallel of `:core:preferences`'s production
 * [UserPreferencesBindingsModule].
 *
 * Same FQN as the production version
 * (`net.kikin.nubecita.core.preferences.di.UserPreferencesBindingsModule`)
 * so any existing `@TestInstallIn(replaces = [UserPreferencesBindingsModule::class])`
 * androidTest references resolve identically across flavors. AGP source-
 * set selection picks this file in benchmark variants only.
 *
 * Single binding: [UserPreferencesRepository] → [FakeUserPreferencesRepository].
 * The benchmark fake always reports `hasSeenOnboarding = true` so the
 * routing gate skips onboarding.
 *
 * See `bd show nubecita-crmi.6` for the broader Section A scope.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UserPreferencesBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindUserPreferencesRepository(
        impl: FakeUserPreferencesRepository,
    ): UserPreferencesRepository
}
