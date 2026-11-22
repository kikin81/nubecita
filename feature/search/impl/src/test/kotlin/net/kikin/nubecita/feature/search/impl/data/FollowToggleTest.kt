package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.feature.search.impl.FakeFollowRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FollowToggleTest {
    private val provider =
        object : SessionStateProvider {
            override val state = MutableStateFlow<SessionState>(signedIn("did:plc:me"))

            override suspend fun refresh() = Unit
        }

    private fun kotlinx.coroutines.test.TestScope.newSut(repo: FakeFollowRepository): Pair<FollowToggle, FollowOverlay> {
        val overlay = FollowOverlay(TestScope(StandardTestDispatcher(testScheduler)), provider)
        return FollowToggle(repo, overlay) to overlay
    }

    private fun kotlinx.coroutines.test.TestScope.tapScope(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler))

    @Test
    fun `a follow writes the optimistic override then confirms it with the record uri`() =
        runTest {
            val repo = FakeFollowRepository(followResult = Result.success("at://follow/new"))
            val (toggle, overlay) = newSut(repo)
            val outcomes = mutableListOf<FollowOutcome>()

            val started = toggle.toggle(tapScope(), "did:plc:a", isFollowing = false, followUri = null) { outcomes += it }

            assertTrue(started)
            assertEquals(FollowOverride(true, null), overlay["did:plc:a"])
            runCurrent()
            assertEquals(FollowOverride(true, "at://follow/new"), overlay["did:plc:a"])
            assertEquals(listOf(FollowOutcome.Followed), outcomes)
        }

    @Test
    fun `a failed follow without a previous override drops the override`() =
        runTest {
            val repo = FakeFollowRepository(followResult = Result.failure(java.io.IOException("net")))
            val (toggle, overlay) = newSut(repo)
            val outcomes = mutableListOf<FollowOutcome>()

            toggle.toggle(tapScope(), "did:plc:a", isFollowing = false, followUri = null) { outcomes += it }
            runCurrent()

            assertNull(overlay["did:plc:a"])
            assertEquals(listOf(FollowOutcome.FollowFailed), outcomes)
        }

    @Test
    fun `a failed follow restores the previous override`() =
        runTest {
            // The account was followed earlier in this session; the unfollow now fails.
            val repo = FakeFollowRepository(unfollowResult = Result.failure(java.io.IOException("net")))
            val (toggle, overlay) = newSut(repo)
            overlay.set("did:plc:a", FollowOverride(true, "at://follow/original"))
            val outcomes = mutableListOf<FollowOutcome>()

            toggle.toggle(tapScope(), "did:plc:a", isFollowing = true, followUri = "at://follow/original") { outcomes += it }
            assertEquals(FollowOverride(false, null), overlay["did:plc:a"], "optimistic unfollow")
            runCurrent()

            assertEquals(FollowOverride(true, "at://follow/original"), overlay["did:plc:a"])
            assertEquals(listOf(FollowOutcome.UnfollowFailed), outcomes)
            assertEquals(listOf("at://follow/original"), repo.unfollowCallUris)
        }

    @Test
    fun `an unfollow keeps the override not following`() =
        runTest {
            val repo = FakeFollowRepository()
            val (toggle, overlay) = newSut(repo)
            val outcomes = mutableListOf<FollowOutcome>()

            toggle.toggle(tapScope(), "did:plc:a", isFollowing = true, followUri = "at://follow/1") { outcomes += it }
            runCurrent()

            assertEquals(FollowOverride(false, null), overlay["did:plc:a"])
            assertEquals(listOf(FollowOutcome.Unfollowed), outcomes)
        }

    @Test
    fun `a second tap on the same account while one is in flight is dropped`() =
        runTest {
            val repo = FakeFollowRepository()
            val (toggle, _) = newSut(repo)
            val scope = tapScope()

            assertTrue(toggle.toggle(scope, "did:plc:a", isFollowing = false, followUri = null) {})
            assertFalse(toggle.toggle(scope, "did:plc:a", isFollowing = false, followUri = null) {})
            runCurrent()

            assertEquals(listOf("did:plc:a"), repo.followCallDids)
        }

    @Test
    fun `an unfollow without a follow record uri does nothing`() =
        runTest {
            val repo = FakeFollowRepository()
            val (toggle, overlay) = newSut(repo)

            val started = toggle.toggle(tapScope(), "did:plc:a", isFollowing = true, followUri = null) {}
            runCurrent()

            assertFalse(started)
            assertNull(overlay["did:plc:a"])
            assertTrue(repo.unfollowCallUris.isEmpty())
        }
}
