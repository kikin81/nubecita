package net.kikin.nubecita.wear.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearActionRequest
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDmAction
import net.kikin.nubecita.core.wearcontract.WearJson
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearcontract.WearReaction
import net.kikin.nubecita.wear.data.db.WatchDmActionDao
import net.kikin.nubecita.wear.data.db.WatchMessageEntity
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity.Companion.KIND_REACT
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity.Companion.KIND_REPLY
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity.Companion.STATUS_FAILED
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity.Companion.STATUS_SENT
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Clock

/** The watch's quick replies and reactions (nubecita-by5w.3). */
interface WatchDmActionRepository {
    /** The account's replies and reactions not yet shown by a DM snapshot, oldest first. */
    fun pending(accountDid: String): Flow<List<WatchPendingDmEntity>>

    /** Queues a quick reply; works offline, delivered when the phone is back in reach. */
    suspend fun sendReply(
        accountDid: String,
        convoId: String,
        text: String,
    )

    suspend fun sendReaction(
        accountDid: String,
        convoId: String,
        messageId: String,
        emoji: String,
    )

    /** Sends a failed reply or reaction again, as a new action with a fresh expiry. */
    suspend fun retry(actionId: String)
}

class RoomWatchDmActionRepository
    @Inject
    constructor(
        private val dao: WatchDmActionDao,
        private val sender: WatchActionSender,
        private val clock: Clock,
    ) : WatchDmActionRepository {
        override fun pending(accountDid: String): Flow<List<WatchPendingDmEntity>> = dao.observe(accountDid)

        override suspend fun sendReply(
            accountDid: String,
            convoId: String,
            text: String,
        ) = queue(accountDid, WearAction.DmReply(convoId, text), KIND_REPLY, text, targetMessageId = null)

        override suspend fun sendReaction(
            accountDid: String,
            convoId: String,
            messageId: String,
            emoji: String,
        ) = queue(accountDid, WearAction.DmReact(convoId, messageId, emoji), KIND_REACT, emoji, targetMessageId = messageId)

        override suspend fun retry(actionId: String) {
            withContext(NonCancellable) {
                val failed = dao.get(actionId)?.takeIf { it.status == STATUS_FAILED } ?: return@withContext
                dao.delete(listOf(actionId))
                val action =
                    if (failed.kind == KIND_REACT && failed.targetMessageId != null) {
                        WearAction.DmReact(failed.convoId, failed.targetMessageId, failed.text)
                    } else {
                        WearAction.DmReply(failed.convoId, failed.text)
                    }
                store(failed.accountDid, action, failed.kind, failed.text, failed.targetMessageId)
            }
        }

        // Not cancellable, like post actions: a stored but unsent reply would sit
        // in "Sending…" forever.
        private suspend fun queue(
            accountDid: String,
            action: WearDmAction,
            kind: String,
            text: String,
            targetMessageId: String?,
        ) = withContext(NonCancellable) { store(accountDid, action, kind, text, targetMessageId) }

        private suspend fun store(
            accountDid: String,
            action: WearDmAction,
            kind: String,
            text: String,
            targetMessageId: String?,
        ) {
            val id = UUID.randomUUID().toString()
            val now = clock.now().toEpochMilliseconds()
            dao.insert(WatchPendingDmEntity(actionId = id, accountDid = accountDid, convoId = action.convoId, kind = kind, text = text, targetMessageId = targetMessageId, createdAtMs = now))
            try {
                val request = WearActionRequest(actionId = id, accountDid = accountDid, createdAtMs = now, action = action)
                sender.send(WearPaths.action(accountDid, id), WearCodec.encode(WearActionRequest.serializer(), request))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Never queued: offer the retry rather than "Sending…" forever.
                Timber.w(error, "failed to queue a watch DM action")
                dao.markFailed(id)
            }
        }
    }

private val reactionsSerializer = ListSerializer(WearReaction.serializer())

/** Encodes a message's reactions for [WatchMessageEntity.reactions]. */
fun List<WearReaction>.toReactionsColumn(): String = WearJson.encodeToString(reactionsSerializer, this)

/** The message's reactions; empty when the stored value can't be read. */
fun WatchMessageEntity.reactionList(): List<WearReaction> = runCatching { WearJson.decodeFromString(reactionsSerializer, reactions) }.getOrDefault(emptyList())

/**
 * The sent replies and reactions a DM snapshot now shows (a reply by its sent
 * message id, a reaction by the viewer's emoji on its message), plus any
 * sent or failed one older than [maxAgeMs]. Unanswered ones stay.
 */
fun settledDmActions(
    messages: List<WatchMessageEntity>,
    actions: List<WatchPendingDmEntity>,
    nowMs: Long,
    maxAgeMs: Long = SETTLE_MAX_AGE_MS,
): List<String> {
    val byId = messages.associateBy { it.messageId }
    return actions
        .filter { it.status != WatchPendingDmEntity.STATUS_SENDING }
        .filter { action ->
            val stale = nowMs - action.createdAtMs > maxAgeMs
            val shown =
                action.status == STATUS_SENT &&
                    when (action.kind) {
                        KIND_REPLY -> action.sentMessageId != null && action.sentMessageId in byId
                        else -> byId[action.targetMessageId]?.reactionList()?.any { it.emoji == action.text && it.mine } == true
                    }
            stale || shown
        }.map { it.actionId }
}
