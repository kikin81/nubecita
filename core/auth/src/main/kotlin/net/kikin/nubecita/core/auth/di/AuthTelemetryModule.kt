package net.kikin.nubecita.core.auth.di

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
import net.kikin.nubecita.core.auth.DataStoreLoginTimestampStore
import net.kikin.nubecita.core.auth.LoginTimestampStore
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifies the small unencrypted Preferences DataStore backing session-loss
 * telemetry (login timestamp), keeping it distinct from the Tink-encrypted
 * `DataStore<OAuthSession?>` this module's sibling provides.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AuthTelemetryDataStore

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthTelemetryModule {
    @Binds
    internal abstract fun bindLoginTimestampStore(impl: DataStoreLoginTimestampStore): LoginTimestampStore

    companion object {
        private const val TELEMETRY_PREFS_NAME = "auth_telemetry"

        @Provides
        @Singleton
        @AuthTelemetryDataStore
        fun provideAuthTelemetryDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile(TELEMETRY_PREFS_NAME) },
            )
    }
}
