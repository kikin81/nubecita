package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import net.kikin.nubecita.core.analytics.TokenRefreshCaller
import net.kikin.nubecita.core.auth.di.AuthTelemetryDataStore
import java.io.IOException
import javax.inject.Inject

/** One refresh POST worth remembering: when it ran and who triggered it. */
internal data class RefreshRecord(
    val epochMillis: Long,
    val caller: TokenRefreshCaller,
)

/** The refreshes that matter when a session is later found dead. */
internal data class RefreshHistory(
    val lastOk: RefreshRecord?,
    val lastLostResponse: RefreshRecord?,
)

/**
 * Persists the last successful refresh and the last lost-response refresh so
 * the session-cleared non-fatal can say what happened BEFORE the clear.
 *
 * In-process breadcrumbs can't: the refresh that actually consumed the token
 * usually ran earlier — often in a background-worker process that has since
 * died — while the clear fires on the next refresh, frequently a foreground
 * cold start seconds after launch. Same unencrypted telemetry DataStore as
 * [LoginTimestampStore]; it must outlive the session and holds no secret.
 */
internal interface RefreshHistoryStore {
    suspend fun recordOk(record: RefreshRecord)

    suspend fun recordLostResponse(record: RefreshRecord)

    suspend fun history(): RefreshHistory
}

internal class DataStoreRefreshHistoryStore
    @Inject
    constructor(
        @param:AuthTelemetryDataStore private val dataStore: DataStore<Preferences>,
    ) : RefreshHistoryStore {
        override suspend fun recordOk(record: RefreshRecord) {
            dataStore.edit {
                it[KEY_LAST_OK_AT] = record.epochMillis
                it[KEY_LAST_OK_CALLER] = record.caller.wire
            }
        }

        override suspend fun recordLostResponse(record: RefreshRecord) {
            dataStore.edit {
                it[KEY_LAST_LOST_AT] = record.epochMillis
                it[KEY_LAST_LOST_CALLER] = record.caller.wire
            }
        }

        // Telemetry-grade read, as in DataStoreLoginTimestampStore: an IO
        // failure degrades to "no history".
        override suspend fun history(): RefreshHistory {
            val prefs =
                dataStore.data
                    .catch { cause ->
                        when (cause) {
                            is IOException -> emit(emptyPreferences())
                            else -> throw cause
                        }
                    }.firstOrNull() ?: emptyPreferences()
            return RefreshHistory(
                lastOk = prefs.record(KEY_LAST_OK_AT, KEY_LAST_OK_CALLER),
                lastLostResponse = prefs.record(KEY_LAST_LOST_AT, KEY_LAST_LOST_CALLER),
            )
        }

        private fun Preferences.record(
            atKey: Preferences.Key<Long>,
            callerKey: Preferences.Key<String>,
        ): RefreshRecord? {
            val at = this[atKey] ?: return null
            val caller =
                TokenRefreshCaller.entries.firstOrNull { it.wire == this[callerKey] }
                    ?: TokenRefreshCaller.Untagged
            return RefreshRecord(epochMillis = at, caller = caller)
        }

        private companion object {
            val KEY_LAST_OK_AT = longPreferencesKey("last_ok_refresh_epoch_millis")
            val KEY_LAST_OK_CALLER = stringPreferencesKey("last_ok_refresh_caller")
            val KEY_LAST_LOST_AT = longPreferencesKey("last_lost_refresh_epoch_millis")
            val KEY_LAST_LOST_CALLER = stringPreferencesKey("last_lost_refresh_caller")
        }
    }
