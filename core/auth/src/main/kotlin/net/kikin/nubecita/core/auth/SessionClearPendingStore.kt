package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import net.kikin.nubecita.core.analytics.SessionClearReason
import net.kikin.nubecita.core.auth.di.AuthTelemetryDataStore
import java.io.IOException
import javax.inject.Inject

/**
 * Durable "a clear started and has not been reported yet" marker.
 *
 * `EncryptedOAuthSessionStore.clear()` destroys the session irreversibly and
 * only then records the non-fatal. Running that tail under `NonCancellable`
 * covers a cancelled scope, but not the process being killed outright — and
 * the in-memory `SessionClearedException` marker dies with it, so nothing on
 * the next launch knows a session was ever lost. This store is written BEFORE
 * the wipe and consumed after the loss is recorded, so the one case that
 * produces zero telemetry today instead produces a non-fatal at next start
 * (nubecita-yv4p).
 *
 * Deliberately NOT part of the encrypted session file, for the same reason
 * [LoginTimestampStore] isn't: it must survive the session being cleared, and
 * it carries no secret — only a bucketed reason.
 */
internal interface SessionClearPendingStore {
    suspend fun markPending(reason: SessionClearReason)

    suspend fun pendingReason(): SessionClearReason?

    suspend fun clearPending()
}

internal class DataStoreSessionClearPendingStore
    @Inject
    constructor(
        @param:AuthTelemetryDataStore private val dataStore: DataStore<Preferences>,
    ) : SessionClearPendingStore {
        override suspend fun markPending(reason: SessionClearReason) {
            dataStore.edit { it[KEY_PENDING_REASON] = reason.wire }
        }

        // Telemetry-grade read, mirroring DataStoreLoginTimestampStore: a
        // DataStore IO failure degrades to "nothing pending" rather than
        // breaking the launch-time recovery pass. An unrecognised wire value
        // (written by a newer build, or corrupted) maps to Unknown — the clear
        // still happened, and dropping it would be worse than mislabelling it.
        override suspend fun pendingReason(): SessionClearReason? =
            dataStore.data
                .catch { cause ->
                    when (cause) {
                        is IOException -> emit(emptyPreferences())
                        else -> throw cause
                    }
                }.firstOrNull()
                ?.get(KEY_PENDING_REASON)
                ?.let { wire -> REASONS_BY_WIRE[wire] ?: SessionClearReason.Unknown }

        override suspend fun clearPending() {
            dataStore.edit { it.remove(KEY_PENDING_REASON) }
        }

        private companion object {
            val KEY_PENDING_REASON = stringPreferencesKey("pending_session_clear_reason")
            val REASONS_BY_WIRE = SessionClearReason.entries.associateBy { it.wire }
        }
    }
