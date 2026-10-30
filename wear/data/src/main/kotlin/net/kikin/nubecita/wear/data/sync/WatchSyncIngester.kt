package net.kikin.nubecita.wear.data.sync

import net.kikin.nubecita.core.wearcontract.WearActionResult
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDecodeResult
import net.kikin.nubecita.core.wearcontract.WearDmSnapshot
import net.kikin.nubecita.core.wearcontract.WearFeedSnapshot
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearcontract.WearPost
import net.kikin.nubecita.core.wearcontract.WearSessionSnapshot
import net.kikin.nubecita.wear.data.db.WatchActionDao
import net.kikin.nubecita.wear.data.db.WatchConvoEntity
import net.kikin.nubecita.wear.data.db.WatchDmActionDao
import net.kikin.nubecita.wear.data.db.WatchDmDao
import net.kikin.nubecita.wear.data.db.WatchDmStateEntity
import net.kikin.nubecita.wear.data.db.WatchFeedDao
import net.kikin.nubecita.wear.data.db.WatchFeedEntity
import net.kikin.nubecita.wear.data.db.WatchMessageEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity
import net.kikin.nubecita.wear.data.settledActions
import net.kikin.nubecita.wear.data.settledDmActions
import net.kikin.nubecita.wear.data.toReactionsColumn
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

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
        private val actionDao: WatchActionDao,
        private val dmDao: WatchDmDao,
        private val dmActionDao: WatchDmActionDao,
        private val clock: Clock,
        private val dmObservers: Set<@JvmSuppressWildcards DmSyncObserver>,
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
            // The user's confirmed actions this snapshot now shows no longer need the overlay.
            val settled = settledActions(posts, actionDao.all(accountDid), nowMs = clock.now().toEpochMilliseconds())
            if (settled.isNotEmpty()) actionDao.delete(settled)
            // Only after the database points at the new files.
            images.pruneFeed(accountDid, feedKey, keepGeneration = generation)
        }

        /**
         * The phone's answer to one of the watch's actions. Ok keeps the action
         * (with the record it created) until a snapshot shows it; failed drops
         * it, which silently reverts the post. Returns false when [path] isn't
         * a result, so the caller only deletes items it understood.
         */
        suspend fun onActionResult(
            path: String,
            json: String,
        ): Boolean {
            val (_, actionId) = WearPaths.parseActionResult(path) ?: return false
            when (val result = WearCodec.decode(WearActionResult.serializer(), json)) {
                is WearDecodeResult.Decoded ->
                    if (result.value.isOk) {
                        actionDao.markConfirmed(actionId, result.value.recordUri, result.value.completedAtMs)
                        dmActionDao.markSent(actionId, result.value.recordUri)
                    } else {
                        actionDao.delete(listOf(actionId))
                        dmActionDao.markFailed(actionId)
                    }
                // Unreadable: treat as failed, so the button doesn't stay locked.
                is WearDecodeResult.UnsupportedVersion, is WearDecodeResult.Malformed -> {
                    actionDao.delete(listOf(actionId))
                    dmActionDao.markFailed(actionId)
                }
            }
            return true
        }

        /**
         * Stores the account's DM snapshot (W4), replacing what was there. Ignored
         * when it isn't the DMs path, can't be decoded, or is older than what's
         * stored.
         */
        suspend fun ingestDms(
            path: String,
            json: String,
        ) {
            val accountDid = WearPaths.parseDms(path) ?: return
            val snapshot =
                when (val result = WearCodec.decode(WearDmSnapshot.serializer(), json)) {
                    is WearDecodeResult.Decoded -> result.value
                    is WearDecodeResult.UnsupportedVersion -> return
                    is WearDecodeResult.Malformed -> {
                        Timber.w(result.cause, "malformed DM snapshot")
                        return
                    }
                }
            val stored = dmDao.publishedAtMs(accountDid)
            if (stored != null && stored >= snapshot.publishedAtMs) return
            val convos =
                snapshot.convos.mapIndexed { position, c ->
                    WatchConvoEntity(accountDid, c.convoId, position, c.title, c.isGroup, c.unreadCount, c.muted, c.lastMessageAtMs)
                }
            val messages =
                snapshot.convos.flatMap { c ->
                    c.messages.mapIndexed { position, m ->
                        WatchMessageEntity(accountDid, c.convoId, position, m.id, m.fromViewer, m.senderName, m.text, m.sentAtMs, m.isDeleted, m.hasAttachment, m.reactions.toReactionsColumn())
                    }
                }
            dmDao.replaceDms(WatchDmStateEntity(accountDid, snapshot.publishedAtMs, snapshot.unreadCount), convos, messages)
            // Replies and reactions this snapshot now shows no longer need their placeholder.
            val settled = settledDmActions(messages, dmActionDao.all(accountDid), nowMs = clock.now().toEpochMilliseconds())
            if (settled.isNotEmpty()) dmActionDao.delete(settled)
            notifyDmsChanged()
        }

        /** The phone deleted the DM snapshot (sign-out or account switch). */
        suspend fun onDmsDeleted(path: String) {
            val accountDid = WearPaths.parseDms(path) ?: return
            dmDao.deleteDms(accountDid)
            notifyDmsChanged()
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
            actionDao.deleteExcept(account?.did.orEmpty())
            dmDao.keepOnlyAccount(account?.did.orEmpty())
            dmActionDao.deleteExcept(account?.did.orEmpty())
            images.keepOnlyAccount(account?.did)
            notifyDmsChanged()
        }

        // An observer failing must not undo or block the sync that just landed.
        private fun notifyDmsChanged() {
            dmObservers.forEach { observer ->
                try {
                    observer.onDmsChanged()
                } catch (error: Exception) {
                    Timber.w(error, "DM sync observer failed")
                }
            }
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
    repostUri = viewer.repostUri,
    bookmarked = viewer.bookmarked,
    imageCount = media?.imageCount ?: 0,
    isVideo = media?.isVideo ?: false,
    hasContentWarning = media?.hasContentWarning ?: false,
    thumbnailPath = thumbnailPath,
)
