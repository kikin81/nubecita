package net.kikin.nubecita.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import net.kikin.nubecita.core.update.di.UpdateDataStore
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * DataStore-backed [UpdatePreferences]. Owns its own preferences file so the
 * update capability stays self-contained; the absent key reads back as `null`.
 *
 * Reads recover from a transient [IOException] to [emptyPreferences] (so a
 * corrupt/unreadable store is treated as "never prompted") rather than
 * propagating — consistent with `DefaultReviewPreferences`.
 */
internal class DefaultUpdatePreferences
    @Inject
    constructor(
        @param:UpdateDataStore private val dataStore: DataStore<Preferences>,
    ) : UpdatePreferences {
        override suspend fun lastPromptedVersionCode(): Int? {
            val prefs =
                dataStore.data
                    .catch { error ->
                        if (error is IOException) {
                            Timber.w(error, "Failed to read update preferences; defaulting to empty")
                            emit(emptyPreferences())
                        } else {
                            throw error
                        }
                    }.first()
            return prefs[Keys.LAST_PROMPTED_VERSION_CODE]
        }

        override suspend fun setLastPromptedVersionCode(versionCode: Int) {
            dataStore.edit { prefs -> prefs[Keys.LAST_PROMPTED_VERSION_CODE] = versionCode }
        }

        private object Keys {
            val LAST_PROMPTED_VERSION_CODE = intPreferencesKey("update_last_prompted_version_code")
        }
    }
