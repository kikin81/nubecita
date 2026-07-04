package net.kikin.nubecita.feature.settings.impl.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.core.preferences.di.UserPreferencesDataStoreModule
import java.util.UUID
import javax.inject.Singleton

/**
 * Replaces `:core:preferences`' production [UserPreferencesDataStoreModule] in
 * `:feature:settings:impl`'s androidTest graph with a DataStore backed by a
 * **unique file per instantiation**.
 *
 * DataStore permits only one active instance per file per process. Each
 * `@HiltAndroidTest` method gets a fresh Hilt `SingletonComponent`, so the
 * production provider (fixed file `user_preferences`) constructed a second
 * DataStore on the same file on the second test → "multiple DataStores active for
 * the same file". Several types inject this DataStore directly
 * (`DefaultUserPreferencesRepository`, `DefaultMessageCheckingPreference`,
 * `DefaultDmPollCursorStore`), so faking one repository isn't enough — swapping the
 * provider itself, keyed to a random file name, is what actually avoids the clash.
 * Nothing here reads persisted state across tests, so a throwaway file is fine.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [UserPreferencesDataStoreModule::class],
)
internal object TestUserPreferencesDataStoreModule {
    @Provides
    @Singleton
    fun provideTestUserPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("test_user_preferences_${UUID.randomUUID()}") },
        )
}
