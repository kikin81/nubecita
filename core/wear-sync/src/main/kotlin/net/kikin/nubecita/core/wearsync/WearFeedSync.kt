package net.kikin.nubecita.core.wearsync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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
     * Publishes one non-urgent snapshot per selected feed from the cache, and
     * deletes the snapshots of feeds that are no longer selected. A feed whose
     * cache is empty is skipped rather than published empty.
     */
    suspend fun publish(accountDid: String)
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

    override suspend fun publish(accountDid: String) {
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
                if (posts.isEmpty()) continue
                val draft = posts.toWearFeedDraft()
                val assets =
                    buildMap {
                        draft.avatarUrls.forEach { (key, url) -> imageEncoder.encode(url, WearImageEncoder.AVATAR_PX)?.let { put(key, it) } }
                        draft.thumbnailUrls.forEach { (key, url) -> imageEncoder.encode(url, WearImageEncoder.THUMBNAIL_PX)?.let { put(key, it) } }
                    }
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
                dataLayer.put(path, WearCodec.encode(WearFeedSnapshot.serializer(), snapshot), urgent = false, assets = assets)
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

    private suspend fun selectedUris(): List<String> =
        watchFeedsPreference.selectedFeedUris
            .first()
            .ifEmpty { setOf(WatchFeedsPreference.FOLLOWING_FEED_URI) }
            .toList()

    internal companion object {
        /** Posts per feed: ~19 KB of JSON, well under the 100 KB payload limit. */
        const val MAX_POSTS = 50

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
