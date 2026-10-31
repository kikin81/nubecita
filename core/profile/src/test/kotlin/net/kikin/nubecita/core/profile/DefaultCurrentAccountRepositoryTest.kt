package net.kikin.nubecita.core.profile

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultCurrentAccountRepositoryTest {
    private val did = "did:plc:alice"
    private val session = MutableStateFlow<SessionState>(SessionState.Loading)
    private val sessionStateProvider = mockk<SessionStateProvider> { every { state } returns session }
    private val actorProfileRepository = mockk<ActorProfileRepository>()

    @Test
    fun `emits a handle-only seed before the profile loads`() =
        runTest {
            val pending = CompletableDeferred<Result<ActorProfile>>()
            coEvery { actorProfileRepository.fetchProfile(did) } coAnswers { pending.await() }
            val repo = repository()

            session.value = SessionState.SignedIn(handle = "alice.bsky.social", did = did)
            runCurrent()

            assertEquals(
                CurrentAccount(did = did, handle = "alice.bsky.social", displayName = "alice.bsky.social", avatarUrl = null),
                repo.account.value,
            )
        }

    @Test
    fun `enriches the account once the profile loads`() =
        runTest {
            coEvery { actorProfileRepository.fetchProfile(did) } returns
                Result.success(ActorProfile(did = did, handle = "alice.bsky.social", displayName = "Alice", avatarUrl = "https://cdn/a"))
            val repo = repository()

            session.value = SessionState.SignedIn(handle = "alice.bsky.social", did = did)
            runCurrent()

            assertEquals(
                CurrentAccount(did = did, handle = "alice.bsky.social", displayName = "Alice", avatarUrl = "https://cdn/a"),
                repo.account.value,
            )
        }

    @Test
    fun `falls back to the handle when the profile has no display name`() =
        runTest {
            coEvery { actorProfileRepository.fetchProfile(did) } returns
                Result.success(ActorProfile(did = did, handle = "alice.bsky.social", displayName = null, avatarUrl = "https://cdn/a"))
            val repo = repository()

            session.value = SessionState.SignedIn(handle = "alice.bsky.social", did = did)
            runCurrent()

            assertEquals("alice.bsky.social", repo.account.value?.displayName)
            assertEquals("https://cdn/a", repo.account.value?.avatarUrl)
        }

    @Test
    fun `keeps the seed when the profile fetch fails`() =
        runTest {
            coEvery { actorProfileRepository.fetchProfile(did) } returns Result.failure(IOException("offline"))
            val repo = repository()

            session.value = SessionState.SignedIn(handle = "alice.bsky.social", did = did)
            runCurrent()

            assertEquals(
                CurrentAccount(did = did, handle = "alice.bsky.social", displayName = "alice.bsky.social", avatarUrl = null),
                repo.account.value,
            )
        }

    @Test
    fun `fetches once per session, even when the session re-emits`() =
        runTest {
            coEvery { actorProfileRepository.fetchProfile(did) } returns
                Result.success(ActorProfile(did = did, handle = "alice.bsky.social", displayName = "Alice", avatarUrl = null))
            repository()

            session.value = SessionState.SignedIn(handle = "alice.bsky.social", did = did)
            runCurrent()
            // A token refresh re-emits the same signed-in account with other details.
            session.value = SessionState.SignedIn(handle = "alice.bsky.social", did = did, pdsUrl = "https://pds.example")
            runCurrent()

            coVerify(exactly = 1) { actorProfileRepository.fetchProfile(did) }
        }

    @Test
    fun `clears the account on sign-out`() =
        runTest {
            coEvery { actorProfileRepository.fetchProfile(did) } returns
                Result.success(ActorProfile(did = did, handle = "alice.bsky.social", displayName = "Alice", avatarUrl = null))
            val repo = repository()
            session.value = SessionState.SignedIn(handle = "alice.bsky.social", did = did)
            runCurrent()

            session.value = SessionState.SignedOut
            runCurrent()

            assertNull(repo.account.value)
        }

    private fun TestScope.repository() =
        DefaultCurrentAccountRepository(
            sessionStateProvider = sessionStateProvider,
            actorProfileRepository = actorProfileRepository,
            scope = backgroundScope,
        )
}
