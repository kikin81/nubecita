package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.core.auth.XrpcClientProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The viewer's feed-view preferences (`app.bsky.actor.defs#feedViewPref`) as a
 * single reactive source of truth, read by the Following and List feeds.
 *
 * [prefs] seeds at [FeedViewPrefs.DEFAULT], which has
 * `hideRepliesByUnfollowed = true` — so a reader observing before the first
 * [refresh] behaves like the official client rather than showing an unfiltered
 * timeline. That default is the fail-safe direction here: briefly hiding a few
 * replies is recoverable, briefly flooding the feed with stranger threads is the
 * exact bug this exists to fix (nubecita-1fmx).
 *
 * Read-only for now. The Settings screen that mutates these lands in
 * `nubecita-1fmx.2` and will add the read-modify-write mutators, mirroring
 * `ModerationPreferencesRepository`.
 */
interface FeedViewPreferencesRepository {
    /** Hot stream of the resolved preferences, seeded with [FeedViewPrefs.DEFAULT]. */
    val prefs: StateFlow<FeedViewPrefs>

    /** Re-read `app.bsky.actor.getPreferences` and publish to [prefs]. */
    suspend fun refresh()

    /**
     * Reset [prefs] back to [FeedViewPrefs.DEFAULT]. Called on sign-out so the
     * next account never reads the previous account's preferences in the window
     * before its own [refresh] lands — the repository is an app-scoped singleton
     * that outlives the session.
     */
    fun resetToDefault()
}

@Singleton
internal class DefaultFeedViewPreferencesRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
    ) : FeedViewPreferencesRepository {
        private val _prefs = MutableStateFlow(FeedViewPrefs.DEFAULT)
        override val prefs: StateFlow<FeedViewPrefs> = _prefs.asStateFlow()

        override suspend fun refresh() {
            val preferences = ActorService(xrpcClientProvider.authenticated()).getPreferences(GetPreferencesRequest()).preferences
            _prefs.value = parseFeedViewPrefs(preferences)
        }

        override fun resetToDefault() {
            _prefs.value = FeedViewPrefs.DEFAULT
        }
    }
