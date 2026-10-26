package net.kikin.nubecita.core.wearsync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feedcache.FeedType
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.preferences.WatchFeedsPreference
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearFeedKey
import net.kikin.nubecita.core.wearcontract.WearFeedSnapshot
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearsync.internal.WearDataLayer
import net.kikin.nubecita.core.wearsync.internal.WearImageEncoder
import net.kikin.nubecita.core.wearsync.internal.toWearFeedDraft
import timber.log.Timber
import kotlin.time.Clock

/**
 * Sends the user's chosen watch feeds to the Wear app (nubecita-jh9c.2).
 *
 * It runs inside the existing background widget refresh: that run asks
 * [feedKeysToRefresh] which extra feeds to refresh, refreshes them through the
 * same cache and auth path (so no new token-refresh caller), then calls
 * [publish]. With no watch paired both are no-ops, so nothing is fetched or
 * sent for nobody.
 */
interface WearFeedSync {
    /** The cache keys of the selected watch feeds, or nothing when no watch has the app. */
    suspend fun feedKeysToRefresh(accountDid: String): List<FeedKey>

    /**
     * Publishes one snapshot per selected feed from the cache, and deletes the
     * snapshots of feeds that are no longer selected. A feed whose cache is
     * empty keeps its previous snapshot. Periodic publishes are non-urgent;
     * [urgent] is for the watch app that just opened and is waiting.
     */
    suspend fun publish(
        accountDid: String,
        urgent: Boolean = false,
    )
}

internal class DefaultWearFeedSync(
    private val dataLayer: WearDataLayer,
    private val watchFeedsPreference: WatchFeedsPreference,
    private val pinnedFeedsRepository: PinnedFeedsRepository,
    private val feedRepository: FeedRepository,
    private val imageEncoder: WearImageEncoder,
    private val clock: Clock,
) : WearFeedSync {
    override suspend fun feedKeysToRefresh(accountDid: String): List<FeedKey> {
        if (!dataLayer.hasWearAppNode()) return emptyList()
        return selectedUris().map { feedKeyFor(accountDid, it) }
    }

    override suspend fun publish(
        accountDid: String,
        urgent: Boolean,
    ) {
        if (!dataLayer.hasWearAppNode()) return
        val uris = selectedUris()
        val names =
            pinnedFeedsRepository
                .observePinnedFeeds()
                .first()
                .feeds
                .associate { it.uri to it.displayName }
        val published = mutableSetOf<String>()
        for (uri in uris) {
            val feedKey = WearFeedKey.forUri(uri)
            val path = WearPaths.feed(accountDid, feedKey)
            // One feed's failure (a decode, a Data Layer hiccup) must not stop
            // the others. Rethrow cancellation.
            try {
                val posts = feedRepository.head(feedKeyFor(accountDid, uri), MAX_POSTS).first()
                if (posts.isEmpty()) {
                    // Nothing cached right now: keep the watch's previous snapshot
                    // (offline-first) rather than letting the cleanup below delete it.
                    published += path
                    continue
                }
                val draft = posts.toWearFeedDraft()
                val assets = encodeAll(draft.avatarUrls, WearImageEncoder.AVATAR_PX) + encodeAll(draft.thumbnailUrls, WearImageEncoder.THUMBNAIL_PX)
                // An image that failed to load is simply not referenced, so the
                // watch never asks for a missing Asset.
                val postsWithAssets =
                    draft.posts.map { post ->
                        post.copy(
                            author = post.author.copy(avatarAssetKey = post.author.avatarAssetKey?.takeIf { it in assets }),
                            media = post.media?.let { it.copy(thumbnailAssetKey = it.thumbnailAssetKey?.takeIf { key -> key in assets }) },
                        )
                    }
                val snapshot =
                    WearFeedSnapshot(
                        publishedAtMs = clock.now().toEpochMilliseconds(),
                        accountDid = accountDid,
                        feedKey = feedKey,
                        feedName = if (uri == WatchFeedsPreference.FOLLOWING_FEED_URI) null else names[uri],
                        posts = postsWithAssets,
                    )
                dataLayer.put(path, WearCodec.encode(WearFeedSnapshot.serializer(), snapshot), urgent = urgent, assets = assets)
                published += path
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Timber.w(error, "wear feed publish failed")
                published += path // keep the previous snapshot rather than deleting it
            }
        }
        // Exact deletes: a Base64 feed key can be a prefix of another feed's key.
        dataLayer.pathsUnder(WearPaths.feeds(accountDid)).filterNot { it in published }.forEach { dataLayer.delete(it) }
    }

    /**
     * Loads and encodes images a few at a time (Coil queues the network work),
     * instead of one by one: a feed can reference ~100 images. Capped so a
     * background run doesn't open dozens of connections at once.
     */
    private suspend fun encodeAll(
        urls: Map<String, String>,
        maxPx: Int,
    ): Map<String, ByteArray> {
        val permits = Semaphore(MAX_CONCURRENT_IMAGES)
        return coroutineScope {
            urls
                .map { (key, url) -> async { permits.withPermit { imageEncoder.encode(url, maxPx)?.let { key to it } } } }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }
    }

    /**
     * The same effective selection Settings → Watch shows: stored URIs that are
     * still pinned (Following always counts), falling back to Following when
     * none are. A feed the user unpinned stops syncing.
     */
    private suspend fun selectedUris(): List<String> {
        val pinned =
            pinnedFeedsRepository
                .observePinnedFeeds()
                .first()
                .feeds
                .mapTo(mutableSetOf()) { it.uri }
        pinned += WatchFeedsPreference.FOLLOWING_FEED_URI
        return watchFeedsPreference.selectedFeedUris
            .first()
            .filter { it in pinned }
            .ifEmpty { listOf(WatchFeedsPreference.FOLLOWING_FEED_URI) }
    }

    internal companion object {
        /** Posts per feed: ~19 KB of JSON, well under the 100 KB payload limit. */
        const val MAX_POSTS = 50

        /** Images loaded at once while building a snapshot. */
        const val MAX_CONCURRENT_IMAGES = 4

        /** Which cache partition a selected feed URI lives in (shared with the app and widget). */
        fun feedKeyFor(
            accountDid: String,
            uri: String,
        ): FeedKey =
            when {
                uri == WatchFeedsPreference.FOLLOWING_FEED_URI -> FeedKey.following(accountDid)
                uri == PinnedFeedsRepository.DISCOVER_FEED_URI -> FeedKey(accountDid, FeedType.DISCOVER, uri)
                "/app.bsky.graph.list/" in uri -> FeedKey(accountDid, FeedType.LIST, uri)
                else -> FeedKey(accountDid, FeedType.CUSTOM, uri)
            }
    }
}
