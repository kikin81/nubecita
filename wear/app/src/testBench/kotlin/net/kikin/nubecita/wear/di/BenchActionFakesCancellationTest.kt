package net.kikin.nubecita.wear.di

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.wear.bench.BenchScenario
import net.kikin.nubecita.wear.bench.BenchScenarios
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The real stores finish a send whose caller was cancelled. The bench fakes
 * must too, or a journey that leaves a screen mid-send finds the row stuck
 * for the rest of the process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BenchActionFakesCancellationTest {
    @AfterEach
    fun reset() {
        BenchScenarios.current.value = BenchScenario.Default
    }

    @Test
    fun `a post whose caller is cancelled still reaches the feed`() =
        runTest {
            val posted = BenchPostedPosts()
            val repository = BenchWatchPostActionRepository(posted)

            val caller = launch { repository.post(DID, "hello") }
            runCurrent()
            assertEquals(1, repository.pending(DID).first().size)
            caller.cancel()
            advanceUntilIdle()

            assertEquals(listOf("hello"), posted.posts.value.map { it.text })
            assertTrue(repository.pending(DID).first().isEmpty())
        }

    @Test
    fun `a slow reply whose caller is cancelled still ends as sent`() =
        runTest {
            BenchScenarios.current.value = BenchScenario.SlowActions
            val repository = BenchWatchDmActionRepository()

            val caller = launch { repository.sendReply(DID, "dm-ana", "on my way") }
            runCurrent()
            assertEquals(
                WatchPendingDmEntity.STATUS_SENDING,
                repository
                    .pending(DID)
                    .first()
                    .single()
                    .status,
            )
            caller.cancel()
            advanceUntilIdle()

            assertEquals(
                WatchPendingDmEntity.STATUS_SENT,
                repository
                    .pending(DID)
                    .first()
                    .single()
                    .status,
            )
        }

    @Test
    fun `a slow like whose caller is cancelled still ends confirmed`() =
        runTest {
            BenchScenarios.current.value = BenchScenario.SlowActions
            val repository = BenchWatchActionRepository()

            val caller = launch { repository.submit(DID, WearAction.Bookmark(postUri = "at://post", postCid = "cid")) }
            runCurrent()
            assertEquals(
                false,
                repository
                    .pending(DID)
                    .first()
                    .single()
                    .confirmed,
            )
            caller.cancel()
            advanceUntilIdle()

            assertEquals(
                true,
                repository
                    .pending(DID)
                    .first()
                    .single()
                    .confirmed,
            )
        }

    private companion object {
        const val DID = "did:plc:nubecitabench"
    }
}
