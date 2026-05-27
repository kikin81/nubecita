package net.kikin.nubecita.core.preferences.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.preferences.DefaultUserPreferencesRepository
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import javax.inject.Singleton

/**
 * Bindings module is `abstract class` (not `object`) and publicly addressable
 * so downstream instrumentation tests can swap individual bindings via
 * `@TestInstallIn(replaces = [UserPreferencesBindingsModule::class])`. Matches
 * the pattern documented on `:core:auth`'s `AuthBindingsModule`.
 *
 * Lives in `src/production/` — the parallel bench-flavor copy in
 * `src/bench/` binds a fake repository. AGP source-set selection
 * includes exactly one copy per variant.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UserPreferencesBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindUserPreferencesRepository(
        impl: DefaultUserPreferencesRepository,
    ): UserPreferencesRepository
}
