package net.kikin.nubecita.core.feeds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [FeedViewPreferencesCoordinator] — the session-state →
 * `refresh()` driver for the viewer's feed-view preferences. Mirrors
 * `ModerationPreferencesCoordinatorTest`.
 *
 * The sign-out reset matters more here than for a normal cache: the repository
 * is an app-scoped singleton, so without it account B's Following feed would be
 * filtered with account A's preferences until B's own refresh lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedViewPreferencesCoordinatorTest {
    @Test
    fun `refreshes when the session is already signed in on start`() =
        runTest {
            val session = FakeSessionStateProvider(signedIn())
            val repo = RecordingFeedViewPreferencesRepository()
            newCoordinator(session, repo, backgroundScope).start()
            testScheduler.runCurrent()

            assertEquals(1, repo.refreshCount)
        }

    @Test
    fun `does not refresh while signed out`() =
        runTest {
            val session = FakeSessionStateProvider(SessionState.SignedOut)
            val repo = RecordingFeedViewPreferencesRepository()
            newCoordinator(session, repo, backgroundScope).start()
            testScheduler.runCurrent()

            assertEquals(0, repo.refreshCount)
        }

    @Test
    fun `refreshes on the transition into signed in`() =
        runTest {
            val session = FakeSessionStateProvider(SessionState.Loading)
            val repo = RecordingFeedViewPreferencesRepository()
            newCoordinator(session, repo, backgroundScope).start()
            testScheduler.runCurrent()
            assertEquals(0, repo.refreshCount)

            session.state.value = signedIn()
            testScheduler.runCurrent()

            assertEquals(1, repo.refreshCount)
        }

    @Test
    fun `resets to the defaults on sign-out so the next account cannot inherit them`() =
        runTest {
            val session = FakeSessionStateProvider(signedIn())
            val repo = RecordingFeedViewPreferencesRepository()
            newCoordinator(session, repo, backgroundScope).start()
            testScheduler.runCurrent()

            session.state.value = SessionState.SignedOut
            testScheduler.runCurrent()

            assertEquals(1, repo.resetCount)
        }

    @Test
    fun `a failing refresh is swallowed so the session collector survives`() =
        runTest {
            val session = FakeSessionStateProvider(signedIn())
            val repo = RecordingFeedViewPreferencesRepository(failRefresh = true)
            newCoordinator(session, repo, backgroundScope).start()
            testScheduler.runCurrent()

            // The collector must still be alive: a later sign-in retries.
            session.state.value = SessionState.SignedOut
            testScheduler.runCurrent()
            session.state.value = signedIn(did = "did:plc:bob")
            testScheduler.runCurrent()

            assertEquals(2, repo.refreshCount)
        }

    @Test
    fun `start is idempotent`() =
        runTest {
            val session = FakeSessionStateProvider(signedIn())
            val repo = RecordingFeedViewPreferencesRepository()
            val coordinator = newCoordinator(session, repo, backgroundScope)

            coordinator.start()
            coordinator.start()
            testScheduler.runCurrent()

            assertEquals(1, repo.refreshCount)
        }

    // ---------- helpers ----------

    private fun signedIn(did: String = "did:plc:alice") = SessionState.SignedIn(handle = "alice.test", did = did)

    private fun newCoordinator(
        session: SessionStateProvider,
        repo: FeedViewPreferencesRepository,
        scope: CoroutineScope,
    ) = FeedViewPreferencesCoordinator(session, repo, scope)

    private class FakeSessionStateProvider(
        initial: SessionState,
    ) : SessionStateProvider {
        override val state = MutableStateFlow(initial)

        override suspend fun refresh() = Unit
    }

    private class RecordingFeedViewPreferencesRepository(
        private val failRefresh: Boolean = false,
    ) : FeedViewPreferencesRepository {
        var refreshCount = 0
        var resetCount = 0

        private val _prefs = MutableStateFlow(FeedViewPrefs.DEFAULT)
        override val prefs: StateFlow<FeedViewPrefs> = _prefs.asStateFlow()

        override suspend fun refresh() {
            refreshCount++
            if (failRefresh) error("boom")
        }

        override fun resetToDefault() {
            resetCount++
            _prefs.value = FeedViewPrefs.DEFAULT
        }
    }
}
