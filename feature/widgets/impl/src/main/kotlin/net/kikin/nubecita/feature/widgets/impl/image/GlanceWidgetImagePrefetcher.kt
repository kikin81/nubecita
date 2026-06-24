package net.kikin.nubecita.feature.widgets.impl.image

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.widgetsync.WidgetImagePrefetcher
import net.kikin.nubecita.feature.widgets.impl.MAX_WIDGET_POSTS
import javax.inject.Inject

/**
 * Real [WidgetImagePrefetcher] (D-C5), invoked by the background refresh worker
 * (B) after a feed partition refreshes + trims. For each post currently in the
 * feed's head it decodes the first thumbnail / video poster to a bounded bitmap
 * via [ThumbnailDecoder] and persists it through [WidgetThumbnailStore], then
 * **evicts** thumbnails for posts that have scrolled out of the head — bounding
 * the image cache to ~the head size per feed (the image analogue of
 * `:core:feed-cache`'s `trimToCap`).
 *
 * Runs off the UI/scroll path: the worker already guards on app-backgrounded,
 * and all work here is dispatched on [IoDispatcher]. One decode per media post
 * (not per gallery) keeps the RemoteViews IPC + battery cost bounded.
 *
 * Bound to [WidgetImagePrefetcher] in the `:app` production flavor (a later
 * task); the bench flavor keeps the no-op. The orchestration is JVM-unit-tested
 * with fakes; the only Android/Coil-bound step is [ThumbnailDecoder].
 */
internal class GlanceWidgetImagePrefetcher
    @Inject
    constructor(
        private val feedRepository: FeedRepository,
        private val thumbnailStore: WidgetThumbnailStore,
        private val decoder: ThumbnailDecoder,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : WidgetImagePrefetcher {
        override suspend fun prefetch(feedKey: FeedKey) =
            withContext(dispatcher) {
                val posts = feedRepository.head(feedKey, MAX_WIDGET_POSTS).first()

                for (post in posts) {
                    val url = widgetThumbnailUrl(post.embed) ?: continue
                    // Skip the network/decode when this post's thumbnail is already
                    // cached — refresh churns the head far more often than a given
                    // post's media changes.
                    if (thumbnailStore.hasThumbnail(feedKey.accountDid, post.id)) continue
                    decoder.decodeToFile(url, thumbnailStore.thumbnailFile(feedKey.accountDid, post.id))
                }

                // Eviction (D-C5): drop thumbnails for posts no longer in the head.
                thumbnailStore.evict(feedKey.accountDid, posts.mapTo(HashSet(posts.size)) { it.id })
            }
    }
