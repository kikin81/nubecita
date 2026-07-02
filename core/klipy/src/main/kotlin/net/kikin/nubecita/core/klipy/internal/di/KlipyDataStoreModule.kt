package net.kikin.nubecita.core.klipy.internal.di

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

@Module
@InstallIn(SingletonComponent::class)
internal object KlipyDataStoreModule {
    private const val PREFERENCES_FILE_NAME = "klipy_preferences"

    @Provides
    @Singleton
    @KlipyPreferences
    fun provideKlipyDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler =
                ReplaceFileCorruptionHandler {
                    Timber.tag("Klipy").w(it, "KLIPY preferences corrupted; replacing with empty store")
                    emptyPreferences()
                },
            produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE_NAME) },
        )
}
