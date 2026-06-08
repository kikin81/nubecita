package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * DataStore-backed [MessageCheckingPreference], sharing the `:core:preferences`
 * `DataStore<Preferences>`. Absence of the key means "never toggled" → the
 * default-on behaviour, so a fresh install checks for messages.
 */
internal class DefaultMessageCheckingPreference
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : MessageCheckingPreference {
        override val enabled: Flow<Boolean> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Failed to read message-checking pref; defaulting to enabled")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }.map { prefs -> prefs[KEY] ?: DEFAULT_ENABLED }

        override suspend fun setEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[KEY] = enabled }
        }

        private companion object {
            const val DEFAULT_ENABLED = true
            val KEY = booleanPreferencesKey("message_checking_enabled")
        }
    }
