package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

internal class DefaultUserPreferencesRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : UserPreferencesRepository {
        // `DataStore.data` cancels its downstream on read failure. `MainActivity`'s
        // bootstrap collector depends on this flow, so a transient IOException
        // (disk full, corruption, etc.) would otherwise leave the app stuck on
        // Splash. Recover to `emptyPreferences()` (which `map`s to `false`),
        // logging the cause; rethrow non-IO errors so genuine bugs aren't masked.
        override val hasSeenOnboarding: Flow<Boolean> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Failed to read user preferences; defaulting hasSeenOnboarding to false")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }.map { prefs -> prefs[Keys.HAS_SEEN_ONBOARDING] == true }

        override suspend fun markOnboardingSeen() {
            dataStore.edit { prefs -> prefs[Keys.HAS_SEEN_ONBOARDING] = true }
        }

        // Mirrors `hasSeenOnboarding`'s IOException recovery: this flow feeds UI
        // that should degrade to the default feed rather than crash on a
        // transient read failure.
        override val lastSelectedFeedUri: Flow<String?> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Failed to read user preferences; defaulting lastSelectedFeedUri to null")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }.map { prefs -> prefs[Keys.LAST_SELECTED_FEED_URI] }

        override suspend fun setLastSelectedFeedUri(uri: String) {
            dataStore.edit { prefs -> prefs[Keys.LAST_SELECTED_FEED_URI] = uri }
        }

        private object Keys {
            val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
            val LAST_SELECTED_FEED_URI = stringPreferencesKey("last_selected_feed_uri")
        }
    }
