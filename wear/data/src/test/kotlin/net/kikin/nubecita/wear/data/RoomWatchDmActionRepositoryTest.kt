package net.kikin.nubecita.wear.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearActionRequest
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDecodeResult
import net.kikin.nubecita.wear.data.db.WatchDmActionDao
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class RoomWatchDmActionRepositoryTest {
    private val alice = "did:plc:alice"
    private val dao = FakeDao()
    private val sender = RecordingSender()
    private var nowMs = 1_000L
    private val repository =
        RoomWatchDmActionRepository(
            dao,
            sender,
            object : Clock {
                override fun now() = Instant.fromEpochMilliseconds(nowMs)
            },
        )

    private fun sentAction(index: Int = 0): WearAction = (WearCodec.decode(WearActionRequest.serializer(), sender.sent[index]) as WearDecodeResult.Decoded).value.action

    @Test
    fun aQuickReply_isStoredAsSending_andQueued() =
        runTest {
            repository.sendReply(alice, "c1", "On my way")

            val row = dao.rows.single()
            assertEquals(WatchPendingDmEntity.KIND_REPLY, row.kind)
            assertEquals(WatchPendingDmEntity.STATUS_SENDING, row.status)
            assertEquals(WearAction.DmReply("c1", "On my way"), sentAction())
        }

    @Test
    fun aReaction_carriesItsTargetMessage() =
        runTest {
            repository.sendReaction(alice, "c1", "m9", "👍")

            assertEquals("m9", dao.rows.single().targetMessageId)
            assertEquals(WearAction.DmReact("c1", "m9", "👍"), sentAction())
        }

    @Test
    fun aSendThatNeverQueued_isMarkedFailed_soTheThreadOffersARetry() =
        runTest {
            sender.fail = true

            repository.sendReply(alice, "c1", "Thanks!")

            assertEquals(WatchPendingDmEntity.STATUS_FAILED, dao.rows.single().status)
        }

    @Test
    fun retry_sendsAFailedReplyAgain_asANewActionWithAFreshTime() =
        runTest {
            sender.fail = true
            repository.sendReply(alice, "c1", "Thanks!")
            val failedId = dao.rows.single().actionId
            sender.fail = false
            nowMs = 9_000L

            repository.retry(failedId)

            val row = dao.rows.single()
            assertNotEquals(failedId, row.actionId)
            assertEquals(WatchPendingDmEntity.STATUS_SENDING, row.status)
            assertEquals(9_000L, row.createdAtMs)
            assertEquals(WearAction.DmReply("c1", "Thanks!"), sentAction())
        }

    private class RecordingSender : WatchActionSender {
        var fail = false
        val sent = mutableListOf<String>()

        override suspend fun send(
            path: String,
            json: String,
        ) {
            if (fail) error("Data Layer unavailable")
            sent += json
        }
    }

    private class FakeDao : WatchDmActionDao {
        val rows = mutableListOf<WatchPendingDmEntity>()

        override fun observe(accountDid: String): Flow<List<WatchPendingDmEntity>> = MutableStateFlow(rows.toList())

        override suspend fun all(accountDid: String) = rows.toList()

        override suspend fun get(actionId: String) = rows.firstOrNull { it.actionId == actionId }

        override suspend fun insert(action: WatchPendingDmEntity) {
            rows += action
        }

        override suspend fun markSent(
            actionId: String,
            sentMessageId: String?,
        ) = Unit

        override suspend fun markFailed(actionId: String) {
            val i = rows.indexOfFirst { it.actionId == actionId }
            if (i >= 0) rows[i] = rows[i].copy(status = WatchPendingDmEntity.STATUS_FAILED)
        }

        override suspend fun delete(actionIds: List<String>) {
            rows.removeAll { it.actionId in actionIds }
        }

        override suspend fun deleteExcept(accountDid: String) = Unit
    }
}
