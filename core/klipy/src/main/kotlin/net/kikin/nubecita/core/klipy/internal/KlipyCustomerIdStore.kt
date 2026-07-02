package net.kikin.nubecita.core.klipy.internal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import net.kikin.nubecita.core.klipy.internal.di.KlipyPreferences
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the stable, anonymous `customer_id` KLIPY needs on fetch and
 * tracking calls (it uses it to personalise trending/search and to key the
 * user's Recents). Generated once on first read and reused across launches;
 * it is NOT the Bluesky DID — no account identity is sent to KLIPY.
 */
@Singleton
internal class KlipyCustomerIdStore
    @Inject
    constructor(
        @KlipyPreferences private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * Returns the customer id, generating and persisting a fresh UUID on first
         * call. The [edit] block is atomic, so concurrent callers converge on a
         * single first-write-wins value.
         */
        suspend fun get(): String {
            val prefs =
                dataStore.edit { mutable ->
                    if (mutable[KEY] == null) {
                        mutable[KEY] = UUID.randomUUID().toString()
                    }
                }
            return prefs[KEY] ?: dataStore.data.first()[KEY] ?: UUID.randomUUID().toString()
        }

        private companion object {
            val KEY = stringPreferencesKey("klipy_customer_id")
        }
    }
