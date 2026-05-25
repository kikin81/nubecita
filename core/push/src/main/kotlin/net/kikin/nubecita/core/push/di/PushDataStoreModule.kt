package net.kikin.nubecita.core.push.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt qualifiers + providers for `:core:push`'s two Preferences DataStores.
 *
 * The two stores live in separate files so a `PushRegistrationStateStore.clear()`
 * triggered by sign-out does not also wipe the cross-session muted-actor
 * cache (mutes outlive any single login session).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PushRegistrationDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MutedActorDataStore

@Module
@InstallIn(SingletonComponent::class)
internal object PushDataStoreModule {
    private const val REGISTRATION_FILE_NAME = "push_registration"
    private const val MUTED_ACTOR_FILE_NAME = "push_muted_actors"

    @Provides
    @Singleton
    @PushRegistrationDataStore
    fun providePushRegistrationDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(REGISTRATION_FILE_NAME) },
        )

    @Provides
    @Singleton
    @MutedActorDataStore
    fun provideMutedActorDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(MUTED_ACTOR_FILE_NAME) },
        )
}
