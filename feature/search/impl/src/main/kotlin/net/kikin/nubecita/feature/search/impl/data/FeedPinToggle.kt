package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import javax.inject.Inject

/** How a [FeedPinToggle.toggle] ended. The caller logs analytics and tells the screen. */
internal enum class PinOutcome {
    Pinned,
    Unpinned,
    PinFailed,
    UnpinFailed,
}

/**
 * The one pin / unpin call for every surface that lists searched feeds (the Feeds tab and
 * the summary pane): drops a second tap on a feed whose call is still running, then calls
 * [PinnedFeedsRepository] and reports how it ended. The caller flips the row optimistically
 * through `onOptimistic` and flips it back on a failure outcome; the pinned state itself
 * comes from [PinnedFeedsRepository.observePinnedFeeds] (see [withPinnedFrom]).
 *
 * Unscoped: each owner gets its own instance, so one surface's in-flight taps never block
 * another's. Not thread-safe; used from the main thread.
 */
internal class FeedPinToggle
    @Inject
    constructor(
        private val repository: PinnedFeedsRepository,
    ) {
        private val activeJobs = mutableMapOf<String, Job>()

        /**
         * Pins or unpins [uri], currently [isPinned]. [scope] is the caller's: the call runs in
         * it and [onOutcome] is delivered from it. [onOptimistic] runs first, with the state the
         * row should show now, before the call can possibly settle. Returns false, doing nothing,
         * when a call for [uri] is already in flight.
         */
        fun toggle(
            scope: CoroutineScope,
            uri: String,
            isPinned: Boolean,
            onOptimistic: (pinned: Boolean) -> Unit,
            onOutcome: (PinOutcome) -> Unit,
        ): Boolean {
            if (activeJobs[uri]?.isActive == true) return false
            onOptimistic(!isPinned)
            activeJobs[uri] =
                scope.launch {
                    val result = if (isPinned) repository.unpinFeed(uri) else repository.pinFeed(uri)
                    result
                        .onSuccess { onOutcome(if (isPinned) PinOutcome.Unpinned else PinOutcome.Pinned) }
                        .onFailure { onOutcome(if (isPinned) PinOutcome.UnpinFailed else PinOutcome.PinFailed) }
                }
            return true
        }
    }

/** This feed with its pinned flag taken from [uris], the viewer's pinned feed URIs. */
internal fun FeedGeneratorUi.withPinnedFrom(uris: Set<String>): FeedGeneratorUi = if (isPinned == (uri in uris)) this else copy(isPinned = uri in uris)
