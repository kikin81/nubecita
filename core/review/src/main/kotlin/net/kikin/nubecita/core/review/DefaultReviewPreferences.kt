package net.kikin.nubecita.core.review

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import net.kikin.nubecita.core.review.di.ReviewDataStore
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * DataStore-backed [ReviewPreferences]. Owns its own preferences file so the
 * review capability stays self-contained. Timestamps are stored as epoch
 * milliseconds; absent keys read back as `null` / `0`.
 *
 * Reads recover from a transient [IOException] to [emptyPreferences] (so a
 * corrupt/unreadable store is treated as "fresh" → not eligible) rather than
 * propagating — consistent with `DefaultUserPreferencesRepository`.
 */
internal class DefaultReviewPreferences
    @Inject
    constructor(
        @param:ReviewDataStore private val dataStore: DataStore<Preferences>,
    ) : ReviewPreferences {
        override suspend fun currentState(): ReviewState {
            val prefs =
                dataStore.data
                    .catch { error ->
                        if (error is IOException) {
                            Timber.w(error, "Failed to read review preferences; defaulting to empty")
                            emit(emptyPreferences())
                        } else {
                            throw error
                        }
                    }.first()
            return ReviewState(
                firstLaunchAt = prefs[Keys.FIRST_LAUNCH_AT]?.let { Instant.fromEpochMilliseconds(it) },
                successfulPostCount = prefs[Keys.POST_COUNT] ?: 0,
                requestCount = prefs[Keys.REQUEST_COUNT] ?: 0,
                lastRequestedAt = prefs[Keys.LAST_REQUESTED_AT]?.let { Instant.fromEpochMilliseconds(it) },
            )
        }

        override suspend fun incrementPostCount() {
            dataStore.edit { prefs -> prefs[Keys.POST_COUNT] = (prefs[Keys.POST_COUNT] ?: 0) + 1 }
        }

        override suspend fun recordReviewRequested(now: Instant) {
            dataStore.edit { prefs ->
                prefs[Keys.REQUEST_COUNT] = (prefs[Keys.REQUEST_COUNT] ?: 0) + 1
                prefs[Keys.LAST_REQUESTED_AT] = now.toEpochMilliseconds()
            }
        }

        override suspend fun stampFirstLaunchIfUnset(now: Instant) {
            dataStore.edit { prefs ->
                if (prefs[Keys.FIRST_LAUNCH_AT] == null) {
                    prefs[Keys.FIRST_LAUNCH_AT] = now.toEpochMilliseconds()
                }
            }
        }

        private object Keys {
            val FIRST_LAUNCH_AT = longPreferencesKey("review_first_launch_at")
            val POST_COUNT = intPreferencesKey("review_successful_post_count")
            val REQUEST_COUNT = intPreferencesKey("review_request_count")
            val LAST_REQUESTED_AT = longPreferencesKey("review_last_requested_at")
        }
    }
