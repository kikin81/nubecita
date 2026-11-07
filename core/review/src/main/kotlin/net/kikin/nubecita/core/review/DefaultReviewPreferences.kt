package net.kikin.nubecita.core.review

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import net.kikin.nubecita.core.review.di.ReviewDataStore
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Instant

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
                successfulPostCount = prefs[Keys.POST_COUNT] ?: 0,
                requestCount = prefs[Keys.REQUEST_COUNT] ?: 0,
                lastRequestedAt = prefs[Keys.LAST_REQUESTED_AT]?.let { Instant.fromEpochMilliseconds(it) },
                activeDayCount = prefs[Keys.ACTIVE_DAY_COUNT] ?: 0,
                lastActiveDay = prefs[Keys.LAST_ACTIVE_DAY],
                likeCount = prefs[Keys.LIKE_COUNT] ?: 0,
            )
        }

        override suspend fun incrementPostCount() {
            dataStore.edit { prefs -> prefs[Keys.POST_COUNT] = (prefs[Keys.POST_COUNT] ?: 0) + 1 }
        }

        override suspend fun recordActiveDay(epochDay: Long) {
            dataStore.edit { prefs ->
                if (prefs[Keys.LAST_ACTIVE_DAY] != epochDay) {
                    prefs[Keys.ACTIVE_DAY_COUNT] = (prefs[Keys.ACTIVE_DAY_COUNT] ?: 0) + 1
                    prefs[Keys.LAST_ACTIVE_DAY] = epochDay
                }
            }
        }

        override suspend fun recordLike() {
            dataStore.edit { prefs -> prefs[Keys.LIKE_COUNT] = (prefs[Keys.LIKE_COUNT] ?: 0) + 1 }
        }

        override suspend fun recordReviewRequested(now: Instant) {
            dataStore.edit { prefs ->
                prefs[Keys.REQUEST_COUNT] = (prefs[Keys.REQUEST_COUNT] ?: 0) + 1
                prefs[Keys.LAST_REQUESTED_AT] = now.toEpochMilliseconds()
            }
        }

        private object Keys {
            val POST_COUNT = intPreferencesKey("review_successful_post_count")
            val REQUEST_COUNT = intPreferencesKey("review_request_count")
            val LAST_REQUESTED_AT = longPreferencesKey("review_last_requested_at")
            val ACTIVE_DAY_COUNT = intPreferencesKey("review_active_day_count")
            val LAST_ACTIVE_DAY = longPreferencesKey("review_last_active_day")
            val LIKE_COUNT = intPreferencesKey("review_like_count")
        }
    }
