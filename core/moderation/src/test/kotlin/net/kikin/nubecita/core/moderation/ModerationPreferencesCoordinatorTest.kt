package net.kikin.nubecita.core.moderation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ModerationPreferencesCoordinator]: the session-state →
 * `refresh()` driver. A fake [SessionStateProvider] feeds states; a counting
 * fake [ModerationPreferencesRepository] records each `refresh()`. The
 * coordinator collects on `runTest`'s `backgroundScope` so its never-ending
 * session collector doesn't block end-of-test join; `testScheduler.runCurrent()`
 * dispatches each emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ModerationPreferencesCoordinatorTest {
    @Test
    fun `refreshes when the session is already signed in on start`() =
        runTest {
            val session = FakeSessionStateProvider(SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice"))
            val repo = CountingModerationPreferencesRepository()
            val coordinator = newCoordinator(session, repo)

            coordinator.start()
            testScheduler.runCurrent()

            assertEquals(1, repo.refreshCount)
        }

    @Test
    fun `does not refresh while signed out`() =
        runTest {
            val session = FakeSessionStateProvider(SessionState.SignedOut)
            val repo = CountingModerationPreferencesRepository()
            val coordinator = newCoordinator(session, repo)

            coordinator.start()
            testScheduler.runCurrent()

            assertEquals(0, repo.refreshCount)
        }

    @Test
    fun `refreshes on the transition into signed in`() =
        runTest {
            val session = FakeSessionStateProvider(SessionState.Loading)
            val repo = CountingModerationPreferencesRepository()
            val coordinator = newCoordinator(session, repo)

            coordinator.start()
            testScheduler.runCurrent()
            assertEquals(0, repo.refreshCount)

            session.state.value = SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice")
            testScheduler.runCurrent()

            assertEquals(1, repo.refreshCount)
        }

    @Test
    fun `re-login against a new account refreshes again`() =
        runTest {
            val session = FakeSessionStateProvider(SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice"))
            val repo = CountingModerationPreferencesRepository()
            val coordinator = newCoordinator(session, repo)

            coordinator.start()
            testScheduler.runCurrent()
            session.state.value = SessionState.SignedOut
            testScheduler.runCurrent()
            session.state.value = SessionState.SignedIn(handle = "bob.test", did = "did:plc:bob")
            testScheduler.runCurrent()

            assertEquals(2, repo.refreshCount)
        }

    @Test
    fun `resets to default on sign-out so prefs do not leak across accounts`() =
        runTest {
            val session = FakeSessionStateProvider(SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice"))
            val repo = CountingModerationPreferencesRepository()
            val coordinator = newCoordinator(session, repo)

            coordinator.start()
            testScheduler.runCurrent()
            assertEquals(0, repo.resetCount)

            session.state.value = SessionState.SignedOut
            testScheduler.runCurrent()

            // Sign-out must clear account A's prefs back to DEFAULT before any
            // account B reads them.
            assertEquals(1, repo.resetCount)
            assertEquals(1, repo.refreshCount)
        }

    @Test
    fun `start is idempotent`() =
        runTest {
            val session = FakeSessionStateProvider(SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice"))
            val repo = CountingModerationPreferencesRepository()
            val coordinator = newCoordinator(session, repo)

            coordinator.start()
            coordinator.start()
            testScheduler.runCurrent()

            // The second start() is a no-op (collectJob already active), so the
            // single signed-in value drives exactly one refresh, not two.
            assertEquals(1, repo.refreshCount)
        }

    @Test
    fun `a refresh failure is swallowed and the next emission retries`() =
        runTest {
            val session = FakeSessionStateProvider(SessionState.Loading)
            val repo = CountingModerationPreferencesRepository(throwOnRefresh = true)
            val coordinator = newCoordinator(session, repo)

            coordinator.start()
            session.state.value = SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice")
            testScheduler.runCurrent()
            assertEquals(1, repo.refreshCount)

            // A failure must not tear down the collector — a later emission still
            // drives another attempt.
            repo.throwOnRefresh = false
            session.state.value = SessionState.SignedOut
            testScheduler.runCurrent()
            session.state.value = SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice")
            testScheduler.runCurrent()

            assertEquals(2, repo.refreshCount)
        }

    // Construct against runTest's `backgroundScope`: the coordinator's
    // `collectLatest` on the session StateFlow never completes, so launching it
    // in the test's own scope would hang runTest's end-of-test join. The
    // background scope is cancelled automatically when each test finishes.
    private fun TestScope.newCoordinator(
        session: SessionStateProvider,
        repo: ModerationPreferencesRepository,
    ): ModerationPreferencesCoordinator =
        ModerationPreferencesCoordinator(
            sessionStateProvider = session,
            repository = repo,
            scope = backgroundScope,
        )
}

private class FakeSessionStateProvider(
    initial: SessionState,
) : SessionStateProvider {
    override val state: MutableStateFlow<SessionState> = MutableStateFlow(initial)

    override suspend fun refresh() = Unit
}

private class CountingModerationPreferencesRepository(
    var throwOnRefresh: Boolean = false,
) : ModerationPreferencesRepository {
    var refreshCount = 0
        private set
    var resetCount = 0
        private set

    override val prefs: StateFlow<ModerationPrefs> = MutableStateFlow(ModerationPrefs.DEFAULT)

    override suspend fun refresh() {
        refreshCount++
        if (throwOnRefresh) error("boom")
    }

    override fun resetToDefault() {
        resetCount++
    }

    override suspend fun setAdultContentEnabled(enabled: Boolean) = Unit

    override suspend fun setVisibility(
        label: ContentLabel,
        visibility: LabelVisibility,
    ) = Unit
}
