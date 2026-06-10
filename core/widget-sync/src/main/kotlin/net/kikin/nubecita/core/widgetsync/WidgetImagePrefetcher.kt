package net.kikin.nubecita.core.widgetsync

import net.kikin.nubecita.core.feedcache.FeedKey
import javax.inject.Inject

/**
 * Glance-free seam the refresh runner calls after a feed partition refreshes +
 * trims, to pre-decode that feed's head thumbnails off the active-scroll path
 * (D-C4). `:core:widget-sync` (B) must not depend on `androidx.glance` (nor on
 * an image loader), so this interface is the only thing the runner knows about;
 * the real implementation — which decodes the first image / video poster per
 * head post to a bounded bitmap and prunes evicted thumbnails — is provided by
 * sub-project C (`:feature:widgets`), bound at the `:app` flavor level (mirrors
 * [WidgetUpdater]).
 */
interface WidgetImagePrefetcher {
    /**
     * Decode and cache the head thumbnails for [feedKey] off the UI/scroll path,
     * pruning thumbnails for posts no longer in the head. Runs only after the
     * feed's own refresh succeeds, so it never prefetches a stale partition.
     */
    suspend fun prefetch(feedKey: FeedKey)
}

/**
 * Default [WidgetImagePrefetcher] that does nothing. Lets B ship + test fully
 * without a widget (C's Glance/image pipeline doesn't exist yet) — the worker
 * refreshes the cache and the prefetch call is a harmless no-op until C swaps
 * in the real implementation at the `:app` production-flavor level.
 */
class NoOpWidgetImagePrefetcher
    @Inject
    constructor() : WidgetImagePrefetcher {
        override suspend fun prefetch(feedKey: FeedKey) = Unit
    }
