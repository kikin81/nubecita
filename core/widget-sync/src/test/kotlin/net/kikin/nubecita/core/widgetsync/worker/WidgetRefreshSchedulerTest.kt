package net.kikin.nubecita.core.widgetsync.worker

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class WidgetRefreshSchedulerTest {
    private val signedIn = SessionState.SignedIn(handle = "me.bsky.social", did = "did:plc:viewer")

    @Test
    fun `schedules when signed in`() =
        runTest {
            val f = scheduler(session = signedIn)
            f.scheduler.start()
            runCurrent()
            assertEquals(1, f.work.scheduled)
            assertEquals(0, f.work.cancelled)
        }

    @Test
    fun `cancels when signed out`() =
        runTest {
            val f = scheduler(session = SessionState.SignedOut)
            f.scheduler.start()
            runCurrent()
            assertEquals(0, f.work.scheduled)
            assertEquals(1, f.work.cancelled)
        }

    @Test
    fun `schedules on sign-in and cancels on logout`() =
        runTest {
            val session = MutableStateFlow<SessionState>(SessionState.SignedOut)
            val f = scheduler(sessionFlow = session)
            f.scheduler.start()
            runCurrent()
            assertEquals(0, f.work.scheduled)
            assertEquals(1, f.work.cancelled)

            session.value = signedIn
            runCurrent()
            assertEquals(1, f.work.scheduled)

            session.value = SessionState.SignedOut
            runCurrent()
            assertEquals(2, f.work.cancelled)
        }

    @Test
    fun `distinct-until-changed avoids redundant scheduling`() =
        runTest {
            val session = MutableStateFlow<SessionState>(signedIn)
            val f = scheduler(sessionFlow = session)
            f.scheduler.start()
            runCurrent()
            // A different signed-in session (e.g. token refresh) must not re-enqueue.
            session.value = SessionState.SignedIn(handle = "me.bsky.social", did = "did:plc:viewer")
            runCurrent()
            assertEquals(1, f.work.scheduled)
        }

    @Test
    fun `start is idempotent`() =
        runTest {
            val f = scheduler(session = signedIn)
            f.scheduler.start()
            f.scheduler.start()
            runCurrent()
            assertEquals(1, f.work.scheduled)
        }

    private class Fixture(
        val scheduler: WidgetRefreshScheduler,
        val work: FakeWorkScheduler,
    )

    private fun TestScope.scheduler(
        session: SessionState = SessionState.SignedOut,
        sessionFlow: MutableStateFlow<SessionState> = MutableStateFlow(session),
    ): Fixture {
        val work = FakeWorkScheduler()
        return Fixture(
            scheduler =
                WidgetRefreshScheduler(
                    scope = backgroundScope,
                    sessionStateProvider = FakeSessionStateProvider(sessionFlow),
                    scheduler = work,
                ),
            work = work,
        )
    }

    private class FakeWorkScheduler : WidgetWorkScheduler {
        var scheduled = 0
        var cancelled = 0
        var refreshedNow = 0

        override suspend fun ensureScheduled() {
            scheduled++
        }

        override suspend fun refreshNow() {
            refreshedNow++
        }

        override suspend fun cancel() {
            cancelled++
        }
    }

    private class FakeSessionStateProvider(
        override val state: MutableStateFlow<SessionState>,
    ) : SessionStateProvider {
        override suspend fun refresh() = Unit
    }
}
