package net.kikin.nubecita.core.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.core.common.network.NetworkStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "may this autoplay right now", so playback call sites never have to
 * combine the preference with the connection themselves.
 *
 * That combination existing in exactly one place is the point. [AutoplayPreference]
 * alone is not an answer — [AutoplayPreference.WIFI_ONLY] depends on the network —
 * and two call sites (feed video, inline GIFs) each recombining it is how they
 * drift out of agreement. Consume these flows; do not read
 * `UserPreferencesRepository.autoplayPreference` at a playback site.
 *
 * Both flows react to the network changing mid-session, which is the behaviour
 * a wifi-only preference implies: walking out of the house should stop autoplay
 * without reopening the app.
 */
@Singleton
class AutoplayPolicy
    @Inject
    constructor(
        preferences: UserPreferencesRepository,
        networkStatus: NetworkStatus,
    ) {
        /**
         * Whether an inline feed video may start on its own.
         *
         * Governs incidental playback while scrolling only — the full-screen
         * Videos tab ignores this, because opening it is an explicit request to
         * watch video.
         */
        val videoAutoplayEnabled: Flow<Boolean> =
            combine(preferences.autoplayPreference, networkStatus.isMetered) { preference, metered ->
                when (preference) {
                    AutoplayPreference.ALWAYS -> true
                    AutoplayPreference.NEVER -> false
                    AutoplayPreference.WIFI_ONLY -> !metered
                }
            }.distinctUntilChanged()

        /**
         * Whether inline animated GIFs may play on their own.
         *
         * Deliberately independent of the network: a GIF is cheap enough that
         * gating it on connection type would be noise the user did not ask for.
         */
        val gifAutoplayEnabled: Flow<Boolean> =
            preferences.autoplayGifs.distinctUntilChanged()
    }
