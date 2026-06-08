package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * DataStore-backed [DmPollCursorStore], sharing the `:core:preferences`
 * `DataStore<Preferences>` with [DefaultUserPreferencesRepository]. The cursor
 * is stored under a DID-suffixed key so accounts stay isolated.
 */
internal class DefaultDmPollCursorStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : DmPollCursorStore {
        override fun cursor(did: String): Flow<String?> =
            dataStore.data
                .catch { error ->
                    // Mirror DefaultUserPreferencesRepository: a transient read
                    // failure degrades to "no cursor" (re-baseline next poll)
                    // rather than cancelling the worker's flow.
                    if (error is IOException) {
                        Timber.w(error, "Failed to read DM-poll cursor; defaulting to null")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }.map { prefs -> prefs[keyFor(did)] }

        override suspend fun setCursor(
            did: String,
            cursor: String,
        ) {
            dataStore.edit { prefs -> prefs[keyFor(did)] = cursor }
        }

        private fun keyFor(did: String): Preferences.Key<String> {
            // A blank DID would collapse every account onto the shared
            // "dm_poll_cursor_" key — fail fast on the upstream bug rather than
            // silently cross-contaminate cursors.
            require(did.isNotBlank()) { "did must not be blank" }
            return stringPreferencesKey("$KEY_PREFIX$did")
        }

        private companion object {
            const val KEY_PREFIX = "dm_poll_cursor_"
        }
    }
