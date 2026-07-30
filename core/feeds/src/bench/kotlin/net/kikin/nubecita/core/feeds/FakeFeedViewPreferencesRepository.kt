package net.kikin.nubecita.core.feeds

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic in-memory [FeedViewPreferencesRepository] for the bench flavor.
 * The bench build never signs in, so [refresh] must issue no network call —
 * it just leaves the seeded defaults in place.
 *
 * Note the bench feed is served by `BenchFakeFeedRepository`, which supplies
 * already-mapped `FeedItemUi` values and therefore never runs the reply filter.
 * These prefs consequently have no visible effect in the bench build; see
 * `nubecita-1fmx.3` for why a bench-only check cannot validate the filter.
 */
@Singleton
internal class FakeFeedViewPreferencesRepository
    @Inject
    constructor() : FeedViewPreferencesRepository {
        private val _prefs = MutableStateFlow(FeedViewPrefs.DEFAULT)
        override val prefs: StateFlow<FeedViewPrefs> = _prefs.asStateFlow()

        override suspend fun refresh() = Unit

        override fun resetToDefault() {
            _prefs.value = FeedViewPrefs.DEFAULT
        }

        override suspend fun setReplyVisibility(visibility: ReplyVisibility) = Unit

        override suspend fun setHideReposts(hide: Boolean) = Unit

        override suspend fun setHideQuotePosts(hide: Boolean) = Unit
    }
