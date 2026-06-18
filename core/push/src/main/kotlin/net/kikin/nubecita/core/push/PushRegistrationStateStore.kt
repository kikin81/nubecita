package net.kikin.nubecita.core.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed persistence for [PushRegistrationState]. The store is
 * append-only from this class's perspective — readers see the last write,
 * writers replace the prior triple atomically.
 *
 * Status is persisted as the enum name string rather than an ordinal so a
 * future variant addition or reorder doesn't silently re-interpret existing
 * stored values.
 */
class PushRegistrationStateStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** Reactive view of the persisted state — emits the current value + every write. */
    val state: Flow<PushRegistrationState> = dataStore.data.map(::decode)

    suspend fun read(): PushRegistrationState = decode(dataStore.data.first())

    private fun decode(prefs: Preferences): PushRegistrationState {
        val statusName = prefs[KEY_STATUS] ?: return PushRegistrationState.Default
        val status =
            PushRegistrationState.Status.entries.firstOrNull { it.name == statusName }
                ?: return PushRegistrationState.Default
        return PushRegistrationState(
            accountDid = prefs[KEY_ACCOUNT_DID],
            fcmToken = prefs[KEY_FCM_TOKEN],
            status = status,
        )
    }

    suspend fun write(state: PushRegistrationState) {
        dataStore.edit { prefs ->
            if (state.accountDid != null) {
                prefs[KEY_ACCOUNT_DID] = state.accountDid
            } else {
                prefs.remove(KEY_ACCOUNT_DID)
            }
            if (state.fcmToken != null) {
                prefs[KEY_FCM_TOKEN] = state.fcmToken
            } else {
                prefs.remove(KEY_FCM_TOKEN)
            }
            prefs[KEY_STATUS] = state.status.name
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_ACCOUNT_DID = stringPreferencesKey("account_did")
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
        private val KEY_STATUS = stringPreferencesKey("status")
    }
}
