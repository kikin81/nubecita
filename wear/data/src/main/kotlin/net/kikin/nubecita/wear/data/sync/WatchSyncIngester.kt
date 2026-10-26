package net.kikin.nubecita.wear.data.sync

import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDecodeResult
import net.kikin.nubecita.core.wearcontract.WearFeedSnapshot
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearcontract.WearPost
import net.kikin.nubecita.core.wearcontract.WearSessionSnapshot
import net.kikin.nubecita.wear.data.db.WatchFeedDao
import net.kikin.nubecita.wear.data.db.WatchFeedEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity
import timber.log.Timber
import javax.inject.Inject

/**
 * Applies what the phone syncs to the watch's offline store. Independent of
 * the Data Layer (it gets JSON and an asset loader), so it is unit-tested with
 * fakes; [WatchSyncListenerService] and [WatchSync.catchUp] feed it.
 */
class WatchSyncIngester
    @Inject
    constructor(
        private val dao: WatchFeedDao,
        private val images: WatchImageStore,
    ) {
        /**
         * Stores a feed snapshot. Ignored when it isn't a feed path, can't be
         * decoded (including a newer major schema: the session screen asks for
         * an update), or is older than what's stored.
         */
        suspend fun ingestFeed(
            path: String,
            json: String,
            loadAsset: suspend (key: String) -> ByteArray?,
        ) {
            val (accountDid, feedKey) = WearPaths.parseFeed(path) ?: return
            val snapshot =
                when (val result = WearCodec.decode(WearFeedSnapshot.serializer(), json)) {
                    is WearDecodeResult.Decoded -> result.value
                    is WearDecodeResult.UnsupportedVersion -> return
                    is WearDecodeResult.Malformed -> {
                        Timber.w(result.cause, "malformed feed snapshot")
                        return
                    }
                }
            val stored = dao.publishedAtMs(accountDid, feedKey)
            if (stored != null && stored >= snapshot.publishedAtMs) return

            val generation = snapshot.publishedAtMs
            val paths = mutableMapOf<String, String?>()

            suspend fun pathFor(key: String?): String? {
                if (key == null) return null
                return paths.getOrPut(key) {
                    loadAsset(key)?.let { bytes -> images.write(accountDid, feedKey, generation, key, bytes) }
                }
            }

            val posts =
                snapshot.posts.mapIndexed { position, post ->
                    post.toEntity(accountDid, feedKey, position, avatarPath = pathFor(post.author.avatarAssetKey), thumbnailPath = pathFor(post.media?.thumbnailAssetKey))
                }
            dao.replaceFeed(WatchFeedEntity(accountDid, feedKey, snapshot.feedName, snapshot.publishedAtMs), posts)
            // Only after the database points at the new files.
            images.pruneFeed(accountDid, feedKey, keepGeneration = generation)
        }

        /** The phone deleted a feed's item (the user deselected it). */
        suspend fun onFeedDeleted(path: String) {
            val (accountDid, feedKey) = WearPaths.parseFeed(path) ?: return
            dao.deleteFeed(accountDid, feedKey)
            images.pruneFeed(accountDid, feedKey, keepGeneration = null)
        }

        /**
         * The session changed or was deleted ([json] null). Everything not
         * belonging to the signed-in account is dropped; signed out drops all.
         */
        suspend fun onSession(json: String?) {
            val account =
                json?.let {
                    when (val result = WearCodec.decode(WearSessionSnapshot.serializer(), it)) {
                        is WearDecodeResult.Decoded -> result.value.account
                        // A newer schema: keep data; the gate asks for an update.
                        is WearDecodeResult.UnsupportedVersion -> return
                        is WearDecodeResult.Malformed -> return
                    }
                }
            dao.keepOnlyAccount(account?.did)
            images.keepOnlyAccount(account?.did)
        }
    }

private fun WearPost.toEntity(
    accountDid: String,
    feedKey: String,
    position: Int,
    avatarPath: String?,
    thumbnailPath: String?,
) = WatchPostEntity(
    accountDid = accountDid,
    feedKey = feedKey,
    position = position,
    uri = uri,
    cid = cid,
    authorDid = author.did,
    authorHandle = author.handle,
    authorDisplayName = author.displayName,
    authorAvatarPath = avatarPath,
    text = text,
    createdAtMs = createdAtMs,
    replyCount = replyCount,
    repostCount = repostCount,
    likeCount = likeCount,
    repostedBy = repostedBy,
    likeUri = viewer.likeUri,
    bookmarked = viewer.bookmarked,
    imageCount = media?.imageCount ?: 0,
    isVideo = media?.isVideo ?: false,
    hasContentWarning = media?.hasContentWarning ?: false,
    thumbnailPath = thumbnailPath,
)
