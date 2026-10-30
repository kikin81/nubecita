package net.kikin.nubecita.wear.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearActionRequest
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDecodeResult
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.wear.data.db.WatchPendingPostEntity
import net.kikin.nubecita.wear.data.db.WatchPostActionDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class RoomWatchPostActionRepositoryTest {
    private val alice = "did:plc:alice"
    private val dao = FakeDao()
    private val sender = RecordingSender()
    private var nowMs = 1_000L
    private val repository =
        RoomWatchPostActionRepository(
            dao,
            sender,
            object : Clock {
                override fun now() = Instant.fromEpochMilliseconds(nowMs)
            },
        )

    private fun sentRequest(index: Int = 0): WearActionRequest = (WearCodec.decode(WearActionRequest.serializer(), sender.sent[index].second) as WearDecodeResult.Decoded).value

    @Test
    fun aPost_isStoredAsSending_andQueuedAsCreatePost_atItsActionPath() =
        runTest {
            repository.post(alice, "Finished my first 10k")

            val row = dao.rows.single()
            assertEquals(WatchPendingPostEntity.STATUS_SENDING, row.status)
            assertEquals(1_000L, row.createdAtMs)
            val request = sentRequest()
            assertEquals(WearAction.CreatePost("Finished my first 10k"), request.action)
            assertEquals(row.actionId, request.actionId)
            assertEquals(1_000L, request.createdAtMs)
            assertEquals(WearPaths.action(alice, row.actionId), sender.sent.single().first)
        }

    @Test
    fun aPostThatNeverQueued_isMarkedFailed_soTheFeedOffersARetry() =
        runTest {
            sender.fail = true

            repository.post(alice, "hi")

            assertEquals(WatchPendingPostEntity.STATUS_FAILED, dao.rows.single().status)
        }

    @Test
    fun retry_sendsAFailedPostAgain_asANewActionWithAFreshTime() =
        runTest {
            sender.fail = true
            repository.post(alice, "hi")
            val failedId = dao.rows.single().actionId
            sender.fail = false
            nowMs = 9_000L

            repository.retry(failedId)

            val row = dao.rows.single()
            assertNotEquals(failedId, row.actionId)
            assertEquals(WatchPendingPostEntity.STATUS_SENDING, row.status)
            assertEquals(9_000L, sentRequest().createdAtMs)
        }

    @Test
    fun retry_ignoresAPostStillSending() =
        runTest {
            repository.post(alice, "hi")
            val id = dao.rows.single().actionId

            repository.retry(id)

            assertEquals(listOf(id), dao.rows.map { it.actionId })
            assertTrue(sender.sent.size == 1)
        }

    private class RecordingSender : WatchActionSender {
        var fail = false
        val sent = mutableListOf<Pair<String, String>>()

        override suspend fun send(
            path: String,
            json: String,
        ) {
            if (fail) error("Data Layer unavailable")
            sent += path to json
        }
    }

    private class FakeDao : WatchPostActionDao {
        val rows = mutableListOf<WatchPendingPostEntity>()

        override fun observe(accountDid: String): Flow<List<WatchPendingPostEntity>> = MutableStateFlow(rows.toList())

        override suspend fun get(actionId: String) = rows.firstOrNull { it.actionId == actionId }

        override suspend fun insert(post: WatchPendingPostEntity) {
            rows += post
        }

        override suspend fun markFailed(actionId: String) {
            val i = rows.indexOfFirst { it.actionId == actionId }
            if (i >= 0) rows[i] = rows[i].copy(status = WatchPendingPostEntity.STATUS_FAILED)
        }

        override suspend fun delete(actionIds: List<String>) {
            rows.removeAll { it.actionId in actionIds }
        }

        override suspend fun deleteExcept(accountDid: String) = Unit
    }
}
