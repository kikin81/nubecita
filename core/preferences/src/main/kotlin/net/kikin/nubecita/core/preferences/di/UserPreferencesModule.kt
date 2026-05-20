package net.kikin.nubecita.core.preferences.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.preferences.DefaultUserPreferencesRepository
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object UserPreferencesDataStoreModule {
    private const val PREFERENCES_FILE_NAME = "user_preferences"

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE_NAME) },
        )
}

/**
 * Bindings module is `abstract class` (not `object`) and publicly addressable
 * so downstream instrumentation tests can swap individual bindings via
 * `@TestInstallIn(replaces = [UserPreferencesBindingsModule::class])`. Matches
 * the pattern documented on `:core:auth`'s `AuthBindingsModule`.
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
