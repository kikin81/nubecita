package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class DefaultUserPreferencesRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : UserPreferencesRepository {
        override val hasSeenOnboarding: Flow<Boolean> =
            dataStore.data.map { prefs -> prefs[Keys.HAS_SEEN_ONBOARDING] == true }

        override suspend fun markOnboardingSeen() {
            dataStore.edit { prefs -> prefs[Keys.HAS_SEEN_ONBOARDING] = true }
        }

        private object Keys {
            val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        }
    }
