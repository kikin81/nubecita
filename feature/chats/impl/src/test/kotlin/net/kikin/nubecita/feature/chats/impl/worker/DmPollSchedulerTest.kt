package net.kikin.nubecita.feature.chats.impl.worker

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.preferences.MessageCheckingPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class DmPollSchedulerTest {
    private val signedIn = SessionState.SignedIn(handle = "me.bsky.social", did = "did:plc:viewer")

    @Test
    fun `schedules when signed in and enabled`() =
        runTest {
            val f = scheduler(session = signedIn, enabled = true)
            f.scheduler.start()
            runCurrent()
            assertEquals(1, f.work.scheduled)
            assertEquals(0, f.work.cancelled)
        }

    @Test
    fun `cancels when signed in but message-checking is off`() =
        runTest {
            val f = scheduler(session = signedIn, enabled = false)
            f.scheduler.start()
            runCurrent()
            assertEquals(0, f.work.scheduled)
            assertEquals(1, f.work.cancelled)
        }

    @Test
    fun `cancels when signed out`() =
        runTest {
            val f = scheduler(session = SessionState.SignedOut, enabled = true)
            f.scheduler.start()
            runCurrent()
            assertEquals(0, f.work.scheduled)
            assertEquals(1, f.work.cancelled)
        }

    @Test
    fun `reschedules on opt-in and cancels on opt-out`() =
        runTest {
            val enabled = MutableStateFlow(false)
            val f = scheduler(session = signedIn, enabledFlow = enabled)
            f.scheduler.start()
            runCurrent()
            assertEquals(0, f.work.scheduled)
            assertEquals(1, f.work.cancelled)

            enabled.value = true
            runCurrent()
            assertEquals(1, f.work.scheduled)

            enabled.value = false
            runCurrent()
            assertEquals(2, f.work.cancelled)
        }

    @Test
    fun `cancels once on logout`() =
        runTest {
            val session = MutableStateFlow<SessionState>(signedIn)
            val f = scheduler(sessionFlow = session, enabled = true)
            f.scheduler.start()
            runCurrent()
            assertEquals(1, f.work.scheduled)

            session.value = SessionState.SignedOut
            runCurrent()
            assertEquals(1, f.work.cancelled)
        }

    @Test
    fun `transient Loading is a no-op, then sign-in schedules without churn`() =
        runTest {
            val session = MutableStateFlow<SessionState>(SessionState.Loading)
            val f = scheduler(sessionFlow = session, enabled = true)
            f.scheduler.start()
            runCurrent()
            // Loading (the pre-restore state on every launch) must NOT cancel —
            // cancelling here churns the unique periodic work (nubecita-1fy.20).
            assertEquals(0, f.work.scheduled)
            assertEquals(0, f.work.cancelled)

            session.value = signedIn
            runCurrent()
            // Resolving to signed-in schedules, with no spurious cancel first.
            assertEquals(1, f.work.scheduled)
            assertEquals(0, f.work.cancelled)
        }

    @Test
    fun `message-checking off cancels even while the session is still Loading`() =
        runTest {
            val f = scheduler(session = SessionState.Loading, enabled = false)
            f.scheduler.start()
            runCurrent()
            assertEquals(0, f.work.scheduled)
            assertEquals(1, f.work.cancelled)
        }

    @Test
    fun `distinct-until-changed avoids redundant scheduling`() =
        runTest {
            val session = MutableStateFlow<SessionState>(signedIn)
            val f = scheduler(sessionFlow = session, enabled = true)
            f.scheduler.start()
            runCurrent()
            // A session emission that doesn't change the (signedIn ∧ enabled) verdict
            // must not re-enqueue.
            session.value = SessionState.SignedIn(handle = "me.bsky.social", did = "did:plc:viewer")
            runCurrent()
            assertEquals(1, f.work.scheduled)
        }

    @Test
    fun `transient Loading does NOT break the distinct-until-changed chain`() =
        runTest {
            val session = MutableStateFlow<SessionState>(signedIn)
            val f = scheduler(sessionFlow = session, enabled = true)
            f.scheduler.start()
            runCurrent()
            assertEquals(1, f.work.scheduled)

            // Emitting Loading (transiently during app restart)
            session.value = SessionState.Loading
            runCurrent()
            assertEquals(1, f.work.scheduled)
            assertEquals(0, f.work.cancelled)

            // Resolving back to SignedIn
            session.value = signedIn
            runCurrent()
            // Loading (which maps to Decision.IGNORE) is filtered out before
            // distinctUntilChanged, so the chain (SCHEDULE -> SCHEDULE) collapses
            // to a single emission. The timer is NOT reset.
            assertEquals(1, f.work.scheduled)
        }

    private class Fixture(
        val scheduler: DmPollScheduler,
        val work: FakeWorkScheduler,
    )

    private fun TestScope.scheduler(
        session: SessionState = SessionState.SignedOut,
        sessionFlow: MutableStateFlow<SessionState> = MutableStateFlow(session),
        enabled: Boolean = true,
        enabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(enabled),
    ): Fixture {
        val work = FakeWorkScheduler()
        return Fixture(
            scheduler =
                DmPollScheduler(
                    scope = backgroundScope,
                    sessionStateProvider = FakeSessionStateProvider(sessionFlow),
                    messageChecking = FakeMessageChecking(enabledFlow),
                    scheduler = work,
                ),
            work = work,
        )
    }

    private class FakeWorkScheduler : DmWorkScheduler {
        var scheduled = 0
        var cancelled = 0

        override suspend fun ensureScheduled() {
            scheduled++
        }

        override suspend fun cancel() {
            cancelled++
        }
    }

    private class FakeMessageChecking(
        override val enabled: MutableStateFlow<Boolean>,
    ) : MessageCheckingPreference {
        override suspend fun setEnabled(enabled: Boolean) {
            this.enabled.value = enabled
        }
    }

    private class FakeSessionStateProvider(
        override val state: MutableStateFlow<SessionState>,
    ) : SessionStateProvider {
        override suspend fun refresh() = Unit
    }
}
