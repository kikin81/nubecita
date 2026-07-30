package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesRequest
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequest
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequestPreferencesUnion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Set how replies appear. One call rather than two booleans — see
     * [ReplyVisibility] for why the pair is collapsed.
     */
    suspend fun setReplyVisibility(visibility: ReplyVisibility)

    /** Toggle whether reposted entries are hidden. */
    suspend fun setHideReposts(hide: Boolean)

    /** Toggle whether posts quoting another post are hidden. */
    suspend fun setHideQuotePosts(hide: Boolean)
}

@Singleton
internal class DefaultFeedViewPreferencesRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
    ) : FeedViewPreferencesRepository {
        // Serializes the read-modify-write in [update] (and [refresh]'s publish)
        // so concurrent setters can't clobber each other with a stale read.
        private val writeMutex = Mutex()

        private val _prefs = MutableStateFlow(FeedViewPrefs.DEFAULT)
        override val prefs: StateFlow<FeedViewPrefs> = _prefs.asStateFlow()

        /**
         * The fetch happens INSIDE [writeMutex], not before it. Fetching outside
         * the lock allows a stale clobber: this refresh reads state A, a
         * concurrent [update] then writes B to the server and publishes B, and
         * this refresh finally takes the lock and overwrites the cache with A —
         * leaving the UI showing A while the account holds B. Holding the lock
         * across the round-trip costs nothing user-visible, because [update]
         * publishes its optimistic value BEFORE contending for the lock.
         */
        override suspend fun refresh() {
            writeMutex.withLock {
                _prefs.value = parseFeedViewPrefs(fetchPreferences())
            }
        }

        override fun resetToDefault() {
            _prefs.value = FeedViewPrefs.DEFAULT
        }

        override suspend fun setReplyVisibility(visibility: ReplyVisibility) = update { it.withReplyVisibility(visibility) }

        override suspend fun setHideReposts(hide: Boolean) = update { it.copy(hideReposts = hide) }

        override suspend fun setHideQuotePosts(hide: Boolean) = update { it.copy(hideQuotePosts = hide) }

        /**
         * Optimistic read-modify-write, mirroring
         * `DefaultModerationPreferencesRepository.update`.
         *
         * Publishes the transformed value to [prefs] IMMEDIATELY — before any
         * network — so the Feed preferences screen (a pure projection of [prefs])
         * reacts on the next frame instead of waiting on the `putPreferences`
         * round-trip. Then, under [writeMutex], re-reads the live array so a
         * change made elsewhere since the last refresh isn't clobbered, applies
         * [transform] to the authoritative server state, writes the merged array
         * back, and republishes the reconciled value.
         *
         * On failure it rolls [prefs] back — unless a later optimistic change
         * already superseded ours — and rethrows so the VM can surface a
         * save-error snackbar over a UI that has already snapped back.
         * Cancellation leaves the optimistic value; the next [refresh]
         * reconciles.
         */
        private suspend fun update(transform: (FeedViewPrefs) -> FeedViewPrefs) {
            val previous = _prefs.value
            val optimistic = transform(previous)
            _prefs.value = optimistic
            try {
                writeMutex.withLock {
                    val original = fetchPreferences()
                    val reconciled = transform(parseFeedViewPrefs(original))
                    writePreferences(mergeFeedViewPrefs(original, reconciled))
                    _prefs.value = reconciled
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (_prefs.value == optimistic) _prefs.value = previous
                throw throwable
            }
        }

        private suspend fun fetchPreferences(): List<GetPreferencesResponsePreferencesUnion> = ActorService(xrpcClientProvider.authenticated()).getPreferences(GetPreferencesRequest()).preferences

        private suspend fun writePreferences(preferences: List<PutPreferencesRequestPreferencesUnion>) {
            ActorService(xrpcClientProvider.authenticated()).putPreferences(PutPreferencesRequest(preferences))
        }
    }
