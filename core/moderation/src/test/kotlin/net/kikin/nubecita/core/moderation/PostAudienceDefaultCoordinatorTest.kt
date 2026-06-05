package net.kikin.nubecita.core.moderation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.posting.PostAudience
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PostAudienceDefaultCoordinator] — the session-state →
 * `refresh()` / `resetToDefault()` driver. Mirrors [ModerationPreferencesCoordinatorTest]:
 * a fake [SessionStateProvider] feeds states, a counting fake repository records
 * each call, the coordinator runs on `backgroundScope`, and
 * `testScheduler.runCurrent()` dispatches each emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PostAudienceDefaultCoordinatorTest {
    @Test
    fun `refreshes when already signed in on start`() =
        runTest {
            val session = AudienceFakeSessionStateProvider(SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice"))
            val repo = CountingPostAudienceDefaultRepository()
            newCoordinator(session, repo).start()
            testScheduler.runCurrent()
            assertEquals(1, repo.refreshCount)
        }

    @Test
    fun `does not refresh while signed out`() =
        runTest {
            val session = AudienceFakeSessionStateProvider(SessionState.SignedOut)
            val repo = CountingPostAudienceDefaultRepository()
            newCoordinator(session, repo).start()
            testScheduler.runCurrent()
            assertEquals(0, repo.refreshCount)
        }

    @Test
    fun `refreshes on the transition into signed in`() =
        runTest {
            val session = AudienceFakeSessionStateProvider(SessionState.Loading)
            val repo = CountingPostAudienceDefaultRepository()
            newCoordinator(session, repo).start()
            testScheduler.runCurrent()
            assertEquals(0, repo.refreshCount)

            session.state.value = SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice")
            testScheduler.runCurrent()
            assertEquals(1, repo.refreshCount)
        }

    @Test
    fun `resets to default on sign-out so the default does not leak across accounts`() =
        runTest {
            val session = AudienceFakeSessionStateProvider(SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice"))
            val repo = CountingPostAudienceDefaultRepository()
            newCoordinator(session, repo).start()
            testScheduler.runCurrent()
            assertEquals(0, repo.resetCount)

            session.state.value = SessionState.SignedOut
            testScheduler.runCurrent()
            assertEquals(1, repo.resetCount)
        }

    @Test
    fun `start is idempotent`() =
        runTest {
            val session = AudienceFakeSessionStateProvider(SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice"))
            val repo = CountingPostAudienceDefaultRepository()
            val coordinator = newCoordinator(session, repo)
            coordinator.start()
            coordinator.start()
            testScheduler.runCurrent()
            assertEquals(1, repo.refreshCount)
        }

    @Test
    fun `a refresh failure is swallowed and the next emission retries`() =
        runTest {
            val session = AudienceFakeSessionStateProvider(SessionState.Loading)
            val repo = CountingPostAudienceDefaultRepository(throwOnRefresh = true)
            newCoordinator(session, repo).start()
            session.state.value = SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice")
            testScheduler.runCurrent()
            assertEquals(1, repo.refreshCount)

            repo.throwOnRefresh = false
            session.state.value = SessionState.SignedOut
            testScheduler.runCurrent()
            session.state.value = SessionState.SignedIn(handle = "alice.test", did = "did:plc:alice")
            testScheduler.runCurrent()
            assertEquals(2, repo.refreshCount)
        }

    private fun TestScope.newCoordinator(
        session: SessionStateProvider,
        repo: PostAudienceDefaultRepository,
    ): PostAudienceDefaultCoordinator =
        PostAudienceDefaultCoordinator(
            sessionStateProvider = session,
            repository = repo,
            scope = backgroundScope,
        )
}

private class AudienceFakeSessionStateProvider(
    initial: SessionState,
) : SessionStateProvider {
    override val state: MutableStateFlow<SessionState> = MutableStateFlow(initial)

    override suspend fun refresh() = Unit
}

private class CountingPostAudienceDefaultRepository(
    var throwOnRefresh: Boolean = false,
) : PostAudienceDefaultRepository {
    var refreshCount = 0
        private set
    var resetCount = 0
        private set

    override val default: StateFlow<PostAudience> = MutableStateFlow(PostAudience.DEFAULT)

    override suspend fun refresh() {
        refreshCount++
        if (throwOnRefresh) error("boom")
    }

    override fun resetToDefault() {
        resetCount++
    }

    override suspend fun setDefault(audience: PostAudience) = Unit
}
