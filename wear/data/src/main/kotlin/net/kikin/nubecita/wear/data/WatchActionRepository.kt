package net.kikin.nubecita.wear.data

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.wearcontract.WearActionRequest
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDataKeys
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearcontract.WearPostAction
import net.kikin.nubecita.wear.data.db.WatchActionDao
import net.kikin.nubecita.wear.data.db.WatchPendingActionEntity
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Clock

/** The watch's own likes, reposts and bookmarks (nubecita-hohv.3). */
interface WatchActionRepository {
    /** The account's actions that haven't settled yet, oldest first. */
    fun pending(accountDid: String): Flow<List<WatchPendingActionEntity>>

    /**
     * Records [action] (shown immediately through the overlay) and queues it
     * for the phone. Works offline: the queued item is delivered when the
     * phone is back in reach.
     */
    suspend fun submit(
        accountDid: String,
        action: WearPostAction,
    )
}

/** Writes a queued action to the Data Layer. A seam so the repository is JVM-testable. */
interface WatchActionSender {
    suspend fun send(
        path: String,
        json: String,
    )
}

/** Production repository over Room and the Data Layer. Bound per flavor by the Wear app. */
class RoomWatchActionRepository
    @Inject
    constructor(
        private val dao: WatchActionDao,
        private val sender: WatchActionSender,
        private val clock: Clock,
    ) : WatchActionRepository {
        override fun pending(accountDid: String): Flow<List<WatchPendingActionEntity>> = dao.observe(accountDid)

        override suspend fun submit(
            accountDid: String,
            action: WearPostAction,
        ) {
            // Not cancellable: a tap followed by closing the app must not leave a
            // stored action that was never sent. Unanswered actions never expire,
            // so it would lock that toggle for good.
            withContext(NonCancellable) { store(accountDid, action) }
        }

        private suspend fun store(
            accountDid: String,
            action: WearPostAction,
        ) {
            val id = UUID.randomUUID().toString()
            val now = clock.now().toEpochMilliseconds()
            // Stored first, so the overlay shows the change at once.
            dao.insert(WatchPendingActionEntity(actionId = id, accountDid = accountDid, postUri = action.postUri, kind = PostActionKind.of(action).wire, createdAtMs = now))
            val request = WearActionRequest(actionId = id, accountDid = accountDid, createdAtMs = now, action = action)
            try {
                sender.send(WearPaths.action(accountDid, id), WearCodec.encode(WearActionRequest.serializer(), request))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Never queued: revert silently, like a failed result.
                Timber.w(error, "failed to queue a watch action")
                dao.delete(listOf(id))
            }
        }
    }

/** Queues an action as an urgent Data Layer item; items persist until the phone deletes them. */
internal class DataLayerWatchActionSender
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WatchActionSender {
        override suspend fun send(
            path: String,
            json: String,
        ) {
            val request =
                PutDataMapRequest
                    .create(path)
                    .apply { dataMap.putString(WearDataKeys.PAYLOAD, json) }
                    .asPutDataRequest()
                    .setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        }
    }
