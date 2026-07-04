package net.kikin.nubecita.core.preferences.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * Provides the [DataStore]<[Preferences]> consumed by the production
 * [net.kikin.nubecita.core.preferences.DefaultUserPreferencesRepository].
 * Lives in `src/main/` (compiled into every variant) because the
 * provider itself is variant-agnostic; the benchmark flavor's
 * `UserPreferencesBindingsModule` binds a fake repository that doesn't
 * consume this DataStore, so the provider is dead-code-eliminated by
 * Hilt's lazy-initialization in that variant.
 *
 * Public (like `UserPreferencesBindingsModule`) so instrumentation graphs in other
 * modules can `@TestInstallIn(replaces = [UserPreferencesDataStoreModule::class])`
 * to swap the fixed-file DataStore for a unique-file test one — DataStore allows one
 * active instance per file per process, which the per-test-method Hilt components
 * otherwise violate.
 */
@Module
@InstallIn(SingletonComponent::class)
object UserPreferencesDataStoreModule {
    private const val PREFERENCES_FILE_NAME = "user_preferences"

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // `DefaultUserPreferencesRepository.hasSeenOnboarding` absorbs transient
            // IOException via `.catch`, but a genuinely corrupted on-disk file
            // throws `CorruptionException` on every read — `.catch` would loop
            // forever. The corruption handler replaces the file with empty
            // preferences once, healing the store so future reads / writes
            // (including the next onboarding flag flip) proceed normally.
            corruptionHandler =
                ReplaceFileCorruptionHandler {
                    Timber.w(it, "User preferences file corrupted; replacing with empty store")
                    emptyPreferences()
                },
            produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE_NAME) },
        )
}
