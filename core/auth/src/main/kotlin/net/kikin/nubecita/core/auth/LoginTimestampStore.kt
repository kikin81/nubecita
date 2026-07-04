package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import net.kikin.nubecita.core.auth.di.AuthTelemetryDataStore
import java.io.IOException
import javax.inject.Inject

/**
 * Persists the instant of the last successful login so session-loss telemetry
 * can report `days_since_login` — the dimension that separates legitimate
 * ~14-day public-client session expiry from premature, spurious logouts.
 *
 * Deliberately NOT part of the encrypted session file: the timestamp must
 * survive the session being cleared (that is exactly when it is read), and it
 * carries no secret.
 */
internal interface LoginTimestampStore {
    suspend fun record(epochMillis: Long)

    suspend fun lastLoginEpochMillis(): Long?
}

internal class DataStoreLoginTimestampStore
    @Inject
    constructor(
        @param:AuthTelemetryDataStore private val dataStore: DataStore<Preferences>,
    ) : LoginTimestampStore {
        override suspend fun record(epochMillis: Long) {
            dataStore.edit { it[KEY_LAST_LOGIN_EPOCH_MILLIS] = epochMillis }
        }

        // Telemetry-grade read: a failed read degrades to "unknown" (null) —
        // it must never break the clear()/sign-out path it decorates.
        override suspend fun lastLoginEpochMillis(): Long? =
            dataStore.data
                .catch { cause ->
                    when (cause) {
                        is IOException -> emit(emptyPreferences())
                        else -> throw cause
                    }
                }.firstOrNull()
                ?.get(KEY_LAST_LOGIN_EPOCH_MILLIS)

        private companion object {
            val KEY_LAST_LOGIN_EPOCH_MILLIS = longPreferencesKey("last_login_epoch_millis")
        }
    }
