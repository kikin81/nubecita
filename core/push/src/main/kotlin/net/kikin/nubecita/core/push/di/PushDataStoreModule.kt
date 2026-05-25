package net.kikin.nubecita.core.push.di

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

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class NotificationsPromptDataStore

@Module
@InstallIn(SingletonComponent::class)
internal object PushDataStoreModule {
    private const val REGISTRATION_FILE_NAME = "push_registration"
    private const val MUTED_ACTOR_FILE_NAME = "push_muted_actors"
    private const val NOTIFICATIONS_PROMPT_FILE_NAME = "push_notifications_prompt"

    @Provides
    @Singleton
    @PushRegistrationDataStore
    fun providePushRegistrationDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // Genuinely corrupted on-disk files throw CorruptionException on
            // every read — without a handler, push registration state would
            // be permanently stuck on the failing read until reinstall.
            // ReplaceFileCorruptionHandler swaps the file for an empty
            // preferences set once, healing the store; the coordinator's
            // next refresh resets to the current session's actual state.
            // Matches the :core:preferences pattern.
            corruptionHandler =
                ReplaceFileCorruptionHandler {
                    Timber.w(it, "Push registration DataStore file corrupted; replacing with empty store")
                    emptyPreferences()
                },
            produceFile = { context.preferencesDataStoreFile(REGISTRATION_FILE_NAME) },
        )

    @Provides
    @Singleton
    @MutedActorDataStore
    fun provideMutedActorDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // See PushRegistrationDataStore comment. Corruption here would
            // leave the muted-actor snapshot permanently empty, fail-opening
            // the dispatcher's mute filter — better to replace + re-fetch
            // on the next foreground refresh.
            corruptionHandler =
                ReplaceFileCorruptionHandler {
                    Timber.w(it, "Muted-actor DataStore file corrupted; replacing with empty store")
                    emptyPreferences()
                },
            produceFile = { context.preferencesDataStoreFile(MUTED_ACTOR_FILE_NAME) },
        )

    @Provides
    @Singleton
    @NotificationsPromptDataStore
    fun provideNotificationsPromptDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // See PushRegistrationDataStore comment. Corruption here would
            // reset the "have we already prompted" flag and cause a re-prompt
            // on next login. Annoying for users but recoverable — they can
            // still grant via system settings if they've denied permanently.
            corruptionHandler =
                ReplaceFileCorruptionHandler {
                    Timber.w(it, "Notifications-prompt DataStore file corrupted; replacing with empty store")
                    emptyPreferences()
                },
            produceFile = { context.preferencesDataStoreFile(NOTIFICATIONS_PROMPT_FILE_NAME) },
        )
}
