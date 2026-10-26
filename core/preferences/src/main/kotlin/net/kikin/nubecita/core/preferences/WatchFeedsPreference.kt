package net.kikin.nubecita.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Which feeds the phone syncs to the Wear app (Settings → Watch,
 * nubecita-jh9c.1): a set of feed URIs, at most [MAX_FEEDS], defaulting to
 * Following alone until the user picks.
 *
 * Its own small preference, like [MessageCheckingPreference], so the many
 * [UserPreferencesRepository] fakes don't each grow a watch-only member.
 */
interface WatchFeedsPreference {
    /** The selected feed URIs; `[FOLLOWING_FEED_URI]` until the user changes it. */
    val selectedFeedUris: Flow<Set<String>>

    /** Replaces the selection. Callers enforce [MAX_FEEDS]; this keeps at most that many. */
    suspend fun setSelectedFeedUris(uris: Set<String>)

    companion object {
        /** The watch shows at most this many feeds (design decision, W2). */
        const val MAX_FEEDS = 3

        /**
         * Following's pseudo-URI. Must equal `PinnedFeedsRepository.FOLLOWING_FEED_URI`
         * in :core:feeds (which this module can't depend on); the Settings Watch
         * ViewModel test pins the two together.
         */
        const val FOLLOWING_FEED_URI = "following"
    }
}
