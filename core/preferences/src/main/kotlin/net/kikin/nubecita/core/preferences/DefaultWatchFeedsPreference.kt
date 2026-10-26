package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.core.preferences.WatchFeedsPreference.Companion.FOLLOWING_FEED_URI
import net.kikin.nubecita.core.preferences.WatchFeedsPreference.Companion.MAX_FEEDS
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

internal class DefaultWatchFeedsPreference
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : WatchFeedsPreference {
        override val selectedFeedUris: Flow<Set<String>> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Failed to read watch-feeds pref; defaulting to Following")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }.map { prefs -> prefs[KEY] ?: DEFAULT }

        override suspend fun setSelectedFeedUris(uris: Set<String>) {
            dataStore.edit { prefs -> prefs[KEY] = uris.take(MAX_FEEDS).toSet() }
        }

        private companion object {
            val DEFAULT = setOf(FOLLOWING_FEED_URI)
            val KEY = stringSetPreferencesKey("watch_feed_uris")
        }
    }
