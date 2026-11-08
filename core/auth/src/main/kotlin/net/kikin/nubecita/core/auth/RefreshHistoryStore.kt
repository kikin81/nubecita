package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

/**
 * A refresh POST about to be sent, written before it goes out so a process that
 * dies mid-request still leaves a trace. [processId] tells the startup pass a
 * previous process's marker from this one's own refresh in progress.
 */
internal data class InFlightRefresh(
    val epochMillis: Long,
    val caller: TokenRefreshCaller,
    val foreground: Boolean,
    val processId: String,
)

/** The refreshes that matter when a session is later found dead. */
internal data class RefreshHistory(
    val lastOk: RefreshRecord?,
    val lastLostResponse: RefreshRecord?,
    val lastDiedInFlight: RefreshRecord? = null,
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

    /** A refresh found unfinished at startup: its process died after sending it. */
    suspend fun recordDiedInFlight(record: RefreshRecord)

    /** Written before the POST, so it must be durable when this returns. */
    suspend fun markInFlight(refresh: InFlightRefresh)

    /** Any outcome at all; later than the marker means that refresh finished. */
    suspend fun recordCompleted(epochMillis: Long)

    /** The in-flight marker, if no outcome was recorded after it. */
    suspend fun unfinished(): InFlightRefresh?

    suspend fun clearInFlight()

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

        override suspend fun recordDiedInFlight(record: RefreshRecord) {
            dataStore.edit {
                it[KEY_LAST_DIED_AT] = record.epochMillis
                it[KEY_LAST_DIED_CALLER] = record.caller.wire
            }
        }

        override suspend fun markInFlight(refresh: InFlightRefresh) {
            dataStore.edit {
                it[KEY_IN_FLIGHT_AT] = refresh.epochMillis
                it[KEY_IN_FLIGHT_CALLER] = refresh.caller.wire
                it[KEY_IN_FLIGHT_FOREGROUND] = refresh.foreground
                it[KEY_IN_FLIGHT_PROCESS] = refresh.processId
            }
        }

        override suspend fun recordCompleted(epochMillis: Long) {
            // Completions are written off the refresh path, so they can land out
            // of order; the latest one is the one that matters.
            dataStore.edit { it[KEY_COMPLETED_AT] = maxOf(it[KEY_COMPLETED_AT] ?: 0L, epochMillis) }
        }

        override suspend fun unfinished(): InFlightRefresh? {
            val prefs = read()
            val at = prefs[KEY_IN_FLIGHT_AT] ?: return null
            if ((prefs[KEY_COMPLETED_AT] ?: Long.MIN_VALUE) >= at) return null
            return InFlightRefresh(
                epochMillis = at,
                caller = callerOf(prefs[KEY_IN_FLIGHT_CALLER]),
                foreground = prefs[KEY_IN_FLIGHT_FOREGROUND] ?: false,
                processId = prefs[KEY_IN_FLIGHT_PROCESS].orEmpty(),
            )
        }

        override suspend fun clearInFlight() {
            dataStore.edit {
                it.remove(KEY_IN_FLIGHT_AT)
                it.remove(KEY_IN_FLIGHT_CALLER)
                it.remove(KEY_IN_FLIGHT_FOREGROUND)
                it.remove(KEY_IN_FLIGHT_PROCESS)
            }
        }

        override suspend fun history(): RefreshHistory {
            val prefs = read()
            return RefreshHistory(
                lastOk = prefs.record(KEY_LAST_OK_AT, KEY_LAST_OK_CALLER),
                lastLostResponse = prefs.record(KEY_LAST_LOST_AT, KEY_LAST_LOST_CALLER),
                lastDiedInFlight = prefs.record(KEY_LAST_DIED_AT, KEY_LAST_DIED_CALLER),
            )
        }

        // Telemetry-grade read, as in DataStoreLoginTimestampStore: an IO
        // failure degrades to "no history".
        private suspend fun read(): Preferences =
            dataStore.data
                .catch { cause ->
                    when (cause) {
                        is IOException -> emit(emptyPreferences())
                        else -> throw cause
                    }
                }.firstOrNull() ?: emptyPreferences()

        private fun callerOf(wire: String?): TokenRefreshCaller = TokenRefreshCaller.entries.firstOrNull { it.wire == wire } ?: TokenRefreshCaller.Untagged

        private fun Preferences.record(
            atKey: Preferences.Key<Long>,
            callerKey: Preferences.Key<String>,
        ): RefreshRecord? {
            val at = this[atKey] ?: return null
            return RefreshRecord(epochMillis = at, caller = callerOf(this[callerKey]))
        }

        private companion object {
            val KEY_LAST_OK_AT = longPreferencesKey("last_ok_refresh_epoch_millis")
            val KEY_LAST_OK_CALLER = stringPreferencesKey("last_ok_refresh_caller")
            val KEY_LAST_LOST_AT = longPreferencesKey("last_lost_refresh_epoch_millis")
            val KEY_LAST_LOST_CALLER = stringPreferencesKey("last_lost_refresh_caller")
            val KEY_LAST_DIED_AT = longPreferencesKey("last_died_refresh_epoch_millis")
            val KEY_LAST_DIED_CALLER = stringPreferencesKey("last_died_refresh_caller")
            val KEY_IN_FLIGHT_AT = longPreferencesKey("in_flight_refresh_epoch_millis")
            val KEY_IN_FLIGHT_CALLER = stringPreferencesKey("in_flight_refresh_caller")
            val KEY_IN_FLIGHT_FOREGROUND = booleanPreferencesKey("in_flight_refresh_foreground")
            val KEY_IN_FLIGHT_PROCESS = stringPreferencesKey("in_flight_refresh_process")
            val KEY_COMPLETED_AT = longPreferencesKey("last_completed_refresh_epoch_millis")
        }
    }
