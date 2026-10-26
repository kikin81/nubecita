package net.kikin.nubecita.wear.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.wear.data.db.WatchActionDao
import net.kikin.nubecita.wear.data.db.WatchPendingActionEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class RoomWatchActionRepositoryTest {
    private val alice = "did:plc:alice"
    private val like = WearAction.Like("at://did:plc:bob/app.bsky.feed.post/3k", "cid")
    private val dao = FakeActionDao()
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_000)
        }

    @Test
    fun submit_storesThePendingAction_thenSendsIt() =
        runTest {
            val sender = RecordingSender()

            RoomWatchActionRepository(dao, sender, clock).submit(alice, like)

            val row = dao.rows.single()
            assertEquals("like", row.kind)
            assertEquals(1_000L, row.createdAtMs)
            assertEquals(listOf(WearPaths.action(alice, row.actionId)), sender.sent)
        }

    @Test
    fun aSendThatFails_dropsTheStoredAction() =
        runTest {
            RoomWatchActionRepository(dao, RecordingSender(fail = true), clock).submit(alice, like)

            assertTrue(dao.rows.isEmpty())
        }

    @Test
    fun cancellingTheCallerMidSend_stillSends_soNoActionIsLeftUnsent() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val sender = RecordingSender(gate = gate)
            val repository = RoomWatchActionRepository(dao, sender, clock)

            val tap = launch { repository.submit(alice, like) }
            advanceUntilIdle()
            tap.cancel()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, sender.sent.size)
            assertEquals(1, dao.rows.size)
        }

    private class RecordingSender(
        private val fail: Boolean = false,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : WatchActionSender {
        val sent = mutableListOf<String>()

        override suspend fun send(
            path: String,
            json: String,
        ) {
            gate?.await()
            if (fail) error("Data Layer unavailable")
            sent += path
        }
    }

    private class FakeActionDao : WatchActionDao {
        val rows = mutableListOf<WatchPendingActionEntity>()

        override fun observe(accountDid: String): Flow<List<WatchPendingActionEntity>> = MutableStateFlow(rows.toList())

        override suspend fun all(accountDid: String) = rows.toList()

        override suspend fun insert(action: WatchPendingActionEntity) {
            rows += action
        }

        override suspend fun markConfirmed(
            actionId: String,
            recordUri: String?,
            completedAtMs: Long,
        ) = Unit

        override suspend fun delete(actionIds: List<String>) {
            rows.removeAll { it.actionId in actionIds }
        }

        override suspend fun deleteExcept(accountDid: String) = Unit
    }
}
