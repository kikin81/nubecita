package net.kikin.nubecita.core.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/**
 * Persistent "have we already shown the POST_NOTIFICATIONS runtime prompt
 * on this install" flag. Kept in a dedicated DataStore file (NOT shared
 * with [PushRegistrationStateStore]) so a sign-out — which clears the
 * registration store — does not also clear the prompt-shown flag and
 * cause re-prompt on the next sign-in.
 *
 * Exposed as an interface so [NotificationsPromptDecider] consumers
 * (notably `:feature:login:impl`'s VM tests) can swap in an in-memory
 * fake without spinning up a DataStore over a temp directory.
 */
interface NotificationsPromptShownStore {
    suspend fun read(): Boolean

    suspend fun markShown()
}

internal class DataStoreNotificationsPromptShownStore(
    private val dataStore: DataStore<Preferences>,
) : NotificationsPromptShownStore {
    override suspend fun read(): Boolean = dataStore.data.first()[KEY_SHOWN] == true

    override suspend fun markShown() {
        dataStore.edit { it[KEY_SHOWN] = true }
    }

    private companion object {
        val KEY_SHOWN = booleanPreferencesKey("notifications_prompt_shown")
    }
}
