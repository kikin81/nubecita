package net.kikin.nubecita.feature.notifications.impl.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsPage
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsUnreadCountStoreTest {
    @Test
    fun `unreadCount starts at zero before any refresh`() =
        runTest {
            val store = NotificationsUnreadCountStore(repository = FakeRepository())

            assertEquals(0, store.unreadCount.value)
        }

    @Test
    fun `refresh with a successful repository response updates the StateFlow value`() =
        runTest {
            val repository = FakeRepository(responses = ArrayDeque(listOf(Result.success(7))))
            val store = NotificationsUnreadCountStore(repository = repository)

            val result = store.refresh()

            assertEquals(Result.success(7), result)
            assertEquals(7, store.unreadCount.value)
        }

    @Test
    fun `refresh with a failed repository response leaves the StateFlow value unchanged`() =
        runTest {
            // Seed the store at 5 from a prior successful tick so we can prove
            // that a later failure DOESN'T overwrite it with 0.
            val responses =
                ArrayDeque(
                    listOf(
                        Result.success(5),
                        Result.failure<Int>(RuntimeException("simulated network error")),
                    ),
                )
            val repository = FakeRepository(responses = responses)
            val store = NotificationsUnreadCountStore(repository = repository)
            store.refresh()
            assertEquals(5, store.unreadCount.value)

            val result = store.refresh()

            assertTrue(result.isFailure, "expected failure, got $result")
            assertEquals(5, store.unreadCount.value, "store must NOT reset to 0 on failure")
        }

    @Test
    fun `concurrent refresh calls collapse to a single in-flight request via Mutex tryLock`() =
        runTest {
            // The repository's first call suspends until the test releases the
            // gate. While suspended, a second refresh() call must short-circuit
            // (tryLock fails) and return immediately with the current cached
            // value. Without the single-flight guard, both calls would race to
            // hit the repository.
            val gate = CompletableDeferred<Int>()
            val repository = GatedRepository(firstCallGate = gate)
            val store = NotificationsUnreadCountStore(repository = repository)

            val firstCall = async { store.refresh() }
            runCurrent() // let firstCall enter the mutex + start the repository call

            val secondCall = async { store.refresh() }
            advanceUntilIdle() // drive the second call to completion (it should short-circuit)

            // The first call is still suspended on the gate; the second call
            // returned with the cached value (0, since the gate hasn't released
            // yet). Repository was hit exactly once.
            assertEquals(1, repository.callCount, "second refresh must skip the repository (single-flight)")
            assertEquals(Result.success(0), secondCall.await())

            // Release the gate; first call now completes and updates the store.
            gate.complete(42)
            assertEquals(Result.success(42), firstCall.await())
            assertEquals(42, store.unreadCount.value)
        }

    @Test
    fun `clear resets the StateFlow value to zero`() =
        runTest {
            val repository = FakeRepository(responses = ArrayDeque(listOf(Result.success(11))))
            val store = NotificationsUnreadCountStore(repository = repository)
            store.refresh()
            assertEquals(11, store.unreadCount.value)

            store.clear()

            assertEquals(0, store.unreadCount.value)
        }

    /**
     * Replays a queued sequence of [Result] responses from [unreadCount]; the
     * other [NotificationsRepository] methods are unused in the store tests
     * and throw `NotImplementedError`. Mirrors the inline fakes used in
     * `:core:push`'s coordinator tests.
     */
    private class FakeRepository(
        private val responses: ArrayDeque<Result<Int>> = ArrayDeque(),
    ) : NotificationsRepository {
        override suspend fun fetchPage(
            filter: NotificationFilter,
            cursor: String?,
        ): Result<NotificationsPage> = error("not used in NotificationsUnreadCountStoreTest")

        override suspend fun markSeen(seenAt: Instant): Result<Unit> = error("not used in NotificationsUnreadCountStoreTest")

        override suspend fun unreadCount(): Result<Int> = responses.removeFirst()
    }

    /**
     * Suspends the first [unreadCount] call until the test completes
     * [firstCallGate]; subsequent calls return immediately with `Result.success(0)`.
     * Counts every observed call. Used to assert the [NotificationsUnreadCountStore]'s
     * [kotlinx.coroutines.sync.Mutex.tryLock] short-circuit.
     */
    private class GatedRepository(
        private val firstCallGate: CompletableDeferred<Int>,
    ) : NotificationsRepository {
        var callCount: Int = 0
            private set

        override suspend fun fetchPage(
            filter: NotificationFilter,
            cursor: String?,
        ): Result<NotificationsPage> = error("not used in NotificationsUnreadCountStoreTest")

        override suspend fun markSeen(seenAt: Instant): Result<Unit> = error("not used in NotificationsUnreadCountStoreTest")

        override suspend fun unreadCount(): Result<Int> {
            callCount += 1
            return if (callCount == 1) {
                Result.success(firstCallGate.await())
            } else {
                Result.success(0)
            }
        }
    }
}
