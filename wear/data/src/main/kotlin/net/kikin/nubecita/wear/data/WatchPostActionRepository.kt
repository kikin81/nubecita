package net.kikin.nubecita.wear.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearActionRequest
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.wear.data.db.WatchPendingPostEntity
import net.kikin.nubecita.wear.data.db.WatchPendingPostEntity.Companion.STATUS_FAILED
import net.kikin.nubecita.wear.data.db.WatchPostActionDao
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Clock

/** Posts written on the watch (nubecita-rxsh.6), sent through the phone. */
interface WatchPostActionRepository {
    /** The account's posts the phone hasn't posted yet, oldest first. */
    fun pending(accountDid: String): Flow<List<WatchPendingPostEntity>>

    /** Queues a post; works offline, delivered when the phone is back in reach. */
    suspend fun post(
        accountDid: String,
        text: String,
    )

    /** Sends a failed post again, as a new action with a fresh expiry. */
    suspend fun retry(actionId: String)
}

class RoomWatchPostActionRepository
    @Inject
    constructor(
        private val dao: WatchPostActionDao,
        private val sender: WatchActionSender,
        private val clock: Clock,
    ) : WatchPostActionRepository {
        override fun pending(accountDid: String): Flow<List<WatchPendingPostEntity>> = dao.observe(accountDid)

        // Not cancellable, like DM replies: a stored but unsent post would sit in
        // "Posting…" forever.
        override suspend fun post(
            accountDid: String,
            text: String,
        ) = withContext(NonCancellable) { store(accountDid, text) }

        override suspend fun retry(actionId: String) {
            withContext(NonCancellable) {
                val failed = dao.get(actionId)?.takeIf { it.status == STATUS_FAILED } ?: return@withContext
                dao.delete(listOf(actionId))
                store(failed.accountDid, failed.text)
            }
        }

        private suspend fun store(
            accountDid: String,
            text: String,
        ) {
            val id = UUID.randomUUID().toString()
            val now = clock.now().toEpochMilliseconds()
            dao.insert(WatchPendingPostEntity(actionId = id, accountDid = accountDid, text = text, createdAtMs = now))
            try {
                val request = WearActionRequest(actionId = id, accountDid = accountDid, createdAtMs = now, action = WearAction.CreatePost(text))
                sender.send(WearPaths.action(accountDid, id), WearCodec.encode(WearActionRequest.serializer(), request))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Never queued: offer the retry rather than "Posting…" forever.
                Timber.w(error, "failed to queue a watch post")
                dao.markFailed(id)
            }
        }
    }
