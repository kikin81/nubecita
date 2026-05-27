package net.kikin.nubecita.feature.notifications.impl.store

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsPage
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsPollingObserverTest {
    // repeatOnLifecycle internally uses Dispatchers.Main.immediate; without
    // a TestDispatcher installed, the call throws "Dispatchers.Main was
    // accessed when the platform dispatcher was absent" and every test
    // assertion fails because the polling loop never runs. We install an
    // UnconfinedTestDispatcher so lifecycle-event dispatches and our scope's
    // coroutines share the same virtual clock as runTest.
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

    @Test
    fun `start is idempotent - second call does not double-launch the polling loop`() =
        runPollingTest { fixture ->
            // Two start() calls must NOT produce two parallel polling jobs.
            // Without the AtomicBoolean guard, every call launched a fresh
            // pair of coroutines (one repeatOnLifecycle + one session-state
            // collector), which would race on store.clear() and double the
            // network budget per tick.
            fixture.observer.start()
            fixture.observer.start()
            runCurrent()
            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()

            // Exactly ONE refresh fires on STARTED. Two parallel loops would
            // both fire on STARTED, producing two calls.
            assertEquals(
                1,
                fixture.repository.unreadCountCalls.size,
                "second start() must short-circuit; saw two refresh calls so two loops launched",
            )

            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(
                2,
                fixture.repository.unreadCountCalls.size,
                "60s tick produces exactly one refresh, not two",
            )
        }

    @Test
    fun `polling fires an immediate refresh on lifecycle reaching STARTED`() =
        runPollingTest { fixture ->
            fixture.observer.start()
            runCurrent() // observer registers + waits for STARTED

            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()

            assertEquals(1, fixture.repository.unreadCountCalls.size, "first refresh fires immediately on STARTED")
        }

    @Test
    fun `polling fires on the 60-second cadence while foregrounded`() =
        runPollingTest { fixture ->
            fixture.observer.start()
            runCurrent()
            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(1, fixture.repository.unreadCountCalls.size)

            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(2, fixture.repository.unreadCountCalls.size, "second poll at t=60s")

            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(3, fixture.repository.unreadCountCalls.size, "third poll at t=120s")
        }

    @Test
    fun `polling stops when the lifecycle drops below STARTED`() =
        runPollingTest { fixture ->
            fixture.observer.start()
            runCurrent()
            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(1, fixture.repository.unreadCountCalls.size)

            // Background the app: lifecycle drops to CREATED (below STARTED).
            // repeatOnLifecycle(STARTED) cancels the inner coroutine; no further
            // polls should fire even after virtual time advances past the 60s
            // boundary.
            fixture.lifecycleRegistry.currentState = Lifecycle.State.CREATED
            advanceTimeBy(120_000L)
            runCurrent()

            assertEquals(1, fixture.repository.unreadCountCalls.size, "no polls fire while backgrounded")
        }

    @Test
    fun `polling resumes when the lifecycle returns to STARTED`() =
        runPollingTest { fixture ->
            fixture.observer.start()
            runCurrent()
            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(1, fixture.repository.unreadCountCalls.size)

            fixture.lifecycleRegistry.currentState = Lifecycle.State.CREATED
            runCurrent()
            // While backgrounded.
            advanceTimeBy(120_000L)
            runCurrent()

            // Return to foreground — repeatOnLifecycle re-runs the block from
            // scratch, so an immediate poll fires before the next 60s delay.
            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()

            assertEquals(2, fixture.repository.unreadCountCalls.size, "polling resumes with an immediate refresh on re-foreground")
        }

    @Test
    fun `consecutive failures back off 60s 120s 240s 300s then cap`() =
        runPollingTest { fixture ->
            // Every unreadCount call fails — drives the full backoff schedule.
            fixture.repository.unreadCountFailures = Int.MAX_VALUE

            fixture.observer.start()
            runCurrent()
            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()

            // First poll at t=0 (immediate on STARTED): fails, backs off to 120s.
            assertEquals(1, fixture.repository.unreadCountCalls.size)

            advanceTimeBy(120_000L)
            runCurrent()
            // Second poll at t=120s: fails, backs off to 240s.
            assertEquals(2, fixture.repository.unreadCountCalls.size)

            advanceTimeBy(240_000L)
            runCurrent()
            // Third poll at t=120 + 240 = 360s: fails, backs off to 300s (cap kicks in: min(480, 300) = 300).
            assertEquals(3, fixture.repository.unreadCountCalls.size)

            advanceTimeBy(300_000L)
            runCurrent()
            // Fourth poll: cap holds at 300s.
            assertEquals(4, fixture.repository.unreadCountCalls.size)

            advanceTimeBy(300_000L)
            runCurrent()
            assertEquals(5, fixture.repository.unreadCountCalls.size, "backoff stays capped at 300s on subsequent failures")
        }

    @Test
    fun `backoff resets to 60s after a successful poll`() =
        runPollingTest { fixture ->
            // First two calls fail (drive backoff to 240s), third succeeds (resets),
            // fourth ticks back at the 60s cadence.
            fixture.repository.unreadCountFailures = 2

            fixture.observer.start()
            runCurrent()
            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(1, fixture.repository.unreadCountCalls.size)

            advanceTimeBy(120_000L)
            runCurrent()
            assertEquals(2, fixture.repository.unreadCountCalls.size)

            advanceTimeBy(240_000L)
            runCurrent()
            // Third call succeeds.
            assertEquals(3, fixture.repository.unreadCountCalls.size)

            // After success, the next delay should be back at 60s — NOT 480s
            // (continued backoff) and NOT 300s (cap).
            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(4, fixture.repository.unreadCountCalls.size, "delay resets to 60s after a successful tick")
        }

    @Test
    fun `successful poll updates the store StateFlow`() =
        runPollingTest { fixture ->
            fixture.repository.unreadCountResponse = 13

            fixture.observer.start()
            runCurrent()
            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()

            assertEquals(13, fixture.store.unreadCount.value)
        }

    @Test
    fun `SignedOut session-state transition clears the store`() =
        runPollingTest(
            sessionFlow = MutableStateFlow(SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice")),
        ) { fixture ->
            // Seed the store at a non-zero value so we can prove the clear hits.
            fixture.repository.unreadCountResponse = 17

            fixture.observer.start()
            runCurrent()
            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(17, fixture.store.unreadCount.value, "sanity: store seeded to 17 after first poll")

            // User logs out. With UnconfinedTestDispatcher the StateFlow
            // emission propagates synchronously to the session-state collector,
            // so a runCurrent() is enough — advanceUntilIdle would also drive
            // the never-terminating polling loop forever (heap OOM).
            (fixture.sessionStateProvider.state as MutableStateFlow).value = SessionState.SignedOut
            runCurrent()

            assertEquals(0, fixture.store.unreadCount.value, "store cleared on SignedOut")
        }

    @Test
    fun `Loading and SignedIn session states do not clear the store`() =
        runPollingTest(sessionFlow = MutableStateFlow(SessionState.Loading)) { fixture ->
            fixture.repository.unreadCountResponse = 5

            fixture.observer.start()
            runCurrent()
            fixture.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()

            (fixture.sessionStateProvider.state as MutableStateFlow).value =
                SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice")
            runCurrent()

            assertEquals(5, fixture.store.unreadCount.value, "store retains its polled value through Loading/SignedIn")
        }

    /**
     * Helper that runs [block] inside a [runTest] tied to the
     * [MainDispatcherExtension]'s dispatcher, with a freshly-built [Fixture],
     * and cancels the fixture's coroutine scope on exit so `runTest`'s auto-
     * drain doesn't hang on the observer's never-terminating
     * `repeatOnLifecycle` and `SessionStateProvider.state.collect` loops.
     */
    private fun runPollingTest(
        sessionFlow: MutableStateFlow<SessionState> =
            MutableStateFlow(SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice")),
        block: suspend TestScope.(Fixture) -> Unit,
    ) = runTest(mainDispatcherExtension.dispatcher) {
        val fixture = newFixture(sessionFlow)
        try {
            block(fixture)
        } finally {
            fixture.scope.cancel()
        }
    }

    private data class Fixture(
        val observer: NotificationsPollingObserver,
        val store: NotificationsUnreadCountStore,
        val repository: FakeNotificationsRepository,
        val sessionStateProvider: SessionStateProvider,
        val lifecycleRegistry: LifecycleRegistry,
        // The LifecycleOwner held internally by LifecycleRegistry is a
        // WeakReference; without a strong ref pinned on the Fixture, GC
        // collects the owner mid-test and setCurrentState throws
        // "LifecycleOwner ... already garbage collected".
        val owner: LifecycleOwner,
        // Held so the harness can cancel() it before runTest's auto-drain
        // tries to advance forever on the StateFlow collect / repeatOnLifecycle
        // loop. Without this, runTest hangs waiting for an unending scheduler.
        val scope: CoroutineScope,
    )

    private fun newFixture(sessionFlow: MutableStateFlow<SessionState>): Fixture {
        // Reuse the extension's dispatcher so Dispatchers.Main, the lifecycle's
        // event-dispatch, and the observer's scope all share one scheduler —
        // without this, repeatOnLifecycle's Main.immediate emission would race
        // the observer's coroutine and assertions would see stale state.
        val dispatcher = mainDispatcherExtension.dispatcher
        val repository = FakeNotificationsRepository()
        val store = NotificationsUnreadCountStore(repository = repository)
        val sessionStateProvider = FakeSessionStateProvider(sessionFlow)
        // createUnsafe skips the main-thread checker. This is the canonical
        // path for unit tests that need to drive a Lifecycle synchronously
        // without TestLifecycleOwner's runBlocking-on-setCurrentState detour
        // (which deadlocks against a virtual TestDispatcher).
        val owner = FakeLifecycleOwner()
        val registry = LifecycleRegistry.createUnsafe(owner).also { owner.registry = it }
        val scope = CoroutineScope(dispatcher + Job())
        val observer =
            NotificationsPollingObserver(
                store = store,
                sessionStateProvider = sessionStateProvider,
                scope = scope,
                lifecycle = registry,
            )
        return Fixture(observer, store, repository, sessionStateProvider, registry, owner, scope)
    }

    /**
     * Trivial LifecycleOwner that delegates to a late-bound [LifecycleRegistry]
     * created from itself. Pulled out as a top-level class so [Fixture] can
     * hold a strong reference to it (preventing the WeakReference inside
     * LifecycleRegistry from being collected mid-test).
     */
    private class FakeLifecycleOwner : LifecycleOwner {
        lateinit var registry: LifecycleRegistry

        override val lifecycle: Lifecycle
            get() = registry
    }

    private class FakeSessionStateProvider(
        override val state: MutableStateFlow<SessionState>,
    ) : SessionStateProvider {
        override suspend fun refresh() {
            // no-op: tests drive the StateFlow directly
        }
    }

    private class FakeNotificationsRepository : NotificationsRepository {
        val unreadCountCalls: MutableList<Unit> = mutableListOf()
        var unreadCountResponse: Int = 0
        var unreadCountFailures: Int = 0

        override suspend fun fetchPage(
            filter: NotificationFilter,
            cursor: String?,
        ): Result<NotificationsPage> = error("not used in NotificationsPollingObserverTest")

        override suspend fun markSeen(seenAt: Instant): Result<Unit> = error("not used in NotificationsPollingObserverTest")

        override suspend fun unreadCount(): Result<Int> {
            unreadCountCalls += Unit
            return if (unreadCountFailures > 0) {
                unreadCountFailures--
                Result.failure(RuntimeException("simulated unreadCount failure"))
            } else {
                Result.success(unreadCountResponse)
            }
        }
    }
}
